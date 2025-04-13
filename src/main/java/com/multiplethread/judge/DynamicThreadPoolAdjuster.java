package com.multiplethread.judge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 动态调整线程池参数的组件
 */
@Component
@EnableScheduling // 启用 Spring 的计划任务执行
public class DynamicThreadPoolAdjuster {

    private static final Logger log = LoggerFactory.getLogger(DynamicThreadPoolAdjuster.class);

    // 资源竞争系数阈值
    private static final double GAMMA_THRESHOLD = 0.3;
    // 获取 CPU 核心数
    private static final int CPU_CORES = Runtime.getRuntime().availableProcessors();
    // 低并发时的核心线程数比例（例如 CPU 核心的 50%）
    private static final double LOW_CONCURRENCY_CORE_RATIO = 0.5;
    // 线性增加步长（每次增加的核心线程数）
    private static final int CORE_POOL_INCREMENT_STEP = 1;

    @Resource
    private ThreadPoolManager threadPoolManager;

    @Resource
    private ThreadPoolMonitor threadPoolMonitor;

    /**
     * 定时任务，定期检查并调整线程池参数
     * fixedDelayString = "PT10S" 表示每隔 10 秒执行一次
     */
    @Scheduled(fixedDelayString = "PT10S") // 使用 ISO 8601 持续时间格式
    public void adjustThreadPool() {
        ThreadPoolExecutor executor = threadPoolManager.getMainExecutor();
        if (executor == null || executor.isShutdown()) {
            log.warn("主线程池未初始化或已关闭，跳过调整。");
            return;
        }

        long avgWaitTime = threadPoolMonitor.getAverageWaitTime();
        long avgExecTime = threadPoolMonitor.getAverageExecutionTime();
        long taskCount = threadPoolMonitor.getTotalTasks(); // 使用 getter 方法

        // 如果周期内没有任务，则不调整
        if (taskCount == 0) {
            log.info("监控周期内无任务执行，跳过调整。");
            // 重置监控器以便下一个周期准确计算
            threadPoolMonitor.reset();
            return;
        }

        double gamma = (avgExecTime > 0) ? (double) avgWaitTime / avgExecTime : 0.0;

        log.info("线程池调整检查：AvgWaitTime={}ms, AvgExecTime={}ms, Gamma={}, Tasks={}",
                 avgWaitTime, avgExecTime, String.format("%.3f", gamma), taskCount);

        int currentCoreSize = executor.getCorePoolSize();
        int currentMaxSize = executor.getMaximumPoolSize(); // 当前最大线程数
        int targetCoreSize = currentCoreSize; // 默认保持不变
        int targetMaxSize = currentMaxSize;   // 默认保持不变

        if (gamma > GAMMA_THRESHOLD) {
            // 高并发状态：线性增加核心线程数，但不超过 CPU 核心数
            if (currentCoreSize < CPU_CORES) {
                targetCoreSize = Math.min(currentCoreSize + CORE_POOL_INCREMENT_STEP, CPU_CORES);
                log.info("高并发状态 (γ > {})，尝试增加核心线程数至: {}", GAMMA_THRESHOLD, targetCoreSize);
            } else {
                log.info("高并发状态，核心线程数已达 CPU 核心数 ({})，不再增加。", CPU_CORES);
            }
            // 在高并发时，可以考虑将最大线程数也设置为 CPU 核心数，以更快响应峰值
            targetMaxSize = CPU_CORES;

        } else {
            // 低并发状态：设置核心线程数为 CPU 核心数的一定比例（例如 50%），但至少为 1
            int lowConcurrencyTarget = Math.max(1, (int) (CPU_CORES * LOW_CONCURRENCY_CORE_RATIO));
            if (currentCoreSize > lowConcurrencyTarget) { // 如果当前核心线程数大于低并发目标值
                targetCoreSize = lowConcurrencyTarget; // 设置目标核心线程数为低并发目标值
                log.info("低并发状态 (γ <= {})，尝试减少核心线程数至: {}", GAMMA_THRESHOLD, targetCoreSize);
            } else {
                log.info("低并发状态，核心线程数 ({}) 已低于或等于目标值 ({})，不再减少。", currentCoreSize, lowConcurrencyTarget);
            }
            // 低并发时，最大线程数可以保持不变或也适当降低，这里我们保持为 CPU 核心数，允许一定的突发
            targetMaxSize = CPU_CORES;
        }

        // 应用调整（仅当目标值与当前值不同时）
        if (targetCoreSize != currentCoreSize) {
            log.warn("动态调整核心线程数：{} -> {}", currentCoreSize, targetCoreSize);
            executor.setCorePoolSize(targetCoreSize);
        }
        if (targetMaxSize != currentMaxSize) {
            // 确保 maximumPoolSize 不小于 corePoolSize
            if (targetMaxSize < targetCoreSize) { // 如果目标最大线程数小于目标核心线程数
                targetMaxSize = targetCoreSize; // 调整为目标核心线程数
                log.warn("目标最大线程数 ({}) 小于目标核心线程数 ({})，调整为 {}。",
                         currentMaxSize, targetCoreSize, targetMaxSize);
            }
            log.warn("动态调整最大线程数：{} -> {}", currentMaxSize, targetMaxSize);
            executor.setMaximumPoolSize(targetMaxSize);
        }

        // 重置监控器以便下一个周期准确计算
        threadPoolMonitor.reset();
    }
}