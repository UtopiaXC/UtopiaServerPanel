package com.utopiaxc.utopiaserverpanel.web.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.utopiaxc.utopiaserverpanel.auth.JwtClaims;
import com.utopiaxc.utopiaserverpanel.auth.JwtUtil;
import com.utopiaxc.utopiaserverpanel.auth.PasswordUtil;
import com.utopiaxc.utopiaserverpanel.auth.WebAuthnUtil;
import com.utopiaxc.utopiaserverpanel.logging.PanelLog;
import com.utopiaxc.utopiaserverpanel.web.context.JsonHelper;
import com.utopiaxc.utopiaserverpanel.web.db.MyBatisFactory;
import com.utopiaxc.utopiaserverpanel.web.db.mapper.PasskeyMapper;
import com.utopiaxc.utopiaserverpanel.web.db.mapper.UserMapper;
import com.utopiaxc.utopiaserverpanel.web.db.model.UserAccount;
import com.utopiaxc.utopiaserverpanel.web.db.model.UserPasskey;

import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebAuthn Passkey registration, passwordless login, post-login 2FA verification, and credential management.
 */
public final class PasskeyService {

    private static final int CHALLENGE_TTL_SECONDS = 300;
    private static final int MAX_CHALLENGES = 4096;
    private static final int MAX_PASSKEYS_PER_USER = 20;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Map<String, ChallengeEntry> CHALLENGES = new ConcurrentHashMap<>();

    private PasskeyService() {}

    private static final class ChallengeEntry {
        final String purpose;
        final Integer userId;
        final String rpId;
        final byte[] challenge;
        final boolean rememberMe;
        final long expiresAt;

        ChallengeEntry(String purpose, Integer userId, String rpId, byte[] challenge, boolean rememberMe, long expiresAt) {
            this.purpose = purpose;
            this.userId = userId;
            this.rpId = rpId;
            this.challenge = challenge;
            this.rememberMe = rememberMe;
            this.expiresAt = expiresAt;
        }
    }

    public static final class ServiceResult {
        private final boolean success;
        private final String errorCode;
        private final String message;
        private final int httpStatus;
        private final Map<String, Object> data;

        private ServiceResult(boolean success, String errorCode, String message, int httpStatus, Map<String, Object> data) {
            this.success = success;
            this.errorCode = errorCode;
            this.message = message;
            this.httpStatus = httpStatus;
            this.data = data;
        }

        public static ServiceResult ok(Map<String, Object> data) {
            return new ServiceResult(true, null, "OK", 200, data);
        }

        public static ServiceResult error(int status, String code, String message) {
            return new ServiceResult(false, code, message, status, null);
        }

        public boolean success() { return success; }
        public String errorCode() { return errorCode; }
        public String message() { return message; }
        public int httpStatus() { return httpStatus; }
        public Map<String, Object> data() { return data; }
    }

    public static ServiceResult beginRegistration(final int userId, final String rpId) {
        return MyBatisFactory.doWorkWithResult(session -> {
            UserAccount acc = session.getMapper(UserMapper.class).findById(userId);
            if (acc == null || !"active".equals(acc.getStatus())) {
                return ServiceResult.error(403, "ACCOUNT_INACTIVE", "Account is not active");
            }
            PasskeyMapper pkMapper = session.getMapper(PasskeyMapper.class);
            List<UserPasskey> existing = pkMapper.listByUserId(userId);
            if (existing.size() >= MAX_PASSKEYS_PER_USER) {
                return ServiceResult.error(400, "PASSKEY_LIMIT_REACHED", "Maximum passkey limit reached (" + MAX_PASSKEYS_PER_USER + ")");
            }

            byte[] challenge = nextChallengeBytes();
            String challengeId = issueChallenge("register", Integer.valueOf(userId), rpId, challenge, false);

            Map<String, Object> rp = new LinkedHashMap<>();
            rp.put("name", SettingsService.getSiteName());
            rp.put("id", rpId);

            Map<String, Object> userMap = new LinkedHashMap<>();
            userMap.put("id", WebAuthnUtil.encodeB64Url(("usp-user-" + acc.getId()).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            userMap.put("name", acc.getUsername());
            String displayName = (acc.getNickname() != null && !acc.getNickname().trim().isEmpty())
                    ? acc.getNickname().trim() : acc.getUsername();
            userMap.put("displayName", displayName);

            List<Map<String, Object>> pubKeyCredParams = new ArrayList<>();
            pubKeyCredParams.add(algParam(WebAuthnUtil.ALG_ES256));
            pubKeyCredParams.add(algParam(WebAuthnUtil.ALG_RS256));

            Map<String, Object> authSel = new LinkedHashMap<>();
            authSel.put("residentKey", "preferred");
            authSel.put("requireResidentKey", Boolean.FALSE);
            authSel.put("userVerification", "preferred");

            List<Map<String, Object>> excludeCreds = toCredentialDescriptors(existing);

            Map<String, Object> publicKey = new LinkedHashMap<>();
            publicKey.put("challenge", WebAuthnUtil.encodeB64Url(challenge));
            publicKey.put("rp", rp);
            publicKey.put("user", userMap);
            publicKey.put("pubKeyCredParams", pubKeyCredParams);
            publicKey.put("timeout", Integer.valueOf(60000));
            publicKey.put("attestation", "none");
            publicKey.put("authenticatorSelection", authSel);
            publicKey.put("excludeCredentials", excludeCreds);

            Map<String, Object> out = new LinkedHashMap<>();
            out.put("challengeId", challengeId);
            out.put("publicKey", publicKey);
            return ServiceResult.ok(out);
        });
    }

    public static ServiceResult finishRegistration(
            final int userId,
            final String challengeId,
            final String requestedName,
            final JsonObject credential,
            final String requestOrigin,
            final String mfaSetupToken,
            final String ip,
            final String userAgent) {
        if (credential == null || challengeId == null || challengeId.trim().isEmpty()) {
            return ServiceResult.error(400, "INVALID_REQUEST", "Missing passkey registration payload");
        }

        // Validate optional onboarding MFA token before consuming challenge if present
        boolean onboardingRemember = false;
        if (mfaSetupToken != null && !mfaSetupToken.trim().isEmpty()) {
            AuthService.ValidatedMfaToken validated = AuthService.validateMfaToken(mfaSetupToken, "mfa_setup");
            if (validated == null || validated.user.getId() != userId) {
                return ServiceResult.error(401, "MFA_TOKEN_INVALID", "Invalid verification session");
            }
            onboardingRemember = validated.rememberMe;
        }

        ChallengeEntry entry = consumeChallenge(challengeId.trim(), "register");
        if (entry == null || entry.userId == null || entry.userId.intValue() != userId) {
            return ServiceResult.error(400, "CHALLENGE_EXPIRED", "Passkey registration challenge expired or invalid");
        }

        JsonObject response = JsonHelper.obj(credential, "response");
        String clientDataJson = JsonHelper.str(response, "clientDataJSON");
        String attestationObj = JsonHelper.str(response, "attestationObject");
        String rawId = JsonHelper.str(credential, "rawId");
        if (rawId == null || rawId.isEmpty()) {
            rawId = JsonHelper.str(credential, "id");
        }
        if (response == null || clientDataJson == null || attestationObj == null) {
            return ServiceResult.error(400, "INVALID_CREDENTIAL", "Missing WebAuthn registration response fields");
        }

        final WebAuthnUtil.RegistrationVerified verified;
        try {
            verified = WebAuthnUtil.verifyRegistration(
                    clientDataJson, attestationObj, rawId, entry.challenge, entry.rpId, requestOrigin);
        } catch (SecurityException se) {
            PanelLog.get().warn("Passkey registration verification failed for userId={}: {}", Integer.valueOf(userId), se.getMessage());
            return ServiceResult.error(400, "WEBAUTHN_FAILED", se.getMessage());
        }

        final List<String> transportList = new ArrayList<>();
        if (response.has("transports") && response.get("transports").isJsonArray()) {
            JsonArray arr = response.getAsJsonArray("transports");
            for (JsonElement el : arr) {
                if (el != null && el.isJsonPrimitive()) {
                    String t = el.getAsString().trim();
                    if (!t.isEmpty() && t.length() <= 32) {
                        transportList.add(t);
                    }
                }
            }
        }
        final String cleanName = sanitizeName(requestedName);
        final long now = System.currentTimeMillis() / 1000L;

        ServiceResult dbRes = MyBatisFactory.doWorkWithResult(session -> {
            PasskeyMapper pkMapper = session.getMapper(PasskeyMapper.class);
            if (pkMapper.findByCredentialId(verified.credentialIdB64) != null) {
                return ServiceResult.error(409, "PASSKEY_EXISTS", "This passkey credential is already registered");
            }
            if (pkMapper.countByUserId(userId) >= MAX_PASSKEYS_PER_USER) {
                return ServiceResult.error(400, "PASSKEY_LIMIT_REACHED", "Maximum passkey limit reached (" + MAX_PASSKEYS_PER_USER + ")");
            }
            UserPasskey pk = new UserPasskey();
            pk.setUserId(userId);
            pk.setCredentialId(verified.credentialIdB64);
            pk.setName(cleanName);
            pk.setPublicKeyCose(verified.publicKeyCoseB64);
            pk.setAlgorithm(verified.algorithm);
            pk.setSignCount(verified.signCount);
            pk.setTransports(JsonHelper.toJson(transportList));
            pk.setCreatedAt(now);
            pk.setLastUsedAt(null);
            pkMapper.insert(pk);

            Map<String, Object> out = new LinkedHashMap<>();
            out.put("passkey", toDto(pk));
            return ServiceResult.ok(out);
        });
        if (!dbRes.success()) {
            return dbRes;
        }

        if (mfaSetupToken != null && !mfaSetupToken.trim().isEmpty()) {
            Map<String, Object> sessionData = AuthService.issueSessionForUser(userId, onboardingRemember, ip, userAgent);
            if (sessionData != null) {
                Map<String, Object> merged = new LinkedHashMap<>(sessionData);
                merged.put("passkey", dbRes.data().get("passkey"));
                return ServiceResult.ok(merged);
            }
        }
        return dbRes;
    }

    public static ServiceResult beginLogin(final String accountHint, final String rpId) {
        List<Map<String, Object>> allowCreds = new ArrayList<>();
        if (accountHint != null && !accountHint.trim().isEmpty()) {
            UserAccount acc = MyBatisFactory.doWorkWithResult(session ->
                    session.getMapper(UserMapper.class).findByUsernameOrEmail(accountHint.trim()));
            if (acc != null) {
                List<UserPasskey> keys = listRaw(acc.getId());
                if (keys.isEmpty()) {
                    return ServiceResult.error(400, "NO_PASSKEYS", "No passkeys registered for this account");
                }
                allowCreds.addAll(toCredentialDescriptors(keys));
            }
        }

        byte[] challenge = nextChallengeBytes();
        String challengeId = issueChallenge("login", null, rpId, challenge, false);

        Map<String, Object> publicKey = new LinkedHashMap<>();
        publicKey.put("challenge", WebAuthnUtil.encodeB64Url(challenge));
        publicKey.put("rpId", rpId);
        publicKey.put("timeout", Integer.valueOf(60000));
        publicKey.put("userVerification", "preferred");
        publicKey.put("allowCredentials", allowCreds);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("challengeId", challengeId);
        out.put("publicKey", publicKey);
        return ServiceResult.ok(out);
    }

    public static ServiceResult finishLogin(
            final String challengeId,
            final JsonObject credential,
            final boolean rememberMe,
            final String requestOrigin,
            final String ip,
            final String userAgent) {
        if (credential == null || challengeId == null || challengeId.trim().isEmpty()) {
            return ServiceResult.error(400, "INVALID_REQUEST", "Missing passkey assertion payload");
        }
        ChallengeEntry entry = consumeChallenge(challengeId.trim(), "login");
        if (entry == null) {
            return ServiceResult.error(400, "CHALLENGE_EXPIRED", "Passkey sign-in challenge expired or invalid");
        }
        return verifyAssertionAndIssueSession(entry, credential, null, rememberMe, requestOrigin, ip, userAgent);
    }

    public static ServiceResult beginMfa(final String mfaToken, final String rpId) {
        AuthService.ValidatedMfaToken validated = AuthService.validateMfaToken(mfaToken, "mfa");
        if (validated == null) {
            return ServiceResult.error(401, "MFA_TOKEN_INVALID", "Verification session expired or invalid");
        }
        List<UserPasskey> keys = listRaw(validated.user.getId());
        if (keys.isEmpty()) {
            return ServiceResult.error(400, "NO_PASSKEYS", "No passkeys registered for this account");
        }

        byte[] challenge = nextChallengeBytes();
        String challengeId = issueChallenge("mfa", Integer.valueOf(validated.user.getId()), rpId, challenge, validated.rememberMe);

        Map<String, Object> publicKey = new LinkedHashMap<>();
        publicKey.put("challenge", WebAuthnUtil.encodeB64Url(challenge));
        publicKey.put("rpId", rpId);
        publicKey.put("timeout", Integer.valueOf(60000));
        publicKey.put("userVerification", "preferred");
        publicKey.put("allowCredentials", toCredentialDescriptors(keys));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("challengeId", challengeId);
        out.put("publicKey", publicKey);
        return ServiceResult.ok(out);
    }

    public static ServiceResult finishMfa(
            final String challengeId,
            final String mfaToken,
            final JsonObject credential,
            final String requestOrigin,
            final String ip,
            final String userAgent) {
        AuthService.ValidatedMfaToken validated = AuthService.validateMfaToken(mfaToken, "mfa");
        if (validated == null) {
            return ServiceResult.error(401, "MFA_TOKEN_INVALID", "Verification session expired or invalid");
        }
        ChallengeEntry entry = consumeChallenge(challengeId != null ? challengeId.trim() : "", "mfa");
        if (entry == null || entry.userId == null || entry.userId.intValue() != validated.user.getId()) {
            return ServiceResult.error(400, "CHALLENGE_EXPIRED", "Passkey verification challenge expired or invalid");
        }
        return verifyAssertionAndIssueSession(entry, credential, Integer.valueOf(validated.user.getId()),
                validated.rememberMe, requestOrigin, ip, userAgent);
    }

    private static ServiceResult verifyAssertionAndIssueSession(
            ChallengeEntry entry,
            JsonObject credential,
            Integer expectedUserId,
            boolean rememberMe,
            String requestOrigin,
            String ip,
            String userAgent) {
        JsonObject response = JsonHelper.obj(credential, "response");
        String credId = JsonHelper.str(credential, "id");
        if ((credId == null || credId.isEmpty()) && credential != null) {
            credId = JsonHelper.str(credential, "rawId");
        }
        String clientDataJson = JsonHelper.str(response, "clientDataJSON");
        String authenticatorData = JsonHelper.str(response, "authenticatorData");
        String signature = JsonHelper.str(response, "signature");
        if (credId == null || response == null || clientDataJson == null || authenticatorData == null || signature == null) {
            return ServiceResult.error(400, "INVALID_CREDENTIAL", "Missing WebAuthn assertion response fields");
        }

        final String targetCredId = credId.trim();
        UserPasskey pk = MyBatisFactory.doWorkWithResult(session ->
                session.getMapper(PasskeyMapper.class).findByCredentialId(targetCredId));
        if (pk == null || (expectedUserId != null && pk.getUserId() != expectedUserId.intValue())) {
            return ServiceResult.error(401, "PASSKEY_NOT_FOUND", "Recognized passkey was not found for this account");
        }

        UserAccount user = MyBatisFactory.doWorkWithResult(session ->
                session.getMapper(UserMapper.class).findById(pk.getUserId()));
        if (user == null) {
            return ServiceResult.error(401, "PASSKEY_NOT_FOUND", "Account not found");
        }
        if (!"active".equals(user.getStatus())) {
            return ServiceResult.error(403, "ACCOUNT_" + user.getStatus().toUpperCase(Locale.ROOT), "Account is not active");
        }
        long now = System.currentTimeMillis() / 1000L;
        if (user.getLockoutUntil() != null && user.getLockoutUntil() > now) {
            return ServiceResult.error(429, "ACCOUNT_LOCKED", "Account temporarily locked due to failed attempts");
        }

        final long nextSignCount;
        try {
            nextSignCount = WebAuthnUtil.verifyAssertion(
                    clientDataJson, authenticatorData, signature,
                    entry.challenge, entry.rpId, requestOrigin,
                    pk.getPublicKeyCose(), pk.getAlgorithm(), pk.getSignCount());
        } catch (SecurityException se) {
            PanelLog.get().warn("Passkey assertion failed for user='{}': {}", user.getUsername(), se.getMessage());
            return ServiceResult.error(401, "PASSKEY_VERIFY_FAILED", se.getMessage());
        }

        MyBatisFactory.doWork(session ->
                session.getMapper(PasskeyMapper.class).updateSignCountAndLastUsed(pk.getId(), nextSignCount, now));

        Map<String, Object> sessionData = AuthService.issueSessionForUser(user.getId(), rememberMe, ip, userAgent);
        if (sessionData == null) {
            return ServiceResult.error(403, "ACCOUNT_INACTIVE", "Unable to sign in");
        }
        return ServiceResult.ok(sessionData);
    }

    public static List<Map<String, Object>> listByUser(final int userId) {
        List<UserPasskey> list = listRaw(userId);
        List<Map<String, Object>> out = new ArrayList<>();
        for (UserPasskey pk : list) {
            out.add(toDto(pk));
        }
        return out;
    }

    public static ServiceResult renamePasskey(final int userId, final int passkeyId, final String newName) {
        final String cleanName = sanitizeName(newName);
        return MyBatisFactory.doWorkWithResult(session -> {
            int updated = session.getMapper(PasskeyMapper.class).rename(passkeyId, userId, cleanName);
            if (updated <= 0) {
                return ServiceResult.error(404, "NOT_FOUND", "Passkey not found");
            }
            return ServiceResult.ok(Collections.singletonMap("name", cleanName));
        });
    }

    public static ServiceResult deletePasskey(final int userId, final int passkeyId) {
        return MyBatisFactory.doWorkWithResult(session -> {
            UserMapper userMapper = session.getMapper(UserMapper.class);
            PasskeyMapper pkMapper = session.getMapper(PasskeyMapper.class);
            UserAccount acc = userMapper.findById(userId);
            if (acc == null) {
                return ServiceResult.error(404, "NOT_FOUND", "User not found");
            }
            UserPasskey target = pkMapper.findByIdAndUser(passkeyId, userId);
            if (target == null) {
                return ServiceResult.error(404, "NOT_FOUND", "Passkey not found");
            }
            boolean policyRequired = SettingsService.isForce2fa() || acc.isRequire2fa();
            if (policyRequired && !acc.isTotpEnabled() && pkMapper.countByUserId(userId) <= 1) {
                return ServiceResult.error(400, "TWO_FA_REQUIRED_BY_POLICY", "Two-factor authentication is enforced by policy");
            }
            pkMapper.deleteByIdAndUser(passkeyId, userId);
            return ServiceResult.ok(Collections.singletonMap("message", "Passkey removed"));
        });
    }

    private static List<UserPasskey> listRaw(final int userId) {
        return MyBatisFactory.doWorkWithResult(session ->
                session.getMapper(PasskeyMapper.class).listByUserId(userId));
    }

    private static Map<String, Object> algParam(int alg) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", "public-key");
        m.put("alg", Integer.valueOf(alg));
        return m;
    }

    private static List<Map<String, Object>> toCredentialDescriptors(List<UserPasskey> list) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (UserPasskey pk : list) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("type", "public-key");
            item.put("id", pk.getCredentialId());
            List<String> transports = parseTransports(pk.getTransports());
            if (!transports.isEmpty()) {
                item.put("transports", transports);
            }
            out.add(item);
        }
        return out;
    }

    private static List<String> parseTransports(String json) {
        if (json == null || json.trim().isEmpty()) return Collections.emptyList();
        try {
            JsonArray arr = JsonHelper.parseArray(json);
            List<String> out = new ArrayList<>();
            for (JsonElement el : arr) {
                if (el != null && el.isJsonPrimitive()) {
                    String s = el.getAsString().trim();
                    if (!s.isEmpty()) out.add(s);
                }
            }
            return out;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private static Map<String, Object> toDto(UserPasskey pk) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", Integer.valueOf(pk.getId()));
        m.put("name", pk.getName());
        m.put("algorithm", pk.getAlgorithm() == WebAuthnUtil.ALG_ES256 ? "ES256" : "RS256");
        m.put("transports", parseTransports(pk.getTransports()));
        m.put("createdAt", Long.valueOf(pk.getCreatedAt()));
        m.put("lastUsedAt", pk.getLastUsedAt());
        return m;
    }

    private static String sanitizeName(String raw) {
        if (raw == null || raw.trim().isEmpty()) return "Passkey";
        String trimmed = raw.trim();
        return trimmed.length() > 64 ? trimmed.substring(0, 64) : trimmed;
    }

    private static byte[] nextChallengeBytes() {
        byte[] buf = new byte[32];
        RANDOM.nextBytes(buf);
        return buf;
    }

    private static String issueChallenge(String purpose, Integer userId, String rpId, byte[] challenge, boolean rememberMe) {
        long now = System.currentTimeMillis() / 1000L;
        if (CHALLENGES.size() >= MAX_CHALLENGES) {
            Iterator<Map.Entry<String, ChallengeEntry>> it = CHALLENGES.entrySet().iterator();
            while (it.hasNext()) {
                if (it.next().getValue().expiresAt < now) {
                    it.remove();
                }
            }
        }
        String id = PasswordUtil.randomHex(16);
        CHALLENGES.put(id, new ChallengeEntry(purpose, userId, rpId, challenge, rememberMe, now + CHALLENGE_TTL_SECONDS));
        return id;
    }

    private static ChallengeEntry consumeChallenge(String challengeId, String expectedPurpose) {
        if (challengeId == null || challengeId.isEmpty()) return null;
        ChallengeEntry entry = CHALLENGES.remove(challengeId);
        if (entry == null) return null;
        long now = System.currentTimeMillis() / 1000L;
        if (entry.expiresAt < now || !expectedPurpose.equals(entry.purpose)) {
            return null;
        }
        return entry;
    }
}
