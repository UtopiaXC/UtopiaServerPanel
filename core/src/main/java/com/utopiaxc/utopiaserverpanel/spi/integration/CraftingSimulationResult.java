package com.utopiaxc.utopiaserverpanel.spi.integration;

import java.util.Collections;
import java.util.List;

/**
 * Simulation outcome before committing an autocrafting order.
 */
public final class CraftingSimulationResult {
    private final boolean possible;
    private final long byteCost;
    private final List<CraftableItemDto> missingItems;
    private final String summary;

    public CraftingSimulationResult(boolean possible, long byteCost, List<CraftableItemDto> missingItems, String summary) {
        this.possible = possible;
        this.byteCost = byteCost;
        this.missingItems = missingItems != null
                ? Collections.unmodifiableList(missingItems)
                : Collections.<CraftableItemDto>emptyList();
        this.summary = summary;
    }

    public boolean isPossible() { return possible; }
    public long getByteCost() { return byteCost; }
    public List<CraftableItemDto> getMissingItems() { return missingItems; }
    public String getSummary() { return summary; }
}

