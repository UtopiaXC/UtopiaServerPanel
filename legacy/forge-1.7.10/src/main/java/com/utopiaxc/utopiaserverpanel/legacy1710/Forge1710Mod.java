package com.utopiaxc.utopiaserverpanel.legacy1710;

import com.utopiaxc.utopiaserverpanel.PanelEngine;
import com.utopiaxc.utopiaserverpanel.config.PanelConfig;
import com.utopiaxc.utopiaserverpanel.platform.shared.BuildConstants;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.event.FMLServerStoppingEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.event.world.ChunkEvent;

import java.nio.file.Path;
import java.util.Locale;

/**
 * Forge 1.7.10 entrypoint bridging FML lifecycle and player events into {@link PanelEngine}.
 */
@Mod(
        modid = PanelEngine.MOD_ID,
        name = BuildConstants.MOD_NAME,
        version = BuildConstants.MOD_VERSION,
        acceptableRemoteVersions = "*",
        acceptedMinecraftVersions = "*"
)
public final class Forge1710Mod {

    private Path configDir;
    private Forge1710GameAdapter activeAdapter;
    private volatile long tickStartNs = 0L;

    @Mod.EventHandler
    public void onPreInit(FMLPreInitializationEvent event) {
        this.configDir = event.getModConfigurationDirectory().toPath().toAbsolutePath().normalize();
        PanelEngine.setLogger(new Log4jBetaPanelLogger(event.getModLog()));
        PanelConfig.initialize(this.configDir);
        Forge1710LogBridge.register();
        FMLCommonHandler.instance().bus().register(this);
        MinecraftForge.EVENT_BUS.register(this);
    }

    @Mod.EventHandler
    public void onServerStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new Forge1710Command());
        this.activeAdapter = new Forge1710GameAdapter(event.getServer(), this.configDir, System.currentTimeMillis());
        PanelEngine.start(this.activeAdapter, null);
    }

    @Mod.EventHandler
    public void onServerStopping(FMLServerStoppingEvent event) {
        PanelEngine.stop();
        this.activeAdapter = null;
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            tickStartNs = System.nanoTime();
            if (activeAdapter != null) {
                activeAdapter.drainMainThreadTasks();
            }
        } else {
            long total = tickStartNs > 0L ? (System.nanoTime() - tickStartNs) : 0L;
            PanelEngine.onServerTick(total, 0L, 0L, 0L);
        }
    }

    @SubscribeEvent
    public void onServerChat(ServerChatEvent event) {
        try {
            EntityPlayerMP player = event.player;
            if (player != null && event.message != null) {
                PanelEngine.onChatMessage(player.getUniqueID().toString(), player.getCommandSenderName(), event.message);
            }
        } catch (Throwable ignored) {}
    }

    @SubscribeEvent
    public void onCommand(CommandEvent event) {
        try {
            String name = event.command != null ? event.command.getCommandName().toLowerCase(Locale.ROOT) : "";
            String[] params = event.parameters;
            if (("tell".equals(name) || "msg".equals(name) || "w".equals(name)) && params != null && params.length >= 2) {
                StringBuilder msg = new StringBuilder();
                for (int i = 1; i < params.length; i++) {
                    if (i > 1) msg.append(' ');
                    msg.append(params[i]);
                }
                String uuid = event.sender instanceof EntityPlayerMP ? ((EntityPlayerMP) event.sender).getUniqueID().toString() : null;
                PanelEngine.onWhisperMessage(uuid, event.sender.getCommandSenderName(), params[0], msg.toString());
            }
        } catch (Throwable ignored) {}
    }

    @SubscribeEvent
    public void onChunkLoad(ChunkEvent.Load event) {
        try {
            if (event.world instanceof WorldServer && !event.world.isRemote) {
                String dim = Forge1710GameAdapter.dimId(event.world.provider.dimensionId);
                PanelEngine.onChunkDirty(dim, event.getChunk().xPosition, event.getChunk().zPosition);
            }
        } catch (Throwable ignored) {}
    }

    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        try {
            if (event.world instanceof WorldServer && !event.world.isRemote) {
                String dim = Forge1710GameAdapter.dimId(event.world.provider.dimensionId);
                PanelEngine.onChunkDirty(dim, event.x >> 4, event.z >> 4);
            }
        } catch (Throwable ignored) {}
    }

    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.player != null) {
            PanelEngine.onPlayerJoin(
                    event.player.getCommandSenderName(),
                    event.player.getUniqueID().toString()
            );
        }
    }

    @SubscribeEvent
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.player != null) {
            PanelEngine.onPlayerLeave(
                    event.player.getCommandSenderName(),
                    event.player.getUniqueID().toString()
            );
        }
    }
}
