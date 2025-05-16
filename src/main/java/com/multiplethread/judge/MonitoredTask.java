package com.multiplethread.judge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * 一个 Runnable 包装器，用于监控任务的等待时间和执行时间。
 */
public class MonitoredTask implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(MonitoredTask.class);

    private final Runnable actualTask;
    private final long submissionTimeNanos;
    private final ThreadPoolMonitor monitor;

    /**
     * 构造函数
     * @param actualTask 实际要执行的任务
     * @param submissionTimeNanos 任务提交时间 (System.nanoTime())
     * @param monitor 线程池监控器实例
     */
    public MonitoredTask(Runnable actualTask, long submissionTimeNanos, ThreadPoolMonitor monitor) {
        this.actualTask = actualTask;
        this.submissionTimeNanos = submissionTimeNanos;
        this.monitor = monitor;
        // Log creation
        // log.trace("MonitoredTask created for: {} with submission time: {}", actualTask, submissionTimeNanos);
    }

    /**
     * 获取实际任务
     * @return 实际要执行的任务
     */
    protected Runnable getActualTask() {
        return actualTask;
    }

    /**
     * 获取线程池监控器
     * @return 线程池监控器实例
     */
    protected ThreadPoolMonitor getMonitor() {
        return monitor;
    }

    @Override
    public void run() {
        // Log entry
        log.trace("监控任务开始运行: {}", actualTask);
        long startTimeNanos = System.nanoTime();
        long waitTimeNanos = startTimeNanos - submissionTimeNanos;
        try {
            actualTask.run();
        } catch (Throwable t) {
            // Log failure before recording
            log.error("任务执行失败: {}", actualTask, t);
            // 添加空指针检查
            if (monitor != null) {
                monitor.recordTaskFailure();
            } else {
                log.error("监控器为null，无法记录任务失败");
            }
            throw t;
        } finally {
            long endTimeNanos = System.nanoTime();
            long executionTimeNanos = endTimeNanos - startTimeNanos;
            // long execMillis = TimeUnit.NANOSECONDS.toMillis(executionTimeNanos); // No longer needed here
            // long waitMillis = TimeUnit.NANOSECONDS.toMillis(waitTimeNanos); // No longer needed here

            // Log calculated times before recording
            log.debug("监控任务完成。等待纳秒: {}, 执行纳秒: {}。调用记录任务时间 (使用纳秒)。",                     waitTimeNanos, executionTimeNanos);

            // Pass nanosecond values directly to the monitor (添加空指针检查)
            if (monitor != null) {
                monitor.recordTaskTimings(executionTimeNanos, waitTimeNanos);
                log.trace("已调用monitor.recordTaskTimings，用于: {}", actualTask);
            } else {
                log.error("监控器为null，无法记录任务时间");
            }
        }
    }
}