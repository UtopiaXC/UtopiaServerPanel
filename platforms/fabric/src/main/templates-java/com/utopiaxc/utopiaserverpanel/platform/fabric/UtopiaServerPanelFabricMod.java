package com.utopiaxc.utopiaserverpanel.platform.fabric;

import com.utopiaxc.utopiaserverpanel.PanelEngine;
//? if mc >= 1.18 {
import com.utopiaxc.utopiaserverpanel.bridge.jvm.OshiTelemetryProvider;
import com.utopiaxc.utopiaserverpanel.bridge.jvm.Slf4jPanelLogger;
import org.slf4j.LoggerFactory;
//?} else {
import com.utopiaxc.utopiaserverpanel.bridge.jvm.Log4jPanelLogger;
import org.apache.logging.log4j.LogManager;
//?}
import com.utopiaxc.utopiaserverpanel.bridge.jvm.Log4j2TerminalBridge;
import com.utopiaxc.utopiaserverpanel.config.PanelConfig;
import com.utopiaxc.utopiaserverpanel.platform.shared.ModernCommandBridge;
import com.utopiaxc.utopiaserverpanel.platform.shared.ModernGameAdapter;
import net.fabricmc.api.ModInitializer;
//? if mc >= 1.19 {
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
//?} else {
import net.fabricmc.fabric.api.command.v1.CommandRegistrationCallback;
//?}
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;

import java.nio.file.Path;

/**
 * Thin Fabric entrypoint spanning 1.16.5 through 26.3+.
 */
public final class UtopiaServerPanelFabricMod implements ModInitializer {

    @Override
    public void onInitialize() {
        final Path configDir = FabricLoader.getInstance().getConfigDir().toAbsolutePath().normalize();
        //? if mc >= 1.18 {
        PanelEngine.setLogger(new Slf4jPanelLogger(LoggerFactory.getLogger(PanelEngine.MOD_ID)));
        //?} else {
        PanelEngine.setLogger(new Log4jPanelLogger(LogManager.getLogger(PanelEngine.MOD_ID)));
        //?}
        PanelConfig.initialize(configDir);
        Log4j2TerminalBridge.register();

        //? if mc >= 1.19 {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                ModernCommandBridge.register(dispatcher));
        //?} else {
        CommandRegistrationCallback.EVENT.register((dispatcher, dedicated) ->
                ModernCommandBridge.register(dispatcher));
        //?}

        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            //? if mc >= 1.18 {
            PanelEngine.start(
                    new ModernGameAdapter(server, configDir, System.currentTimeMillis()),
                    new OshiTelemetryProvider()
            );
            //?} else {
            PanelEngine.start(
                    new ModernGameAdapter(server, configDir, System.currentTimeMillis()),
                    null
            );
            //?}
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> PanelEngine.stop());

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.player;
            PanelEngine.onPlayerJoin(player.getName().getString(), player.getStringUUID());
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayer player = handler.player;
            PanelEngine.onPlayerLeave(player.getName().getString(), player.getStringUUID());
        });
    }
}

