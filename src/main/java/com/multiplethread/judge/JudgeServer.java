package com.multiplethread.judge;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import java.util.stream.IntStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class JudgeServer {
    private static final Logger log = LoggerFactory.getLogger(JudgeServer.class);

    @Resource
    private ThreadPoolManager threadPoolManager;

    @Resource
    private NQueen nQueenSolver;

    private final SystemResourceMonitor systemResourceMonitor;

    public JudgeServer(SystemResourceMonitor systemResourceMonitor) {
        this.systemResourceMonitor = systemResourceMonitor;
    }

    static class NamedThreadFactory implements ThreadFactory {
        private final String namePrefix;
        private final AtomicInteger threadNumber = new AtomicInteger(1);
        private final boolean daemon;

        public NamedThreadFactory(String namePrefix, boolean daemon) {
            this.namePrefix = namePrefix;
            this.daemon = daemon;
        }

        public NamedThreadFactory(String namePrefix) {
            this(namePrefix, false);
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, namePrefix + "-thread-" + threadNumber.getAndIncrement());
            t.setDaemon(daemon);
            return t;
        }
    }

    public static class DynamicExecutionResult {
        public final int[] results;
        public final String monitorReport;

        public DynamicExecutionResult(int[] results, String monitorReport) {
            this.results = results;
            this.monitorReport = monitorReport;
        }
    }

    /**
     * 运行评测。
     * 当 useMultipleThread = true (对应 oj.threadModel=multiple):
     *   为当前请求创建固定数量的原始线程 (非线程池)。
     *   线程数量可通过系统属性 oj.multiple.corePoolSize 配置 (默认CPU核心数/2，最小为1)。
     *   测试用例将平均分配给这些线程执行。
     * 当 useMultipleThread = false (对应 oj.threadModel=single):
     *   单线程串行执行所有测试用例。
     *
     * @param cases 测试用例
     * @param useMultipleThread 是否启用针对当前请求的多线程处理模式
     * @return 评测结果
     */
    public int[] runWithOriginalMultiThread(List<Integer> cases, boolean useMultipleThread) {
        int[] results = new int[cases.size()];

        if (useMultipleThread) {
            // 读取可配置的线程数，默认为 CPU 核心数的一半，至少为1
            int configuredThreadCount = Integer.getInteger("oj.multiple.corePoolSize", Math.max(1, Runtime.getRuntime().availableProcessors() / 2));
            // 确保线程数不超过测试用例数，且至少为1
            int actualThreadCount = Math.max(1, Math.min(configuredThreadCount, cases.size()));

        

            List<Thread> threads = new ArrayList<>(actualThreadCount);
            int numCases = cases.size();

            // 将测试用例平均分配给这些线程
            for (int i = 0; i < actualThreadCount; i++) {
                final int workerIndex = i;
                Runnable workerTask = () -> {
                    // 计算这个worker线程应该处理的case的起始和结束索引
                    int startIndex = workerIndex * numCases / actualThreadCount;
                    int endIndex = (workerIndex + 1) * numCases / actualThreadCount;
                    if (workerIndex == actualThreadCount - 1) { // 最后一个worker处理剩余所有
                        endIndex = numCases;
                    }

                    for (int j = startIndex; j < endIndex; j++) {
                        try {
                            results[j] = nQueenSolver.run(cases.get(j));
                        } catch (Exception e) {
                             // 考虑记录日志或将错误信息存入results特定标记
                             System.err.println("原始线程执行 nQueenSolver.run 出错 (case: " + cases.get(j) + "): " + e.getMessage());
                             // results[j] = -1; // 例如标记为错误
                        }
                    }
                };
                threads.add(new Thread(workerTask));
            }

            // 启动所有线程
            for (Thread t : threads) {
                t.start();
            }

            // 等待所有线程完成
            for (Thread t : threads) {
                try {
                    t.join();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    System.err.println("等待评测线程完成时被中断 (multiple mode - 原始线程)");
                    // 可以选择重新尝试join或直接退出，取决于错误处理策略
                }
            }
        } else {
            // 单线程执行
            for (int i = 0; i < cases.size(); i++) {
                results[i] = nQueenSolver.run(cases.get(i));
            }
        }
        return results;
    }

    /**
     * 为每个请求创建一个专用的动态线程池运行评测。
     * @param cases 测试用例列表
     * @return DynamicExecutionResult 包含评测结果数组和监控报告
     */
    public DynamicExecutionResult runWithDynamicThreadPool(List<Integer> cases) {
        int n = cases.size();
        int[] results = new int[n];
        CountDownLatch latch = new CountDownLatch(n);
        String requestPoolName = "RequestDynamicPool-" + UUID.randomUUID().toString().substring(0, 8);

        log.info("[{}] 为{}个测试用例创建动态线程池。最小核心数={}, 最大核心数={}.", 
                 requestPoolName, n, DynamicThreadPoolAdjuster.MIN_CORE_POOL_SIZE, DynamicThreadPoolAdjuster.MAX_CORE_POOL_SIZE);

        // 使用数组持有线程池引用，使其可以在lambda表达式中被安全访问
        final ThreadPoolExecutor[] executorHolder = new ThreadPoolExecutor[1];
        DynamicThreadPoolAdjuster adjuster = null;
        ThreadPoolMonitor perRequestMonitor = null;
        String report = "No report generated.";

        try {
            // 1. Create ThreadPoolMonitor for this request
            if (this.systemResourceMonitor == null) {
                log.error("[{}] 系统资源监控器为空。无法继续创建动态线程池。", requestPoolName);
                throw new IllegalStateException("SystemResourceMonitor not available in JudgeServer for pool " + requestPoolName);
            }
            perRequestMonitor = new ThreadPoolMonitor(this.systemResourceMonitor);

            // 2. Create ThreadPoolExecutor for this request
            executorHolder[0] = new ThreadPoolExecutor(
                    DynamicThreadPoolAdjuster.MIN_CORE_POOL_SIZE,      // initial core pool size
                    DynamicThreadPoolAdjuster.MAX_CORE_POOL_SIZE,     // maximum core pool size (8)
                    60L, TimeUnit.SECONDS,                           // keepAliveTime
                    new LinkedBlockingQueue<>(),                      // work queue
                    new NamedThreadFactory(requestPoolName, true) // daemon threads for request-specific pool
            );

            // 3. Create and start DynamicThreadPoolAdjuster for this executor
            adjuster = new DynamicThreadPoolAdjuster(
                    requestPoolName,
                    executorHolder[0],
                    perRequestMonitor,
                    this.systemResourceMonitor
            );
            adjuster.start();

            log.info("[{}] 向专用动态线程池提交{}个任务。", requestPoolName, n);
            final ThreadPoolMonitor monitorForTasks = perRequestMonitor;

            // 4. Submit tasks
            IntStream.range(0, n).forEach(i -> {
                final int caseValue = cases.get(i);
                final long submitTimeNanos = System.nanoTime(); 

                Runnable actualTask = () -> {
                    long startTimeNanos = System.nanoTime();
                    long waitTimeNanos = startTimeNanos - submitTimeNanos;
                    try {
                        results[i] = nQueenSolver.run(caseValue);
                    } catch (Exception e) {
                        log.error("[{}] 任务执行错误 (测试用例 {}): {}", requestPoolName, caseValue, e.getMessage(), e);
                        if (monitorForTasks != null) monitorForTasks.recordTaskFailure();
                    } finally {
                        long endTimeNanos = System.nanoTime();
                        long executionTimeNanos = endTimeNanos - startTimeNanos;
                        if (monitorForTasks != null) monitorForTasks.recordTaskTimings(executionTimeNanos, waitTimeNanos);
                        latch.countDown();
                        log.trace("[{}] 测试用例 {} 的任务已完成，锁存器计数: {}。", requestPoolName, caseValue, latch.getCount());
                    }
                };
                
                // 通过数组安全访问线程池
                if(executorHolder[0] != null && !executorHolder[0].isShutdown()){
                    executorHolder[0].submit(actualTask);
                } else {
                    log.warn("[{}] 在提交测试用例 {} 的任务前，执行器已关闭或为空。任务将不会运行。", requestPoolName, caseValue);
                    latch.countDown(); 
                    results[i] = -1;
                }
            });

            log.info("[{}] 所有 {} 个任务已提交。等待完成...", requestPoolName, n);
            latch.await(); 
            log.info("[{}] 所有 {} 个任务已完成。", requestPoolName, n);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("[{}] 动态线程池执行被中断。", requestPoolName, e);
        } catch (Exception e) {
            log.error("[{}] 动态线程池执行期间发生意外错误: {}", requestPoolName, e.getMessage(), e);
        } finally {
            log.info("[{}] 开始清理动态线程池。", requestPoolName);
            if (adjuster != null) {
                log.info("[{}] 关闭调整器。", requestPoolName);
                adjuster.shutdown();
            }
            
            // 在finally块中通过数组安全访问线程池
            if (executorHolder[0] != null) {
                log.info("[{}] 关闭执行器。", requestPoolName);
                executorHolder[0].shutdown();
                try {
                    if (!executorHolder[0].awaitTermination(10, TimeUnit.SECONDS)) {
                        log.warn("[{}] 执行器在10秒内未终止。强制调用shutdownNow()。", requestPoolName);
                        executorHolder[0].shutdownNow();
                    } else {
                        log.info("[{}] 执行器已优雅关闭。", requestPoolName);
                    }
                } catch (InterruptedException ex) {
                    log.warn("[{}] 等待执行器关闭时被中断。强制调用shutdownNow()。", requestPoolName);
                    executorHolder[0].shutdownNow();
                    Thread.currentThread().interrupt();
                }
            }
            
            if (perRequestMonitor != null && executorHolder[0] != null) {
                report = perRequestMonitor.getReport(executorHolder[0]); 
                log.info("[{}] 监控报告已生成。", requestPoolName);
            } else if (perRequestMonitor != null) {
                report = "Executor was null, partial monitor report: \n" + perRequestMonitor.getReport(null);
            }
            log.info("[{}] 清理完成。", requestPoolName);
        }
        
        return new DynamicExecutionResult(results, report);
    }
    
    /**
     * 使用带有超时功能的线程池运行评测
     * @param cases 测试用例列表
     * @param timeoutMillis 每个任务的超时时间（毫秒）
     * @return 评测结果数组
     */
//    public int[] runWithTimeoutThreadPool(List<Integer> cases, long timeoutMillis) {
//        int n = cases.size();
//        int[] results = new int[n];
//
//        // 用-1初始化结果数组，表示任务未完成或超时
//        Arrays.fill(results, -1);
//
//        CountDownLatch latch = new CountDownLatch(n);
//
//        // 确保线程池已初始化
//        ThreadPoolExecutor executor = threadPoolManager.getMainExecutor();
//        if (executor == null) {
//             System.err.println("警告：主线程池在 JudgeServer 中发现未初始化。");
//             threadPoolManager.initializeMainExecutor(ThreadPoolArgs.DYNAMIC_INITIAL);
//             executor = threadPoolManager.getMainExecutor();
//             if(executor == null) {
//                throw new IllegalStateException("无法初始化主线程池！");
//             }
//        }
//
//        IntStream.range(0, n).forEach(i -> {
//            final int caseValue = cases.get(i);
//            Runnable task = () -> {
//                try {
//                    Thread.sleep(10); // 模拟编译时间耗时
//                    results[i] = nQueenSolver.run(caseValue);
//                    Thread.sleep(10); // 模拟持久化时间耗时
//                } catch (InterruptedException e) {
//                    // 任务被中断，可能是因为超时
//                    System.err.println("任务被中断 (case " + caseValue + "): 可能是因为超时");
//                    Thread.currentThread().interrupt();
//                } catch (Exception e) {
//                    System.err.println("任务执行出错 (case " + caseValue + "): " + e.getMessage());
//                } finally {
//                    latch.countDown();
//                }
//            };
//            // 使用 ThreadPoolManager 提交带超时的任务
//            threadPoolManager.submitTaskWithTimeout(task, timeoutMillis);
//        });
//
//        try {
//            // 等待所有任务完成或超时
//            latch.await();
//        } catch (InterruptedException e) {
//            Thread.currentThread().interrupt();
//            System.err.println("等待超时线程池评测完成时被中断");
//        }
//
//        return results;
//    }

    /**
     * 使用固定大小线程池运行评测 (CPU核心数)
     * @param cases 测试用例
     * @return 评测结果
     */
    public int[] runWithFixedThreadPool(List<Integer> cases) {
        int numCores = Runtime.getRuntime().availableProcessors();
        // 创建一个固定大小的线程池，核心线程数和最大线程数都等于CPU核心数
        // 使用 LinkedBlockingQueue 作为无界队列，但通常任务数是已知的 (cases.size())
        // KeepAliveTime 对于核心线程数等于最大线程数的固定线程池通常不重要，但可以设置一个值
        ThreadPoolExecutor fixedExecutor = new ThreadPoolExecutor(
                numCores, 
                numCores, 
                60L, TimeUnit.SECONDS, 
                new LinkedBlockingQueue<Runnable>()
        );
        
        //System.out.println("使用固定大小线程池运行，大小: " + numCores);

        int[] results = new int[cases.size()];
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < cases.size(); i++) {
            final int index = i;
            final int caseValue = cases.get(i);
            futures.add(fixedExecutor.submit(() -> results[index] = nQueenSolver.run(caseValue)));
        }

        waitForFutures(futures);

        // 关闭线程池
        fixedExecutor.shutdown();
        try {
            if (!fixedExecutor.awaitTermination(60, TimeUnit.SECONDS)) {
                fixedExecutor.shutdownNow();
            }
        } catch (InterruptedException ie) {
            fixedExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        return results;
    }

    // 辅助方法，用于等待所有Future完成
    private void waitForFutures(List<Future<?>> futures) {
        // ... existing code ...
    }
}

