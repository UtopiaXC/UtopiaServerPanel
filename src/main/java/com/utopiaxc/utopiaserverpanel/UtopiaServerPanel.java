package com.utopiaxc.utopiaserverpanel;

import com.utopiaxc.utopiaserverpanel.command.USPCommands;
import com.utopiaxc.utopiaserverpanel.terminal.TerminalCapture;
import com.utopiaxc.utopiaserverpanel.web.WebServer;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

@Mod(UtopiaServerPanel.MODID)
public class UtopiaServerPanel {
    public static final String MODID = "utopia_server_panel";
    public static final Logger LOGGER = LogUtils.getLogger();

    public UtopiaServerPanel(ModContainer modContainer) {
        TerminalCapture.register();
        NeoForge.EVENT_BUS.register(this);
        NeoForge.EVENT_BUS.register(new USPCommands());
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("Starting UtopiaServerPanel...");
        WebServer.start(event.getServer(), Config.WEB_PORT.getAsInt());
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        LOGGER.info("Stopping UtopiaServerPanel...");
        WebServer.stop();
    }
}
