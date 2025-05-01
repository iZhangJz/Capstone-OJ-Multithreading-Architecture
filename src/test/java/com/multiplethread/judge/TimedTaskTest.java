package com.multiplethread.judge;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * TimedTask类的单元测试
 * 验证超时中断功能是否正确实现
 */
class TimedTaskTest {

    private ThreadPoolMonitor monitor;
    private ScheduledExecutorService timeoutExecutor;

    @BeforeEach
    void setUp() {
        monitor = new ThreadPoolMonitor();
        timeoutExecutor = Executors.newScheduledThreadPool(2);
    }

    /**
     * 测试任务在超时前完成
     */
    @Test
    @DisplayName("测试任务在超时前完成")
    void testTaskCompletesBeforeTimeout() throws Exception {
        // 准备一个会在100ms内完成的任务
        final AtomicBoolean taskCompleted = new AtomicBoolean(false);
        Runnable task = () -> {
            try {
                Thread.sleep(50); // 休眠50ms
                taskCompleted.set(true);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };

        // 创建TimedTask实例，设置200ms超时
        TimedTask timedTask = new TimedTask(task, System.nanoTime(),
                monitor, 200, timeoutExecutor);
        
        // 在单独的线程中执行任务
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<?> future = executor.submit(timedTask);
        
        // 等待任务完成
        future.get(300, TimeUnit.MILLISECONDS);
        
        // 验证任务正常完成
        assertTrue(taskCompleted.get(), "任务应该在超时前完成");
        
        executor.shutdown();
    }

    /**
     * 测试任务超时被中断
     */
    @Test
    @DisplayName("测试任务超时被中断")
    void testTaskInterruptedOnTimeout() throws Exception {
        // 准备一个会运行很长时间的任务
        final AtomicBoolean wasInterrupted = new AtomicBoolean(false);
        Runnable task = () -> {
            try {
                Thread.sleep(500); // 休眠500ms
            } catch (InterruptedException e) {
                wasInterrupted.set(true);
                Thread.currentThread().interrupt();
            }
        };

        // 创建TimedTask实例，设置100ms超时
        TimedTask timedTask = new TimedTask(task, System.nanoTime(), monitor, 100, timeoutExecutor);
        
        // 在单独的线程中执行任务
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.submit(timedTask);
        
        // 等待足够的时间让任务超时
        Thread.sleep(200);
        
        // 验证任务是否被中断
        assertTrue(wasInterrupted.get(), "任务应该在超时后被中断");
        
        executor.shutdown();
    }

    /**
     * 测试多个任务的超时处理
     */
    @Test
    @DisplayName("测试多个任务的超时处理")
    void testMultipleTasksWithTimeout() throws Exception {
        final int taskCount = 6;
        final CountDownLatch allTasksStarted = new CountDownLatch(taskCount);
        final CountDownLatch allTasksCompleted = new CountDownLatch(taskCount);
        final AtomicLong interruptedCount = new AtomicLong(0);
        
        // 创建线程池
        ExecutorService executorService = Executors.newFixedThreadPool(taskCount);
        
        // 提交多个任务
        for (int i = 0; i < taskCount; i++) {
            final int taskId = i;
            
            Runnable task = () -> {
                try {
                    allTasksStarted.countDown();
                    // 奇数任务运行时间长，会超时
                    // 偶数任务运行时间短，不会超时
                    Thread.sleep(taskId % 2 == 0 ? 50 : 500);
                } catch (InterruptedException e) {
                    interruptedCount.incrementAndGet();
                    Thread.currentThread().interrupt();
                } finally {
                    allTasksCompleted.countDown();
                }
            };
            
            // 创建TimedTask实例，设置100ms超时
            TimedTask timedTask = new TimedTask(task, System.nanoTime(), monitor, 100, timeoutExecutor);
            executorService.submit(timedTask);
        }
        
        // 等待所有任务开始
        assertTrue(allTasksStarted.await(200, TimeUnit.MILLISECONDS), "所有任务应该都已开始执行");
        
        // 等待所有任务完成或超时
        assertTrue(allTasksCompleted.await(600, TimeUnit.MILLISECONDS), "所有任务应该都已完成或被超时中断");
        
        // 验证被中断的任务数量
        // 有taskCount/2的奇数任务应该被中断
        assertEquals(taskCount / 2, interruptedCount.get(), "应该有一半的任务被超时中断");
        
        executorService.shutdown();
    }
} 