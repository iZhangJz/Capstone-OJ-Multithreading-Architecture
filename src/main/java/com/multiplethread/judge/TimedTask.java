package com.multiplethread.judge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;

/**
 * 一个带有超时功能的任务包装器，继承自MonitoredTask
 * 相关文档: docs/modules/2025-05-01-TimedTask-新增.md
 */
public class TimedTask extends MonitoredTask {

    private static final Logger log = LoggerFactory.getLogger(TimedTask.class);
    private final long timeoutMillis;
    private final ScheduledExecutorService timeoutExecutor;
    private Future<?> taskFuture;
    private Thread taskThread;

    /**
     * 构造函数
     * @param actualTask 实际要执行的任务
     * @param submissionTimeNanos 任务提交时间 (System.nanoTime())
     * @param monitor 线程池监控器实例
     * @param timeoutMillis 任务超时时间（毫秒）
     * @param timeoutExecutor 用于调度超时检查的线程池
     */
    public TimedTask(Runnable actualTask, long submissionTimeNanos, ThreadPoolMonitor monitor,
                    long timeoutMillis, ScheduledExecutorService timeoutExecutor) {
        super(actualTask, submissionTimeNanos, monitor);
        this.timeoutMillis = timeoutMillis;
        this.timeoutExecutor = timeoutExecutor;
    }

    @Override
    public void run() {
        // 保存当前线程，以便在超时时中断
        taskThread = Thread.currentThread();

        // 创建一个超时检查器，超时后会中断任务线程
        ScheduledFuture<?> timeoutFuture = timeoutExecutor.schedule(() -> {
            if (taskThread != null && taskThread.isAlive()) {
                log.warn("任务执行超时（{}毫秒），正在中断...", timeoutMillis);
                taskThread.interrupt();
                // 确保中断信号被发送
                log.info("已向线程 {} 发送中断信号", taskThread.getName());

                // 如果有Future，也尝试取消它
                if (taskFuture != null && !taskFuture.isDone()) {
                    log.info("取消 Future 任务");
                    taskFuture.cancel(true);
                }
            }
        }, timeoutMillis, TimeUnit.MILLISECONDS);

        try {
            // 使用父类中的 actualTask
            Runnable actualTask = getActualTask();

            // 使用FutureTask包装实际任务
            FutureTask<?> futureTask = new FutureTask<>(() -> {
                try {
                    // 执行前先检查线程中断状态
                    if (Thread.currentThread().isInterrupted()) {
                        throw new InterruptedException("任务开始前已被中断");
                    }

                    actualTask.run();

                    // 执行后再次检查中断状态
                    if (Thread.currentThread().isInterrupted()) {
                        throw new InterruptedException("任务执行过程中被中断");
                    }

                    return null;
                } catch (Throwable t) {
                    // 捕获任何异常
                    if (t instanceof InterruptedException || Thread.currentThread().isInterrupted()) {
                        log.warn("任务执行被中断");
                        // 重置中断状态以确保其他代码能检测到
                        Thread.currentThread().interrupt();
                    }
                    throw t;
                }
            });

            this.taskFuture = futureTask;

            // 执行包装的任务
            futureTask.run();

            // 如果到这里且线程已被中断，需要确保异常被抛出
            if (Thread.currentThread().isInterrupted()) {
                log.warn("任务完成后检测到中断状态");
                // 不需要显式抛出InterruptedException，改为标记中断状态供后续处理
                Thread.currentThread().interrupt();
            }

        } catch (Exception e) {
            log.error("带超时的任务执行失败: {}", e.getMessage(), e);
            // 确保中断状态被传播
            if (e instanceof InterruptedException || Thread.currentThread().isInterrupted()) {
                // 重置中断标志
                Thread.currentThread().interrupt();
                // 记录超时
                getMonitor().recordTaskTimeout();
            }
            throw e;
        } finally {
            // 无论任务是否成功完成，都取消超时检查器
            timeoutFuture.cancel(false);
        }
    }
}