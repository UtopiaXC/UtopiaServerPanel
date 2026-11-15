package com.utopiaxc.utopiaserverpanel.web.db.model;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.utopiaxc.utopiaserverpanel.web.context.JsonHelper;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Per-user visibility controls for the public player profile page (/{username}).
 * By default only username and avatar are public; all other fields and MC data are hidden.
 */
public class ProfilePrivacyConfig {
    private boolean publicProfileEnabled = true;
    private boolean showUsername = true;
    private boolean showAvatar = true;
    private boolean showNickname = false;
    private boolean showBio = false;
    private boolean showEmail = false;
    private boolean showGender = false;
    private boolean showBirthday = false;
    private boolean showSocialAccounts = false;
    private boolean showRole = false;
    private boolean showRegisteredAt = false;
    private boolean showMcCharacters = false;
    private boolean showMcOnlineStatus = false;
    private boolean showMcPlaytime = false;
    private boolean showMcAchievements = false;
    private boolean showMcStats = false;

    public static ProfilePrivacyConfig defaults() {
        return new ProfilePrivacyConfig();
    }

    public static ProfilePrivacyConfig fromJson(String rawJson) {
        ProfilePrivacyConfig cfg = new ProfilePrivacyConfig();
        if (rawJson == null || rawJson.trim().isEmpty()) {
            return cfg;
        }
        try {
            JsonObject obj = JsonHelper.parseObject(rawJson);
            if (obj == null) return cfg;
            cfg.publicProfileEnabled = boolOrDefault(obj, "publicProfileEnabled", true);
            cfg.showUsername = boolOrDefault(obj, "showUsername", true);
            cfg.showAvatar = boolOrDefault(obj, "showAvatar", true);
            cfg.showNickname = boolOrDefault(obj, "showNickname", false);
            cfg.showBio = boolOrDefault(obj, "showBio", false);
            cfg.showEmail = boolOrDefault(obj, "showEmail", false);
            cfg.showGender = boolOrDefault(obj, "showGender", false);
            cfg.showBirthday = boolOrDefault(obj, "showBirthday", false);
            cfg.showSocialAccounts = boolOrDefault(obj, "showSocialAccounts", false);
            cfg.showRole = boolOrDefault(obj, "showRole", false);
            cfg.showRegisteredAt = boolOrDefault(obj, "showRegisteredAt", false);
            cfg.showMcCharacters = boolOrDefault(obj, "showMcCharacters", false);
            cfg.showMcOnlineStatus = boolOrDefault(obj, "showMcOnlineStatus", false);
            cfg.showMcPlaytime = boolOrDefault(obj, "showMcPlaytime", false);
            cfg.showMcAchievements = boolOrDefault(obj, "showMcAchievements", false);
            cfg.showMcStats = boolOrDefault(obj, "showMcStats", false);
        } catch (Exception ignored) {
            return defaults();
        }
        return cfg;
    }

    private static boolean boolOrDefault(JsonObject obj, String key, boolean fallback) {
        if (!obj.has(key)) return fallback;
        JsonElement el = obj.get(key);
        if (el == null || el.isJsonNull() || !el.isJsonPrimitive()) return fallback;
        try {
            return el.getAsBoolean();
        } catch (Exception e) {
            return fallback;
        }
    }

    public Map<String, Boolean> toMap() {
        Map<String, Boolean> m = new LinkedHashMap<String, Boolean>();
        m.put("publicProfileEnabled", Boolean.valueOf(publicProfileEnabled));
        m.put("showUsername", Boolean.valueOf(showUsername));
        m.put("showAvatar", Boolean.valueOf(showAvatar));
        m.put("showNickname", Boolean.valueOf(showNickname));
        m.put("showBio", Boolean.valueOf(showBio));
        m.put("showEmail", Boolean.valueOf(showEmail));
        m.put("showGender", Boolean.valueOf(showGender));
        m.put("showBirthday", Boolean.valueOf(showBirthday));
        m.put("showSocialAccounts", Boolean.valueOf(showSocialAccounts));
        m.put("showRole", Boolean.valueOf(showRole));
        m.put("showRegisteredAt", Boolean.valueOf(showRegisteredAt));
        m.put("showMcCharacters", Boolean.valueOf(showMcCharacters));
        m.put("showMcOnlineStatus", Boolean.valueOf(showMcOnlineStatus));
        m.put("showMcPlaytime", Boolean.valueOf(showMcPlaytime));
        m.put("showMcAchievements", Boolean.valueOf(showMcAchievements));
        m.put("showMcStats", Boolean.valueOf(showMcStats));
        return m;
    }

    public String toJson() {
        return JsonHelper.toJson(toMap());
    }

    public boolean isPublicProfileEnabled() { return publicProfileEnabled; }
    public void setPublicProfileEnabled(boolean publicProfileEnabled) { this.publicProfileEnabled = publicProfileEnabled; }

    public boolean isShowUsername() { return showUsername; }
    public void setShowUsername(boolean showUsername) { this.showUsername = showUsername; }

    public boolean isShowAvatar() { return showAvatar; }
    public void setShowAvatar(boolean showAvatar) { this.showAvatar = showAvatar; }

    public boolean isShowNickname() { return showNickname; }
    public void setShowNickname(boolean showNickname) { this.showNickname = showNickname; }

    public boolean isShowBio() { return showBio; }
    public void setShowBio(boolean showBio) { this.showBio = showBio; }

    public boolean isShowEmail() { return showEmail; }
    public void setShowEmail(boolean showEmail) { this.showEmail = showEmail; }

    public boolean isShowGender() { return showGender; }
    public void setShowGender(boolean showGender) { this.showGender = showGender; }

    public boolean isShowBirthday() { return showBirthday; }
    public void setShowBirthday(boolean showBirthday) { this.showBirthday = showBirthday; }

    public boolean isShowSocialAccounts() { return showSocialAccounts; }
    public void setShowSocialAccounts(boolean showSocialAccounts) { this.showSocialAccounts = showSocialAccounts; }

    public boolean isShowRole() { return showRole; }
    public void setShowRole(boolean showRole) { this.showRole = showRole; }

    public boolean isShowRegisteredAt() { return showRegisteredAt; }
    public void setShowRegisteredAt(boolean showRegisteredAt) { this.showRegisteredAt = showRegisteredAt; }

    public boolean isShowMcCharacters() { return showMcCharacters; }
    public void setShowMcCharacters(boolean showMcCharacters) { this.showMcCharacters = showMcCharacters; }

    public boolean isShowMcOnlineStatus() { return showMcOnlineStatus; }
    public void setShowMcOnlineStatus(boolean showMcOnlineStatus) { this.showMcOnlineStatus = showMcOnlineStatus; }

    public boolean isShowMcPlaytime() { return showMcPlaytime; }
    public void setShowMcPlaytime(boolean showMcPlaytime) { this.showMcPlaytime = showMcPlaytime; }

    public boolean isShowMcAchievements() { return showMcAchievements; }
    public void setShowMcAchievements(boolean showMcAchievements) { this.showMcAchievements = showMcAchievements; }

    public boolean isShowMcStats() { return showMcStats; }
    public void setShowMcStats(boolean showMcStats) { this.showMcStats = showMcStats; }
}
