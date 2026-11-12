package com.utopiaxc.utopiaserverpanel.platform.forge;

import com.utopiaxc.utopiaserverpanel.PanelEngine;
//? if mc >= 1.18 {
import com.mojang.logging.LogUtils;
import com.utopiaxc.utopiaserverpanel.bridge.jvm.OshiTelemetryProvider;
import com.utopiaxc.utopiaserverpanel.bridge.jvm.Slf4jPanelLogger;
import org.slf4j.Logger;
//?} else {
import com.utopiaxc.utopiaserverpanel.bridge.jvm.Log4jPanelLogger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
//?}
import com.utopiaxc.utopiaserverpanel.bridge.jvm.Log4j2TerminalBridge;
import com.utopiaxc.utopiaserverpanel.config.PanelConfig;
import com.utopiaxc.utopiaserverpanel.platform.shared.ModernCommandBridge;
import com.utopiaxc.utopiaserverpanel.platform.shared.ModernGameAdapter;
import net.minecraft.world.entity.player.Player;
//? if mc < 1.21.6 {
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
//?}
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
//? if mc >= 1.18 {
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
//?} else {
import net.minecraftforge.fml.event.server.FMLServerStartingEvent;
import net.minecraftforge.fml.event.server.FMLServerStoppingEvent;
//?}
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;

/**
 * Thin Forge entrypoint spanning 1.16.5 through 26.3+.
 */
@Mod(PanelEngine.MOD_ID)
public final class UtopiaServerPanelForgeMod {
    //? if mc >= 1.18 {
    public static final Logger LOGGER = LogUtils.getLogger();
    //?} else {
    public static final Logger LOGGER = LogManager.getLogger(PanelEngine.MOD_ID);
    //?}

    public UtopiaServerPanelForgeMod() {
        //? if mc >= 1.18 {
        PanelEngine.setLogger(new Slf4jPanelLogger(LOGGER));
        //?} else {
        PanelEngine.setLogger(new Log4jPanelLogger(LOGGER));
        //?}
        PanelConfig.initialize(FMLPaths.CONFIGDIR.get());
        Log4j2TerminalBridge.register();
        //? if mc >= 1.21.6 {
        RegisterCommandsEvent.BUS.addListener(UtopiaServerPanelForgeMod::onRegisterCommands);
        ServerStartingEvent.BUS.addListener(UtopiaServerPanelForgeMod::onServerStarting);
        ServerStoppingEvent.BUS.addListener(UtopiaServerPanelForgeMod::onServerStopping);
        PlayerEvent.PlayerLoggedInEvent.BUS.addListener(UtopiaServerPanelForgeMod::onPlayerLogin);
        PlayerEvent.PlayerLoggedOutEvent.BUS.addListener(UtopiaServerPanelForgeMod::onPlayerLogout);
        //?} else {
        MinecraftForge.EVENT_BUS.register(UtopiaServerPanelForgeMod.class);
        //?}
    }

    //? if mc < 1.21.6 {
    @SubscribeEvent
    //?}
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        ModernCommandBridge.register(event.getDispatcher());
    }

    //? if mc >= 1.18 {
    //? if mc < 1.21.6 {
    @SubscribeEvent
    //?}
    public static void onServerStarting(ServerStartingEvent event) {
        PanelEngine.start(
                new ModernGameAdapter(
                        event.getServer(),
                        FMLPaths.CONFIGDIR.get().toAbsolutePath().normalize(),
                        System.currentTimeMillis()
                ),
                new OshiTelemetryProvider()
        );
    }

    //? if mc < 1.21.6 {
    @SubscribeEvent
    //?}
    public static void onServerStopping(ServerStoppingEvent event) {
        PanelEngine.stop();
    }
    //?} else {
    @SubscribeEvent
    public static void onServerStarting(FMLServerStartingEvent event) {
        PanelEngine.start(
                new ModernGameAdapter(
                        event.getServer(),
                        FMLPaths.CONFIGDIR.get().toAbsolutePath().normalize(),
                        System.currentTimeMillis()
                ),
                null
        );
    }

    @SubscribeEvent
    public static void onServerStopping(FMLServerStoppingEvent event) {
        PanelEngine.stop();
    }
    //?}

    //? if mc < 1.21.6 {
    @SubscribeEvent
    //?}
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        //? if mc >= 1.19 {
        Player player = event.getEntity();
        //?} else {
        Player player = event.getPlayer();
        //?}
        PanelEngine.onPlayerJoin(player.getName().getString(), player.getStringUUID());
    }

    //? if mc < 1.21.6 {
    @SubscribeEvent
    //?}
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        //? if mc >= 1.19 {
        Player player = event.getEntity();
        //?} else {
        Player player = event.getPlayer();
        //?}
        PanelEngine.onPlayerLeave(player.getName().getString(), player.getStringUUID());
    }
}
