package com.multiplethread.judge;

import com.multiplethread.model.ThreadPoolArgs;
// import jakarta.annotation.PreDestroy; // 使用 javax 替代
import javax.annotation.PreDestroy;     // 导入 javax.annotation.PreDestroy
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 线程池管理类
 * 负责创建和管理应用主线程池，并提供任务提交接口
 */
@Component
public class ThreadPoolManager {

    @Autowired
    private ThreadPoolMonitor threadPoolMonitor;

    // 持有主线程池实例
    private ThreadPoolExecutor mainExecutor;
    
    // 线程工厂，用于创建线程并命名
    private static class JudgeThreadFactory implements ThreadFactory {
        private static final AtomicInteger poolNumber = new AtomicInteger(1);
        private final ThreadGroup group;
        private final AtomicInteger threadNumber = new AtomicInteger(1);
        private final String namePrefix;
        
        JudgeThreadFactory(String poolName) { // 允许指定线程池名称
            SecurityManager s = System.getSecurityManager();
            group = (s != null) ? s.getThreadGroup() : Thread.currentThread().getThreadGroup();
            namePrefix = poolName + "-" + poolNumber.getAndIncrement() + "-thread-";
        }
        
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(group, r, namePrefix + threadNumber.getAndIncrement(), 0);
            if (t.isDaemon()) {
                t.setDaemon(false);
            }
            if (t.getPriority() != Thread.NORM_PRIORITY) {
                t.setPriority(Thread.NORM_PRIORITY);
            }
            return t;
        }
    }
    
    /**
     * 创建并初始化应用主线程池
     * @param args 线程池参数
     * @return 初始化的线程池
     */
    public synchronized ThreadPoolExecutor initializeMainExecutor(ThreadPoolArgs args) {
        if (this.mainExecutor == null || this.mainExecutor.isShutdown()) {
            this.mainExecutor = new ThreadPoolExecutor(
                    args.getCorePoolSize(),
                    args.getMaximumPoolSize(),
                    args.getKeepAliveTime(),
                    TimeUnit.SECONDS,
                    new LinkedBlockingQueue<>(),
                    new JudgeThreadFactory("main-judge-pool"), // 指定名称
                    (r, executor) -> {
                        threadPoolMonitor.recordTaskRejection();
                         throw new RejectedExecutionException("Task " + r.toString() +
                                                          " rejected from " +
                                                          executor.toString());
                    }
            );
        } else {
            System.out.println("主线程池已初始化。");
        }
        return this.mainExecutor;
    }

    /**
     * 获取主线程池实例
     * @return ThreadPoolExecutor 实例，如果未初始化则返回 null
     */
    public ThreadPoolExecutor getMainExecutor() {
        return mainExecutor;
    }

    /**
     * 提交任务到主线程池
     * @param task 要执行的任务
     */
    public void submitTask(Runnable task) {
        ThreadPoolExecutor executor = getMainExecutor();
        if (executor == null || executor.isShutdown()) {
            System.err.println("主线程池未初始化或已关闭，无法提交任务");
            // 实际应用中应考虑更健壮的处理，例如初始化或抛出特定异常
            return;
        }
        long submissionTimeNanos = System.nanoTime();
        MonitoredTask monitoredTask = new MonitoredTask(task, submissionTimeNanos, threadPoolMonitor);
        try {
            executor.execute(monitoredTask);
        } catch (RejectedExecutionException e) {
            System.err.println("任务提交被拒绝: " + e.getMessage());
        }
    }
    
    /**
     * 关闭主线程池（在应用关闭时调用）
     */
    @PreDestroy
    public void shutdownMainExecutor() {
        shutdownThreadPool(this.mainExecutor);
    }

    /**
     * 通用关闭线程池方法
     * @param executor 线程池
     */
    private void shutdownThreadPool(ExecutorService executor) { // 改为 private
        if (executor != null && !executor.isShutdown()) {
            System.out.println("开始关闭线程池: " + executor);
            executor.shutdown();
            try {
                if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                    System.err.println("线程池未在60秒内关闭，强制关闭。");
                    executor.shutdownNow();
                } else {
                    System.out.println("线程池已成功关闭。");
                }
            } catch (InterruptedException e) {
                System.err.println("关闭线程池被中断，强制关闭。");
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
    
    /**
     * 获取主线程池状态报告
     * @return 状态报告字符串
     */
    public String getMainThreadPoolStatusReport() {
        ThreadPoolExecutor executor = getMainExecutor();
        if (executor == null) {
            return "主线程池未初始化";
        }
        return threadPoolMonitor.getReport(executor);
    }

    // 移除旧的 getThreadPoolStatus 方法，用 getMainThreadPoolStatusReport 替代
    /*
    public String getThreadPoolStatus(ThreadPoolExecutor executor) {
        // ...
    }
    */
} 