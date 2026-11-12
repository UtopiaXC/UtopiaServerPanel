package com.utopiaxc.utopiaserverpanel.system;

/**
 * Immutable snapshot of JVM, OS, disk, and server folder telemetry.
 */
public final class SystemResourcesSnapshot {
    private final long jvmMaxMemory;
    private final long jvmUsedMemory;
    private final long systemTotalMemory;
    private final long systemUsedMemory;
    private final double cpuLoad;
    private final double[] coreLoads;
    private final long diskTotalSpace;
    private final long diskFreeSpace;
    private final long diskUsableSpace;
    private final long gameFolderSize;

    public SystemResourcesSnapshot(
            long jvmMaxMemory,
            long jvmUsedMemory,
            long systemTotalMemory,
            long systemUsedMemory,
            double cpuLoad,
            double[] coreLoads,
            long diskTotalSpace,
            long diskFreeSpace,
            long diskUsableSpace,
            long gameFolderSize
    ) {
        this.jvmMaxMemory = jvmMaxMemory;
        this.jvmUsedMemory = jvmUsedMemory;
        this.systemTotalMemory = systemTotalMemory;
        this.systemUsedMemory = systemUsedMemory;
        this.cpuLoad = cpuLoad;
        this.coreLoads = coreLoads != null ? coreLoads : new double[0];
        this.diskTotalSpace = diskTotalSpace;
        this.diskFreeSpace = diskFreeSpace;
        this.diskUsableSpace = diskUsableSpace;
        this.gameFolderSize = gameFolderSize;
    }

    public long getJvmMaxMemory() { return jvmMaxMemory; }
    public long getJvmUsedMemory() { return jvmUsedMemory; }
    public long getSystemTotalMemory() { return systemTotalMemory; }
    public long getSystemUsedMemory() { return systemUsedMemory; }
    public double getCpuLoad() { return cpuLoad; }
    public double[] getCoreLoads() { return coreLoads; }
    public long getDiskTotalSpace() { return diskTotalSpace; }
    public long getDiskFreeSpace() { return diskFreeSpace; }
    public long getDiskUsableSpace() { return diskUsableSpace; }
    public long getGameFolderSize() { return gameFolderSize; }
}

