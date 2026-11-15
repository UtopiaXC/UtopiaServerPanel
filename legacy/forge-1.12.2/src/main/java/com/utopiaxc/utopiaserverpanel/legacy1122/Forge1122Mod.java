package com.utopiaxc.utopiaserverpanel.legacy1122;

import com.utopiaxc.utopiaserverpanel.PanelEngine;
import com.utopiaxc.utopiaserverpanel.config.PanelConfig;
import com.utopiaxc.utopiaserverpanel.platform.shared.BuildConstants;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.event.FMLServerStoppingEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.nio.file.Path;
import java.util.Locale;

/**
 * Forge 1.12.2 entrypoint bridging FML lifecycle and player events into {@link PanelEngine}.
 */
@Mod(
        modid = PanelEngine.MOD_ID,
        name = BuildConstants.MOD_NAME,
        version = BuildConstants.MOD_VERSION,
        acceptableRemoteVersions = "*",
        acceptedMinecraftVersions = "*"
)
public final class Forge1122Mod {

    private Path configDir;
    private volatile long tickStartNs = 0L;

    @Mod.EventHandler
    public void onPreInit(FMLPreInitializationEvent event) {
        this.configDir = event.getModConfigurationDirectory().toPath().toAbsolutePath().normalize();
        PanelEngine.setLogger(new Log4j112PanelLogger(event.getModLog()));
        PanelConfig.initialize(this.configDir);
        Forge1122LogBridge.register();
        MinecraftForge.EVENT_BUS.register(this);
    }

    @Mod.EventHandler
    public void onServerStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new Forge1122Command());
        PanelEngine.start(
                new Forge1122GameAdapter(event.getServer(), this.configDir, System.currentTimeMillis()),
                null
        );
    }

    @Mod.EventHandler
    public void onServerStopping(FMLServerStoppingEvent event) {
        PanelEngine.stop();
    }

    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.player != null) {
            PanelEngine.onPlayerJoin(
                    event.player.getName(),
                    event.player.getUniqueID().toString()
            );
        }
    }

    @SubscribeEvent
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.player != null) {
            PanelEngine.onPlayerLeave(
                    event.player.getName(),
                    event.player.getUniqueID().toString()
            );
        }
    }

    @SubscribeEvent
    public void onServerChat(ServerChatEvent event) {
        try {
            EntityPlayerMP player = event.getPlayer();
            if (player != null && event.getMessage() != null) {
                PanelEngine.onChatMessage(player.getUniqueID().toString(), player.getName(), event.getMessage());
            }
        } catch (Throwable ignored) {}
    }

    @SubscribeEvent
    public void onCommand(CommandEvent event) {
        try {
            String name = event.getCommand() != null ? event.getCommand().getName().toLowerCase(Locale.ROOT) : "";
            String[] params = event.getParameters();
            if (("tell".equals(name) || "msg".equals(name) || "w".equals(name)) && params != null && params.length >= 2) {
                StringBuilder msg = new StringBuilder();
                for (int i = 1; i < params.length; i++) {
                    if (i > 1) msg.append(' ');
                    msg.append(params[i]);
                }
                Entity ent = event.getSender().getCommandSenderEntity();
                String uuid = ent != null ? ent.getUniqueID().toString() : null;
                PanelEngine.onWhisperMessage(uuid, event.getSender().getName(), params[0], msg.toString());
            }
        } catch (Throwable ignored) {}
    }

    @SubscribeEvent
    public void onChunkLoad(ChunkEvent.Load event) {
        try {
            if (event.getWorld() instanceof WorldServer && !event.getWorld().isRemote) {
                String dim = Forge1122GameAdapter.dimId(event.getWorld().provider.getDimension());
                PanelEngine.onChunkDirty(dim, event.getChunk().x, event.getChunk().z);
            }
        } catch (Throwable ignored) {}
    }

    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        try {
            if (event.getWorld() instanceof WorldServer && !event.getWorld().isRemote) {
                String dim = Forge1122GameAdapter.dimId(event.getWorld().provider.getDimension());
                PanelEngine.onChunkDirty(dim, event.getPos().getX() >> 4, event.getPos().getZ() >> 4);
            }
        } catch (Throwable ignored) {}
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            tickStartNs = System.nanoTime();
        } else {
            long total = tickStartNs > 0L ? (System.nanoTime() - tickStartNs) : 0L;
            PanelEngine.onServerTick(total, 0L, 0L, 0L);
        }
    }
}
