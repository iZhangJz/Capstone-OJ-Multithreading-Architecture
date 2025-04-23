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

    @Override
    public void run() {
        // Log entry
        log.trace("MonitoredTask run() started for: {}", actualTask);
        long startTimeNanos = System.nanoTime();
        long waitTimeNanos = startTimeNanos - submissionTimeNanos;
        try {
            actualTask.run();
        } catch (Throwable t) {
            // Log failure before recording
            log.error("Task execution failed for: {}", actualTask, t);
            monitor.recordTaskFailure();
            throw t; 
        } finally {
            long endTimeNanos = System.nanoTime();
            long executionTimeNanos = endTimeNanos - startTimeNanos;
            // long execMillis = TimeUnit.NANOSECONDS.toMillis(executionTimeNanos); // No longer needed here
            // long waitMillis = TimeUnit.NANOSECONDS.toMillis(waitTimeNanos); // No longer needed here
            
            // Log calculated times before recording
            log.debug("MonitoredTask finished. WaitNanos: {}, ExecNanos: {}. Calling recordTaskTimings (with nanos).",
                     waitTimeNanos, executionTimeNanos);
                     
            // Pass nanosecond values directly to the monitor
            monitor.recordTaskTimings(executionTimeNanos, waitTimeNanos);
            log.trace("Called monitor.recordTaskTimings for: {}", actualTask);
        }
    }
} 