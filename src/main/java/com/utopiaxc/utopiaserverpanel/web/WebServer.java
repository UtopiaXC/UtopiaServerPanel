package com.utopiaxc.utopiaserverpanel.web;

import com.utopiaxc.utopiaserverpanel.Config;
import com.utopiaxc.utopiaserverpanel.UtopiaServerPanel;
import com.utopiaxc.utopiaserverpanel.web.controller.StatusController;
import com.utopiaxc.utopiaserverpanel.web.controller.WebSocketController;
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
     * Registers middlewares and API routes, then binds the Netty server.
     */
    public static void start(MinecraftServer ms, int port) {
        minecraftServer = ms;
        startTime = System.currentTimeMillis();

        // Register middlewares
        MiddlewarePipeline.getInstance()
                .use(new CorsMiddleware());

        // Register API routes
        Router.getInstance()
                .get("/api/status", StatusController::getStatus);

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

        UtopiaServerPanel.LOGGER.info("UtopiaServerPanel web server stopped");
    }
}
