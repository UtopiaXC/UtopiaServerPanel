package com.utopiaxc.utopiaserverpanel.web.service;

import com.utopiaxc.utopiaserverpanel.web.db.MyBatisFactory;
import com.utopiaxc.utopiaserverpanel.web.db.mapper.TokenMapper;

/**
 * Service for managing site-wide configuration (e.g. site name).
 * Stored in the server_config table.
 */
public final class SiteConfigService {

    private static final String DEFAULT_SITE_NAME = "Utopia Server Panel";

    private static volatile String cachedSiteName = null;

    private SiteConfigService() {}

    /** Get the configured site name, or the default. */
    public static String getSiteName() {
        if (cachedSiteName != null) return cachedSiteName;
        return MyBatisFactory.doWorkWithResult(session -> {
            TokenMapper mapper = session.getMapper(TokenMapper.class);
            String val = mapper.getConfig("site_name");
            cachedSiteName = (val != null && !val.isEmpty()) ? val : DEFAULT_SITE_NAME;
            return cachedSiteName;
        });
    }

    /** Set the site name. */
    public static void setSiteName(String name) {
        String safeName = (name == null || name.trim().isEmpty()) ? DEFAULT_SITE_NAME : name.trim();
        MyBatisFactory.doWork(session -> {
            TokenMapper mapper = session.getMapper(TokenMapper.class);
            mapper.setConfig("site_name", safeName);
        });
        cachedSiteName = safeName;
    }
}
