package com.utopiaxc.utopiaserverpanel.web;

import com.utopiaxc.utopiaserverpanel.Config;
import com.utopiaxc.utopiaserverpanel.UtopiaServerPanel;
import com.utopiaxc.utopiaserverpanel.web.controller.*;
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
 * This is the main entry point for the web panel — call {@link #start} from
 * the server starting event and {@link #stop} from the server stopping event.
 */
public class WebServer {
    private static EventLoopGroup bossGroup;
    private static EventLoopGroup workerGroup;
    private static Channel serverChannel;

    private static MinecraftServer minecraftServer;
    private static long startTime;
    private static ScheduledExecutorService broadcastScheduler;

    public static MinecraftServer getMinecraftServer() { return minecraftServer; }
    public static long getStartTime() { return startTime; }

    /**
     * Start the web server on the given port.
     * Initializes database, registers middlewares and API routes, then binds Netty.
     */
    public static void start(MinecraftServer ms, int port) {
        minecraftServer = ms;
        startTime = System.currentTimeMillis();

        // ── Initialize database ──
        String configDir = ms.getServerDirectory().resolve("config").toAbsolutePath().toString();
        MyBatisFactory.initialize(configDir);
        Schema.initialize();

        // ── Register middlewares ──
        MiddlewarePipeline.getInstance()
                .use(new CorsMiddleware())
                .use(new AuthMiddleware());

        // ── Register API routes ──
        Router router = Router.getInstance();

        // Existing - status is public (no auth required)
        router.get("/api/status", StatusController::getStatus);

        // Auth (whitelisted in AuthMiddleware)
        router.post("/api/auth/login", AuthController::login);
        router.post("/api/auth/refresh", AuthController::refresh);
        router.post("/api/auth/logout", AuthController::logout);
        router.post("/api/auth/register", AuthController::register);

        // Auth (authenticated)
        router.post("/api/auth/change-password", AuthController::changePassword);
        router.get("/api/auth/me", AuthController::me);
        router.get("/api/auth/permissions", AuthController::permissions);

        // Admin - Users
        router.get("/api/admin/users", AdminController::listUsers, "admin.users.read");
        router.post("/api/admin/users", AdminController::createUser, "admin.users.edit");
        router.put("/api/admin/users/{id}", AdminController::updateUser, "admin.users.edit");
        router.delete("/api/admin/users/{id}", AdminController::deleteUser, "admin.users.edit");

        // Admin - Roles
        router.get("/api/admin/roles", AdminController::listRoles, "admin.roles.read");
        router.get("/api/admin/roles/{id}", AdminController::getRole, "admin.roles.read");
        router.post("/api/admin/roles", AdminController::createRole, "admin.roles.edit");
        router.put("/api/admin/roles/{id}", AdminController::updateRole, "admin.roles.edit");
        router.delete("/api/admin/roles/{id}", AdminController::deleteRole, "admin.roles.edit");

        // Admin - Permissions
        router.get("/api/admin/permissions", AdminController::listPermissions, "admin.roles.read");

        // Binding
        router.post("/api/binding/bind", BindingController::bind, "auth.binding.manage");
        router.post("/api/binding/unbind", BindingController::unbind, "auth.binding.manage");

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
                WebSocketController.broadcastStatus(
                        minecraftServer, startTime);
            } catch (Exception e) {
                UtopiaServerPanel.LOGGER.warn("Status broadcast error", e);
            }
        }, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
        UtopiaServerPanel.LOGGER.info("Status broadcast started with interval {} ms", intervalMs);
    }

    /** Gracefully shut down the web server. */
    public static void stop() {
        // Shut down broadcast scheduler first
        if (broadcastScheduler != null) {
            broadcastScheduler.shutdownNow();
            broadcastScheduler = null;
        }

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
        MyBatisFactory.shutdown();

        UtopiaServerPanel.LOGGER.info("UtopiaServerPanel web server stopped");
    }
}
