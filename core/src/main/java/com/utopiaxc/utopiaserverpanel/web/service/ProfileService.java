package com.utopiaxc.utopiaserverpanel.web.service;

import com.utopiaxc.utopiaserverpanel.auth.CryptoUtil;
import com.utopiaxc.utopiaserverpanel.auth.PasswordUtil;
import com.utopiaxc.utopiaserverpanel.auth.TotpUtil;
import com.utopiaxc.utopiaserverpanel.web.db.MyBatisFactory;
import com.utopiaxc.utopiaserverpanel.web.db.mapper.ApiTokenMapper;
import com.utopiaxc.utopiaserverpanel.web.db.mapper.SecurityMapper;
import com.utopiaxc.utopiaserverpanel.web.db.mapper.SessionMapper;
import com.utopiaxc.utopiaserverpanel.web.db.mapper.UserMapper;
import com.utopiaxc.utopiaserverpanel.web.db.model.ApiToken;
import com.utopiaxc.utopiaserverpanel.web.db.model.LoginHistoryEntry;
import com.utopiaxc.utopiaserverpanel.web.db.model.UserAccount;
import com.utopiaxc.utopiaserverpanel.web.db.model.UserSession;
import com.utopiaxc.utopiaserverpanel.web.db.model.UserTotp;

import java.util.*;

/**
 * Handles self-service profile, password, TOTP 2FA setup/disable, active sessions,
 * Personal Access Tokens (PAT), and login audit history.
 */
public final class ProfileService {

    private ProfileService() {}

    public static Map<String, Object> getCurrentUserInfo(int userId) {
        return MyBatisFactory.doWorkWithResult(session -> {
            UserAccount acc = session.getMapper(UserMapper.class).findById(userId);
            if (acc == null) return null;
            Map<String, Integer> perms = PermissionService.getUserPermissionLevels(userId);
            return AuthService.buildUserProfileDto(acc, perms);
        });
    }

    public static final class SimpleResult {
        private final boolean success;
        private final String errorCode;
        private final String message;
        private final Map<String, Object> data;

        public SimpleResult(boolean success, String errorCode, String message, Map<String, Object> data) {
            this.success = success;
            this.errorCode = errorCode;
            this.message = message;
            this.data = data;
        }

        public boolean success() { return success; }
        public String errorCode() { return errorCode; }
        public String message() { return message; }
        public Map<String, Object> data() { return data; }
    }

    public static SimpleResult updateProfile(final int userId, String nickname, String email, String locale) {
        final String cleanNick = nickname != null ? nickname.trim() : "";
        final String cleanEmail = (email != null && !email.trim().isEmpty()) ? email.trim() : null;
        if (cleanEmail != null && !cleanEmail.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            return new SimpleResult(false, "INVALID_EMAIL", "Invalid email address", null);
        }
        final String cleanLocale = (locale != null && !locale.trim().isEmpty()) ? locale.trim() : "zh-CN";

        return MyBatisFactory.doWorkWithResult(session -> {
            UserMapper mapper = session.getMapper(UserMapper.class);
            UserAccount existing = mapper.findById(userId);
            if (existing == null) return new SimpleResult(false, "NOT_FOUND", "User not found", null);
            if (cleanEmail != null && mapper.countByEmailExclude(cleanEmail, userId) > 0) {
                return new SimpleResult(false, "EMAIL_EXISTS", "Email already in use", null);
            }
            long now = System.currentTimeMillis() / 1000L;
            String finalNick = cleanNick.isEmpty() ? existing.getUsername() : cleanNick;
            mapper.updateProfile(userId, finalNick, cleanEmail, cleanLocale, now);
            return new SimpleResult(true, null, "Profile updated", getCurrentUserInfo(userId));
        });
    }

    public static SimpleResult changeUsername(final int userId, String newUsername, final String currentPassword) {
        if (newUsername == null || !newUsername.trim().matches("^[a-zA-Z0-9_]{3,32}$")) {
            return new SimpleResult(false, "INVALID_USERNAME", "Username must be 3-32 alphanumeric or underscore characters", null);
        }
        final String clean = newUsername.trim();
        return MyBatisFactory.doWorkWithResult(session -> {
            UserMapper mapper = session.getMapper(UserMapper.class);
            UserAccount acc = mapper.findById(userId);
            if (acc == null) return new SimpleResult(false, "NOT_FOUND", "User not found", null);
            if (!PasswordUtil.verify(currentPassword, acc.getPasswordHash())) {
                return new SimpleResult(false, "INVALID_PASSWORD", "Current password is incorrect", null);
            }
            if (!clean.equalsIgnoreCase(acc.getUsername()) && mapper.countByUsername(clean) > 0) {
                return new SimpleResult(false, "USERNAME_EXISTS", "Username already taken", null);
            }
            mapper.updateUsername(userId, clean, System.currentTimeMillis() / 1000L);
            return new SimpleResult(true, null, "Username updated", getCurrentUserInfo(userId));
        });
    }

    public static SimpleResult changePassword(final int userId, final String currentPassword,
                                              final String newPassword, final String keepSessionId) {
        if (newPassword == null || newPassword.length() < 6) {
            return new SimpleResult(false, "WEAK_PASSWORD", "Password must be at least 6 characters", null);
        }
        return MyBatisFactory.doWorkWithResult(session -> {
            UserMapper userMapper = session.getMapper(UserMapper.class);
            SessionMapper sessionMapper = session.getMapper(SessionMapper.class);
            UserAccount acc = userMapper.findById(userId);
            if (acc == null) return new SimpleResult(false, "NOT_FOUND", "User not found", null);
            if (!PasswordUtil.verify(currentPassword, acc.getPasswordHash())) {
                return new SimpleResult(false, "WRONG_PASSWORD", "Current password is incorrect", null);
            }

            long now = System.currentTimeMillis() / 1000L;
            userMapper.updatePassword(userId, PasswordUtil.hash(newPassword), 0, now);

            if (keepSessionId != null && !keepSessionId.trim().isEmpty()) {
                sessionMapper.deleteOtherSessions(userId, keepSessionId);
                UserAccount updated = userMapper.findById(userId);
                if (updated != null) {
                    UserSession cur = sessionMapper.findBySessionId(keepSessionId);
                    if (cur != null) {
                        sessionMapper.rotateRefreshToken(keepSessionId, cur.getTokenHash(), updated.getTokenVersion(), now, cur.getExpiresAt());
                    }
                }
            } else {
                sessionMapper.deleteAllForUser(userId);
            }

            return new SimpleResult(true, null, "Password changed", null);
        });
    }

    // ── TOTP 2FA ──

    public static SimpleResult beginTotpSetup(final int userId) {
        return MyBatisFactory.doWorkWithResult(session -> {
            UserMapper userMapper = session.getMapper(UserMapper.class);
            SecurityMapper secMapper = session.getMapper(SecurityMapper.class);
            UserAccount acc = userMapper.findById(userId);
            if (acc == null) return new SimpleResult(false, "NOT_FOUND", "User not found", null);

            UserTotp existing = secMapper.findTotpByUserId(userId);
            if (existing != null && existing.isEnabled()) {
                return new SimpleResult(false, "TWO_FA_ALREADY_ENABLED", "Two-factor authentication is already enabled", null);
            }

            String secret = TotpUtil.generateSecret();
            String encrypted = CryptoUtil.encrypt(secret);
            long now = System.currentTimeMillis() / 1000L;

            UserTotp totp = new UserTotp();
            totp.setUserId(userId);
            totp.setSecretEncrypted("");
            totp.setPendingSecretEncrypted(encrypted);
            totp.setEnabled(false);
            totp.setVerifiedAt(null);
            totp.setUpdatedAt(now);
            secMapper.upsertTotp(totp);

            String uri = TotpUtil.buildOtpAuthUri(SettingsService.getSiteName(), acc.getUsername(), secret);
            Map<String, Object> data = new LinkedHashMap<String, Object>();
            data.put("secret", secret);
            data.put("otpauthUri", uri);
            return new SimpleResult(true, null, "TOTP secret generated", data);
        });
    }

    public static SimpleResult confirmTotpSetup(int userId, String code) {
        return MyBatisFactory.doWorkWithResult(session -> {
            SecurityMapper secMapper = session.getMapper(SecurityMapper.class);
            UserTotp existing = secMapper.findTotpByUserId(userId);
            if (existing == null || existing.getPendingSecretEncrypted() == null || existing.getPendingSecretEncrypted().isEmpty()) {
                return new SimpleResult(false, "NO_PENDING_TOTP", "No pending TOTP setup found", null);
            }
            String secret = CryptoUtil.decrypt(existing.getPendingSecretEncrypted());
            if (!TotpUtil.verifyCode(secret, code)) {
                return new SimpleResult(false, "INVALID_TOTP", "Invalid verification code", null);
            }

            long now = System.currentTimeMillis() / 1000L;
            existing.setSecretEncrypted(existing.getPendingSecretEncrypted());
            existing.setPendingSecretEncrypted("");
            existing.setEnabled(true);
            existing.setVerifiedAt(now);
            existing.setUpdatedAt(now);
            secMapper.upsertTotp(existing);
            return new SimpleResult(true, null, "Two-factor authentication enabled", null);
        });
    }

    public static SimpleResult disableTotp(int userId, String password, String code) {
        return MyBatisFactory.doWorkWithResult(session -> {
            UserMapper userMapper = session.getMapper(UserMapper.class);
            SecurityMapper secMapper = session.getMapper(SecurityMapper.class);
            UserAccount acc = userMapper.findById(userId);
            if (acc == null) return new SimpleResult(false, "NOT_FOUND", "User not found", null);

            if (SettingsService.isForce2fa() || acc.isRequire2fa()) {
                return new SimpleResult(false, "TWO_FA_REQUIRED_BY_POLICY", "Two-factor authentication is enforced by policy", null);
            }
            if (!PasswordUtil.verify(password, acc.getPasswordHash())) {
                return new SimpleResult(false, "WRONG_PASSWORD", "Password is incorrect", null);
            }

            UserTotp existing = secMapper.findTotpByUserId(userId);
            if (existing != null && existing.isEnabled()) {
                String secret = CryptoUtil.decrypt(existing.getSecretEncrypted());
                if (code == null || !TotpUtil.verifyCode(secret, code)) {
                    return new SimpleResult(false, "INVALID_TOTP", "Invalid 2FA verification code", null);
                }
            }

            secMapper.deleteTotpByUserId(userId);
            return new SimpleResult(true, null, "Two-factor authentication disabled", null);
        });
    }

    // ── Active Sessions ──

    public static List<Map<String, Object>> listSessions(int userId, String currentSessionId) {
        long now = System.currentTimeMillis() / 1000L;
        return MyBatisFactory.doWorkWithResult(session -> {
            List<UserSession> list = session.getMapper(SessionMapper.class).listActiveByUser(userId, now);
            List<Map<String, Object>> result = new ArrayList<>();
            for (UserSession s : list) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("sessionId", s.getSessionId());
                m.put("ipAddress", s.getIpAddress());
                m.put("userAgent", s.getUserAgent());
                m.put("rememberMe", s.isRememberMe());
                m.put("createdAt", s.getCreatedAt());
                m.put("lastSeenAt", s.getLastSeenAt());
                m.put("expiresAt", s.getExpiresAt());
                m.put("current", s.getSessionId().equals(currentSessionId));
                result.add(m);
            }
            return result;
        });
    }

    public static boolean revokeSession(int userId, String sessionId) {
        return MyBatisFactory.doWorkWithResult(session ->
                session.getMapper(SessionMapper.class).deleteSessionForUser(sessionId, userId) > 0);
    }

    public static List<LoginHistoryEntry> listLoginHistory(int userId, int limit) {
        return MyBatisFactory.doWorkWithResult(session ->
                session.getMapper(SessionMapper.class).listLoginHistory(userId, Math.max(1, Math.min(50, limit))));
    }

    // ── Personal Access Tokens (PAT) ──

    public static List<Map<String, Object>> listApiTokens(int userId) {
        return MyBatisFactory.doWorkWithResult(session -> {
            List<ApiToken> tokens = session.getMapper(ApiTokenMapper.class).listByUser(userId);
            List<Map<String, Object>> result = new ArrayList<>();
            for (ApiToken t : tokens) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", t.getId());
                m.put("name", t.getName());
                m.put("prefix", t.getPrefix());
                m.put("scopes", t.getScopes());
                m.put("lastUsedAt", t.getLastUsedAt());
                m.put("expiresAt", t.getExpiresAt());
                m.put("createdAt", t.getCreatedAt());
                result.add(m);
            }
            return result;
        });
    }

    public static SimpleResult createApiToken(final int userId, String name, Integer expiresDays, String scopes) {
        if (name == null || name.trim().isEmpty()) {
            return new SimpleResult(false, "TOKEN_NAME_REQUIRED", "Token name is required", null);
        }
        String cleanName = name.trim();
        if (cleanName.length() > 64) {
            cleanName = cleanName.substring(0, 64);
        }
        String rawToken = "usp_" + PasswordUtil.randomUrlSafeToken(32);
        String prefix = rawToken.substring(0, 12);
        String hash = PasswordUtil.sha256Hex(rawToken);
        long now = System.currentTimeMillis() / 1000L;
        Long expiresAt = (expiresDays != null && expiresDays > 0) ? Long.valueOf(now + expiresDays.longValue() * 86400L) : null;
        String cleanScopes = (scopes != null && !scopes.trim().isEmpty()) ? scopes.trim() : "*";

        final ApiToken token = new ApiToken();
        token.setUserId(userId);
        token.setName(cleanName);
        token.setPrefix(prefix);
        token.setTokenHash(hash);
        token.setScopes(cleanScopes);
        token.setExpiresAt(expiresAt);
        token.setCreatedAt(now);

        MyBatisFactory.doWork(session ->
                session.getMapper(ApiTokenMapper.class).insert(token));

        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("id", Integer.valueOf(token.getId()));
        data.put("name", token.getName());
        data.put("prefix", token.getPrefix());
        data.put("scopes", token.getScopes());
        data.put("expiresAt", token.getExpiresAt());
        data.put("createdAt", Long.valueOf(token.getCreatedAt()));
        data.put("token", rawToken);
        return new SimpleResult(true, null, "API token created", data);
    }

    public static boolean revokeApiToken(int userId, int tokenId) {
        return MyBatisFactory.doWorkWithResult(session ->
                session.getMapper(ApiTokenMapper.class).deleteForUser(tokenId, userId) > 0);
    }
}

