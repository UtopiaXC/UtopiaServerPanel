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
        MyBatisFactory.initialize(configDir);
        Schema.initialize();
        SetupService.initializeOnStartup();
        MonitorConfigService.loadAll();

        ServerStatusCache.getInstance().start();
        ServerLifecycleService.recordStart();

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
        router.get("/api/captcha/challenge", CaptchaController::challenge);

        // Authentication & Recovery
        router.post("/api/auth/login", AuthController::login);
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
        router.put("/api/me/username", ProfileController::changeUsername);
        router.post("/api/me/password", ProfileController::changePassword);
        router.post("/api/me/2fa/setup", ProfileController::setup2fa);
        router.post("/api/me/2fa/confirm", ProfileController::confirm2fa);
        router.post("/api/me/2fa/disable", ProfileController::disable2fa);
        router.get("/api/me/sessions", ProfileController::listSessions);
        router.delete("/api/me/sessions/{id}", ProfileController::revokeSession);
        router.get("/api/me/login-history", ProfileController::loginHistory);
        router.get("/api/me/tokens", ProfileController::listTokens);
        router.post("/api/me/tokens", ProfileController::createToken);
        router.delete("/api/me/tokens/{id}", ProfileController::revokeToken);

        // Minecraft Player & Binding
        router.post("/api/binding/bind", BindingController::bind);
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

        // Admin - Settings & SMTP
        router.get("/api/admin/settings", AdminSettingsController::getSettings, "admin:1");
        router.patch("/api/admin/settings", AdminSettingsController::updateSettings, "admin:2");
        router.post("/api/admin/settings/test-smtp", AdminSettingsController::testSmtp, "admin:2");
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
