package com.utopiaxc.utopiaserverpanel.web;

import com.utopiaxc.utopiaserverpanel.Config;
import com.utopiaxc.utopiaserverpanel.UtopiaServerPanel;
import com.utopiaxc.utopiaserverpanel.adapter.AdapterRegistry;
import com.utopiaxc.utopiaserverpanel.adapter.NeoForgeAdapter;
import com.utopiaxc.utopiaserverpanel.web.cache.PlayerDataCache;
import com.utopiaxc.utopiaserverpanel.web.cache.ServerStatusCache;
import com.utopiaxc.utopiaserverpanel.web.controller.*;
import com.utopiaxc.utopiaserverpanel.web.service.MonitorConfigService;
import com.utopiaxc.utopiaserverpanel.web.service.PlayerEventTracker;
import com.utopiaxc.utopiaserverpanel.web.service.ServerLifecycleService;
import com.utopiaxc.utopiaserverpanel.web.db.MyBatisFactory;
import com.utopiaxc.utopiaserverpanel.web.db.Schema;
import com.utopiaxc.utopiaserverpanel.web.middleware.AuthMiddleware;
import com.utopiaxc.utopiaserverpanel.web.middleware.CorsMiddleware;
import com.utopiaxc.utopiaserverpanel.web.middleware.MiddlewarePipeline;
import com.utopiaxc.utopiaserverpanel.web.router.Router;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import net.minecraft.server.MinecraftServer;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Unified Netty-based web server serving both HTTP and WebSocket on a single port.
 * <p>
 * This is the main entry point for the web panel -- call {@link #start} from
 * the server starting event and {@link #stop} from the server stopping event.
 */
public class WebServer {
    private static EventLoopGroup bossGroup;
    private static EventLoopGroup workerGroup;
    private static Channel serverChannel;

    private static ScheduledExecutorService broadcastScheduler;

    /**
     * Start the web server on the given port.
     * Initializes adapter, database, caches, middlewares, and API routes, then binds Netty.
     */
    public static void start(MinecraftServer ms, int port) {
        long startTime = System.currentTimeMillis();

        // -- Initialize adapter layer --
        AdapterRegistry.initialize(new NeoForgeAdapter(ms, startTime));

        // -- Initialize database --
        String configDir = ms.getServerDirectory().resolve("config").toAbsolutePath().toString();
        MyBatisFactory.initialize(configDir);
        Schema.initialize();

        // -- Initialize caches --
        ServerStatusCache.getInstance().start();

        // -- Load monitor configuration --
        MonitorConfigService.loadAll();

        // -- Record server start lifecycle event --
        ServerLifecycleService.recordStart();

        // -- Register middlewares --
        MiddlewarePipeline.getInstance()
                .use(new CorsMiddleware())
                .use(new AuthMiddleware());

        // -- Register API routes --
        Router router = Router.getInstance();

        // Status is public (no auth required)
        router.get("/api/status", StatusController::getStatus);

        // Auth (whitelisted in AuthMiddleware)
        router.post("/api/auth/login", AuthController::login);
        router.post("/api/auth/refresh", AuthController::refresh);
        router.post("/api/auth/logout", AuthController::logout);
        router.post("/api/auth/register", AuthController::register);
        router.get("/api/auth/guest-permissions", AuthController::guestPermissions);

        // Auth (authenticated)
        router.post("/api/auth/change-password", AuthController::changePassword);
        router.get("/api/auth/me", AuthController::me);
        router.get("/api/auth/permissions", AuthController::permissions);
        router.put("/api/auth/username", AuthController::changeUsername);

        // Admin - Users
        router.get("/api/admin/users", AdminController::listUsers, "admin:1");
        router.post("/api/admin/users", AdminController::createUser, "admin:2");
        router.put("/api/admin/users/{id}", AdminController::updateUser, "admin:2");
        router.delete("/api/admin/users/{id}", AdminController::deleteUser, "admin:2");

        // Admin - Roles
        router.get("/api/admin/roles", AdminController::listRoles, "admin:1");
        router.get("/api/admin/roles/{id}", AdminController::getRole, "admin:1");
        router.post("/api/admin/roles", AdminController::createRole, "admin:2");
        router.put("/api/admin/roles/{id}", AdminController::updateRole, "admin:2");
        router.delete("/api/admin/roles/{id}", AdminController::deleteRole, "admin:2");

        // Admin - Permissions
        router.get("/api/admin/permissions", AdminController::listPermissions, "admin:1");

        // Binding
        router.post("/api/binding/bind", BindingController::bind);
        router.post("/api/binding/unbind", BindingController::unbind);

        // Player data (authenticated)
        router.get("/api/player/me", PlayerController::getMyPlayerData);

        // Settings
        router.get("/api/settings/site", SettingsController::getSiteName);
        router.put("/api/settings/site", SettingsController::setSiteName, "admin:2");
        router.get("/api/settings/monitor", SettingsController::getMonitorConfig, "admin:1");
        router.put("/api/settings/monitor", SettingsController::setMonitorConfig, "admin:2");

        // Monitor logs
        router.get("/api/monitor/perf", MonitorController::queryPerfLogs, "logs:1");
        router.get("/api/monitor/players", MonitorController::queryPlayerEvents, "logs:1");
        router.get("/api/monitor/config", MonitorController::getDisplayConfig);
        router.get("/api/monitor/lifecycle", MonitorController::queryLifecyclePaged, "logs:1");
        router.get("/api/monitor/lifecycle/range", MonitorController::queryLifecycleRange, "logs:1");

        // Start Netty
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
            UtopiaServerPanel.LOGGER.info("UtopiaServerPanel web server started on port {}", port);
        } catch (Exception e) {
            UtopiaServerPanel.LOGGER.error("Failed to start web server on port {}", port, e);
            stop();
            return;
        }

        // Start periodic status broadcast to all connected WebSocket clients
        startBroadcast();
    }

    /** Start a scheduled task that broadcasts server status to all WS clients. */
    private static void startBroadcast() {
        int intervalMs = Config.BROADCAST_INTERVAL.getAsInt();
        broadcastScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "UtopiaPanel-StatusBroadcast");
            t.setDaemon(true);
            return t;
        });
        broadcastScheduler.scheduleWithFixedDelay(() -> {
            try {
                WebSocketController.broadcastStatus();
            } catch (Exception e) {
                UtopiaServerPanel.LOGGER.warn("Status broadcast error", e);
            }
        }, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
//        UtopiaServerPanel.LOGGER.info("Status broadcast started with interval {} ms", intervalMs);
    }

    /** Gracefully shut down the web server. */
    public static void stop() {
        // Record normal shutdown lifecycle event (must be before DB shutdown)
        ServerLifecycleService.recordNormalStop();

        // Shut down broadcast scheduler first
        if (broadcastScheduler != null) {
            broadcastScheduler.shutdownNow();
            broadcastScheduler = null;
        }

        // Shut down caches
        ServerStatusCache.getInstance().stop();
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

        // Clean up singletons for potential re-registration on server restart
        Router.getInstance().clear();
        MiddlewarePipeline.getInstance().clear();
        AdapterRegistry.shutdown();
        MyBatisFactory.shutdown();

        UtopiaServerPanel.LOGGER.info("UtopiaServerPanel web server stopped");
    }
}
