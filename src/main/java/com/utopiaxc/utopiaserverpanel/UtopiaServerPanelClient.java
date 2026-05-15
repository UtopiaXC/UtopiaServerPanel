package com.utopiaxc.utopiaserverpanel;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/**
 * Client-side mod class. Will not load on dedicated servers.
 */
@Mod(value = UtopiaServerPanel.MODID, dist = Dist.CLIENT)
public class UtopiaServerPanelClient {
    public UtopiaServerPanelClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }
}
