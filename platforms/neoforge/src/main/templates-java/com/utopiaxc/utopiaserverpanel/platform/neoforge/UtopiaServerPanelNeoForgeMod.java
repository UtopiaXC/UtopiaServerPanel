package com.utopiaxc.utopiaserverpanel.platform.neoforge;

import com.mojang.logging.LogUtils;
import com.utopiaxc.utopiaserverpanel.PanelEngine;
import com.utopiaxc.utopiaserverpanel.bridge.jvm.Log4j2TerminalBridge;
import com.utopiaxc.utopiaserverpanel.bridge.jvm.OshiTelemetryProvider;
import com.utopiaxc.utopiaserverpanel.bridge.jvm.Slf4jPanelLogger;
import com.utopiaxc.utopiaserverpanel.config.PanelConfig;
import com.utopiaxc.utopiaserverpanel.platform.shared.ModernCommandBridge;
import com.utopiaxc.utopiaserverpanel.platform.shared.ModernGameAdapter;
import com.utopiaxc.utopiaserverpanel.platform.shared.ModernMapBridge;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.CommandEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
//? if mc >= 26.1 {
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
//?}
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
//? if mc >= 1.20.5 {
import net.neoforged.neoforge.event.tick.ServerTickEvent;
//?} else {
import net.neoforged.neoforge.event.TickEvent;
//?}
import org.slf4j.Logger;

/**
 * Thin NeoForge entrypoint spanning 1.20.4 through 26.3+.
 */
@Mod(PanelEngine.MOD_ID)
public final class UtopiaServerPanelNeoForgeMod {
    public static final Logger LOGGER = LogUtils.getLogger();
    private volatile long tickStartNs = 0L;

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

    @SubscribeEvent
    public void onServerChat(ServerChatEvent event) {
        try {
            ServerPlayer player = event.getPlayer();
            if (player != null && event.getRawText() != null) {
                PanelEngine.onChatMessage(player.getStringUUID(), player.getName().getString(), event.getRawText());
            }
        } catch (Throwable ignored) {}
    }

    @SubscribeEvent
    public void onCommand(CommandEvent event) {
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

    @SubscribeEvent
    public void onChunkLoad(ChunkEvent.Load event) {
        try {
            if (event.getLevel() instanceof ServerLevel) {
                ServerLevel sl = (ServerLevel) event.getLevel();
                //? if mc >= 26.1 {
                PanelEngine.onChunkDirty(ModernMapBridge.dimensionId(sl), event.getChunk().getPos().x(), event.getChunk().getPos().z());
                //?} else {
                PanelEngine.onChunkDirty(ModernMapBridge.dimensionId(sl), event.getChunk().getPos().x, event.getChunk().getPos().z);
                //?}
            }
        } catch (Throwable ignored) {}
    }

    @SubscribeEvent
    //? if mc >= 26.1 {
    public void onBlockBreak(BreakBlockEvent event) {
    //?} else {
    public void onBlockBreak(BlockEvent.BreakEvent event) {
    //?}
        try {
            if (event.getLevel() instanceof ServerLevel) {
                ServerLevel sl = (ServerLevel) event.getLevel();
                PanelEngine.onChunkDirty(ModernMapBridge.dimensionId(sl), event.getPos().getX() >> 4, event.getPos().getZ() >> 4);
            }
        } catch (Throwable ignored) {}
    }

    @SubscribeEvent
    public void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        try {
            if (event.getLevel() instanceof ServerLevel) {
                ServerLevel sl = (ServerLevel) event.getLevel();
                PanelEngine.onChunkDirty(ModernMapBridge.dimensionId(sl), event.getPos().getX() >> 4, event.getPos().getZ() >> 4);
            }
        } catch (Throwable ignored) {}
    }

    //? if mc >= 1.20.5 {
    @SubscribeEvent
    public void onTickPre(ServerTickEvent.Pre event) {
        tickStartNs = System.nanoTime();
    }

    @SubscribeEvent
    public void onTickPost(ServerTickEvent.Post event) {
        long start = tickStartNs;
        long total = start > 0L ? (System.nanoTime() - start) : 0L;
        PanelEngine.onServerTick(total, 0L, 0L, 0L);
    }
    //?} else {
    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            tickStartNs = System.nanoTime();
        } else {
            long start = tickStartNs;
            long total = start > 0L ? (System.nanoTime() - start) : 0L;
            PanelEngine.onServerTick(total, 0L, 0L, 0L);
        }
    }
    //?}
}

