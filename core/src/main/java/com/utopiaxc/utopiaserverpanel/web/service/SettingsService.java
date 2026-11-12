package com.utopiaxc.utopiaserverpanel.web.service;

import com.utopiaxc.utopiaserverpanel.auth.CryptoUtil;
import com.utopiaxc.utopiaserverpanel.mail.SmtpMailer;
import com.utopiaxc.utopiaserverpanel.web.db.MyBatisFactory;
import com.utopiaxc.utopiaserverpanel.web.db.mapper.SettingsMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Centralized cached access over system_settings table.
 * Separates public safe site settings from private admin/SMTP settings.
 */
public final class SettingsService {
    private static final ConcurrentHashMap<String, String> CACHE = new ConcurrentHashMap<>();
    private static volatile boolean loaded = false;

    private SettingsService() {}

    public static synchronized void reload() {
        CACHE.clear();
        MyBatisFactory.doWork(session -> {
            List<Map<String, Object>> all = session.getMapper(SettingsMapper.class).getAll();
            for (Map<String, Object> row : all) {
                String k = (String) row.get("configKey");
                String v = (String) row.get("configValue");
                if (k != null && v != null) {
                    CACHE.put(k, v);
                }
            }
        });
        loaded = true;
    }

    private static void ensureLoaded() {
        if (!loaded) reload();
    }

    public static String get(String key, String fallback) {
        ensureLoaded();
        String val = CACHE.get(key);
        return (val != null) ? val : fallback;
    }

    public static boolean getBool(String key, boolean fallback) {
        String val = get(key, null);
        if (val == null || val.isEmpty()) return fallback;
        return "true".equalsIgnoreCase(val) || "1".equals(val);
    }

    public static int getInt(String key, int fallback) {
        String val = get(key, null);
        if (val == null || val.isEmpty()) return fallback;
        try {
            return Integer.parseInt(val);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public static void set(String key, String value) {
        long now = System.currentTimeMillis() / 1000L;
        String safeVal = value != null ? value : "";
        MyBatisFactory.doWork(session ->
                session.getMapper(SettingsMapper.class).setValue(key, safeVal, now));
        CACHE.put(key, safeVal);
    }

    public static void setMany(Map<String, String> entries) {
        long now = System.currentTimeMillis() / 1000L;
        MyBatisFactory.doWork(session -> {
            SettingsMapper mapper = session.getMapper(SettingsMapper.class);
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                String val = entry.getValue() != null ? entry.getValue() : "";
                mapper.setValue(entry.getKey(), val, now);
                CACHE.put(entry.getKey(), val);
            }
        });
    }

    // ── Convenience Accessors ──

    public static boolean isSetupCompleted() {
        return getBool("setup_completed", false);
    }

    public static String getSiteName() {
        String name = get("site_name", "Utopia Server Panel");
        return name.trim().isEmpty() ? "Utopia Server Panel" : name;
    }

    public static String getBaseUrl() {
        return get("base_url", "");
    }

    public static String getDefaultLocale() {
        return get("default_locale", "zh-CN");
    }

    public static boolean isAllowRegistration() {
        return getBool("allow_registration", true);
    }

    public static int getDefaultRegisterRoleId() {
        return getInt("default_register_role_id", PermissionService.USER_ROLE_ID);
    }

    public static boolean isRequireEmailVerification() {
        return isSmtpEnabled() && getBool("require_email_verification", false);
    }

    public static boolean isRequireBindingOnRegister() {
        return getBool("require_binding_on_register", false);
    }

    public static boolean isForcePlayerBinding() {
        return getBool("force_player_binding", false);
    }

    public static boolean isSmtpEnabled() {
        return getBool("smtp_enabled", false) && !get("smtp_host", "").trim().isEmpty();
    }

    public static String getCaptchaPolicy() {
        return get("captcha_policy", "auto");
    }

    public static int getCaptchaDifficulty() {
        return Math.max(10_000, getInt("captcha_difficulty", 80_000));
    }

    public static int getSessionTimeoutDays() {
        return Math.max(1, getInt("session_timeout_days", 7));
    }

    public static boolean isForce2fa() {
        return getBool("force_2fa", false);
    }

    public static int getMaxFailedLogins() {
        return Math.max(3, getInt("max_failed_logins", 5));
    }

    public static int getLockoutMinutes() {
        return Math.max(1, getInt("lockout_minutes", 15));
    }

    public static SmtpMailer.SmtpConfig getSmtpConfig() {
        String encryptedPass = get("smtp_password", "");
        String rawPass = "";
        if (!encryptedPass.isEmpty()) {
            try {
                rawPass = CryptoUtil.decrypt(encryptedPass);
            } catch (Exception e) {
                rawPass = "";
            }
        }
        return new SmtpMailer.SmtpConfig(
                get("smtp_host", ""),
                getInt("smtp_port", 465),
                get("smtp_encryption", "ssl"),
                get("smtp_user", ""),
                rawPass,
                get("smtp_from", ""),
                get("smtp_from_name", getSiteName())
        );
    }

    public static void saveSmtpPassword(String plainPassword) {
        if (plainPassword == null || plainPassword.isEmpty()) return;
        set("smtp_password", CryptoUtil.encrypt(plainPassword));
    }

    public static void clearSmtpPassword() {
        set("smtp_password", "");
    }

    /**
     * Publicly visible metadata (NEVER exposes SMTP password, host, credentials, or crypto keys).
     */
    public static Map<String, Object> getPublicSiteInfo() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("setupCompleted", isSetupCompleted());
        info.put("siteName", getSiteName());
        info.put("siteDescription", get("site_description", "Minecraft Server Management Panel"));
        info.put("baseUrl", getBaseUrl());
        info.put("defaultLocale", getDefaultLocale());
        info.put("allowRegistration", isAllowRegistration());
        info.put("requireEmailVerification", isRequireEmailVerification());
        info.put("requireBindingOnRegister", isRequireBindingOnRegister());
        info.put("forcePlayerBinding", isForcePlayerBinding());
        info.put("smtpEnabled", isSmtpEnabled());
        info.put("captchaPolicy", getCaptchaPolicy());
        info.put("force2fa", isForce2fa());
        info.put("guestPermissions", PermissionService.getGuestPermissionLevels());
        info.put("permissionsCatalog", PermissionService.listPermissionDefinitions());
        info.put("monitorShowPlayerNames", MonitorConfigService.isShowPlayerNames());
        return info;
    }

    /**
     * Complete categorized settings for Administrators (masks secrets safely).
     */
    public static Map<String, Object> getAdminSettings() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("siteName", getSiteName());
        out.put("siteDescription", get("site_description", ""));
        out.put("baseUrl", getBaseUrl());
        out.put("defaultLocale", getDefaultLocale());

        out.put("allowRegistration", isAllowRegistration());
        out.put("defaultRegisterRoleId", getDefaultRegisterRoleId());
        out.put("requireEmailVerification", getBool("require_email_verification", false));
        out.put("requireBindingOnRegister", isRequireBindingOnRegister());
        out.put("forcePlayerBinding", isForcePlayerBinding());

        out.put("captchaPolicy", getCaptchaPolicy());
        out.put("captchaDifficulty", getCaptchaDifficulty());
        out.put("sessionTimeoutDays", getSessionTimeoutDays());
        out.put("force2fa", isForce2fa());
        out.put("maxFailedLogins", getMaxFailedLogins());
        out.put("lockoutMinutes", getLockoutMinutes());

        out.put("smtpEnabled", getBool("smtp_enabled", false));
        out.put("smtpHost", get("smtp_host", ""));
        out.put("smtpPort", getInt("smtp_port", 465));
        out.put("smtpEncryption", get("smtp_encryption", "ssl"));
        out.put("smtpUser", get("smtp_user", ""));
        out.put("hasSmtpPassword", !get("smtp_password", "").isEmpty());
        out.put("smtpFrom", get("smtp_from", ""));
        out.put("smtpFromName", get("smtp_from_name", getSiteName()));

        out.put("monitorEnabled", MonitorConfigService.isEnabled());
        out.put("monitorInterval", MonitorConfigService.getIntervalSeconds());
        out.put("monitorRetentionDays", MonitorConfigService.getRetentionDays());
        out.put("monitorShowPlayerNames", MonitorConfigService.isShowPlayerNames());
        return out;
    }
}

