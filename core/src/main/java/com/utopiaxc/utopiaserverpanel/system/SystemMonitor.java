package com.utopiaxc.utopiaserverpanel.system;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Shared JVM, OS, disk, and server directory telemetry collector for all platforms.
 */
public final class SystemMonitor {
    private static final long GAME_FOLDER_CACHE_MS = 30_000L;

    private static volatile SystemTelemetryProvider telemetryProvider = new JdkSystemTelemetryProvider();
    private static volatile long cachedGameFolderSize = 0L;
    private static volatile long lastGameFolderScanMs = 0L;

    private SystemMonitor() {}

    public static void setTelemetryProvider(SystemTelemetryProvider provider) {
        telemetryProvider = provider != null ? provider : new JdkSystemTelemetryProvider();
    }

    public static void reset() {
        telemetryProvider = new JdkSystemTelemetryProvider();
        cachedGameFolderSize = 0L;
        lastGameFolderScanMs = 0L;
    }

    public static SystemResourcesSnapshot capture(Path serverDirectory) {
        Runtime runtime = Runtime.getRuntime();
        long jvmMax = runtime.maxMemory();
        long jvmUsed = runtime.totalMemory() - runtime.freeMemory();

        SystemTelemetryProvider provider = telemetryProvider;
        long sysTotal = 0L;
        long sysUsed = 0L;
        double cpuLoad = 0.0;
        double[] coreLoads = new double[0];
        if (provider != null) {
            try {
                sysTotal = Math.max(0L, provider.getSystemTotalMemoryBytes());
                sysUsed = Math.max(0L, provider.getSystemUsedMemoryBytes());
                double rawCpu = provider.getSystemCpuLoadPercent();
                cpuLoad = (Double.isNaN(rawCpu) || Double.isInfinite(rawCpu)) ? 0.0 : Math.max(-1.0, rawCpu);
                double[] rawCores = provider.getCoreCpuLoadsPercent();
                if (rawCores != null) {
                    coreLoads = rawCores;
                }
            } catch (Exception ignored) {}
        }

        long diskTotal = 0L;
        long diskFree = 0L;
        long diskUsable = 0L;
        long folderSize = 0L;
        if (serverDirectory != null) {
            try {
                File dir = serverDirectory.toFile().getAbsoluteFile();
                diskTotal = Math.max(0L, dir.getTotalSpace());
                diskFree = Math.max(0L, dir.getFreeSpace());
                diskUsable = Math.max(0L, dir.getUsableSpace());
                folderSize = getCachedFolderSize(dir);
            } catch (Exception ignored) {}
        }

        return new SystemResourcesSnapshot(
                jvmMax, jvmUsed,
                sysTotal, sysUsed,
                cpuLoad, coreLoads,
                diskTotal, diskFree, diskUsable,
                folderSize
        );
    }

    private static long getCachedFolderSize(File serverDir) {
        long now = System.currentTimeMillis();
        if (lastGameFolderScanMs > 0 && now - lastGameFolderScanMs < GAME_FOLDER_CACHE_MS) {
            return cachedGameFolderSize;
        }
        long size = computeFolderSize(serverDir);
        cachedGameFolderSize = size;
        lastGameFolderScanMs = now;
        return size;
    }

    private static long computeFolderSize(File folder) {
        if (folder == null || Files.isSymbolicLink(folder.toPath())) {
            return 0L;
        }
        long length = 0L;
        File[] files = folder.listFiles();
        if (files != null) {
            for (File file : files) {
                if (Files.isSymbolicLink(file.toPath())) {
                    continue;
                }
                if (file.isFile()) {
                    length += file.length();
                } else if (file.isDirectory()) {
                    length += computeFolderSize(file);
                }
            }
        }
        return length;
    }
}

