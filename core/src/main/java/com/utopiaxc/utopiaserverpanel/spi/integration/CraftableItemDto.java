package com.utopiaxc.utopiaserverpanel.spi.integration;

/**
 * Pure Java representation of a stored or craftable item exposed by storage/autocrafting mods (e.g., AE2/RS).
 */
public final class CraftableItemDto {
    private final String itemId;
    private final String displayName;
    private final String nbtHash;
    private final long availableCount;
    private final boolean craftable;

    public CraftableItemDto(String itemId, String displayName, String nbtHash, long availableCount, boolean craftable) {
        this.itemId = itemId;
        this.displayName = displayName;
        this.nbtHash = nbtHash;
        this.availableCount = availableCount;
        this.craftable = craftable;
    }

    public String getItemId() { return itemId; }
    public String getDisplayName() { return displayName; }
    public String getNbtHash() { return nbtHash; }
    public long getAvailableCount() { return availableCount; }
    public boolean isCraftable() { return craftable; }
}

