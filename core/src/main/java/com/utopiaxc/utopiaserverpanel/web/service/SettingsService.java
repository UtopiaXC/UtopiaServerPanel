package com.utopiaxc.utopiaserverpanel.web.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.utopiaxc.utopiaserverpanel.adapter.AdapterRegistry;
import com.utopiaxc.utopiaserverpanel.adapter.GameAdapter;
import com.utopiaxc.utopiaserverpanel.adapter.model.ServerPropertiesSnapshot;
import com.utopiaxc.utopiaserverpanel.adapter.util.ServerPropertiesReader;
import com.utopiaxc.utopiaserverpanel.auth.CryptoUtil;
import com.utopiaxc.utopiaserverpanel.mail.SmtpMailer;
import com.utopiaxc.utopiaserverpanel.web.context.JsonHelper;
import com.utopiaxc.utopiaserverpanel.web.db.MyBatisFactory;
import com.utopiaxc.utopiaserverpanel.web.db.mapper.SettingsMapper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Centralized cached access over system_settings table.
 * Separates public safe site settings from private admin/SMTP settings.
 */
public final class SettingsService {
    private static final ConcurrentHashMap<String, String> CACHE = new ConcurrentHashMap<>();
    private static volatile boolean loaded = false;
    private static final int MAX_FAVICON_BYTES = 256 * 1024;
    private static final Set<String> ALLOWED_FAVICON_MIMES = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
            "image/png",
            "image/x-icon",
            "image/vnd.microsoft.icon",
            "image/jpeg",
            "image/webp",
            "image/svg+xml"
    )));

    public static final class FaviconPayload {
        private final byte[] bytes;
        private final String contentType;

        public FaviconPayload(byte[] bytes, String contentType) {
            this.bytes = bytes;
            this.contentType = contentType;
        }

        public byte[] getBytes() {
            return bytes;
        }

        public String getContentType() {
            return contentType;
        }
    }

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

    public static boolean hasCustomFavicon() {
        return !get("site_favicon", "").trim().isEmpty();
    }

    public static String getFaviconVersion() {
        String ver = get("site_favicon_version", "default").trim();
        if (ver.isEmpty()) {
            return hasCustomFavicon() ? "1" : "default";
        }
        return ver;
    }

    public static String getFaviconUrl() {
        return "/api/site/favicon?v=" + getFaviconVersion();
    }

    public static FaviconPayload decodeFaviconDataUrl(String dataUrl) {
        if (dataUrl == null) {
            throw new IllegalArgumentException("Favicon dataUrl is required");
        }
        String trimmed = dataUrl.trim();
        if (!trimmed.toLowerCase(Locale.ROOT).startsWith("data:")) {
            throw new IllegalArgumentException("Invalid favicon format");
        }
        int commaIdx = trimmed.indexOf(',');
        if (commaIdx <= 5) {
            throw new IllegalArgumentException("Invalid favicon data URI");
        }
        String meta = trimmed.substring(5, commaIdx);
        String payload = trimmed.substring(commaIdx + 1).replaceAll("\\s+", "");
        boolean isBase64 = meta.toLowerCase(Locale.ROOT).endsWith(";base64");
        if (!isBase64) {
            throw new IllegalArgumentException("Favicon must be base64 encoded");
        }
        String mime = meta.substring(0, meta.length() - ";base64".length()).trim().toLowerCase(Locale.ROOT);
        if (!ALLOWED_FAVICON_MIMES.contains(mime)) {
            throw new IllegalArgumentException("Unsupported image format: " + mime);
        }
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(payload);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid base64 image payload");
        }
        if (decoded.length == 0) {
            throw new IllegalArgumentException("Favicon image is empty");
        }
        if (decoded.length > MAX_FAVICON_BYTES) {
            throw new IllegalArgumentException("Favicon exceeds 256 KB limit");
        }
        return new FaviconPayload(decoded, mime);
    }

    public static void saveFavicon(String dataUrl) {
        FaviconPayload parsed = decodeFaviconDataUrl(dataUrl);
        String canonical = "data:" + parsed.getContentType() + ";base64," + Base64.getEncoder().encodeToString(parsed.getBytes());
        Map<String, String> updates = new LinkedHashMap<>();
        updates.put("site_favicon", canonical);
        updates.put("site_favicon_version", String.valueOf(System.currentTimeMillis()));
        setMany(updates);
    }

    public static void resetFavicon() {
        Map<String, String> updates = new LinkedHashMap<>();
        updates.put("site_favicon", "");
        updates.put("site_favicon_version", "default");
        setMany(updates);
    }

    public static FaviconPayload getCustomFaviconPayload() {
        String raw = get("site_favicon", "").trim();
        if (raw.isEmpty()) {
            return null;
        }
        try {
            return decodeFaviconDataUrl(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
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
        info.put("hasCustomFavicon", Boolean.valueOf(hasCustomFavicon()));
        info.put("faviconVersion", getFaviconVersion());
        info.put("faviconUrl", getFaviconUrl());
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
        info.put("mapEnabled", Boolean.valueOf(isMapEnabled()));
        info.put("serverWhitelistEnabled", Boolean.valueOf(isServerWhitelistEnabled()));
        info.put("whitelistManagementEnabled", Boolean.valueOf(isWhitelistManagementEnabled()));
        info.put("whitelistFeatureVisible", Boolean.valueOf(isWhitelistFeatureVisible()));
        info.put("whitelistAllowGuest", Boolean.valueOf(PermissionService.guestMeetsRequirement("whitelist_apply:2")));
        info.put("whitelistSocialRequired", Boolean.valueOf(isWhitelistSocialRequired()));
        info.put("whitelistRequiredSocialPlatforms", getWhitelistRequiredSocialPlatforms());
        info.put("chatSyncEnabled", Boolean.valueOf(getBool("chat_sync_enabled", true)));
        info.put("webChatSendEnabled", Boolean.valueOf(getBool("web_chat_send_enabled", true)));
        return info;
    }

    public static boolean isMapEnabled() {
        return getBool("map_enabled", true);
    }

    public static boolean isWhitelistManagementEnabled() {
        return getBool("whitelist_management_enabled", false);
    }

    public static boolean isServerWhitelistEnabled() {
        try {
            GameAdapter adapter = AdapterRegistry.getAdapter();
            if (adapter == null) return false;
            ServerPropertiesSnapshot snap = adapter.getServerProperties();
            if (snap != null) {
                return snap.isWhiteList();
            }
            Properties raw = ServerPropertiesReader.loadRawServerProperties(adapter.getServerDirectory());
            return Boolean.parseBoolean(raw.getProperty("white-list", "false"));
        } catch (Throwable e) {
            return false;
        }
    }

    public static boolean isWhitelistFeatureVisible() {
        return isServerWhitelistEnabled() && isWhitelistManagementEnabled();
    }

    public static boolean isWhitelistSendEmail() {
        return isSmtpEnabled() && getBool("whitelist_send_email", false);
    }

    public static boolean isWhitelistSocialRequired() {
        return getBool("whitelist_social_required", false);
    }

    public static List<String> getWhitelistRequiredSocialPlatforms() {
        String raw = get("whitelist_required_social_platforms", "[]");
        if (raw == null || raw.trim().isEmpty()) {
            return Collections.emptyList();
        }
        try {
            JsonArray arr = JsonHelper.parseArray(raw);
            List<String> out = new ArrayList<>();
            for (JsonElement el : arr) {
                if (el != null && el.isJsonPrimitive()) {
                    String p = el.getAsString().trim();
                    if (!p.isEmpty() && !out.contains(p)) {
                        out.add(p);
                    }
                }
            }
            return out;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    public static void setGuestWhitelistApplyAllowed(boolean allow) {
        Map<String, Integer> levels = new LinkedHashMap<>(PermissionService.getGuestPermissionLevels());
        levels.put("whitelist_apply", Integer.valueOf(allow ? PermissionLevel.FULL.getLevel() : PermissionLevel.DENY.getLevel()));
        PermissionService.setRoleLevels(PermissionService.GUEST_ROLE_ID, levels);
    }

    public static boolean isServerOnlineMode() {
        try {
            GameAdapter adapter = AdapterRegistry.getAdapter();
            if (adapter == null) return true;
            ServerPropertiesSnapshot snap = adapter.getServerProperties();
            if (snap != null) {
                return snap.isOnlineMode();
            }
            Properties raw = ServerPropertiesReader.loadRawServerProperties(adapter.getServerDirectory());
            return Boolean.parseBoolean(raw.getProperty("online-mode", "true"));
        } catch (Throwable e) {
            return true;
        }
    }

    public static String getMapLayerVisibility(String layer) {
        String raw = get("map_vis_" + layer, "readonly");
        return "full".equalsIgnoreCase(raw) ? "full" : "readonly";
    }

    private static String normalizeVis(String key, String fallback) {
        String raw = get(key, fallback);
        if ("full".equalsIgnoreCase(raw)) return "full";
        if ("readonly".equalsIgnoreCase(raw)) return "readonly";
        return fallback;
    }

    public static Map<String, String> getMapLayerVisibility() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("terrain", normalizeVis("map_vis_terrain", "readonly"));
        m.put("netherCeiling", normalizeVis("map_vis_nether_ceiling", "readonly"));
        m.put("grid", normalizeVis("map_vis_grid", "readonly"));
        m.put("coordinates", normalizeVis("map_vis_coordinates", "readonly"));
        m.put("waterDepth", normalizeVis("map_vis_water_depth", "readonly"));
        m.put("heightShading", normalizeVis("map_vis_height_shading", "readonly"));
        m.put("biomeTint", normalizeVis("map_vis_biome_tint", "readonly"));
        m.put("players", normalizeVis("map_vis_players", "readonly"));
        m.put("waypoints", normalizeVis("map_vis_waypoints", "readonly"));
        m.put("mobsHostile", normalizeVis("map_vis_mobs_hostile", "full"));
        m.put("mobsPassive", normalizeVis("map_vis_mobs_passive", "full"));
        m.put("entities", normalizeVis("map_vis_entities", "full"));
        m.put("structures", normalizeVis("map_vis_structures", "full"));
        m.put("teleport", normalizeVis("map_vis_teleport", "full"));
        return m;
    }

    public static boolean isWebChatSendEnabled() {
        return getBool("chat_sync_enabled", true) && getBool("web_chat_send_enabled", true);
    }

    public static String getString(String key, String fallback) {
        return get(key, fallback);
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
        out.put("hasCustomFavicon", Boolean.valueOf(hasCustomFavicon()));
        out.put("faviconVersion", getFaviconVersion());
        out.put("faviconUrl", getFaviconUrl());

        out.put("allowRegistration", isAllowRegistration());
        out.put("defaultRegisterRoleId", getDefaultRegisterRoleId());
        out.put("requireEmailVerification", Boolean.valueOf(isRequireEmailVerification()));
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

        // Map settings
        Map<String, String> mapVis = getMapLayerVisibility();
        out.put("mapEnabled", Boolean.valueOf(isMapEnabled()));
        out.put("mapRenderMode", get("map_render_mode", "hybrid"));
        out.put("mapMaxChunksPerTick", Integer.valueOf(getInt("map_max_chunks_per_tick", 2)));
        out.put("mapCacheMaxMb", Integer.valueOf(getInt("map_cache_max_mb", 512)));
        out.put("mapVisTerrain", mapVis.get("terrain"));
        out.put("mapVisNetherCeiling", mapVis.get("netherCeiling"));
        out.put("mapVisGrid", mapVis.get("grid"));
        out.put("mapVisCoordinates", mapVis.get("coordinates"));
        out.put("mapVisWaterDepth", mapVis.get("waterDepth"));
        out.put("mapVisHeightShading", mapVis.get("heightShading"));
        out.put("mapVisBiomeTint", mapVis.get("biomeTint"));
        out.put("mapVisPlayers", mapVis.get("players"));
        out.put("mapVisWaypoints", mapVis.get("waypoints"));
        out.put("mapVisMobsHostile", mapVis.get("mobsHostile"));
        out.put("mapVisMobsPassive", mapVis.get("mobsPassive"));
        out.put("mapVisEntities", mapVis.get("entities"));
        out.put("mapVisStructures", mapVis.get("structures"));
        out.put("mapVisTeleport", mapVis.get("teleport"));

        // Whitelist management & review
        out.put("serverWhitelistEnabled", Boolean.valueOf(isServerWhitelistEnabled()));
        out.put("whitelistManagementEnabled", Boolean.valueOf(isWhitelistManagementEnabled()));
        out.put("whitelistFeatureVisible", Boolean.valueOf(isWhitelistFeatureVisible()));
        out.put("whitelistAllowGuest", Boolean.valueOf(PermissionService.guestMeetsRequirement("whitelist_apply:2")));
        out.put("whitelistApplyCooldownSec", Integer.valueOf(getInt("whitelist_apply_cooldown_sec", 60)));
        out.put("whitelistMaxPendingPerIp", Integer.valueOf(getInt("whitelist_max_pending_per_ip", 5)));
        out.put("whitelistSendEmail", Boolean.valueOf(isWhitelistSendEmail()));
        out.put("whitelistSocialRequired", Boolean.valueOf(isWhitelistSocialRequired()));
        out.put("whitelistRequiredSocialPlatforms", getWhitelistRequiredSocialPlatforms());

        // Restart & chat
        out.put("serverRestartCommand", get("server_restart_command", ""));
        out.put("serverRestartHint", get("server_restart_hint", ""));
        out.put("chatSyncEnabled", Boolean.valueOf(getBool("chat_sync_enabled", true)));
        out.put("webChatSendEnabled", Boolean.valueOf(getBool("web_chat_send_enabled", true)));
        out.put("chatRetentionDays", Integer.valueOf(getInt("chat_retention_days", 30)));
        return out;
    }
}

