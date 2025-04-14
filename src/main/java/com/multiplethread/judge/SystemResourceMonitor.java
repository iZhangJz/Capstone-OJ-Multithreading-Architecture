package com.multiplethread.judge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import com.sun.management.OperatingSystemMXBean;

/**
 * 系统资源监控类
 * 用于监控CPU和内存利用率
 */
@Component
public class SystemResourceMonitor {
    
    private static final Logger log = LoggerFactory.getLogger(SystemResourceMonitor.class);
    
    // 获取操作系统MXBean
    private final OperatingSystemMXBean osMXBean;

    // 上次系统CPU利用率
    private double lastSystemCpuUsage = 0.0;
    // 上次进程CPU利用率
    private double lastProcessCpuUsage = 0.0;
    
    // 构造函数
    public SystemResourceMonitor() {
        this.osMXBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        log.info("SystemResourceMonitor initialized");
    }
    
    /**
     * 获取系统CPU利用率
     * @return CPU利用率，范围[0.0, 1.0]，如果无法获取则返回负值
     */
    public double getSystemCpuUsage() {
        double cpuUsage = osMXBean.getSystemCpuLoad();
        
        // 如果返回负值或0，可能是首次调用或无法获取
        if (cpuUsage < 0.0) {
            log.debug("Unable to get system CPU usage, returning last known value: {}", lastSystemCpuUsage);
            return lastSystemCpuUsage;
        }
        
        // 更新最后一次有效的系统CPU利用率
        lastSystemCpuUsage = cpuUsage;
        
        log.debug("Current system CPU usage: {}", cpuUsage);
        return cpuUsage;
    }
    
    /**
     * 获取JVM进程CPU利用率
     * @return JVM进程CPU利用率，范围[0.0, 1.0]，如果无法获取则返回上一次的值
     */
    public double getProcessCpuUsage() {
        double processCpu = osMXBean.getProcessCpuLoad();
        
        // 如果返回负值，表示无法获取，返回上一次的值
        if (processCpu < 0.0) {
            log.warn("Unable to get process CPU usage, returning last known value: {}", lastProcessCpuUsage);
            return lastProcessCpuUsage;
        }
        
        // 更新最后一次有效的进程CPU利用率
        lastProcessCpuUsage = processCpu;
        
        log.debug("Current process CPU usage: {}", processCpu);
        return processCpu;
    }
    
    /**
     * 获取系统内存利用率
     * @return 内存利用率，范围[0.0, 1.0]
     */
    public double getSystemMemoryUsage() {
        long totalMemory = osMXBean.getTotalPhysicalMemorySize();
        long freeMemory = osMXBean.getFreePhysicalMemorySize();
        
        if (totalMemory <= 0) {
            log.warn("Invalid total memory size: {}", totalMemory);
            return 0.0;
        }
        
        double memoryUsage = (double)(totalMemory - freeMemory) / totalMemory;
        log.debug("Current system memory usage: {} (Total: {}, Free: {})", 
                 String.format("%.3f", memoryUsage), totalMemory, freeMemory);
        
        return memoryUsage;
    }
    
    /**
     * 获取JVM内存利用率
     * @return JVM内存利用率，范围[0.0, 1.0]
     */
    public double getJvmMemoryUsage() {
        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long maxMemory = runtime.maxMemory();
        
        // 当前已使用的内存
        long usedMemory = totalMemory - freeMemory;
        
        // 相对于最大可用内存的使用率
        double memoryUsage = (double) usedMemory / maxMemory;
        
        log.debug("Current JVM memory usage: {} (Used: {}, Max: {})", 
                 String.format("%.3f", memoryUsage), usedMemory, maxMemory);
        
        return memoryUsage;
    }
    
    /**
     * 获取系统资源报告
     * @return 系统资源报告字符串
     */
    public String getSystemResourceReport() {
        double systemCpuUsage = getSystemCpuUsage();
        double processCpuUsage = getProcessCpuUsage();
        double systemMemoryUsage = getSystemMemoryUsage();
        double jvmMemoryUsage = getJvmMemoryUsage();
        
        StringBuilder report = new StringBuilder();
        report.append("系统资源监控报告:\n");
        report.append("====================\n");
        report.append(String.format("系统CPU利用率: %.2f%%\n", systemCpuUsage * 100));
        report.append(String.format("JVM进程CPU利用率: %.2f%%\n", processCpuUsage * 100));
        report.append(String.format("系统内存利用率: %.2f%%\n", systemMemoryUsage * 100));
        report.append(String.format("JVM内存利用率: %.2f%%\n", jvmMemoryUsage * 100));
        report.append(String.format("可用处理器数量: %d\n", osMXBean.getAvailableProcessors()));
        report.append(String.format("总物理内存: %d MB\n", osMXBean.getTotalPhysicalMemorySize() / (1024 * 1024)));
        report.append(String.format("可用物理内存: %d MB\n", osMXBean.getFreePhysicalMemorySize() / (1024 * 1024)));
        
        return report.toString();
    }
}
