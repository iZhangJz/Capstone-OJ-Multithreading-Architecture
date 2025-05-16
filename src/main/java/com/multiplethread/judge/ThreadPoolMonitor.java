package com.multiplethread.judge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 线程池监控类
 * 用于收集线程池的性能指标 (使用纳秒精度)
 */
public class ThreadPoolMonitor {

    private static final Logger log = LoggerFactory.getLogger(ThreadPoolMonitor.class);

    private final SystemResourceMonitor systemResourceMonitor;

    // 任务执行计数器
    final AtomicLong totalTasks = new AtomicLong(0);
    // 任务执行总时间 (纳秒)
    private final AtomicLong totalExecutionTimeNanos = new AtomicLong(0);
    // 所有任务等待总时间 (纳秒)
    private final AtomicLong totalWaitTimeNanos = new AtomicLong(0);
    // 任务执行最大时间 (毫秒，保持不变，通常最大值不需要纳秒精度)
    private final AtomicLong maxExecutionTime = new AtomicLong(0);
    // 任务执行最小时间 (毫秒，保持不变)
    private final AtomicLong minExecutionTime = new AtomicLong(Long.MAX_VALUE);
    // 任务拒绝计数器
    private final AtomicLong rejectedTasks = new AtomicLong(0);
    // 任务异常计数器
    private final AtomicLong failedTasks = new AtomicLong(0);

    public ThreadPoolMonitor(SystemResourceMonitor systemResourceMonitor) {
        this.systemResourceMonitor = systemResourceMonitor;
    }

    /**
     * 记录任务执行时间和等待时间 (纳秒)
     * @param executionTimeNanos 执行时间（纳秒）
     * @param waitTimeNanos 等待时间（纳秒）
     */
    public void recordTaskTimings(long executionTimeNanos, long waitTimeNanos) {
        log.debug("记录任务时间: 执行时间纳秒 = {}纳秒, 等待时间纳秒 = {}纳秒", executionTimeNanos, waitTimeNanos);

        if (executionTimeNanos < 0 || waitTimeNanos < 0) {
             log.warn("收到负时间值: 执行时间纳秒={}, 等待时间纳秒={}. 忽略此记录。", executionTimeNanos, waitTimeNanos);
             return;
        }

        long currentTaskCount = totalTasks.incrementAndGet(); //
        long currentTotalExecTime = totalExecutionTimeNanos.addAndGet(executionTimeNanos);
        long currentTotalWaitTime = totalWaitTimeNanos.addAndGet(waitTimeNanos);

        log.trace("更新总计: 任务数={}, 总执行时间纳秒={}, 总等待时间纳秒={}",
                 currentTaskCount, currentTotalExecTime, currentTotalWaitTime);

        // --- 更新最大/最小执行时间 (以毫秒为单位) ---
        long executionTimeMillis = TimeUnit.NANOSECONDS.toMillis(executionTimeNanos);

        // 更新最大执行时间
        long currentMax;
        do {
            currentMax = maxExecutionTime.get();
            if (executionTimeMillis <= currentMax) {
                break;
            }
        } while (!maxExecutionTime.compareAndSet(currentMax, executionTimeMillis));

        // 更新最小执行时间
        long currentMin;
        do {
            currentMin = minExecutionTime.get();
            // Handle the initial Long.MAX_VALUE case and ensure positive millis time
            if (executionTimeMillis >= currentMin && currentMin != Long.MAX_VALUE) {
                break;
            }
            // Only set if the new time is smaller (and potentially the first valid one)
        } while (executionTimeMillis < currentMin && !minExecutionTime.compareAndSet(currentMin, executionTimeMillis));
    }

    /**
     * 记录任务拒绝
     */
    public void recordTaskRejection() {
        long count = rejectedTasks.incrementAndGet();
        log.warn("任务被拒绝。总拒绝数: {}", count);
    }

    /**
     * 记录任务异常
     */
    public void recordTaskFailure() {
        long count = failedTasks.incrementAndGet();
        log.error("任务失败。总失败数: {}", count);
    }

    /**
     * 记录任务超时
     */
    public void recordTaskTimeout() {
        log.warn("任务超时并被中断");
        // 任务超时也算作失败
        recordTaskFailure();
    }

    /**
     * 重置监控数据
     */
    public void reset() {
        log.info("重置线程池监控统计数据。");
        totalTasks.set(0);
        totalExecutionTimeNanos.set(0); // Reset nanos
        totalWaitTimeNanos.set(0);    // Reset nanos
        maxExecutionTime.set(0);
        minExecutionTime.set(Long.MAX_VALUE);
        rejectedTasks.set(0);
        failedTasks.set(0);
    }

    /**
     * 获取总任务数
     */
    public long getTotalTasks() {
        return totalTasks.get();
    }

    /**
     * 获取平均执行时间（毫秒）- 使用浮点数计算避免精度丢失
     */
    public long getAverageExecutionTime() {
        long count = totalTasks.get();
        if (count == 0) {
            return 0;
        }
        // 计算平均执行时间（纳秒）使用浮点数计算避免精度丢失
        double avgNanos = (double) totalExecutionTimeNanos.get() / count;
        // 将平均纳秒转换为毫秒（四舍五入或截断）
        return (long) (avgNanos / 1_000_000.0);
    }

    /**
     * 获取平均等待时间（毫秒）- 使用浮点数计算避免精度丢失
     */
    public long getAverageWaitTime() {
        long count = totalTasks.get();
         if (count == 0) {
            return 0;
        }
        // 计算平均等待时间（纳秒）使用浮点数计算避免精度丢失
        double avgNanos = (double) totalWaitTimeNanos.get() / count;
        // 将平均纳秒转换为毫秒（四舍五入或截断）
        return (long) (avgNanos / 1_000_000.0);
    }

    /**
     * 获取监控报告
     * @param executor 线程池
     * @return 监控报告
     */
    public String getReport(ThreadPoolExecutor executor) {
        long taskCount = totalTasks.get();
        long avgExecTimeMillis = getAverageExecutionTime();
        long avgWaitTimeMillis = getAverageWaitTime();

        long minTime = minExecutionTime.get();
        if (minTime == Long.MAX_VALUE) {
            minTime = 0;
        }
        long maxTime = maxExecutionTime.get();

        double gamma = (avgExecTimeMillis > 0) ? (double) avgWaitTimeMillis / avgExecTimeMillis : 0.0;

        // 获取系统资源利用率（添加空指针检查）
        double systemCpuUsage = 0.0;
        double processCpuUsage = 0.0;
        double systemMemoryUsage = 0.0;
        double jvmMemoryUsage = 0.0;

        if (systemResourceMonitor != null) {
            systemCpuUsage = systemResourceMonitor.getSystemCpuUsage();
            processCpuUsage = systemResourceMonitor.getProcessCpuUsage();
            systemMemoryUsage = systemResourceMonitor.getSystemMemoryUsage();
            jvmMemoryUsage = systemResourceMonitor.getJvmMemoryUsage();
        } else {
            log.warn("系统资源监控器为空（应该已被注入），无法获取系统资源使用情况报告。");
        }

        log.debug("生成报告: 平均执行时间毫秒={}, 平均等待时间毫秒={}, Gamma={}, 任务数={}, CPU={}, 内存={}",
                  avgExecTimeMillis, avgWaitTimeMillis, String.format("%.3f", gamma), taskCount,
                  String.format("%.2f%%", systemCpuUsage * 100), String.format("%.2f%%", systemMemoryUsage * 100));

        StringBuilder report = new StringBuilder();
        report.append("线程池监控报告:\n");
        report.append("====================\n");
        report.append(String.format("统计周期内任务数: %d\n", taskCount));
        report.append(String.format("平均执行时间: %d ms\n", avgExecTimeMillis));
        report.append(String.format("平均等待时间: %d ms\n", avgWaitTimeMillis));
        report.append(String.format("资源竞争系数 γ: %.3f\n", gamma));
        report.append(String.format("最大执行时间: %d ms\n", maxTime));
        report.append(String.format("最小执行时间: %d ms\n", minTime));
        report.append(String.format("拒绝任务数: %d\n", rejectedTasks.get()));
        report.append(String.format("失败任务数: %d\n", failedTasks.get()));

        // 添加系统资源利用率信息
        report.append("\n系统资源利用率:\n");
        report.append(String.format("系统CPU利用率: %.2f%%\n", systemCpuUsage * 100));
        report.append(String.format("JVM进程CPU利用率: %.2f%%\n", processCpuUsage * 100));
        report.append(String.format("系统内存利用率: %.2f%%\n", systemMemoryUsage * 100));
        report.append(String.format("JVM内存利用率: %.2f%%\n", jvmMemoryUsage * 100));

        if (executor != null) {
            report.append("\n线程池实时状态:\n");
            report.append(String.format("活跃线程数: %d\n", executor.getActiveCount()));
            report.append(String.format("当前线程数: %d\n", executor.getPoolSize()));
            report.append(String.format("核心线程数: %d\n", executor.getCorePoolSize()));
            report.append(String.format("最大线程数: %d\n", executor.getMaximumPoolSize()));
            report.append(String.format("任务队列大小: %d\n", executor.getQueue().size()));
            report.append(String.format("总完成任务数 (Executor): %d\n", executor.getCompletedTaskCount()));
        }

        return report.toString();
    }
}