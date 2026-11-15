package com.utopiaxc.utopiaserverpanel.web.service;

import com.utopiaxc.utopiaserverpanel.adapter.AdapterRegistry;
import com.utopiaxc.utopiaserverpanel.adapter.GameAdapter;
import com.utopiaxc.utopiaserverpanel.adapter.model.ServerStatusSnapshot;
import com.utopiaxc.utopiaserverpanel.spi.profiler.ProfilerHotspotSnapshot;
import com.utopiaxc.utopiaserverpanel.web.db.model.PlayerBindingInfo;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Low-overhead server lag diagnostics &amp; tick-phase profiler service.
 * Aggregates rolling MC tick phase timings, dimension/chunk entity &amp; block-entity hotspots,
 * and JVM memory / GC / thread diagnostics.
 */
public final class ProfilerService {
    private static final int RING_SIZE = 120;
    private static final long[] TOTAL_NS = new long[RING_SIZE];
    private static final long[] WORLD_NS = new long[RING_SIZE];
    private static final long[] ENTITY_NS = new long[RING_SIZE];
    private static final long[] BLOCK_ENTITY_NS = new long[RING_SIZE];
    private static int ringIndex = 0;
    private static int ringCount = 0;
    private static final Object RING_LOCK = new Object();

    private static volatile ProfilerHotspotSnapshot cachedHotspots;
    private static volatile long cachedHotspotsAt = 0L;

    private ProfilerService() {}

    /**
     * Record a single server tick's measured durations (in nanoseconds).
     * Negative values for sub-phases indicate the platform could not isolate that phase independently.
     */
    public static void recordTickTiming(long totalNs, long worldNs, long entityNs, long blockEntityNs) {
        synchronized (RING_LOCK) {
            TOTAL_NS[ringIndex] = Math.max(0L, totalNs);
            WORLD_NS[ringIndex] = Math.max(0L, worldNs);
            ENTITY_NS[ringIndex] = Math.max(0L, entityNs);
            BLOCK_ENTITY_NS[ringIndex] = Math.max(0L, blockEntityNs);
            ringIndex = (ringIndex + 1) % RING_SIZE;
            if (ringCount < RING_SIZE) ringCount++;
        }
    }

    public static Map<String, Object> getProfilerSnapshot(boolean forceRefresh) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("mapEnabled", Boolean.valueOf(SettingsService.isMapEnabled()));
        result.put("tickPhases", buildTickPhaseSummary());
        result.put("jvm", buildJvmDiagnostics());
        result.put("hotspots", getHotspotSnapshot(forceRefresh));
        return result;
    }

    public static ProfilerHotspotSnapshot getHotspotSnapshot(boolean forceRefresh) {
        long now = System.currentTimeMillis();
        ProfilerHotspotSnapshot current = cachedHotspots;
        if (!forceRefresh && current != null && (now - cachedHotspotsAt) < 3000L) {
            return current;
        }
        GameAdapter adapter = AdapterRegistry.getNullable();
        ProfilerHotspotSnapshot fresh = null;
        if (adapter != null) {
            try {
                fresh = adapter.collectHotspotSnapshot();
            } catch (Throwable ignored) {}
        }
        if (fresh == null) {
            fresh = new ProfilerHotspotSnapshot();
        }
        cachedHotspots = fresh;
        cachedHotspotsAt = now;
        return fresh;
    }

    public static boolean teleportBoundAdmin(int userId, String dimension, double x, double y, double z) {
        return teleportBoundAdmin(userId, null, dimension, x, y, z);
    }

    public static boolean teleportBoundAdmin(int userId, String playerUuid, String dimension, double x, double y, double z) {
        PlayerBindingInfo binding = BindingService.findByUserAndUuid(userId, playerUuid);
        if (binding == null || binding.getPlayerName() == null) {
            return false;
        }
        GameAdapter adapter = AdapterRegistry.getNullable();
        if (adapter == null) return false;
        return adapter.teleportPlayerTo(binding.getPlayerName(), dimension, x, y, z);
    }

    private static Map<String, Object> buildTickPhaseSummary() {
        long sumTotal = 0L;
        long sumWorld = 0L;
        long sumEntity = 0L;
        long sumBlockEntity = 0L;
        long maxTotal = 0L;
        int n;
        synchronized (RING_LOCK) {
            n = ringCount;
            for (int i = 0; i < n; i++) {
                long t = TOTAL_NS[i];
                sumTotal += t;
                sumWorld += WORLD_NS[i];
                sumEntity += ENTITY_NS[i];
                sumBlockEntity += BLOCK_ENTITY_NS[i];
                if (t > maxTotal) maxTotal = t;
            }
        }

        double avgTotalMs = n > 0 ? (sumTotal / (double) n) / 1_000_000.0 : 0.0;
        double maxTotalMs = maxTotal / 1_000_000.0;

        // Fall back to adapter MSPT when rolling hook had no samples yet
        GameAdapter adapter = AdapterRegistry.getNullable();
        double statusTps = 20.0;
        if (adapter != null) {
            ServerStatusSnapshot st = adapter.getServerStatus();
            if (st != null) {
                statusTps = st.getTps();
                if (avgTotalMs <= 0.01 && st.getTickTimeMs() > 0) {
                    avgTotalMs = st.getTickTimeMs();
                    if (maxTotalMs < avgTotalMs) maxTotalMs = avgTotalMs;
                }
            }
        }

        double avgWorldMs = n > 0 ? (sumWorld / (double) n) / 1_000_000.0 : 0.0;
        double avgEntityMs = n > 0 ? (sumEntity / (double) n) / 1_000_000.0 : 0.0;
        double avgBeMs = n > 0 ? (sumBlockEntity / (double) n) / 1_000_000.0 : 0.0;

        // Ensure component bars remain consistent even if sub-phases overlap or weren't separately split
        double accounted = avgEntityMs + avgBeMs;
        double worldOnlyMs = avgWorldMs > accounted ? (avgWorldMs - accounted) : (avgWorldMs > 0 ? avgWorldMs : 0.0);
        double otherMs = Math.max(0.0, avgTotalMs - (worldOnlyMs + accounted));

        List<Map<String, Object>> phases = new ArrayList<Map<String, Object>>();
        phases.add(phaseRow("world_terrain", worldOnlyMs, avgTotalMs));
        phases.add(phaseRow("entities", avgEntityMs, avgTotalMs));
        phases.add(phaseRow("block_entities", avgBeMs, avgTotalMs));
        phases.add(phaseRow("network_and_other", otherMs, avgTotalMs));

        Map<String, Object> summary = new LinkedHashMap<String, Object>();
        summary.put("samples", Integer.valueOf(n));
        summary.put("avgMspt", Double.valueOf(round2(avgTotalMs)));
        summary.put("maxMspt", Double.valueOf(round2(maxTotalMs)));
        summary.put("tps", Double.valueOf(round2(statusTps)));
        summary.put("budgetMs", Double.valueOf(50.0));
        summary.put("budgetUsagePct", Double.valueOf(round2(Math.min(200.0, (avgTotalMs / 50.0) * 100.0))));
        summary.put("phases", phases);
        return summary;
    }

    private static Map<String, Object> phaseRow(String key, double ms, double totalMs) {
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        row.put("key", key);
        row.put("ms", Double.valueOf(round2(ms)));
        double pct = totalMs > 0.0001 ? (ms / totalMs) * 100.0 : 0.0;
        row.put("sharePct", Double.valueOf(round2(pct)));
        double budgetPct = (ms / 50.0) * 100.0;
        row.put("budgetPct", Double.valueOf(round2(budgetPct)));
        return row;
    }

    private static Map<String, Object> buildJvmDiagnostics() {
        Map<String, Object> jvm = new LinkedHashMap<String, Object>();
        try {
            MemoryUsage heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
            MemoryUsage nonHeap = ManagementFactory.getMemoryMXBean().getNonHeapMemoryUsage();
            jvm.put("heapUsedMb", Long.valueOf(heap.getUsed() / (1024L * 1024L)));
            jvm.put("heapCommittedMb", Long.valueOf(heap.getCommitted() / (1024L * 1024L)));
            jvm.put("heapMaxMb", Long.valueOf(Math.max(0L, heap.getMax()) / (1024L * 1024L)));
            jvm.put("nonHeapUsedMb", Long.valueOf(nonHeap.getUsed() / (1024L * 1024L)));

            List<Map<String, Object>> gcs = new ArrayList<Map<String, Object>>();
            for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
                Map<String, Object> g = new LinkedHashMap<String, Object>();
                g.put("name", gc.getName());
                g.put("count", Long.valueOf(gc.getCollectionCount()));
                g.put("timeMs", Long.valueOf(gc.getCollectionTime()));
                gcs.add(g);
            }
            jvm.put("gc", gcs);

            ThreadMXBean threads = ManagementFactory.getThreadMXBean();
            jvm.put("threadCount", Integer.valueOf(threads.getThreadCount()));
            jvm.put("daemonThreadCount", Integer.valueOf(threads.getDaemonThreadCount()));
            long[] deadlocked = threads.findDeadlockedThreads();
            jvm.put("deadlockedThreads", Integer.valueOf(deadlocked != null ? deadlocked.length : 0));

            Map<String, Integer> states = new LinkedHashMap<String, Integer>();
            ThreadInfo[] infos = threads.getThreadInfo(threads.getAllThreadIds(), 0);
            if (infos != null) {
                for (ThreadInfo ti : infos) {
                    if (ti != null && ti.getThreadState() != null) {
                        String st = ti.getThreadState().name();
                        Integer cur = states.get(st);
                        states.put(st, Integer.valueOf(cur != null ? cur.intValue() + 1 : 1));
                    }
                }
            }
            jvm.put("threadStates", states);
        } catch (Throwable t) {
            jvm.put("error", t.getMessage());
        }
        return jvm;
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
