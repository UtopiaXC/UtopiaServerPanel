package com.utopiaxc.utopiaserverpanel.spi.integration;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central soft-dependency registry populated by loader/version-specific integration bridges.
 */
public final class ModIntegrationRegistry {
    private static final Map<String, CraftingNetworkProvider> CRAFTING_PROVIDERS =
            new ConcurrentHashMap<String, CraftingNetworkProvider>();

    private ModIntegrationRegistry() {}

    public static void registerCraftingProvider(CraftingNetworkProvider provider) {
        if (provider != null && provider.getIntegrationId() != null) {
            CRAFTING_PROVIDERS.put(provider.getIntegrationId(), provider);
        }
    }

    public static CraftingNetworkProvider getCraftingProvider(String integrationId) {
        return integrationId != null ? CRAFTING_PROVIDERS.get(integrationId) : null;
    }

    public static Collection<CraftingNetworkProvider> listCraftingProviders() {
        return Collections.unmodifiableCollection(CRAFTING_PROVIDERS.values());
    }

    public static void clear() {
        CRAFTING_PROVIDERS.clear();
    }
}

