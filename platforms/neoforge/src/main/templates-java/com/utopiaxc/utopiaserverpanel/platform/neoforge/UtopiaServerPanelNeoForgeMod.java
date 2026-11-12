package com.utopiaxc.utopiaserverpanel.platform.neoforge;

import com.mojang.logging.LogUtils;
import com.utopiaxc.utopiaserverpanel.PanelEngine;
import com.utopiaxc.utopiaserverpanel.bridge.jvm.Log4j2TerminalBridge;
import com.utopiaxc.utopiaserverpanel.bridge.jvm.OshiTelemetryProvider;
import com.utopiaxc.utopiaserverpanel.bridge.jvm.Slf4jPanelLogger;
import com.utopiaxc.utopiaserverpanel.config.PanelConfig;
import com.utopiaxc.utopiaserverpanel.platform.shared.ModernCommandBridge;
import com.utopiaxc.utopiaserverpanel.platform.shared.ModernGameAdapter;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.slf4j.Logger;

/**
 * Thin NeoForge entrypoint spanning 1.20.4 through 26.3+.
 */
@Mod(PanelEngine.MOD_ID)
public final class UtopiaServerPanelNeoForgeMod {
    public static final Logger LOGGER = LogUtils.getLogger();

    public UtopiaServerPanelNeoForgeMod() {
        PanelEngine.setLogger(new Slf4jPanelLogger(LOGGER));
        PanelConfig.initialize(FMLPaths.CONFIGDIR.get());
        Log4j2TerminalBridge.register();
        NeoForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        ModernCommandBridge.register(event.getDispatcher());
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        PanelEngine.start(
                new ModernGameAdapter(
                        event.getServer(),
                        FMLPaths.CONFIGDIR.get().toAbsolutePath().normalize(),
                        System.currentTimeMillis()
                ),
                new OshiTelemetryProvider()
        );
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        PanelEngine.stop();
    }

    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        Player player = event.getEntity();
        PanelEngine.onPlayerJoin(player.getName().getString(), player.getStringUUID());
    }

    @SubscribeEvent
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        Player player = event.getEntity();
        PanelEngine.onPlayerLeave(player.getName().getString(), player.getStringUUID());
    }
}

