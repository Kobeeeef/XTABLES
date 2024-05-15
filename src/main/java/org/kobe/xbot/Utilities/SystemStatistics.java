package org.kobe.xbot.Utilities;

import com.sun.management.OperatingSystemMXBean;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.ThreadMXBean;

public class SystemStatistics {
    private final long freeMemoryMB;
    private final long maxMemoryMB;

    private final double processCpuLoadPercentage;
    private final int availableProcessors;
    private final long totalThreads;
    private final long nanoTime;
    private final int totalClients;
    private static final MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
    private static final OperatingSystemMXBean osMXBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
    private static final ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
    public SystemStatistics(int totalClients) {
        this.nanoTime = System.nanoTime();
        this.totalClients = totalClients;
        this.maxMemoryMB = osMXBean.getTotalMemorySize() / (1024 * 1024);
        this.freeMemoryMB = osMXBean.getFreeMemorySize() / (1024 * 1024);
        this.processCpuLoadPercentage = osMXBean.getCpuLoad() * 100;
        this.availableProcessors = osMXBean.getAvailableProcessors();
        this.totalThreads = threadMXBean.getThreadCount();
    }

    public int getTotalClients() {
        return totalClients;
    }

    public long getNanoTime() {
        return nanoTime;
    }

    // Getters for all fields with units in method names
    public long getFreeMemoryMB() {
        return freeMemoryMB;
    }

    public long getMaxMemoryMB() {
        return maxMemoryMB;
    }


    public double getProcessCpuLoadPercentage() {
        return processCpuLoadPercentage;
    }

    public int getAvailableProcessors() {
        return availableProcessors;
    }

    public long getTotalThreads() {
        return totalThreads;
    }



}
