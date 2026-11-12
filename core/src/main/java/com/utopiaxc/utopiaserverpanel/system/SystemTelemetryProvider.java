package com.utopiaxc.utopiaserverpanel.system;

/**
 * Optional SPI providing OS-level CPU and memory telemetry.
 * Modern MC platforms (1.18+) can bind MC's built-in OSHI without bundling extra JARs;
 * legacy versions automatically fall back to {@link JdkSystemTelemetryProvider}.
 */
public interface SystemTelemetryProvider {
    long getSystemTotalMemoryBytes();

    long getSystemUsedMemoryBytes();

    /**
     * System CPU utilization in percentage [0.0, 100.0], or -1 if unavailable.
     */
    double getSystemCpuLoadPercent();

    /**
     * Per-core CPU utilization percentages [0.0, 100.0]; empty array when unavailable.
     */
    double[] getCoreCpuLoadsPercent();
}

