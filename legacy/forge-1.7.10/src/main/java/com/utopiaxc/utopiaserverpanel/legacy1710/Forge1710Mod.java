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

import java.nio.file.Path;

/**
 * Forge 1.7.10 entrypoint bridging FML lifecycle and player events into {@link PanelEngine}.
 */
@Mod(
        modid = PanelEngine.MOD_ID,
        name = BuildConstants.MOD_NAME,
        version = BuildConstants.MOD_VERSION,
        acceptableRemoteVersions = "*",
        acceptedMinecraftVersions = "[1.7.10]"
)
public final class Forge1710Mod {

    private Path configDir;
    private Forge1710GameAdapter activeAdapter;

    @Mod.EventHandler
    public void onPreInit(FMLPreInitializationEvent event) {
        this.configDir = event.getModConfigurationDirectory().toPath().toAbsolutePath().normalize();
        PanelEngine.setLogger(new Log4jBetaPanelLogger(event.getModLog()));
        PanelConfig.initialize(this.configDir);
        Forge1710LogBridge.register();
        FMLCommonHandler.instance().bus().register(this);
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
        if (event.phase == TickEvent.Phase.START && activeAdapter != null) {
            activeAdapter.drainMainThreadTasks();
        }
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
