package org.kobe.xbot.Utilities;

import com.sun.management.OperatingSystemMXBean;
import org.kobe.xbot.JServer.XTablesServer;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadMXBean;
import java.util.List;

public class SystemStatistics {
    private final long freeMemoryMB;
    private final long usedMemoryMB;
    private final long maxMemoryMB;
    private final double processCpuLoadPercentage;
    private final int availableProcessors;
    private final long totalThreads;
    private final long nanoTime;
    private final String health;
    private final double powerUsageWatts;
    private final int totalClients;
    private XTableStatus status;
    private int totalPullMessages;
    private int totalReplyMessages;
    private int totalPublishMessages;
    private double pullPs;
    private double replyPs;
    private double publishPs;
    private int maxIterationsPerSecond;
    private final String ip;
    private final String processId;
    private final String langVersion;
    private final String langVendor;
    private final String jvmName;
    private String hostname;
    private List<ClientData> clientDataList;
    private String version;
    private final String type = "JAVA";
    private long nextClientRegistryUpdate;

    private static final MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
    private static final OperatingSystemMXBean osMXBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
    private static final ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
    private static final RuntimeMXBean runtimeMXBean = ManagementFactory.getRuntimeMXBean();


    public SystemStatistics(XTablesServer instance) {
        this.nanoTime = System.nanoTime();
        if(instance.getClientRegistry() != null) {
            this.totalClients = instance.getClientRegistry().getClients().size();
        } else {
            this.totalClients = -1;
        }
        this.maxMemoryMB = osMXBean.getTotalPhysicalMemorySize() / (1024 * 1024);
        this.freeMemoryMB = osMXBean.getFreePhysicalMemorySize() / (1024 * 1024);
        this.usedMemoryMB = maxMemoryMB - freeMemoryMB;
        this.processCpuLoadPercentage = osMXBean.getSystemCpuLoad() * 100;
        this.availableProcessors = osMXBean.getAvailableProcessors();
        this.powerUsageWatts = 0;
        this.totalThreads = threadMXBean.getThreadCount();
        this.processId = runtimeMXBean.getName().split("@")[0];
        this.langVersion = System.getProperty("java.version");
        this.langVendor = System.getProperty("java.vendor");
        this.jvmName = System.getProperty("java.vm.name");
        this.ip = Utilities.getLocalIPAddress();
        if (usedMemoryMB <= maxMemoryMB * 0.5 && processCpuLoadPercentage < 50) {
            this.health = HealthStatus.GOOD.name();
        } else if (usedMemoryMB <= maxMemoryMB * 0.6 && processCpuLoadPercentage < 70) {
            this.health = HealthStatus.OKAY.name();
        } else if (usedMemoryMB <= maxMemoryMB * 0.7 && processCpuLoadPercentage < 85) {
            this.health = HealthStatus.STRESSED.name();
        } else if (usedMemoryMB <= maxMemoryMB * 0.85 && processCpuLoadPercentage < 95) {
            this.health = HealthStatus.OVERLOAD.name();
        } else {
            this.health = HealthStatus.CRITICAL.name();
        }
        this.totalPullMessages = instance.pullMessages.get();
        this.totalReplyMessages = instance.replyMessages.get();
        this.totalPublishMessages = instance.publishMessages.get();
        this.version = instance.getVersion();
        this.status = instance.getStatus();
        if (instance.getClientRegistry() != null) {
            this.nextClientRegistryUpdate = instance.getClientRegistry().getMillisBeforeNextLoop();
        } else {
            this.nextClientRegistryUpdate = -1;
        }
        this.maxIterationsPerSecond = instance.getIterationSpeed();
        if(instance.getRate() != null) {
            this.publishPs = instance.getRate().getPublishMessagesPerSecond();
            this.pullPs = instance.getRate().getPullMessagesPerSecond();
            this.replyPs = instance.getRate().getReplyMessagesPerSecond();
        }
    }

    public int getMaxIterationsPerSecond() {
        return maxIterationsPerSecond;
    }

    public SystemStatistics setMaxIterationsPerSecond(int maxIterationsPerSecond) {
        this.maxIterationsPerSecond = maxIterationsPerSecond;
        return this;
    }

    public enum HealthStatus {
        GOOD, OKAY, STRESSED, OVERLOAD, CRITICAL, UNKNOWN
    }


    public SystemStatistics setHostname(String hostname) {
        this.hostname = hostname;
        return this;
    }

    public String getIp() {
        return ip;
    }

    public SystemStatistics setVersion(String version) {
        this.version = version;
        return this;
    }

    public int getTotalPublishMessages() {
        return totalPublishMessages;
    }

    public SystemStatistics setTotalPublishMessages(int totalPublishMessages) {
        this.totalPublishMessages = totalPublishMessages;
        return this;
    }

    public int getTotalReplyMessages() {
        return totalReplyMessages;
    }

    public SystemStatistics setTotalReplyMessages(int totalReplyMessages) {
        this.totalReplyMessages = totalReplyMessages;
        return this;
    }

    public int getTotalPullMessages() {
        return totalPullMessages;
    }

    public SystemStatistics setTotalPullMessages(int totalPullMessages) {
        this.totalPullMessages = totalPullMessages;
        return this;
    }

    public XTableStatus getStatus() {
        return status;
    }

    public SystemStatistics setStatus(XTableStatus status) {
        this.status = status;
        return this;
    }

    public List<ClientData> getClientDataList() {
        return clientDataList;
    }

    public SystemStatistics setClientDataList(List<ClientData> clientDataList) {
        this.clientDataList = clientDataList;
        return this;
    }

    public int getTotalClients() {
        return totalClients;
    }

    public long getNanoTime() {
        return nanoTime;
    }

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
