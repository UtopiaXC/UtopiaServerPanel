package com.utopiaxc.utopiaserverpanel.web.service;

import com.utopiaxc.utopiaserverpanel.auth.PasswordUtil;
import com.utopiaxc.utopiaserverpanel.config.PanelConfig;
import com.utopiaxc.utopiaserverpanel.logging.PanelLog;
import com.utopiaxc.utopiaserverpanel.web.db.MyBatisFactory;
import com.utopiaxc.utopiaserverpanel.web.db.mapper.UserMapper;
import com.utopiaxc.utopiaserverpanel.web.db.model.UserAccount;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages first-run initialization wizard state and one-time Setup Token lifecycle.
 * Setup tokens are persisted into config/utopia_server_panel.toml and readable
 * by OPs via '/usp setup token'. Never printed to server console logs.
 */
public final class SetupService {
    private static volatile String activeToken;
    private static final ConcurrentHashMap<String, Integer> VERIFY_FAILS = new ConcurrentHashMap<>();

    private SetupService() {}

    public static synchronized void initializeOnStartup() {
        SettingsService.reload();
        if (SettingsService.isSetupCompleted()) {
            activeToken = null;
            return;
        }
        ensureSetupToken();
    }

    public static synchronized String ensureSetupToken() {
        if (SettingsService.isSetupCompleted()) {
            return null;
        }
        String cfgToken = "";
        try {
            cfgToken = PanelConfig.getSetupToken();
        } catch (Exception ignored) {}

        if (cfgToken != null && !cfgToken.trim().isEmpty()) {
            activeToken = cfgToken.trim();
            return activeToken;
        }
        if (activeToken != null && !activeToken.trim().isEmpty()) {
            return activeToken;
        }

        String generated = "USP-" + PasswordUtil.randomCode(4) + "-" + PasswordUtil.randomCode(4)
                + "-" + PasswordUtil.randomCode(4) + "-" + PasswordUtil.randomCode(4);
        activeToken = generated;
        try {
            PanelConfig.setSetupToken(generated);
        } catch (Exception e) {
            PanelLog.get().warn("Could not save setupToken into config file: {}", e.getMessage());
        }
        return activeToken;
    }

    public static boolean isSetupRequired() {
        return !SettingsService.isSetupCompleted();
    }

    public static boolean verifyToken(String clientIp, String candidateToken) {
        if (!isSetupRequired()) return false;
        String expected = ensureSetupToken();
        if (expected == null || candidateToken == null) return false;

        Integer fails = VERIFY_FAILS.getOrDefault(clientIp, 0);
        if (fails >= 20) {
            return false;
        }

        boolean match = PasswordUtil.constantTimeEquals(expected.trim(), candidateToken.trim());
        if (!match) {
            VERIFY_FAILS.put(clientIp, fails + 1);
            return false;
        }
        VERIFY_FAILS.remove(clientIp);
        return true;
    }

    public static final class SetupRequest {
        private final String token;
        private final String siteName;
        private final String siteDescription;
        private final String baseUrl;
        private final String defaultLocale;
        private final boolean smtpEnabled;
        private final String smtpHost;
        private final int smtpPort;
        private final String smtpEncryption;
        private final String smtpUser;
        private final String smtpPassword;
        private final String smtpFrom;
        private final String smtpFromName;
        private final String adminUsername;
        private final String adminNickname;
        private final String adminEmail;
        private final String adminPassword;

        public SetupRequest(
                String token,
                String siteName,
                String siteDescription,
                String baseUrl,
                String defaultLocale,
                boolean smtpEnabled,
                String smtpHost,
                int smtpPort,
                String smtpEncryption,
                String smtpUser,
                String smtpPassword,
                String smtpFrom,
                String smtpFromName,
                String adminUsername,
                String adminNickname,
                String adminEmail,
                String adminPassword
        ) {
            this.token = token;
            this.siteName = siteName;
            this.siteDescription = siteDescription;
            this.baseUrl = baseUrl;
            this.defaultLocale = defaultLocale;
            this.smtpEnabled = smtpEnabled;
            this.smtpHost = smtpHost;
            this.smtpPort = smtpPort;
            this.smtpEncryption = smtpEncryption;
            this.smtpUser = smtpUser;
            this.smtpPassword = smtpPassword;
            this.smtpFrom = smtpFrom;
            this.smtpFromName = smtpFromName;
            this.adminUsername = adminUsername;
            this.adminNickname = adminNickname;
            this.adminEmail = adminEmail;
            this.adminPassword = adminPassword;
        }

        public String token() { return token; }
        public String siteName() { return siteName; }
        public String siteDescription() { return siteDescription; }
        public String baseUrl() { return baseUrl; }
        public String defaultLocale() { return defaultLocale; }
        public boolean smtpEnabled() { return smtpEnabled; }
        public String smtpHost() { return smtpHost; }
        public int smtpPort() { return smtpPort; }
        public String smtpEncryption() { return smtpEncryption; }
        public String smtpUser() { return smtpUser; }
        public String smtpPassword() { return smtpPassword; }
        public String smtpFrom() { return smtpFrom; }
        public String smtpFromName() { return smtpFromName; }
        public String adminUsername() { return adminUsername; }
        public String adminNickname() { return adminNickname; }
        public String adminEmail() { return adminEmail; }
        public String adminPassword() { return adminPassword; }
    }

    public static final class SetupResult {
        private final boolean success;
        private final String errorCode;
        private final String message;

        public SetupResult(boolean success, String errorCode, String message) {
            this.success = success;
            this.errorCode = errorCode;
            this.message = message;
        }

        public boolean success() { return success; }
        public String errorCode() { return errorCode; }
        public String message() { return message; }
    }

    public static synchronized SetupResult completeSetup(String clientIp, SetupRequest req) {
        if (!isSetupRequired()) {
            return new SetupResult(false, "SETUP_ALREADY_COMPLETED", "Setup has already been completed");
        }
        if (!verifyToken(clientIp, req.token())) {
            return new SetupResult(false, "INVALID_SETUP_TOKEN", "Invalid setup token");
        }
        if (req.adminUsername() == null || !req.adminUsername().trim().matches("^[a-zA-Z0-9_]{3,32}$")) {
            return new SetupResult(false, "INVALID_USERNAME", "Username must be 3-32 alphanumeric or underscore characters");
        }
        if (req.adminPassword() == null || req.adminPassword().length() < 6) {
            return new SetupResult(false, "WEAK_PASSWORD", "Password must be at least 6 characters");
        }
        String email = req.adminEmail() != null && !req.adminEmail().trim().isEmpty() ? req.adminEmail().trim() : null;
        if (req.smtpEnabled() && email == null) {
            return new SetupResult(false, "EMAIL_REQUIRED", "Administrator email is required when SMTP is enabled");
        }
        if (email != null && !email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            return new SetupResult(false, "INVALID_EMAIL", "Invalid email format");
        }

        // Save system settings
        Map<String, String> settings = new LinkedHashMap<>();
        settings.put("site_name", (req.siteName() != null && !req.siteName().trim().isEmpty()) ? req.siteName().trim() : "Utopia Server Panel");
        settings.put("site_description", req.siteDescription() != null ? req.siteDescription().trim() : "");
        settings.put("base_url", req.baseUrl() != null ? req.baseUrl().trim() : "");
        settings.put("default_locale", (req.defaultLocale() != null && !req.defaultLocale().trim().isEmpty()) ? req.defaultLocale().trim() : "zh-CN");

        settings.put("smtp_enabled", String.valueOf(req.smtpEnabled()));
        settings.put("smtp_host", req.smtpHost() != null ? req.smtpHost().trim() : "");
        settings.put("smtp_port", String.valueOf(req.smtpPort() > 0 ? req.smtpPort() : 465));
        settings.put("smtp_encryption", (req.smtpEncryption() != null && !req.smtpEncryption().trim().isEmpty()) ? req.smtpEncryption().trim() : "ssl");
        settings.put("smtp_user", req.smtpUser() != null ? req.smtpUser().trim() : "");
        settings.put("smtp_from", req.smtpFrom() != null ? req.smtpFrom().trim() : "");
        settings.put("smtp_from_name", (req.smtpFromName() != null && !req.smtpFromName().trim().isEmpty())
                ? req.smtpFromName().trim()
                : settings.get("site_name"));

        SettingsService.setMany(settings);
        if (req.smtpPassword() != null && !req.smtpPassword().isEmpty()) {
            SettingsService.saveSmtpPassword(req.smtpPassword());
        }

        // Create initial admin account
        long now = System.currentTimeMillis() / 1000L;
        String username = req.adminUsername().trim();
        String nickname = (req.adminNickname() != null && !req.adminNickname().trim().isEmpty())
                ? req.adminNickname().trim()
                : username;

        MyBatisFactory.doWork(session -> {
            UserMapper userMapper = session.getMapper(UserMapper.class);
            UserAccount admin = new UserAccount();
            admin.setUsername(username);
            admin.setNickname(nickname);
            admin.setEmail(email);
            admin.setPasswordHash(PasswordUtil.hash(req.adminPassword()));
            admin.setRoleId(PermissionService.ADMIN_ROLE_ID);
            admin.setStatus("active");
            admin.setMustChangePassword(false);
            admin.setRequire2fa(false);
            admin.setLocale(settings.get("default_locale"));
            admin.setCreatedAt(now);
            admin.setUpdatedAt(now);
            userMapper.insert(admin);
        });

        SettingsService.set("setup_completed", "true");
        activeToken = null;
        try {
            PanelConfig.setSetupToken("");
        } catch (Exception ignored) {}

        PanelLog.get().info("Initial setup completed for administrator '{}'", username);
        return new SetupResult(true, null, "Setup completed");
    }
}

