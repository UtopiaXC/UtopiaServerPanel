package com.utopiaxc.utopiaserverpanel.web;

import com.utopiaxc.utopiaserverpanel.adapter.AdapterRegistry;
import com.utopiaxc.utopiaserverpanel.config.PanelConfig;
import com.utopiaxc.utopiaserverpanel.logging.PanelLog;
import com.utopiaxc.utopiaserverpanel.web.cache.PlayerDataCache;
import com.utopiaxc.utopiaserverpanel.web.cache.ServerStatusCache;
import com.utopiaxc.utopiaserverpanel.web.controller.*;
import com.utopiaxc.utopiaserverpanel.web.db.MyBatisFactory;
import com.utopiaxc.utopiaserverpanel.web.db.Schema;
import com.utopiaxc.utopiaserverpanel.web.middleware.AuthMiddleware;
import com.utopiaxc.utopiaserverpanel.web.middleware.CorsMiddleware;
import com.utopiaxc.utopiaserverpanel.web.middleware.MiddlewarePipeline;
import com.utopiaxc.utopiaserverpanel.web.router.Router;
import com.utopiaxc.utopiaserverpanel.web.service.MonitorConfigService;
import com.utopiaxc.utopiaserverpanel.web.service.PlayerEventTracker;
import com.utopiaxc.utopiaserverpanel.web.service.ServerLifecycleService;
import com.utopiaxc.utopiaserverpanel.web.service.SetupService;
import com.utopiaxc.utopiaserverpanel.web.service.WorldRegistryService;
import com.utopiaxc.utopiaserverpanel.web.service.map.MapService;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * Unified Netty HTTP & WebSocket server hosting the Utopia Server Panel API and frontend SPA.
 */
public class WebServer {
    private static EventLoopGroup bossGroup;
    private static EventLoopGroup workerGroup;
    private static Channel serverChannel;
    private static ScheduledExecutorService broadcastScheduler;

    public static void start(String configDir, int port) {
        java.nio.file.Path dbPath = WorldRegistryService.initializeForActiveWorld(AdapterRegistry.getAdapter());
        MyBatisFactory.initialize(dbPath);
        Schema.initialize();
        SetupService.initializeOnStartup();
        MonitorConfigService.loadAll();

        ServerStatusCache.getInstance().start();
        ServerLifecycleService.recordStart();
        MapService.start();

        MiddlewarePipeline.getInstance()
                .use(new CorsMiddleware())
                .use(new AuthMiddleware());

        registerRoutes();

        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();

        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new WebServerInitializer())
                    .option(ChannelOption.SO_BACKLOG, 128)
                    .childOption(ChannelOption.SO_KEEPALIVE, true);

            serverChannel = bootstrap.bind(port).sync().channel();
            PanelLog.get().info("UtopiaServerPanel web server started on port {}", Integer.valueOf(port));
        } catch (Exception e) {
            PanelLog.get().error("Failed to start web server on port " + port, e);
            stop();
            return;
        }

        startBroadcast();
    }

    private static void registerRoutes() {
        Router router = Router.getInstance();

        // Setup & Public Metadata
        router.get("/api/setup/status", SetupController::status);
        router.post("/api/setup/verify-token", SetupController::verifyToken);
        router.post("/api/setup/test-smtp", SetupController::testSmtp);
        router.post("/api/setup/complete", SetupController::complete);
        router.get("/api/site", SiteController::getPublicSiteInfo);
        router.get("/api/site/favicon", SiteController::getFavicon);
        router.get("/favicon.ico", SiteController::getFavicon);
        router.get("/api/captcha/challenge", CaptchaController::challenge);
        router.get("/api/public/users/{username}", PublicProfileController::getUserProfile);
        router.get("/api/public/mc-skin", PublicProfileController::getMcSkin);

        // Authentication & Recovery
        router.post("/api/auth/login", AuthController::login);
        router.post("/api/auth/passkey/login/options", PasskeyController::beginLogin);
        router.post("/api/auth/passkey/login/verify", PasskeyController::finishLogin);
        router.post("/api/auth/mfa/passkey/options", PasskeyController::beginMfa);
        router.post("/api/auth/mfa/passkey/verify", PasskeyController::finishMfa);
        router.post("/api/auth/register", AuthController::register);
        router.post("/api/auth/activate", AuthController::activate);
        router.post("/api/auth/refresh", AuthController::refresh);
        router.post("/api/auth/logout", AuthController::logout);
        router.post("/api/auth/forgot-password", AuthController::forgotPassword);
        router.post("/api/auth/reset-password", AuthController::resetPassword);
        router.post("/api/auth/forgot-2fa", AuthController::forgot2fa);
        router.post("/api/auth/reset-2fa", AuthController::reset2fa);
        router.get("/api/auth/guest-permissions", AuthController::guestPermissions);

        // Self-service Profile & Security (/api/me/*)
        router.get("/api/me", ProfileController::me);
        router.patch("/api/me/profile", ProfileController::updateProfile);
        router.get("/api/me/public-profile-settings", ProfileController::getPublicProfilePrivacy);
        router.put("/api/me/public-profile-settings", ProfileController::updatePublicProfilePrivacy);
        router.put("/api/me/username", ProfileController::changeUsername);
        router.post("/api/me/password", ProfileController::changePassword);
        router.post("/api/me/2fa/setup", ProfileController::setup2fa);
        router.post("/api/me/2fa/confirm", ProfileController::confirm2fa);
        router.post("/api/me/2fa/disable", ProfileController::disable2fa);
        router.get("/api/me/passkeys", PasskeyController::listMine);
        router.post("/api/me/passkeys/register/options", PasskeyController::beginRegister);
        router.post("/api/me/passkeys/register/verify", PasskeyController::finishRegister);
        router.put("/api/me/passkeys/{id}", PasskeyController::renameMine);
        router.delete("/api/me/passkeys/{id}", PasskeyController::deleteMine);
        router.get("/api/me/sessions", ProfileController::listSessions);
        router.delete("/api/me/sessions/{id}", ProfileController::revokeSession);
        router.get("/api/me/login-history", ProfileController::loginHistory);
        router.get("/api/me/tokens", ProfileController::listTokens);
        router.post("/api/me/tokens", ProfileController::createToken);
        router.delete("/api/me/tokens/{id}", ProfileController::revokeToken);

        // Minecraft Player & Binding
        router.get("/api/binding/list", BindingController::list);
        router.post("/api/binding/bind", BindingController::bind);
        router.post("/api/binding/primary", BindingController::setPrimary);
        router.post("/api/binding/unbind", BindingController::unbind);
        router.get("/api/player/me", PlayerController::getMyPlayerData);

        // Server Status & Monitoring Telemetry
        router.get("/api/status", StatusController::getStatus, "dashboard:1");
        router.get("/api/monitor/config", MonitorController::getDisplayConfig);
        router.get("/api/monitor/perf", MonitorController::queryPerfLogs, "logs:1");
        router.get("/api/monitor/players", MonitorController::queryPlayerEvents, "logs:1");
        router.get("/api/monitor/lifecycle", MonitorController::queryLifecyclePaged, "logs:1");
        router.get("/api/monitor/lifecycle/range", MonitorController::queryLifecycleRange, "logs:1");

        // Admin - Users
        router.get("/api/admin/users", AdminUserController::listUsers, "admin:1");
        router.post("/api/admin/users", AdminUserController::createUser, "admin:2");
        router.patch("/api/admin/users/{id}", AdminUserController::updateUser, "admin:2");
        router.delete("/api/admin/users/{id}", AdminUserController::deleteUser, "admin:2");
        router.post("/api/admin/users/{id}/unbind", AdminUserController::unbindPlayer, "admin:2");
        router.post("/api/admin/users/{id}/reset-2fa", AdminUserController::reset2fa, "admin:2");
        router.post("/api/admin/users/{id}/unlock", AdminUserController::unlockUser, "admin:2");

        // Admin - Roles & Permissions
        router.get("/api/admin/roles", AdminRoleController::listRoles, "admin:1");
        router.get("/api/admin/roles/{id}", AdminRoleController::getRole, "admin:1");
        router.post("/api/admin/roles", AdminRoleController::createRole, "admin:2");
        router.put("/api/admin/roles/{id}", AdminRoleController::updateRole, "admin:2");
        router.delete("/api/admin/roles/{id}", AdminRoleController::deleteRole, "admin:2");
        router.get("/api/admin/permissions", AdminRoleController::listPermissions, "admin:1");

        // Admin - Settings & SMTP & Worlds
        router.get("/api/admin/settings", AdminSettingsController::getSettings, "admin:1");
        router.patch("/api/admin/settings", AdminSettingsController::updateSettings, "admin:2");
        router.post("/api/admin/settings/favicon", AdminSettingsController::uploadFavicon, "admin:2");
        router.delete("/api/admin/settings/favicon", AdminSettingsController::resetFavicon, "admin:2");
        router.post("/api/admin/settings/whitelist-toggle", AdminSettingsController::toggleWhitelist, "admin:2");
        router.post("/api/admin/settings/whitelist-guest-toggle", AdminSettingsController::toggleGuestWhitelist, "admin:2");
        router.post("/api/admin/settings/test-smtp", AdminSettingsController::testSmtp, "admin:2");
        router.get("/api/admin/worlds", AdminSettingsController::listWorlds, "admin:1");

        // Web Map & Waypoints (permission checked inside + guest support via Router)
        router.get("/api/map/bootstrap", MapController::bootstrap, "map:1");
        router.get("/api/map/tile", MapController::getTile, "map:1");
        router.get("/api/map/regions", MapController::getRegions, "map:1");
        router.get("/api/map/overlays", MapController::getOverlays, "map:1");
        router.post("/api/map/waypoints", MapController::createWaypoint, "map:2");
        router.delete("/api/map/waypoints/{id}", MapController::deleteWaypoint, "map:2");
        router.post("/api/map/teleport", MapController::teleport, "map:1");
        router.post("/api/map/reset-health", MapController::resetHealth, "admin:2");
        router.post("/api/map/clear-cache", MapController::clearCache, "admin:2");

        // Performance Profiler & Lag Diagnostics
        router.get("/api/profiler/snapshot", ProfilerController::getSnapshot, "profiler:1");
        router.post("/api/profiler/teleport", ProfilerController::teleport, "profiler:2");

        // Mods & Server Config Editor
        router.get("/api/mods/overview", ModConfigController::getOverview, "mods_config:1");
        router.get("/api/mods/config", ModConfigController::readConfig, "mods_config:1");
        router.put("/api/mods/config", ModConfigController::saveConfig, "mods_config:2");
        router.get("/api/mods/server-properties", ModConfigController::getServerProperties, "mods_config:1");
        router.put("/api/mods/server-properties", ModConfigController::saveServerProperties, "mods_config:2");
        router.post("/api/mods/restart", ModConfigController::triggerRestart, "mods_config:2");

        // Whitelist Portal & Admin Review
        router.get("/api/whitelist/status", WhitelistController::getStatus);
        router.get("/api/whitelist/lookup", WhitelistController::lookupPlayer);
        router.post("/api/whitelist/apply", WhitelistController::apply);
        router.get("/api/whitelist/admin/applications", WhitelistController::listApplications, "whitelist_manage:1");
        router.post("/api/whitelist/admin/applications/{id}/review", WhitelistController::reviewApplication, "whitelist_manage:2");
        router.get("/api/whitelist/admin/entries", WhitelistController::listEntries, "whitelist_manage:1");
        router.post("/api/whitelist/admin/entries", WhitelistController::addEntry, "whitelist_manage:2");
        router.delete("/api/whitelist/admin/entries/{name}", WhitelistController::removeEntry, "whitelist_manage:2");

        // In-Site Inbox & Web <-> Game Chat Sync
        router.get("/api/messages/inbox", MessageController::listInbox);
        router.get("/api/messages/sent", MessageController::listSent);
        router.get("/api/messages/unread-count", MessageController::unreadCount);
        router.post("/api/messages/send", MessageController::send);
        router.post("/api/messages/read-all", MessageController::markAllRead);
        router.post("/api/messages/{id}/read", MessageController::markRead);
        router.get("/api/chat/history", MessageController::listChatHistory);
        router.post("/api/chat/send", MessageController::sendChat);
    }

    private static void startBroadcast() {
        int intervalMs = PanelConfig.getBroadcastIntervalMs();
        broadcastScheduler = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "UtopiaPanel-StatusBroadcast");
                t.setDaemon(true);
                return t;
            }
        });
        broadcastScheduler.scheduleWithFixedDelay(new Runnable() {
            @Override
            public void run() {
                try {
                    WebSocketController.broadcastStatus();
                } catch (Exception e) {
                    PanelLog.get().warn("Status broadcast error", e);
                }
            }
        }, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
    }

    public static void stop() {
        ServerLifecycleService.recordNormalStop();

        if (broadcastScheduler != null) {
            broadcastScheduler.shutdownNow();
            broadcastScheduler = null;
        }

        ServerStatusCache.getInstance().stop();
        MapService.stop();
        WebSocketController.reset();
        PlayerDataCache.getInstance().shutdown();
        PlayerEventTracker.getInstance().shutdown();

        if (serverChannel != null) {
            serverChannel.close();
            serverChannel = null;
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
            bossGroup = null;
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
            workerGroup = null;
        }

        Router.getInstance().clear();
        MiddlewarePipeline.getInstance().clear();
        AdapterRegistry.shutdown();
        MyBatisFactory.shutdown();

        PanelLog.get().info("UtopiaServerPanel web server stopped");
    }
}
