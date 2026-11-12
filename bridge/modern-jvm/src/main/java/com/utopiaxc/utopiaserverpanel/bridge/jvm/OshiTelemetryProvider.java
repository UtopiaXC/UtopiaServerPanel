package com.utopiaxc.utopiaserverpanel.bridge.jvm;

import com.utopiaxc.utopiaserverpanel.system.SystemTelemetryProvider;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
import oshi.hardware.HardwareAbstractionLayer;

/**
 * OS-level CPU and memory telemetry backed by Minecraft's bundled OSHI 6 library (1.18.2+).
 */
public final class OshiTelemetryProvider implements SystemTelemetryProvider {
    private final HardwareAbstractionLayer hardware = new SystemInfo().getHardware();
    private final Object tickLock = new Object();

    private long[] prevSystemTicks;
    private long[][] prevCoreTicks;

    public OshiTelemetryProvider() {
        CentralProcessor processor = hardware.getProcessor();
        this.prevSystemTicks = processor.getSystemCpuLoadTicks();
        this.prevCoreTicks = processor.getProcessorCpuLoadTicks();
    }

    @Override
    public long getSystemTotalMemoryBytes() {
        GlobalMemory memory = hardware.getMemory();
        return memory.getTotal();
    }

    @Override
    public long getSystemUsedMemoryBytes() {
        GlobalMemory memory = hardware.getMemory();
        return Math.max(0L, memory.getTotal() - memory.getAvailable());
    }

    @Override
    public double getSystemCpuLoadPercent() {
        synchronized (tickLock) {
            CentralProcessor processor = hardware.getProcessor();
            if (prevSystemTicks == null) {
                prevSystemTicks = processor.getSystemCpuLoadTicks();
            }
            double raw = processor.getSystemCpuLoadBetweenTicks(prevSystemTicks) * 100.0;
            prevSystemTicks = processor.getSystemCpuLoadTicks();
            return Double.isFinite(raw) ? Math.max(0.0, raw) : 0.0;
        }
    }

    @Override
    public double[] getCoreCpuLoadsPercent() {
        synchronized (tickLock) {
            CentralProcessor processor = hardware.getProcessor();
            if (prevCoreTicks == null) {
                prevCoreTicks = processor.getProcessorCpuLoadTicks();
            }
            double[] rawCores = processor.getProcessorCpuLoadBetweenTicks(prevCoreTicks);
            prevCoreTicks = processor.getProcessorCpuLoadTicks();
            if (rawCores == null) {
                return new double[0];
            }
            double[] result = new double[rawCores.length];
            for (int i = 0; i < rawCores.length; i++) {
                double val = rawCores[i] * 100.0;
                result[i] = Double.isFinite(val) ? Math.max(0.0, val) : 0.0;
            }
            return result;
        }
    }
}

