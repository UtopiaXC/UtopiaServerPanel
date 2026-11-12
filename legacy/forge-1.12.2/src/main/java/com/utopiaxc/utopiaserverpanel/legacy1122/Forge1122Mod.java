package com.utopiaxc.utopiaserverpanel.legacy1122;

import com.utopiaxc.utopiaserverpanel.PanelEngine;
import com.utopiaxc.utopiaserverpanel.config.PanelConfig;
import com.utopiaxc.utopiaserverpanel.platform.shared.BuildConstants;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.event.FMLServerStoppingEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;

import java.nio.file.Path;

/**
 * Forge 1.12.2 entrypoint bridging FML lifecycle and player events into {@link PanelEngine}.
 */
@Mod(
        modid = PanelEngine.MOD_ID,
        name = BuildConstants.MOD_NAME,
        version = BuildConstants.MOD_VERSION,
        acceptableRemoteVersions = "*",
        acceptedMinecraftVersions = "[1.12,1.12.2]"
)
public final class Forge1122Mod {

    private Path configDir;

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
}
