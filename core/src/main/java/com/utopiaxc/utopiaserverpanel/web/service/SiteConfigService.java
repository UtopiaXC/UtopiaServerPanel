package com.utopiaxc.utopiaserverpanel.web.service;

/**
 * Compatibility facade delegating to SettingsService for site branding configuration.
 */
public final class SiteConfigService {

    private SiteConfigService() {}

    public static String getSiteName() {
        return SettingsService.getSiteName();
    }

    public static void setSiteName(String name) {
        if (name == null || name.trim().isEmpty()) return;
        SettingsService.set("site_name", name.trim());
    }
}
