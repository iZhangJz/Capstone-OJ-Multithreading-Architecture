package com.multiplethread.judge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.text.DecimalFormat; // For precise formatting if needed

/**
 * 动态调整线程池参数的组件, 现在为每个线程池实例工作。
 */
public class DynamicThreadPoolAdjuster {

    private static final Logger log = LoggerFactory.getLogger(DynamicThreadPoolAdjuster.class);
    private static final DecimalFormat dfPercent = new DecimalFormat("#.##");
    private static final DecimalFormat dfGamma = new DecimalFormat("#.###");

    // 核心线程数限制 - 改为 public 以便外部访问
    public static final int MIN_CORE_POOL_SIZE = 2;
    public static final int MAX_CORE_POOL_SIZE = 8; // 每个请求的线程池最大核心线程数

    // 资源竞争系数阈值
    private static final double GAMMA_THRESHOLD = 0.3;
    // 获取 CPU 核心数 - 这个可能仍然有用，用于比较，但不是硬性限制池大小
    private static final int SYSTEM_CPU_CORES = Runtime.getRuntime().availableProcessors();
    // 线性增加步长（每次增加的核心线程数）
    private static final int CORE_POOL_INCREMENT_STEP = 1;
    // CPU利用率高阈值（超过此值认为系统CPU负载较高）
    private static final double HIGH_CPU_THRESHOLD = 1; // 80%
    // CPU利用率低阈值（低于此值认为系统CPU负载较低）
    private static final double LOW_CPU_THRESHOLD = 0.2; // 20%
    // 内存利用率高阈值（超过此值认为系统内存压力较大）
    private static final double HIGH_MEMORY_THRESHOLD = 0.8; // 80%

    // New thresholds for queue-based adjustments when no tasks completed in interval
    private static final int QUEUE_SIZE_TO_CORE_RATIO_FOR_INCREASE = 2; // If queue size is 2x core size
    private static final double CPU_THRESHOLD_FOR_QUEUE_INCREASE = 0.75; // 75% CPU, don't increase if higher
    private static final double MEMORY_THRESHOLD_FOR_QUEUE_INCREASE = 0.85; // 85% Memory, don't increase if higher

    private final ThreadPoolExecutor executorService; // 要调整的线程池
    private final ThreadPoolMonitor threadPoolMonitor; // 监控特定线程池的实例
    private final SystemResourceMonitor systemResourceMonitor; // 系统资源监控器 (可以共享)
    private final ScheduledExecutorService scheduler;
    private final String poolName; // 用于日志记录，区分不同请求的线程池

    // 将调整间隔从秒改为毫秒，并设定为500ms
    private static final long ADJUST_INTERVAL_MILLIS = 500; // 调整间隔为500毫秒

    public DynamicThreadPoolAdjuster(String poolName, ThreadPoolExecutor executorService, ThreadPoolMonitor threadPoolMonitor, SystemResourceMonitor systemResourceMonitor) {
        this.poolName = poolName != null ? poolName : "DynamicPool";
        this.executorService = executorService;
        this.threadPoolMonitor = threadPoolMonitor; // 这个 monitor 实例是为 executorService 服务的
        this.systemResourceMonitor = systemResourceMonitor;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, this.poolName + "-AdjusterThread");
            t.setDaemon(true); // 将调度线程设置为守护线程
            return t;
        });
    }

    public void start() {
        if (scheduler.isShutdown()) {
            log.warn("[{}] 调度器已关闭，无法启动。", poolName);
            return;
        }
        log.info("[{}] 启动动态线程池调整器。最小核心线程数={}, 最大核心线程数={}, 调整间隔={}ms",
                poolName, MIN_CORE_POOL_SIZE, MAX_CORE_POOL_SIZE, ADJUST_INTERVAL_MILLIS);
        this.scheduler.scheduleWithFixedDelay(this::adjustThreadPoolInternal, ADJUST_INTERVAL_MILLIS, ADJUST_INTERVAL_MILLIS, TimeUnit.MILLISECONDS);
    }

    public void shutdown() {
        log.info("[{}] 正在关闭动态线程池调整器。", poolName);
        if (!scheduler.isShutdown()) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        log.info("[{}] 调度器已成功关闭。", poolName);
    }

    // 原 @Scheduled 方法，现在是内部调用
    private void adjustThreadPoolInternal() {
        if (scheduler.isShutdown() || executorService == null || executorService.isShutdown() || executorService.isTerminating()) {
            if (!scheduler.isShutdown()) { // if scheduler is still running but executor is not usable
                 log.warn("[{}] 执行器不可用 (null, shutdown or terminating)，正在停止调度器。", poolName);
                 this.shutdown(); // Stop this adjuster's scheduler
            } else {
                 log.warn("[{}] 调度器自身已关闭，跳过调整。", poolName);
            }
            return;
        }

        try {
            performAdjustment();
        } catch (Exception e) {
            log.error("[{}] 线程池调整过程中出错: {}", poolName, e.getMessage(), e);
        }
    }

    private void performAdjustment() {
        // Ensure monitor is valid before using
        if (threadPoolMonitor == null) {
            log.error("[{}] ThreadPoolMonitor 为 null，无法执行调整。", poolName);
            return;
        }
        
        double processCpuUsage = systemResourceMonitor.getProcessCpuUsage();
        double systemMemoryUsage = systemResourceMonitor.getSystemMemoryUsage();
        
        // Statistics from the monitor for the last interval (assuming reset clears them for next interval)
        long avgWaitTime = threadPoolMonitor.getAverageWaitTime(); // Assuming these are from last interval after reset
        long avgExecTime = threadPoolMonitor.getAverageExecutionTime();
        long tasksCompletedInInterval = threadPoolMonitor.getTotalTasks(); // Assuming this is tasks *since last reset*

        int currentCoreSize = executorService.getCorePoolSize();
        int queueSize = executorService.getQueue().size();
        int activeThreads = executorService.getActiveCount();
        int calculatedTargetCoreSize = currentCoreSize;

        log.debug("[{}] 检查调整: 当前核心数={}, 队列任务数={}, 活跃线程数={}, 上周期完成任务数={}, CPU={}%, Mem={}%",
                poolName, currentCoreSize, queueSize, activeThreads, tasksCompletedInInterval,
                dfPercent.format(processCpuUsage * 100), dfPercent.format(systemMemoryUsage * 100));

        if (tasksCompletedInInterval > 0) {
            double gamma = (avgExecTime > 0) ? (double) avgWaitTime / avgExecTime : (avgWaitTime > 0 ? Double.MAX_VALUE : 0.0);

            log.info("[{}] 调整检查 (有任务完成): 平均等待={}ms, 平均执行={}ms, Gamma={}, 完成任务数={}, CPU={}%, Mem={}%",
                    poolName, avgWaitTime, avgExecTime, dfGamma.format(gamma), tasksCompletedInInterval,
                    dfPercent.format(processCpuUsage * 100), dfPercent.format(systemMemoryUsage * 100));

            if (gamma > GAMMA_THRESHOLD) {
                if (processCpuUsage > HIGH_CPU_THRESHOLD || systemMemoryUsage > HIGH_MEMORY_THRESHOLD) {
                    log.info("[{}] 高 Gamma (>{}), 但系统资源紧张 (CPU: {}%, Mem: {}%). 保持核心线程数: {}.",
                            poolName, dfGamma.format(GAMMA_THRESHOLD), dfPercent.format(processCpuUsage * 100),
                            dfPercent.format(systemMemoryUsage * 100), currentCoreSize);
                } else if (currentCoreSize < MAX_CORE_POOL_SIZE) {
                    calculatedTargetCoreSize = Math.min(currentCoreSize + CORE_POOL_INCREMENT_STEP, MAX_CORE_POOL_SIZE);
                    log.info("[{}] 高 Gamma (>{}), 资源充足. 增加核心线程数从 {} 到 {}.",
                            poolName, dfGamma.format(GAMMA_THRESHOLD), currentCoreSize, calculatedTargetCoreSize);
                } else {
                    log.info("[{}] 高 Gamma (>{}), 但核心线程数 ({}) 已达最大值 {}. 不增加.",
                            poolName, dfGamma.format(GAMMA_THRESHOLD), currentCoreSize, MAX_CORE_POOL_SIZE);
                }
            } else { // gamma <= GAMMA_THRESHOLD
                if (processCpuUsage < LOW_CPU_THRESHOLD) {
                    if (currentCoreSize > MIN_CORE_POOL_SIZE) {
                        calculatedTargetCoreSize = Math.max(MIN_CORE_POOL_SIZE, currentCoreSize - CORE_POOL_INCREMENT_STEP);
                        log.info("[{}] 低 Gamma (≤{}), CPU利用率低 ({}%). 减少核心线程数从 {} 到 {}.",
                                poolName, dfGamma.format(GAMMA_THRESHOLD), dfPercent.format(processCpuUsage * 100), currentCoreSize, calculatedTargetCoreSize);
                    } else {
                        log.info("[{}] 低 Gamma (≤{}), CPU利用率低, 但核心线程数 ({}) 已是最小值 {}. 不变更.",
                                poolName, dfGamma.format(GAMMA_THRESHOLD), currentCoreSize, MIN_CORE_POOL_SIZE);
                    }
                } else {
                    int moderateTarget = Math.max(MIN_CORE_POOL_SIZE, MAX_CORE_POOL_SIZE / 2);
                    if (currentCoreSize > moderateTarget) {
                        calculatedTargetCoreSize = Math.max(moderateTarget, currentCoreSize - CORE_POOL_INCREMENT_STEP);
                        log.info("[{}] 低 Gamma (≤{}), CPU利用率中等 ({}%). 当前核心 {} > 适中目标 {}. 减少到 {}.",
                                poolName, dfGamma.format(GAMMA_THRESHOLD), dfPercent.format(processCpuUsage * 100), currentCoreSize, moderateTarget, calculatedTargetCoreSize);
                    } else {
                        log.info("[{}] 低 Gamma (≤{}), CPU利用率中等. 核心线程数 ({}) ≤ 适中目标 {}. 不变更.",
                                poolName, dfGamma.format(GAMMA_THRESHOLD), currentCoreSize, moderateTarget);
                    }
                }
            }
        } else { // tasksCompletedInInterval == 0
            if (queueSize > 0 || activeThreads > 0) {
                log.info("[{}] 上周期无任务完成, 但队列中有 {} 个任务或 {} 个活跃线程. CPU: {}%, Mem: {}%.",
                        poolName, queueSize, activeThreads, dfPercent.format(processCpuUsage * 100), dfPercent.format(systemMemoryUsage * 100));
                // Strategy: If queue is significantly backlogged and resources allow, consider a small increase
                if (queueSize > currentCoreSize * QUEUE_SIZE_TO_CORE_RATIO_FOR_INCREASE &&
                    currentCoreSize < MAX_CORE_POOL_SIZE &&
                    processCpuUsage < CPU_THRESHOLD_FOR_QUEUE_INCREASE &&
                    systemMemoryUsage < MEMORY_THRESHOLD_FOR_QUEUE_INCREASE) {
                    
                    calculatedTargetCoreSize = Math.min(currentCoreSize + CORE_POOL_INCREMENT_STEP, MAX_CORE_POOL_SIZE);
                    if (calculatedTargetCoreSize > currentCoreSize) {
                        log.info("[{}] 队列积压 ({} 任务) 且资源允许 (CPU {}% < {}%, Mem {}% < {}%). 尝试增加核心线程数从 {} 到 {}.",
                                poolName, queueSize,
                                dfPercent.format(processCpuUsage * 100), dfPercent.format(CPU_THRESHOLD_FOR_QUEUE_INCREASE * 100),
                                dfPercent.format(systemMemoryUsage * 100), dfPercent.format(MEMORY_THRESHOLD_FOR_QUEUE_INCREASE * 100),
                                currentCoreSize, calculatedTargetCoreSize);
                    } else {
                        // Reset to current if no increase happens, to avoid unintended shrink by falling through
                        calculatedTargetCoreSize = currentCoreSize; 
                    }
                } else {
                    log.info("[{}] 队列积压或有活跃线程，但未满足主动增加条件 (队列比:{}, CPU:{}, Mem:{}) 或已达最大核心数. 保持核心线程数: {}.",
                            poolName, (currentCoreSize > 0 ? (double)queueSize/currentCoreSize : (queueSize > 0 ? "Inf" : "0")), // Avoid division by zero if currentCoreSize is 0
                            dfPercent.format(processCpuUsage*100), dfPercent.format(systemMemoryUsage*100), currentCoreSize);
                    calculatedTargetCoreSize = currentCoreSize; // Maintain current size
                }
            } else { // Truly idle: tasksCompletedInInterval == 0, queueSize == 0, activeThreads == 0
                log.info("[{}] 线程池真正空闲 (无完成任务, 队列为空, 无活跃线程). CPU: {}%, Mem: {}%.",
                        poolName, dfPercent.format(processCpuUsage * 100), dfPercent.format(systemMemoryUsage * 100));
                if (currentCoreSize > MIN_CORE_POOL_SIZE) {
                     // Only shrink if CPU usage is also low, to avoid shrinking if system is busy due to other factors
                    if (processCpuUsage < LOW_CPU_THRESHOLD) { // Use existing LOW_CPU_THRESHOLD
                        calculatedTargetCoreSize = MIN_CORE_POOL_SIZE; // Shrink more aggressively to min if truly idle and low CPU
                        log.info("[{}] 空闲且CPU利用率低. 缩减核心线程数从 {} 到最小值 {}.", poolName, currentCoreSize, calculatedTargetCoreSize);
                    } else {
                        log.info("[{}] 空闲但CPU利用率 ({}%) 不低. 保持核心线程数 {}.", poolName, dfPercent.format(processCpuUsage*100), currentCoreSize);
                        calculatedTargetCoreSize = currentCoreSize; // Maintain current size
                    }
                } else {
                    log.info("[{}] 空闲: 核心线程数 ({}) 已是最小值 {}. 不变更.", poolName, currentCoreSize, MIN_CORE_POOL_SIZE);
                    calculatedTargetCoreSize = currentCoreSize; // Ensure it stays at min
                }
            }
        }

        int finalTargetPoolSize = Math.max(MIN_CORE_POOL_SIZE, Math.min(calculatedTargetCoreSize, MAX_CORE_POOL_SIZE));

        int currentActualCoreSize = executorService.getCorePoolSize();
        int currentActualMaxSize = executorService.getMaximumPoolSize();

        if (finalTargetPoolSize == currentActualCoreSize && finalTargetPoolSize == currentActualMaxSize) {
            // log.debug("[{}] 目标线程数 {} 与当前核心数 {} 及最大数 {} 相同，无需调整。", poolName, finalTargetPoolSize, currentActualCoreSize, currentActualMaxSize);
        } else {
             log.info("[{}] 计算出的最终目标核心/最大线程数: {}. 当前核心: {}, 当前最大: {}.", poolName, finalTargetPoolSize, currentActualCoreSize, currentActualMaxSize);
            // Apply adjustment logic
            if (currentActualMaxSize < finalTargetPoolSize) {
                log.info("[{}] 调整前: 设置最大线程数从 {} 到 {}.", poolName, currentActualMaxSize, finalTargetPoolSize);
                executorService.setMaximumPoolSize(finalTargetPoolSize);
            }
            
            if (currentActualCoreSize != finalTargetPoolSize) {
                log.info("[{}] 调整中: 设置核心线程数从 {} 到 {}.", poolName, currentActualCoreSize, finalTargetPoolSize);
                executorService.setCorePoolSize(finalTargetPoolSize);
            }

            // Ensure current maximum is at least new core size, and align with finalTargetPoolSize
            if (executorService.getMaximumPoolSize() < executorService.getCorePoolSize() || executorService.getMaximumPoolSize() != finalTargetPoolSize) {
                int newMax = Math.max(executorService.getCorePoolSize(), finalTargetPoolSize);
                 if (executorService.getMaximumPoolSize() != newMax) {
                    log.info("[{}] 调整后: 同步最大线程数从 {} 到 {}.", poolName, executorService.getMaximumPoolSize(), newMax);
                    executorService.setMaximumPoolSize(newMax);
                 }
            }
        }
        
        // Reset monitor statistics for the next interval.
        // This is crucial for `tasksCompletedInInterval` to reflect the count *since last adjustment*.
        threadPoolMonitor.reset();
    }
}