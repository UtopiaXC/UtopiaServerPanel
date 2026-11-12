package com.utopiaxc.utopiaserverpanel.system;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.lang.reflect.Method;

/**
 * Zero-dependency fallback {@link SystemTelemetryProvider} using standard JVM MXBeans.
 * Keeps legacy builds (MC 1.7.10 ~ 1.16.5) free of native JNA/OSHI overhead while still
 * reporting real system CPU utilization and physical memory stats.
 */
public final class JdkSystemTelemetryProvider implements SystemTelemetryProvider {
    private static final double[] EMPTY_CORES = new double[0];

    private final OperatingSystemMXBean osBean;
    private final Method totalMemMethod;
    private final Method freeMemMethod;
    private final Method cpuLoadMethod;

    public JdkSystemTelemetryProvider() {
        this.osBean = ManagementFactory.getOperatingSystemMXBean();
        this.totalMemMethod = findMethod(osBean, "getTotalMemorySize", "getTotalPhysicalMemorySize");
        this.freeMemMethod = findMethod(osBean, "getFreeMemorySize", "getFreePhysicalMemorySize");
        this.cpuLoadMethod = findMethod(osBean, "getCpuLoad", "getSystemCpuLoad");
    }

    @Override
    public long getSystemTotalMemoryBytes() {
        return invokeLong(totalMemMethod, osBean);
    }

    @Override
    public long getSystemUsedMemoryBytes() {
        long total = getSystemTotalMemoryBytes();
        long free = invokeLong(freeMemMethod, osBean);
        if (total <= 0 || free < 0) return 0L;
        return Math.max(0L, total - free);
    }

    @Override
    public double getSystemCpuLoadPercent() {
        double raw = invokeDouble(cpuLoadMethod, osBean);
        if (Double.isNaN(raw) || Double.isInfinite(raw) || raw < 0.0) {
            double loadAvg = osBean.getSystemLoadAverage();
            int cpus = Math.max(1, osBean.getAvailableProcessors());
            if (loadAvg >= 0.0) {
                return Math.min(100.0, (loadAvg / cpus) * 100.0);
            }
            return -1.0;
        }
        return Math.max(0.0, Math.min(100.0, raw * 100.0));
    }

    @Override
    public double[] getCoreCpuLoadsPercent() {
        return EMPTY_CORES;
    }

    private static Method findMethod(Object target, String... names) {
        if (target == null) return null;
        for (String name : names) {
            try {
                Method m = target.getClass().getMethod(name);
                m.setAccessible(true);
                return m;
            } catch (Exception ignored) {
                // Try public interface method if implementation class is package-private on Java 9+
            }
            for (Class<?> iface : target.getClass().getInterfaces()) {
                try {
                    Method m = iface.getMethod(name);
                    m.setAccessible(true);
                    return m;
                } catch (Exception ignored) {}
            }
        }
        return null;
    }

    private static long invokeLong(Method m, Object target) {
        if (m == null) return 0L;
        try {
            Object v = m.invoke(target);
            return v instanceof Number ? ((Number) v).longValue() : 0L;
        } catch (Exception e) {
            return 0L;
        }
    }

    private static double invokeDouble(Method m, Object target) {
        if (m == null) return -1.0;
        try {
            Object v = m.invoke(target);
            return v instanceof Number ? ((Number) v).doubleValue() : -1.0;
        } catch (Exception e) {
            return -1.0;
        }
    }
}

