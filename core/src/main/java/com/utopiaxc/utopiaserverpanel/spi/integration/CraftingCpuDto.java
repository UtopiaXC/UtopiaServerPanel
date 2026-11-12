package com.utopiaxc.utopiaserverpanel.spi.integration;

/**
 * Pure Java status descriptor of a crafting co-processor / CPU cluster in a storage network.
 */
public final class CraftingCpuDto {
    private final String name;
    private final long storageBytes;
    private final int coProcessors;
    private final boolean busy;
    private final String currentJobName;
    private final long remainingItems;

    public CraftingCpuDto(
            String name,
            long storageBytes,
            int coProcessors,
            boolean busy,
            String currentJobName,
            long remainingItems
    ) {
        this.name = name;
        this.storageBytes = storageBytes;
        this.coProcessors = coProcessors;
        this.busy = busy;
        this.currentJobName = currentJobName;
        this.remainingItems = remainingItems;
    }

    public String getName() { return name; }
    public long getStorageBytes() { return storageBytes; }
    public int getCoProcessors() { return coProcessors; }
    public boolean isBusy() { return busy; }
    public String getCurrentJobName() { return currentJobName; }
    public long getRemainingItems() { return remainingItems; }
}

