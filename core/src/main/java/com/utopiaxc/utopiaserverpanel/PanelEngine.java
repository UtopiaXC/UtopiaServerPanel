package com.utopiaxc.utopiaserverpanel;

import com.utopiaxc.utopiaserverpanel.adapter.AdapterRegistry;
import com.utopiaxc.utopiaserverpanel.adapter.GameAdapter;
import com.utopiaxc.utopiaserverpanel.command.UspCommandService;
import com.utopiaxc.utopiaserverpanel.config.PanelConfig;
import com.utopiaxc.utopiaserverpanel.logging.PanelLog;
import com.utopiaxc.utopiaserverpanel.logging.PanelLogger;
import com.utopiaxc.utopiaserverpanel.spi.integration.ModIntegrationRegistry;
import com.utopiaxc.utopiaserverpanel.spi.map.MapIntegrationRegistry;
import com.utopiaxc.utopiaserverpanel.system.SystemMonitor;
import com.utopiaxc.utopiaserverpanel.system.SystemTelemetryProvider;
import com.utopiaxc.utopiaserverpanel.web.WebServer;
import com.utopiaxc.utopiaserverpanel.web.service.PlayerEventTracker;

import java.nio.file.Path;

/**
 * Primary lifecycle facade exposed by {@code core} for all loader/platform adapters.
 */
public final class PanelEngine {
    public static final String MOD_ID = "utopia_server_panel";
    private static final UspCommandService COMMAND_SERVICE = new UspCommandService();

    private PanelEngine() {}

    public static void setLogger(PanelLogger logger) {
        PanelLog.setLogger(logger);
    }

    public static UspCommandService commands() {
        return COMMAND_SERVICE;
    }

    public static void start(GameAdapter adapter) {
        start(adapter, null);
    }

    public static void start(GameAdapter adapter, SystemTelemetryProvider telemetryProvider) {
        if (adapter == null) {
            throw new IllegalArgumentException("GameAdapter must not be null");
        }
        PanelLog.get().info("Starting UtopiaServerPanel ({})...", adapter.getPlatformName());
        AdapterRegistry.initialize(adapter);
        SystemMonitor.setTelemetryProvider(telemetryProvider);

        Path configDir = adapter.getConfigDirectory();
        PanelConfig.initialize(configDir);
        WebServer.start(configDir.toAbsolutePath().toString(), PanelConfig.getWebPort());
    }

    public static void stop() {
        PanelLog.get().info("Stopping UtopiaServerPanel...");
        WebServer.stop();
        MapIntegrationRegistry.clear();
        ModIntegrationRegistry.clear();
        SystemMonitor.reset();
    }

    public static void onPlayerJoin(String playerName, String playerUuid) {
        PlayerEventTracker.getInstance().onPlayerJoin(playerName, playerUuid);
    }

    public static void onPlayerLeave(String playerName, String playerUuid) {
        PlayerEventTracker.getInstance().onPlayerLeave(playerName, playerUuid);
    }
}

