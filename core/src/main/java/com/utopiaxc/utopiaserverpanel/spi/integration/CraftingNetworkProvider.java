package com.utopiaxc.utopiaserverpanel.spi.integration;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Asynchronous soft-dependency SPI for logistics/autocrafting mods (e.g., Applied Energistics 2,
 * Refined Storage). Implementations are registered only when the target mod is detected loaded
 * and must execute game-state mutations on the server main thread.
 */
public interface CraftingNetworkProvider {

    /** Unique provider ID (e.g., "ae2", "refinedstorage"). */
    String getIntegrationId();

    /** Human-readable provider name. */
    String getDisplayName();

    CompletableFuture<List<CraftableItemDto>> listCraftables(String networkId);

    CompletableFuture<List<CraftingCpuDto>> listCraftingCpus(String networkId);

    CompletableFuture<CraftingSimulationResult> simulateCraft(String networkId, String itemId, String nbtHash, long count);

    CompletableFuture<CraftingJobResult> submitCraft(String networkId, String itemId, String nbtHash, long count, String cpuName);
}

