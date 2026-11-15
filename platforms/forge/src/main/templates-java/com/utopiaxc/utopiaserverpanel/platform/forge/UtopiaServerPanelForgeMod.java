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
import com.utopiaxc.utopiaserverpanel.platform.shared.ModernMapBridge;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
//? if mc < 1.21.6 {
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
//?}
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
//? if mc < 1.21.6 {
//? if mc >= 1.19 {
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ChunkEvent;
//?} else {
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.event.world.ChunkEvent;
//?}
//?}
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
    private static volatile long tickStartNs = 0L;

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
        ServerChatEvent.BUS.addListener(UtopiaServerPanelForgeMod::onServerChat);
        CommandEvent.BUS.addListener(UtopiaServerPanelForgeMod::onCommand);
        TickEvent.ServerTickEvent.Pre.BUS.addListener(UtopiaServerPanelForgeMod::onTickPre);
        TickEvent.ServerTickEvent.Post.BUS.addListener(UtopiaServerPanelForgeMod::onTickPost);
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

    //? if mc < 1.21.6 {
    @SubscribeEvent
    //?}
    public static void onServerChat(ServerChatEvent event) {
        try {
            ServerPlayer player = event.getPlayer();
            if (player != null) {
                //? if mc >= 1.19 {
                String msg = event.getRawText();
                //?} else {
                String msg = event.getMessage();
                //?}
                PanelEngine.onChatMessage(player.getStringUUID(), player.getName().getString(), msg);
            }
        } catch (Throwable ignored) {}
    }

    //? if mc < 1.21.6 {
    @SubscribeEvent
    //?}
    public static void onCommand(CommandEvent event) {
        try {
            String raw = event.getParseResults().getReader().getString().trim();
            if (raw.startsWith("/")) raw = raw.substring(1);
            String[] parts = raw.split("\\s+", 3);
            if (parts.length >= 3) {
                String cmd = parts[0].toLowerCase(java.util.Locale.ROOT);
                if ("tell".equals(cmd) || "msg".equals(cmd) || "w".equals(cmd)) {
                    net.minecraft.world.entity.Entity srcEnt = event.getParseResults().getContext().getSource().getEntity();
                    String senderName = event.getParseResults().getContext().getSource().getTextName();
                    String senderUuid = srcEnt != null ? srcEnt.getStringUUID() : null;
                    PanelEngine.onWhisperMessage(senderUuid, senderName, parts[1], parts[2]);
                }
            }
        } catch (Throwable ignored) {}
    }

    //? if mc >= 1.21.6 {
    public static void onTickPre(TickEvent.ServerTickEvent.Pre event) {
        tickStartNs = System.nanoTime();
    }

    public static void onTickPost(TickEvent.ServerTickEvent.Post event) {
        long start = tickStartNs;
        long total = start > 0L ? (System.nanoTime() - start) : 0L;
        PanelEngine.onServerTick(total, 0L, 0L, 0L);
    }
    //?} else {
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            tickStartNs = System.nanoTime();
        } else {
            long start = tickStartNs;
            long total = start > 0L ? (System.nanoTime() - start) : 0L;
            PanelEngine.onServerTick(total, 0L, 0L, 0L);
        }
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        try {
            //? if mc >= 1.19 {
            Object lvl = event.getLevel();
            //?} else {
            Object lvl = event.getWorld();
            //?}
            if (lvl instanceof ServerLevel) {
                ServerLevel sl = (ServerLevel) lvl;
                PanelEngine.onChunkDirty(ModernMapBridge.dimensionId(sl), event.getChunk().getPos().x, event.getChunk().getPos().z);
            }
        } catch (Throwable ignored) {}
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        try {
            //? if mc >= 1.19 {
            Object lvl = event.getLevel();
            //?} else {
            Object lvl = event.getWorld();
            //?}
            if (lvl instanceof ServerLevel) {
                ServerLevel sl = (ServerLevel) lvl;
                PanelEngine.onChunkDirty(ModernMapBridge.dimensionId(sl), event.getPos().getX() >> 4, event.getPos().getZ() >> 4);
            }
        } catch (Throwable ignored) {}
    }
    //?}
}
