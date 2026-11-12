package com.utopiaxc.utopiaserverpanel.web.service;

import com.utopiaxc.utopiaserverpanel.auth.*;
import com.utopiaxc.utopiaserverpanel.logging.PanelLog;
import com.utopiaxc.utopiaserverpanel.mail.EmailTemplateRenderer;
import com.utopiaxc.utopiaserverpanel.mail.SmtpMailer;
import com.utopiaxc.utopiaserverpanel.web.db.MyBatisFactory;
import com.utopiaxc.utopiaserverpanel.web.db.mapper.*;
import com.utopiaxc.utopiaserverpanel.web.db.model.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Handles login (with anti-brute-force lockout, ALTCHA PoW captcha, TOTP 2FA & forced 2FA setup),
 * registration (with email verification and MC binding rules), sessions, password/2FA recovery.
 */
public final class AuthService {
    private static final long PASSWORD_RESET_TTL_SECONDS = 30 * 60L;
    private static final long ACTIVATION_TTL_SECONDS = 24 * 3600L;

    private AuthService() {}

    public static final class LoginRequest {
        private final String username;
        private final String password;
        private final boolean rememberMe;
        private final String captcha;
        private final String totpCode;
        private final String mfaToken;
        private final String setupTotpCode;

        public LoginRequest(String username, String password, boolean rememberMe, String captcha,
                            String totpCode, String mfaToken, String setupTotpCode) {
            this.username = username;
            this.password = password;
            this.rememberMe = rememberMe;
            this.captcha = captcha;
            this.totpCode = totpCode;
            this.mfaToken = mfaToken;
            this.setupTotpCode = setupTotpCode;
        }

        public String username() { return username; }
        public String password() { return password; }
        public boolean rememberMe() { return rememberMe; }
        public String captcha() { return captcha; }
        public String totpCode() { return totpCode; }
        public String mfaToken() { return mfaToken; }
        public String setupTotpCode() { return setupTotpCode; }
    }

    public static final class LoginResult {
        private final boolean success;
        private final String errorCode;
        private final String message;
        private final int httpStatus;
        private final boolean captchaRequired;
        private final boolean mfaRequired;
        private final boolean setup2faRequired;
        private final String mfaToken;
        private final Long retryAfterSeconds;
        private final Map<String, Object> data;

        public LoginResult(boolean success, String errorCode, String message, int httpStatus,
                           boolean captchaRequired, boolean mfaRequired, boolean setup2faRequired,
                           String mfaToken, Long retryAfterSeconds, Map<String, Object> data) {
            this.success = success;
            this.errorCode = errorCode;
            this.message = message;
            this.httpStatus = httpStatus;
            this.captchaRequired = captchaRequired;
            this.mfaRequired = mfaRequired;
            this.setup2faRequired = setup2faRequired;
            this.mfaToken = mfaToken;
            this.retryAfterSeconds = retryAfterSeconds;
            this.data = data;
        }

        public boolean success() { return success; }
        public String errorCode() { return errorCode; }
        public String message() { return message; }
        public int httpStatus() { return httpStatus; }
        public boolean captchaRequired() { return captchaRequired; }
        public boolean mfaRequired() { return mfaRequired; }
        public boolean setup2faRequired() { return setup2faRequired; }
        public String mfaToken() { return mfaToken; }
        public Long retryAfterSeconds() { return retryAfterSeconds; }
        public Map<String, Object> data() { return data; }

        public static LoginResult ok(Map<String, Object> data) {
            return new LoginResult(true, null, "OK", 200, false, false, false, null, null, data);
        }
        public static LoginResult challenge(boolean mfa, boolean setup2fa, String mfaToken) {
            return new LoginResult(false, mfa ? "MFA_REQUIRED" : "SETUP_2FA_REQUIRED",
                    mfa ? "Two-factor verification required" : "Two-factor setup required",
                    200, false, mfa, setup2fa, mfaToken, null, null);
        }
        public static LoginResult error(int status, String code, String message, boolean captchaReq, Long retryAfter) {
            return new LoginResult(false, code, message, status, captchaReq, false, false, null, retryAfter, null);
        }
    }

    public static LoginResult login(LoginRequest req, String ip, String userAgent) {
        long now = System.currentTimeMillis() / 1000L;

        // Branch A: Completing MFA or forced 2FA setup using mfaToken
        if (req.mfaToken() != null && !req.mfaToken().trim().isEmpty()) {
            JwtClaims claims = JwtUtil.validateToken(req.mfaToken());
            if (claims == null) {
                return LoginResult.error(401, "MFA_TOKEN_EXPIRED", "Verification session expired, please sign in again", false, null);
            }
            String purpose = JwtUtil.getPurpose(claims);
            final int userId = JwtUtil.getUserId(claims);
            int tokenVer = JwtUtil.getTokenVersion(claims);
            boolean rem = JwtUtil.getRememberMe(claims);

            UserAccount acc = MyBatisFactory.doWorkWithResult(session ->
                    session.getMapper(UserMapper.class).findById(userId));
            if (acc == null || acc.getTokenVersion() != tokenVer || !"active".equals(acc.getStatus())) {
                return LoginResult.error(401, "MFA_TOKEN_INVALID", "Invalid verification session", false, null);
            }

            if ("mfa".equals(purpose)) {
                UserTotp totp = MyBatisFactory.doWorkWithResult(session ->
                        session.getMapper(SecurityMapper.class).findTotpByUserId(userId));
                if (totp == null || !totp.isEnabled()) {
                    return LoginResult.error(400, "TWO_FA_NOT_ENABLED", "Two-factor authentication is not enabled", false, null);
                }
                String secret = CryptoUtil.decrypt(totp.getSecretEncrypted());
                if (!TotpUtil.verifyCode(secret, req.totpCode())) {
                    return LoginResult.error(401, "INVALID_TOTP", "Invalid verification code", false, null);
                }
                return LoginResult.ok(issueSession(acc, rem, ip, userAgent));
            } else if ("mfa_setup".equals(purpose)) {
                ProfileService.SimpleResult confirmRes = ProfileService.confirmTotpSetup(userId, req.setupTotpCode());
                if (!confirmRes.success()) {
                    return LoginResult.error(400, confirmRes.errorCode(), confirmRes.message(), false, null);
                }
                UserAccount refreshed = MyBatisFactory.doWorkWithResult(session ->
                        session.getMapper(UserMapper.class).findById(userId));
                return LoginResult.ok(issueSession(refreshed, rem, ip, userAgent));
            }
            return LoginResult.error(400, "INVALID_MFA_PURPOSE", "Unsupported verification token", false, null);
        }

        // Branch B: Standard password authentication
        final String accountInput = req.username() != null ? req.username().trim() : "";
        String passwordInput = req.password() != null ? req.password() : "";
        if (accountInput.isEmpty() || passwordInput.isEmpty()) {
            return LoginResult.error(400, "MISSING_CREDENTIALS", "Username and password are required", false, null);
        }

        // Check CAPTCHA policy
        String captchaPolicy = SettingsService.getCaptchaPolicy();
        boolean needCaptcha = "always".equalsIgnoreCase(captchaPolicy)
                || ("auto".equalsIgnoreCase(captchaPolicy) && CaptchaService.isCaptchaRequiredAuto(ip, accountInput));
        if (needCaptcha) {
            if (req.captcha() == null || req.captcha().trim().isEmpty() || !CaptchaService.verifyPayload(req.captcha())) {
                return LoginResult.error(403, "CAPTCHA_REQUIRED", "Security verification required", true, null);
            }
        }

        final UserAccount user = MyBatisFactory.doWorkWithResult(session ->
                session.getMapper(UserMapper.class).findByUsernameOrEmail(accountInput));

        if (user == null) {
            CaptchaService.recordFailure(ip, accountInput);
            boolean nextCaptcha = !"off".equalsIgnoreCase(captchaPolicy) && CaptchaService.isCaptchaRequiredAuto(ip, accountInput);
            return LoginResult.error(401, "INVALID_CREDENTIALS", "Invalid username or password", nextCaptcha, null);
        }

        // Status checks
        switch (user.getStatus()) {
            case "banned":
                recordLoginAudit(user.getId(), ip, userAgent, false, "BANNED");
                return LoginResult.error(403, "ACCOUNT_BANNED", "Account has been banned", false, null);
            case "frozen":
                recordLoginAudit(user.getId(), ip, userAgent, false, "FROZEN");
                return LoginResult.error(403, "ACCOUNT_FROZEN", "Account is frozen", false, null);
            case "inactive":
                recordLoginAudit(user.getId(), ip, userAgent, false, "INACTIVE");
                return LoginResult.error(403, "ACCOUNT_INACTIVE", "Account email verification is pending", false, null);
            default:
                break;
        }

        // Lockout check
        if (user.getLockoutUntil() != null && user.getLockoutUntil() > now) {
            long waitSec = user.getLockoutUntil() - now;
            recordLoginAudit(user.getId(), ip, userAgent, false, "LOCKED");
            return LoginResult.error(429, "ACCOUNT_LOCKED", "Too many failed attempts", true, Long.valueOf(waitSec));
        }

        if (!PasswordUtil.verify(passwordInput, user.getPasswordHash())) {
            final int nextAttempts = user.getFailedLoginAttempts() + 1;
            int maxFails = SettingsService.getMaxFailedLogins();
            Long nextLockout = null;
            if (nextAttempts >= maxFails) {
                nextLockout = Long.valueOf(now + SettingsService.getLockoutMinutes() * 60L);
            }
            final Long lockVal = nextLockout;
            MyBatisFactory.doWork(session ->
                    session.getMapper(UserMapper.class).updateLoginFailures(user.getId(), nextAttempts, lockVal, now));
            CaptchaService.recordFailure(ip, accountInput);
            recordLoginAudit(user.getId(), ip, userAgent, false, "BAD_PASSWORD");

            if (nextLockout != null) {
                return LoginResult.error(429, "ACCOUNT_LOCKED", "Account temporarily locked due to failed attempts", true,
                        Long.valueOf(SettingsService.getLockoutMinutes() * 60L));
            }
            boolean nextCaptcha = !"off".equalsIgnoreCase(captchaPolicy) && CaptchaService.isCaptchaRequiredAuto(ip, accountInput);
            return LoginResult.error(401, "INVALID_CREDENTIALS", "Invalid username or password", nextCaptcha, null);
        }

        // Password verified: reset failure counters
        if (user.getFailedLoginAttempts() > 0 || user.getLockoutUntil() != null) {
            MyBatisFactory.doWork(session ->
                    session.getMapper(UserMapper.class).updateLoginFailures(user.getId(), 0, null, now));
        }
        CaptchaService.clearFailures(ip, accountInput);

        // 2FA verification or forced 2FA onboarding
        if (user.isTotpEnabled()) {
            if (req.totpCode() != null && !req.totpCode().trim().isEmpty()) {
                UserTotp totp = MyBatisFactory.doWorkWithResult(session ->
                        session.getMapper(SecurityMapper.class).findTotpByUserId(user.getId()));
                String secret = CryptoUtil.decrypt(totp.getSecretEncrypted());
                if (!TotpUtil.verifyCode(secret, req.totpCode())) {
                    recordLoginAudit(user.getId(), ip, userAgent, false, "BAD_TOTP");
                    return LoginResult.error(401, "INVALID_TOTP", "Invalid verification code", false, null);
                }
            } else {
                String mfaToken = JwtUtil.generateMfaToken(user.getId(), user.getTokenVersion(), "mfa", req.rememberMe());
                return LoginResult.challenge(true, false, mfaToken);
            }
        } else if (SettingsService.isForce2fa() || user.isRequire2fa()) {
            String setupToken = JwtUtil.generateMfaToken(user.getId(), user.getTokenVersion(), "mfa_setup", req.rememberMe());
            return LoginResult.challenge(false, true, setupToken);
        }

        return LoginResult.ok(issueSession(user, req.rememberMe(), ip, userAgent));
    }

    private static Map<String, Object> issueSession(UserAccount user, boolean rememberMe, String ip, String userAgent) {
        long now = System.currentTimeMillis() / 1000L;
        int days = rememberMe ? SettingsService.getSessionTimeoutDays() : 1;
        long expiresAt = now + days * 86400L;

        String sessionId = PasswordUtil.randomHex(16);
        String rawRefreshToken = "uspr_" + PasswordUtil.randomUrlSafeToken(32);
        String tokenHash = PasswordUtil.sha256Hex(rawRefreshToken);

        final UserSession sess = new UserSession();
        sess.setSessionId(sessionId);
        sess.setUserId(user.getId());
        sess.setTokenHash(tokenHash);
        sess.setTokenVersion(user.getTokenVersion());
        sess.setIpAddress(ip != null ? ip : "");
        sess.setUserAgent(userAgent != null ? truncate(userAgent, 255) : "");
        sess.setRememberMe(rememberMe);
        sess.setCreatedAt(now);
        sess.setLastSeenAt(now);
        sess.setExpiresAt(expiresAt);

        MyBatisFactory.doWork(session ->
                session.getMapper(SessionMapper.class).insertSession(sess));

        recordLoginAudit(user.getId(), ip, userAgent, true, "");

        String accessToken = JwtUtil.generateAccessToken(
                user.getId(), user.getUsername(), user.getRoleId(), user.getTokenVersion(), sessionId);
        Map<String, Integer> perms = PermissionService.getUserPermissionLevels(user.getId());
        Map<String, Object> userDto = buildUserProfileDto(user, perms);

        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("accessToken", accessToken);
        data.put("refreshToken", rawRefreshToken);
        data.put("sessionId", sessionId);
        data.put("user", userDto);
        data.put("permissions", perms);
        data.put("guestPermissions", PermissionService.getGuestPermissionLevels());
        data.put("mustChangePassword", Boolean.valueOf(user.isMustChangePassword()));
        data.put("needsBinding", Boolean.valueOf(isBindingRequiredFor(user, perms)));
        return data;
    }

    public static Map<String, Object> refreshSession(String rawRefreshToken, String ip, String userAgent) {
        if (rawRefreshToken == null || rawRefreshToken.trim().isEmpty()) return null;
        final long now = System.currentTimeMillis() / 1000L;
        final String tokenHash = PasswordUtil.sha256Hex(rawRefreshToken.trim());

        return MyBatisFactory.doWorkWithResult(session -> {
            SessionMapper sessionMapper = session.getMapper(SessionMapper.class);
            UserMapper userMapper = session.getMapper(UserMapper.class);

            UserSession existing = sessionMapper.findByTokenHash(tokenHash);
            if (existing == null || existing.getExpiresAt() < now) {
                if (existing != null) sessionMapper.deleteByTokenHash(tokenHash);
                return null;
            }

            UserAccount user = userMapper.findById(existing.getUserId());
            if (user == null || !"active".equals(user.getStatus()) || user.getTokenVersion() != existing.getTokenVersion()) {
                sessionMapper.deleteSessionForUser(existing.getSessionId(), existing.getUserId());
                return null;
            }

            String nextRawRefresh = "uspr_" + PasswordUtil.randomUrlSafeToken(32);
            String nextHash = PasswordUtil.sha256Hex(nextRawRefresh);
            int days = existing.isRememberMe() ? SettingsService.getSessionTimeoutDays() : 1;
            long nextExpires = now + days * 86400L;
            sessionMapper.rotateRefreshToken(existing.getSessionId(), nextHash, user.getTokenVersion(), now, nextExpires);

            String accessToken = JwtUtil.generateAccessToken(
                    user.getId(), user.getUsername(), user.getRoleId(), user.getTokenVersion(), existing.getSessionId());
            Map<String, Integer> perms = PermissionService.getUserPermissionLevels(user.getId());

            Map<String, Object> data = new LinkedHashMap<String, Object>();
            data.put("accessToken", accessToken);
            data.put("refreshToken", nextRawRefresh);
            data.put("sessionId", existing.getSessionId());
            data.put("user", buildUserProfileDto(user, perms));
            data.put("permissions", perms);
            data.put("guestPermissions", PermissionService.getGuestPermissionLevels());
            data.put("mustChangePassword", Boolean.valueOf(user.isMustChangePassword()));
            data.put("needsBinding", Boolean.valueOf(isBindingRequiredFor(user, perms)));
            return data;
        });
    }

    public static void logout(String rawRefreshToken, String sessionId, Integer userId) {
        MyBatisFactory.doWork(session -> {
            SessionMapper mapper = session.getMapper(SessionMapper.class);
            if (rawRefreshToken != null && !rawRefreshToken.trim().isEmpty()) {
                mapper.deleteByTokenHash(PasswordUtil.sha256Hex(rawRefreshToken.trim()));
            }
            if (sessionId != null && userId != null) {
                mapper.deleteSessionForUser(sessionId, userId.intValue());
            }
        });
    }

    // ── Registration & Activation ──

    public static final class RegisterRequest {
        private final String username;
        private final String nickname;
        private final String email;
        private final String password;
        private final String bindingCode;
        private final String captcha;

        public RegisterRequest(String username, String nickname, String email, String password,
                               String bindingCode, String captcha) {
            this.username = username;
            this.nickname = nickname;
            this.email = email;
            this.password = password;
            this.bindingCode = bindingCode;
            this.captcha = captcha;
        }

        public String username() { return username; }
        public String nickname() { return nickname; }
        public String email() { return email; }
        public String password() { return password; }
        public String bindingCode() { return bindingCode; }
        public String captcha() { return captcha; }
    }

    public static final class ActionResult {
        private final boolean success;
        private final String errorCode;
        private final String message;
        private final boolean activationRequired;

        public ActionResult(boolean success, String errorCode, String message, boolean activationRequired) {
            this.success = success;
            this.errorCode = errorCode;
            this.message = message;
            this.activationRequired = activationRequired;
        }

        public boolean success() { return success; }
        public String errorCode() { return errorCode; }
        public String message() { return message; }
        public boolean activationRequired() { return activationRequired; }
    }

    public static ActionResult register(RegisterRequest req, String requestBaseUrl, String locale) {
        if (!SettingsService.isAllowRegistration()) {
            return new ActionResult(false, "REGISTRATION_DISABLED", "Public registration is disabled", false);
        }
        String captchaPolicy = SettingsService.getCaptchaPolicy();
        if (!"off".equalsIgnoreCase(captchaPolicy)) {
            if (req.captcha() == null || !CaptchaService.verifyPayload(req.captcha())) {
                return new ActionResult(false, "CAPTCHA_REQUIRED", "Security verification failed", false);
            }
        }
        if (req.username() == null || !req.username().trim().matches("^[a-zA-Z0-9_]{3,32}$")) {
            return new ActionResult(false, "INVALID_USERNAME", "Username must be 3-32 alphanumeric or underscore characters", false);
        }
        if (req.password() == null || req.password().length() < 6) {
            return new ActionResult(false, "WEAK_PASSWORD", "Password must be at least 6 characters", false);
        }

        final boolean requireEmailVer = SettingsService.isRequireEmailVerification();
        final String cleanEmail = (req.email() != null && !req.email().trim().isEmpty()) ? req.email().trim() : null;
        if (requireEmailVer && cleanEmail == null) {
            return new ActionResult(false, "EMAIL_REQUIRED", "Email address is required", false);
        }
        if (cleanEmail != null && !cleanEmail.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            return new ActionResult(false, "INVALID_EMAIL", "Invalid email format", false);
        }

        boolean requireBinding = SettingsService.isRequireBindingOnRegister();
        final String codeInput = (req.bindingCode() != null && !req.bindingCode().trim().isEmpty())
                ? req.bindingCode().trim().toUpperCase(java.util.Locale.ROOT) : null;
        if (requireBinding && codeInput == null) {
            return new ActionResult(false, "BINDING_CODE_REQUIRED", "Minecraft binding code is required", false);
        }

        final String cleanUser = req.username().trim();
        final String cleanNick = (req.nickname() != null && !req.nickname().trim().isEmpty()) ? req.nickname().trim() : cleanUser;
        final long now = System.currentTimeMillis() / 1000L;

        return MyBatisFactory.doWorkWithResult(session -> {
            UserMapper userMapper = session.getMapper(UserMapper.class);
            PlayerBindingMapper bindingMapper = session.getMapper(PlayerBindingMapper.class);
            SecurityMapper secMapper = session.getMapper(SecurityMapper.class);
            RoleMapper roleMapper = session.getMapper(RoleMapper.class);

            if (userMapper.countByUsername(cleanUser) > 0) {
                return new ActionResult(false, "USERNAME_EXISTS", "Username already exists", false);
            }
            if (cleanEmail != null && userMapper.countByEmailExclude(cleanEmail, -1) > 0) {
                return new ActionResult(false, "EMAIL_EXISTS", "Email already in use", false);
            }

            String bindPlayerUuid = null;
            if (codeInput != null) {
                BindingCodeInfo codeInfo = bindingMapper.findByCode(codeInput);
                if (codeInfo == null || codeInfo.getConsumedAt() != null || codeInfo.getExpiresAt() < now) {
                    return new ActionResult(false, "INVALID_BINDING_CODE", "Invalid or expired binding code", false);
                }
                if (bindingMapper.countByPlayerUuid(codeInfo.getPlayerUuid()) > 0) {
                    return new ActionResult(false, "PLAYER_ALREADY_BOUND", "Player already bound to another account", false);
                }
                bindPlayerUuid = codeInfo.getPlayerUuid();
            }

            int roleId = SettingsService.getDefaultRegisterRoleId();
            RoleInfo targetRole = roleMapper.findById(roleId);
            if (targetRole == null || "guest".equalsIgnoreCase(targetRole.getName()) || roleId == PermissionService.ADMIN_ROLE_ID) {
                roleId = PermissionService.USER_ROLE_ID;
            }

            UserAccount acc = new UserAccount();
            acc.setUsername(cleanUser);
            acc.setNickname(cleanNick);
            acc.setEmail(cleanEmail);
            acc.setPasswordHash(PasswordUtil.hash(req.password()));
            acc.setRoleId(roleId);
            acc.setStatus(requireEmailVer ? "inactive" : "active");
            acc.setMustChangePassword(false);
            acc.setRequire2fa(false);
            acc.setLocale(locale != null ? locale : SettingsService.getDefaultLocale());
            acc.setCreatedAt(now);
            acc.setUpdatedAt(now);
            userMapper.insert(acc);

            if (bindPlayerUuid != null) {
                bindingMapper.insertBinding(acc.getId(), bindPlayerUuid, now);
                bindingMapper.markCodeConsumed(codeInput, now);
            }

            if (requireEmailVer && cleanEmail != null) {
                String rawToken = PasswordUtil.randomHex(16);
                RecoveryToken rt = new RecoveryToken();
                rt.setUserId(acc.getId());
                rt.setTokenHash(PasswordUtil.sha256Hex(rawToken));
                rt.setTokenType("activation");
                rt.setSource("email");
                rt.setExpiresAt(now + ACTIVATION_TTL_SECONDS);
                rt.setCreatedAt(now);
                secMapper.insertRecoveryToken(rt);

                sendActionEmailAsync(acc.getLocale(), "activation", cleanEmail, cleanUser, requestBaseUrl, rawToken);
            }

            return new ActionResult(true, null, requireEmailVer
                    ? "Account created, please check email to activate"
                    : "Registration successful", requireEmailVer);
        });
    }

    public static ActionResult activateAccount(String rawToken) {
        if (rawToken == null || rawToken.trim().isEmpty()) {
            return new ActionResult(false, "INVALID_TOKEN", "Activation token is required", false);
        }
        final long now = System.currentTimeMillis() / 1000L;
        final String hash = PasswordUtil.sha256Hex(rawToken.trim());

        return MyBatisFactory.doWorkWithResult(session -> {
            SecurityMapper secMapper = session.getMapper(SecurityMapper.class);
            UserMapper userMapper = session.getMapper(UserMapper.class);

            RecoveryToken rt = secMapper.findRecoveryToken(hash, "activation");
            if (rt == null || rt.getUsedAt() != null || rt.getExpiresAt() < now) {
                return new ActionResult(false, "INVALID_OR_EXPIRED_TOKEN", "Activation token is invalid or expired", false);
            }

            userMapper.updateStatus(rt.getUserId(), "active", now);
            secMapper.markRecoveryTokenUsed(rt.getId(), now);
            return new ActionResult(true, null, "Account activated", false);
        });
    }

    // ── Password & 2FA Recovery ──

    public static ActionResult sendRecoveryEmail(String account, final String tokenType, String captcha,
                                                 String requestBaseUrl, String locale) {
        if (!SettingsService.isSmtpEnabled()) {
            return new ActionResult(false, "SMTP_DISABLED", "SMTP email service is not enabled; use /usp recover in Minecraft if your player is bound", false);
        }
        String captchaPolicy = SettingsService.getCaptchaPolicy();
        if (!"off".equalsIgnoreCase(captchaPolicy)) {
            if (captcha == null || !CaptchaService.verifyPayload(captcha)) {
                return new ActionResult(false, "CAPTCHA_REQUIRED", "Security verification failed", false);
            }
        }
        if (account == null || account.trim().isEmpty()) {
            return new ActionResult(false, "ACCOUNT_REQUIRED", "Username or email is required", false);
        }

        final String trimmedAcc = account.trim();
        final UserAccount user = MyBatisFactory.doWorkWithResult(session ->
                session.getMapper(UserMapper.class).findByUsernameOrEmail(trimmedAcc));
        if (user == null || user.getEmail() == null || user.getEmail().trim().isEmpty()) {
            // Do not leak whether account exists; respond success cleanly
            return new ActionResult(true, null, "If a verified email matches, instructions have been sent", false);
        }

        final long now = System.currentTimeMillis() / 1000L;
        String rawToken = PasswordUtil.randomHex(16);
        final String tokenHash = PasswordUtil.sha256Hex(rawToken);

        MyBatisFactory.doWork(session -> {
            SecurityMapper secMapper = session.getMapper(SecurityMapper.class);
            secMapper.invalidateUnusedTokens(user.getId(), tokenType, now);
            RecoveryToken rt = new RecoveryToken();
            rt.setUserId(user.getId());
            rt.setTokenHash(tokenHash);
            rt.setTokenType(tokenType);
            rt.setSource("email");
            rt.setExpiresAt(now + PASSWORD_RESET_TTL_SECONDS);
            rt.setCreatedAt(now);
            secMapper.insertRecoveryToken(rt);
        });

        sendActionEmailAsync(locale != null ? locale : user.getLocale(), tokenType, user.getEmail(), user.getUsername(), requestBaseUrl, rawToken);
        return new ActionResult(true, null, "Recovery email sent", false);
    }

    public static ActionResult resetPasswordWithToken(String rawToken, final String newPassword) {
        if (rawToken == null || rawToken.trim().isEmpty()) {
            return new ActionResult(false, "INVALID_TOKEN", "Reset token is required", false);
        }
        if (newPassword == null || newPassword.length() < 6) {
            return new ActionResult(false, "WEAK_PASSWORD", "Password must be at least 6 characters", false);
        }
        final long now = System.currentTimeMillis() / 1000L;
        final String hash = PasswordUtil.sha256Hex(rawToken.trim());

        return MyBatisFactory.doWorkWithResult(session -> {
            SecurityMapper secMapper = session.getMapper(SecurityMapper.class);
            UserMapper userMapper = session.getMapper(UserMapper.class);
            SessionMapper sessionMapper = session.getMapper(SessionMapper.class);

            RecoveryToken rt = secMapper.findRecoveryToken(hash, "password");
            if (rt == null || rt.getUsedAt() != null || rt.getExpiresAt() < now) {
                return new ActionResult(false, "INVALID_OR_EXPIRED_TOKEN", "Reset token is invalid or expired", false);
            }

            userMapper.updatePassword(rt.getUserId(), PasswordUtil.hash(newPassword), 0, now);
            userMapper.updateLoginFailures(rt.getUserId(), 0, null, now);
            sessionMapper.deleteAllForUser(rt.getUserId());
            secMapper.markRecoveryTokenUsed(rt.getId(), now);
            return new ActionResult(true, null, "Password has been reset", false);
        });
    }

    public static ActionResult reset2faWithToken(String rawToken) {
        if (rawToken == null || rawToken.trim().isEmpty()) {
            return new ActionResult(false, "INVALID_TOKEN", "Reset token is required", false);
        }
        final long now = System.currentTimeMillis() / 1000L;
        final String hash = PasswordUtil.sha256Hex(rawToken.trim());

        return MyBatisFactory.doWorkWithResult(session -> {
            SecurityMapper secMapper = session.getMapper(SecurityMapper.class);
            UserMapper userMapper = session.getMapper(UserMapper.class);
            SessionMapper sessionMapper = session.getMapper(SessionMapper.class);

            RecoveryToken rt = secMapper.findRecoveryToken(hash, "2fa");
            if (rt == null || rt.getUsedAt() != null || rt.getExpiresAt() < now) {
                return new ActionResult(false, "INVALID_OR_EXPIRED_TOKEN", "Reset token is invalid or expired", false);
            }

            secMapper.deleteTotpByUserId(rt.getUserId());
            userMapper.bumpTokenVersion(rt.getUserId(), now);
            sessionMapper.deleteAllForUser(rt.getUserId());
            secMapper.markRecoveryTokenUsed(rt.getId(), now);
            return new ActionResult(true, null, "Two-factor authentication has been removed", false);
        });
    }

    // ── Helpers ──

    public static Map<String, Object> buildUserProfileDto(UserAccount user, Map<String, Integer> perms) {
        Map<String, Object> info = new LinkedHashMap<String, Object>();
        info.put("id", Integer.valueOf(user.getId()));
        info.put("username", user.getUsername());
        info.put("nickname", user.getNickname());
        info.put("email", user.getEmail());
        info.put("roleId", Integer.valueOf(user.getRoleId()));
        info.put("roleName", user.getRoleName());
        info.put("roleDisplayName", user.getRoleDisplayName());
        info.put("status", user.getStatus());
        info.put("mustChangePassword", Boolean.valueOf(user.isMustChangePassword()));
        info.put("require2fa", Boolean.valueOf(user.isRequire2fa()));
        info.put("totpEnabled", Boolean.valueOf(user.isTotpEnabled()));
        info.put("bound", Boolean.valueOf(user.isBound()));
        info.put("playerUuid", user.getPlayerUuid());
        info.put("playerName", user.getPlayerName());
        info.put("boundAt", user.getBoundAt());
        info.put("locale", user.getLocale());
        info.put("createdAt", Long.valueOf(user.getCreatedAt()));
        info.put("permissions", perms);
        info.put("needsBinding", Boolean.valueOf(isBindingRequiredFor(user, perms)));
        info.put("needs2fa", Boolean.valueOf((SettingsService.isForce2fa() || user.isRequire2fa()) && !user.isTotpEnabled()));
        return info;
    }

    private static boolean isBindingRequiredFor(UserAccount user, Map<String, Integer> perms) {
        if (!SettingsService.isForcePlayerBinding()) return false;
        Integer adminLvl = perms.get("admin");
        if (adminLvl != null && adminLvl.intValue() >= PermissionLevel.FULL.getLevel()) return false;
        return !user.isBound();
    }

    private static void recordLoginAudit(final int userId, final String ip, final String userAgent,
                                         final boolean success, final String reason) {
        MyBatisFactory.doWork(session -> {
            LoginHistoryEntry entry = new LoginHistoryEntry();
            entry.setUserId(userId);
            entry.setIpAddress(ip != null ? ip : "");
            entry.setUserAgent(userAgent != null ? truncate(userAgent, 255) : "");
            entry.setSuccess(success);
            entry.setFailReason(reason != null ? reason : "");
            entry.setLoginTime(System.currentTimeMillis() / 1000L);
            session.getMapper(SessionMapper.class).insertLoginHistory(entry);
        });
    }

    private static void sendActionEmailAsync(String locale, final String type, final String toEmail, String username,
                                             String fallbackBaseUrl, String rawToken) {
        String configuredBase = SettingsService.getBaseUrl();
        String base = firstBaseUrl(configuredBase);
        if (base.isEmpty()) base = fallbackBaseUrl != null ? fallbackBaseUrl : "";
        base = base.replaceAll("/+$", "");

        String mode;
        if ("activation".equals(type)) {
            mode = "activate";
        } else if ("2fa".equals(type)) {
            mode = "reset-2fa";
        } else {
            mode = "reset-password";
        }

        String actionUrl = base + "/login?mode=" + mode + "&token=" + rawToken;
        String siteName = SettingsService.getSiteName();
        final SmtpMailer.SmtpConfig smtpCfg = SettingsService.getSmtpConfig();
        final EmailTemplateRenderer.RenderedEmail rendered = EmailTemplateRenderer.renderActionEmail(
                locale, siteName, type, username, actionUrl, rawToken);

        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    SmtpMailer.sendHtmlEmail(smtpCfg, toEmail, rendered.getSubject(), rendered.getHtml(), rendered.getText());
                } catch (Exception e) {
                    PanelLog.get().warn("Failed to send {} email to {}: {}", type, toEmail, e.getMessage());
                }
            }
        }, "usp-mail-" + type);
        t.setDaemon(true);
        t.start();
    }

    private static String firstBaseUrl(String raw) {
        if (raw == null || raw.trim().isEmpty()) return "";
        String[] parts = raw.split("[\\r\\n,]+");
        for (String p : parts) {
            String trimmed = p.trim();
            if (!trimmed.isEmpty()) return trimmed;
        }
        return "";
    }

    private static String truncate(String s, int maxLen) {
        return s.length() <= maxLen ? s : s.substring(0, maxLen);
    }
}
