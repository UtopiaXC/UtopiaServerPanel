package com.utopiaxc.utopiaserverpanel.web.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.utopiaxc.utopiaserverpanel.adapter.AdapterRegistry;
import com.utopiaxc.utopiaserverpanel.adapter.GameAdapter;
import com.utopiaxc.utopiaserverpanel.adapter.util.McPlayerProfileReader;
import com.utopiaxc.utopiaserverpanel.auth.CryptoUtil;
import com.utopiaxc.utopiaserverpanel.auth.PasswordUtil;
import com.utopiaxc.utopiaserverpanel.auth.TotpUtil;
import com.utopiaxc.utopiaserverpanel.auth.UsernamePolicy;
import com.utopiaxc.utopiaserverpanel.web.context.JsonHelper;
import com.utopiaxc.utopiaserverpanel.web.db.MyBatisFactory;
import com.utopiaxc.utopiaserverpanel.web.db.mapper.ApiTokenMapper;
import com.utopiaxc.utopiaserverpanel.web.db.mapper.SecurityMapper;
import com.utopiaxc.utopiaserverpanel.web.db.mapper.SessionMapper;
import com.utopiaxc.utopiaserverpanel.web.db.mapper.UserMapper;
import com.utopiaxc.utopiaserverpanel.web.db.mapper.UserProfileMapper;
import com.utopiaxc.utopiaserverpanel.web.db.model.ApiToken;
import com.utopiaxc.utopiaserverpanel.web.db.model.LoginHistoryEntry;
import com.utopiaxc.utopiaserverpanel.web.db.model.PlayerBindingInfo;
import com.utopiaxc.utopiaserverpanel.web.db.model.ProfilePrivacyConfig;
import com.utopiaxc.utopiaserverpanel.web.db.model.UserAccount;
import com.utopiaxc.utopiaserverpanel.web.db.model.UserProfileInfo;
import com.utopiaxc.utopiaserverpanel.web.db.model.UserSession;
import com.utopiaxc.utopiaserverpanel.web.db.model.UserTotp;

import java.nio.file.Path;
import java.util.*;

/**
 * Handles self-service profile, password, TOTP 2FA setup/disable, active sessions,
 * Personal Access Tokens (PAT), and login audit history.
 */
public final class ProfileService {

    private static final int MAX_AVATAR_BYTES = 262144;
    private static final int MAX_BIO_LENGTH = 500;
    private static final int MAX_SOCIAL_ENTRIES = 15;
    private static final Set<String> VALID_GENDERS = Collections.unmodifiableSet(new HashSet<String>(
            Arrays.asList("unspecified", "male", "female", "other", "prefer_not_to_say")));
    private static final Set<String> ALLOWED_AVATAR_MIMES = Collections.unmodifiableSet(new HashSet<String>(
            Arrays.asList("image/png", "image/jpeg", "image/webp", "image/gif")));

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
        return updateProfile(userId, nickname, email, locale, null, null, null, null, null, false,
                false, false, false, false);
    }

    public static SimpleResult updateProfile(
            final int userId,
            String nickname,
            String email,
            String locale,
            String gender,
            String birthday,
            String bio,
            JsonElement socialAccountsEl,
            String avatarData,
            boolean avatarProvided,
            boolean genderProvided,
            boolean birthdayProvided,
            boolean bioProvided,
            boolean socialProvided) {
        final String cleanNick = nickname != null ? nickname.trim() : "";
        final String cleanEmail = (email != null && !email.trim().isEmpty()) ? email.trim() : null;
        if (cleanEmail != null && !cleanEmail.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            return new SimpleResult(false, "INVALID_EMAIL", "Invalid email address", null);
        }
        final String cleanLocale = (locale != null && !locale.trim().isEmpty()) ? locale.trim() : "zh-CN";

        final String normalizedGender;
        if (genderProvided) {
            String g = gender != null ? gender.trim().toLowerCase(Locale.ROOT) : "unspecified";
            if (!VALID_GENDERS.contains(g)) {
                return new SimpleResult(false, "INVALID_GENDER", "Unsupported gender option", null);
            }
            normalizedGender = g;
        } else {
            normalizedGender = null;
        }

        final String normalizedBirthday;
        if (birthdayProvided) {
            String b = birthday != null ? birthday.trim() : "";
            if (!b.isEmpty() && !b.matches("^\\d{4}-\\d{2}-\\d{2}$")) {
                return new SimpleResult(false, "INVALID_BIRTHDAY", "Birthday must be YYYY-MM-DD", null);
            }
            normalizedBirthday = b;
        } else {
            normalizedBirthday = null;
        }

        final String normalizedBio;
        if (bioProvided) {
            String rawBio = bio != null ? bio.trim() : "";
            normalizedBio = rawBio.length() > MAX_BIO_LENGTH ? rawBio.substring(0, MAX_BIO_LENGTH) : rawBio;
        } else {
            normalizedBio = null;
        }

        final String normalizedSocialJson;
        if (socialProvided) {
            try {
                normalizedSocialJson = normalizeSocialAccountsJson(socialAccountsEl);
            } catch (IllegalArgumentException e) {
                return new SimpleResult(false, "INVALID_SOCIAL_ACCOUNTS", e.getMessage(), null);
            }
        } else {
            normalizedSocialJson = null;
        }

        final String normalizedAvatar;
        if (avatarProvided) {
            try {
                normalizedAvatar = normalizeAvatarDataUrl(avatarData);
            } catch (IllegalArgumentException e) {
                return new SimpleResult(false, "INVALID_AVATAR", e.getMessage(), null);
            }
        } else {
            normalizedAvatar = null;
        }

        return MyBatisFactory.doWorkWithResult(session -> {
            UserMapper mapper = session.getMapper(UserMapper.class);
            UserProfileMapper profileMapper = session.getMapper(UserProfileMapper.class);
            UserAccount existing = mapper.findById(userId);
            if (existing == null) return new SimpleResult(false, "NOT_FOUND", "User not found", null);
            if (cleanEmail != null && mapper.countByEmailExclude(cleanEmail, userId) > 0) {
                return new SimpleResult(false, "EMAIL_EXISTS", "Email already in use", null);
            }
            long now = System.currentTimeMillis() / 1000L;
            String finalNick = cleanNick.isEmpty() ? existing.getUsername() : cleanNick;
            mapper.updateProfile(userId, finalNick, cleanEmail, cleanLocale, now);

            UserProfileInfo currentExtra = profileMapper.findByUserId(userId);
            String finalAvatar = avatarProvided ? normalizedAvatar
                    : (currentExtra != null && currentExtra.getAvatarData() != null ? currentExtra.getAvatarData() : "");
            String finalGender = genderProvided ? normalizedGender
                    : (currentExtra != null && currentExtra.getGender() != null ? currentExtra.getGender() : "unspecified");
            String finalBirthday = birthdayProvided ? normalizedBirthday
                    : (currentExtra != null && currentExtra.getBirthday() != null ? currentExtra.getBirthday() : "");
            String finalBio = bioProvided ? normalizedBio
                    : (currentExtra != null && currentExtra.getBio() != null ? currentExtra.getBio() : "");
            String finalSocial = socialProvided ? normalizedSocialJson
                    : (currentExtra != null && currentExtra.getSocialAccounts() != null ? currentExtra.getSocialAccounts() : "[]");

            profileMapper.upsertProfileDetails(userId, finalAvatar, finalGender, finalBirthday, finalBio, finalSocial, now);
            return new SimpleResult(true, null, "Profile updated", getCurrentUserInfo(userId));
        });
    }

    public static SimpleResult changeUsername(final int userId, String newUsername, final String currentPassword) {
        UsernamePolicy.ValidationResult check = UsernamePolicy.validate(newUsername);
        if (!check.isValid()) {
            return new SimpleResult(false, check.getErrorCode(), check.getMessage(), null);
        }
        final String clean = check.getNormalized();
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

            if ((SettingsService.isForce2fa() || acc.isRequire2fa()) && acc.getPasskeyCount() <= 0) {
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

    // ── Extended Profile & Public Player Homepage ──

    public static UserProfileInfo getProfileExtras(int userId) {
        return MyBatisFactory.doWorkWithResult(session ->
                session.getMapper(UserProfileMapper.class).findByUserId(userId));
    }

    public static Map<String, Boolean> getPrivacyConfig(int userId) {
        UserProfileInfo info = getProfileExtras(userId);
        ProfilePrivacyConfig cfg = ProfilePrivacyConfig.fromJson(info != null ? info.getPrivacyJson() : null);
        return cfg.toMap();
    }

    public static SimpleResult updatePrivacyConfig(final int userId, final JsonObject body) {
        if (body == null) {
            return new SimpleResult(false, "INVALID_REQUEST", "Privacy settings payload required", null);
        }
        return MyBatisFactory.doWorkWithResult(session -> {
            UserMapper userMapper = session.getMapper(UserMapper.class);
            if (userMapper.findById(userId) == null) {
                return new SimpleResult(false, "NOT_FOUND", "User not found", null);
            }
            UserProfileMapper profileMapper = session.getMapper(UserProfileMapper.class);
            UserProfileInfo existing = profileMapper.findByUserId(userId);
            ProfilePrivacyConfig cfg = ProfilePrivacyConfig.fromJson(existing != null ? existing.getPrivacyJson() : null);

            if (body.has("publicProfileEnabled") && body.get("publicProfileEnabled").isJsonPrimitive()) {
                cfg.setPublicProfileEnabled(body.get("publicProfileEnabled").getAsBoolean());
            }
            if (body.has("showUsername") && body.get("showUsername").isJsonPrimitive()) {
                cfg.setShowUsername(body.get("showUsername").getAsBoolean());
            }
            if (body.has("showAvatar") && body.get("showAvatar").isJsonPrimitive()) {
                cfg.setShowAvatar(body.get("showAvatar").getAsBoolean());
            }
            if (body.has("showNickname") && body.get("showNickname").isJsonPrimitive()) {
                cfg.setShowNickname(body.get("showNickname").getAsBoolean());
            }
            if (body.has("showBio") && body.get("showBio").isJsonPrimitive()) {
                cfg.setShowBio(body.get("showBio").getAsBoolean());
            }
            if (body.has("showEmail") && body.get("showEmail").isJsonPrimitive()) {
                cfg.setShowEmail(body.get("showEmail").getAsBoolean());
            }
            if (body.has("showGender") && body.get("showGender").isJsonPrimitive()) {
                cfg.setShowGender(body.get("showGender").getAsBoolean());
            }
            if (body.has("showBirthday") && body.get("showBirthday").isJsonPrimitive()) {
                cfg.setShowBirthday(body.get("showBirthday").getAsBoolean());
            }
            if (body.has("showSocialAccounts") && body.get("showSocialAccounts").isJsonPrimitive()) {
                cfg.setShowSocialAccounts(body.get("showSocialAccounts").getAsBoolean());
            }
            if (body.has("showRole") && body.get("showRole").isJsonPrimitive()) {
                cfg.setShowRole(body.get("showRole").getAsBoolean());
            }
            if (body.has("showRegisteredAt") && body.get("showRegisteredAt").isJsonPrimitive()) {
                cfg.setShowRegisteredAt(body.get("showRegisteredAt").getAsBoolean());
            }
            if (body.has("showMcCharacters") && body.get("showMcCharacters").isJsonPrimitive()) {
                cfg.setShowMcCharacters(body.get("showMcCharacters").getAsBoolean());
            }
            if (body.has("showMcOnlineStatus") && body.get("showMcOnlineStatus").isJsonPrimitive()) {
                cfg.setShowMcOnlineStatus(body.get("showMcOnlineStatus").getAsBoolean());
            }
            if (body.has("showMcPlaytime") && body.get("showMcPlaytime").isJsonPrimitive()) {
                cfg.setShowMcPlaytime(body.get("showMcPlaytime").getAsBoolean());
            }
            if (body.has("showMcAchievements") && body.get("showMcAchievements").isJsonPrimitive()) {
                cfg.setShowMcAchievements(body.get("showMcAchievements").getAsBoolean());
            }
            if (body.has("showMcStats") && body.get("showMcStats").isJsonPrimitive()) {
                cfg.setShowMcStats(body.get("showMcStats").getAsBoolean());
            }

            long now = System.currentTimeMillis() / 1000L;
            profileMapper.upsertPrivacy(userId, cfg.toJson(), now);
            return new SimpleResult(true, null, "Privacy settings updated", getCurrentUserInfo(userId));
        });
    }

    public static Map<String, Object> getPublicProfile(String rawUsername, Integer viewerUserId) {
        if (rawUsername == null || rawUsername.trim().isEmpty()) {
            return null;
        }
        final String targetUsername = rawUsername.trim();
        UserAccount target = MyBatisFactory.doWorkWithResult(session ->
                session.getMapper(UserMapper.class).findByUsername(targetUsername));
        if (target == null) {
            return null;
        }

        boolean isOwner = viewerUserId != null && viewerUserId.intValue() == target.getId();
        UserProfileInfo extra = getProfileExtras(target.getId());
        ProfilePrivacyConfig privacy = ProfilePrivacyConfig.fromJson(extra != null ? extra.getPrivacyJson() : null);

        if (!privacy.isPublicProfileEnabled() && !isOwner) {
            Map<String, Object> hidden = new LinkedHashMap<String, Object>();
            hidden.put("exists", Boolean.TRUE);
            hidden.put("publicProfileEnabled", Boolean.FALSE);
            hidden.put("isOwner", Boolean.FALSE);
            return hidden;
        }

        Map<String, Object> dto = new LinkedHashMap<String, Object>();
        dto.put("exists", Boolean.TRUE);
        dto.put("publicProfileEnabled", Boolean.valueOf(privacy.isPublicProfileEnabled()));
        dto.put("isOwner", Boolean.valueOf(isOwner));
        dto.put("visibleFields", privacy.toMap());

        if (privacy.isShowUsername()) {
            dto.put("username", target.getUsername());
        }
        if (privacy.isShowAvatar()) {
            dto.put("avatarData", extra != null && extra.getAvatarData() != null ? extra.getAvatarData() : "");
        }
        if (privacy.isShowNickname()) {
            dto.put("nickname", target.getNickname());
        }
        if (privacy.isShowBio()) {
            dto.put("bio", extra != null && extra.getBio() != null ? extra.getBio() : "");
        }
        if (privacy.isShowEmail()) {
            dto.put("email", target.getEmail());
        }
        if (privacy.isShowGender()) {
            dto.put("gender", extra != null && extra.getGender() != null ? extra.getGender() : "unspecified");
        }
        if (privacy.isShowBirthday()) {
            dto.put("birthday", extra != null && extra.getBirthday() != null ? extra.getBirthday() : "");
        }
        if (privacy.isShowSocialAccounts()) {
            dto.put("socialAccounts", parseSocialAccounts(extra != null ? extra.getSocialAccounts() : null));
        }
        if (privacy.isShowRole()) {
            dto.put("roleName", target.getRoleName());
            dto.put("roleDisplayName", target.getRoleDisplayName());
        }
        if (privacy.isShowRegisteredAt()) {
            dto.put("createdAt", Long.valueOf(target.getCreatedAt()));
        }

        if (privacy.isShowMcCharacters()) {
            List<PlayerBindingInfo> bindings = BindingService.listByUserId(target.getId());
            GameAdapter adapter = AdapterRegistry.getNullable();
            Path worldDir = resolveWorldDirectory(adapter);

            List<Map<String, Object>> mcList = new ArrayList<Map<String, Object>>(bindings.size());
            for (PlayerBindingInfo b : bindings) {
                Map<String, Object> charMap = McPlayerProfileReader.readCharacterData(
                        worldDir,
                        b.getPlayerUuid(),
                        b.getPlayerName(),
                        adapter,
                        privacy.isShowMcOnlineStatus(),
                        privacy.isShowMcPlaytime(),
                        privacy.isShowMcAchievements(),
                        privacy.isShowMcStats());
                charMap.put("primary", Boolean.valueOf(b.isPrimary()));
                charMap.put("boundAt", Long.valueOf(b.getBoundAt()));
                mcList.add(charMap);
            }
            dto.put("mcCharacters", mcList);
        }

        return dto;
    }

    private static Path resolveWorldDirectory(GameAdapter adapter) {
        if (adapter != null) {
            try {
                Path p = adapter.getWorldDirectory();
                if (p != null) return p;
            } catch (Exception ignored) {
                // fall back to registry
            }
        }
        return WorldRegistryService.getActiveWorldDir();
    }

    public static List<Map<String, String>> parseSocialAccounts(String rawJson) {
        List<Map<String, String>> list = new ArrayList<Map<String, String>>();
        if (rawJson == null || rawJson.trim().isEmpty()) {
            return list;
        }
        String trimmed = rawJson.trim();
        if (!trimmed.startsWith("[")) {
            return list;
        }
        try {
            JsonArray arr = JsonHelper.parseArray(trimmed);
            for (JsonElement el : arr) {
                if (el == null || !el.isJsonObject()) continue;
                JsonObject obj = el.getAsJsonObject();
                String account = JsonHelper.str(obj, "account");
                if (account == null || account.trim().isEmpty()) continue;
                String platform = JsonHelper.str(obj, "platform");
                String customPlatform = JsonHelper.str(obj, "customPlatform");
                String cleanPlatform = (platform != null && !platform.trim().isEmpty()) ? platform.trim() : "custom";
                Map<String, String> entry = new LinkedHashMap<String, String>();
                entry.put("platform", cleanPlatform);
                if ("custom".equalsIgnoreCase(cleanPlatform) && customPlatform != null && !customPlatform.trim().isEmpty()) {
                    entry.put("customPlatform", customPlatform.trim());
                }
                entry.put("account", account.trim());
                list.add(entry);
            }
        } catch (Exception ignored) {
            return Collections.emptyList();
        }
        return list;
    }

    private static String normalizeSocialAccountsJson(JsonElement el) {
        if (el == null || el.isJsonNull()) {
            return "[]";
        }
        JsonArray arr;
        if (el.isJsonArray()) {
            arr = el.getAsJsonArray();
        } else if (el.isJsonPrimitive()) {
            String raw = el.getAsString().trim();
            if (raw.isEmpty()) return "[]";
            if (!raw.startsWith("[")) {
                throw new IllegalArgumentException("Social accounts must be a JSON array");
            }
            arr = JsonHelper.parseArray(raw);
        } else {
            throw new IllegalArgumentException("Invalid social accounts format");
        }

        List<Map<String, String>> normalized = new ArrayList<Map<String, String>>();
        for (JsonElement itemEl : arr) {
            if (normalized.size() >= MAX_SOCIAL_ENTRIES) break;
            if (itemEl == null || !itemEl.isJsonObject()) continue;
            JsonObject obj = itemEl.getAsJsonObject();
            String account = JsonHelper.str(obj, "account");
            if (account == null || account.trim().isEmpty()) continue;
            String cleanAcc = account.trim();
            if (cleanAcc.length() > 160) cleanAcc = cleanAcc.substring(0, 160);
            String platform = JsonHelper.str(obj, "platform");
            String cleanPlatform = (platform != null && !platform.trim().isEmpty()) ? platform.trim() : "custom";
            if (cleanPlatform.length() > 32) cleanPlatform = cleanPlatform.substring(0, 32);

            Map<String, String> entry = new LinkedHashMap<String, String>();
            entry.put("platform", cleanPlatform);
            if ("custom".equalsIgnoreCase(cleanPlatform)) {
                String customPlatform = JsonHelper.str(obj, "customPlatform");
                if (customPlatform != null && !customPlatform.trim().isEmpty()) {
                    String cleanCustom = customPlatform.trim();
                    entry.put("customPlatform", cleanCustom.length() > 48 ? cleanCustom.substring(0, 48) : cleanCustom);
                }
            }
            entry.put("account", cleanAcc);
            normalized.add(entry);
        }
        return JsonHelper.toJson(normalized);
    }

    private static String normalizeAvatarDataUrl(String dataUrl) {
        if (dataUrl == null || dataUrl.trim().isEmpty()) {
            return "";
        }
        String trimmed = dataUrl.trim();
        if (!trimmed.toLowerCase(Locale.ROOT).startsWith("data:")) {
            throw new IllegalArgumentException("Invalid avatar data URI");
        }
        int commaIdx = trimmed.indexOf(',');
        if (commaIdx <= 5) {
            throw new IllegalArgumentException("Invalid avatar data URI");
        }
        String meta = trimmed.substring(5, commaIdx);
        String payload = trimmed.substring(commaIdx + 1).replaceAll("\\s+", "");
        if (!meta.toLowerCase(Locale.ROOT).endsWith(";base64")) {
            throw new IllegalArgumentException("Avatar image must be base64 encoded");
        }
        String mime = meta.substring(0, meta.length() - ";base64".length()).trim().toLowerCase(Locale.ROOT);
        if (!ALLOWED_AVATAR_MIMES.contains(mime)) {
            throw new IllegalArgumentException("Unsupported avatar image format: " + mime);
        }
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(payload);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid base64 avatar payload");
        }
        if (decoded.length == 0) {
            throw new IllegalArgumentException("Avatar image is empty");
        }
        if (decoded.length > MAX_AVATAR_BYTES) {
            throw new IllegalArgumentException("Avatar exceeds 256 KB limit");
        }
        return "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(decoded);
    }
}

