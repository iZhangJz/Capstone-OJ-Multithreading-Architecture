package com.multiplethread.judge;
import java.util.*;
import java.util.concurrent.*;
import com.multiplethread.model.ThreadPoolArgs;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
public class JudgeServer {

    @Resource
    private ThreadPoolManager threadPoolManager;

    @Resource
    private NQueen nQueenSolver;

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
     * 使用由 ThreadPoolManager 管理的 *动态* 线程池运行评测 (新方法)
     * @param cases 测试用例列表
     * @return 评测结果数组
     */
    public int[] runWithDynamicThreadPool(List<Integer> cases) {
        int n = cases.size();
        int[] results = new int[n];
        CountDownLatch latch = new CountDownLatch(n);

        // 确保线程池已初始化
        ThreadPoolExecutor executor = threadPoolManager.getMainExecutor();
        if (executor == null) {
             // 在实际应用中，初始化应由 Controller 或启动配置保证
             System.err.println("警告：主线程池在 JudgeServer 中发现未初始化。Controller 应负责初始化。");
             // 尝试恢复性初始化，但不推荐
             threadPoolManager.initializeMainExecutor(ThreadPoolArgs.DYNAMIC_INITIAL);
             executor = threadPoolManager.getMainExecutor();
             if(executor == null) {
                throw new IllegalStateException("无法初始化主线程池！");
             } 
        }

        IntStream.range(0, n).forEach(i -> {
            final int caseValue = cases.get(i);
            Runnable task = () -> {
                try {
                    results[i] = nQueenSolver.run(caseValue);
                } catch (Exception e) {
                    System.err.println("任务执行出错 (case " + caseValue + "): " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            };
            // 使用 ThreadPoolManager 提交任务，它会自动包装成 MonitoredTask
            threadPoolManager.submitTask(task);
        });

        try {
            // 等待所有任务完成
            latch.await(); 
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("等待动态线程池评测完成时被中断");
        }
        
        // 不需要在这里关闭线程池，由 ThreadPoolManager 的 @PreDestroy 管理
        return results;
    }
    
    /**
     * 使用带有超时功能的线程池运行评测
     * @param cases 测试用例列表
     * @param timeoutMillis 每个任务的超时时间（毫秒）
     * @return 评测结果数组
     */
    public int[] runWithTimeoutThreadPool(List<Integer> cases, long timeoutMillis) {
        int n = cases.size();
        int[] results = new int[n];
        
        // 用-1初始化结果数组，表示任务未完成或超时
        Arrays.fill(results, -1);
        
        CountDownLatch latch = new CountDownLatch(n);

        // 确保线程池已初始化
        ThreadPoolExecutor executor = threadPoolManager.getMainExecutor();
        if (executor == null) {
             System.err.println("警告：主线程池在 JudgeServer 中发现未初始化。");
             threadPoolManager.initializeMainExecutor(ThreadPoolArgs.DYNAMIC_INITIAL);
             executor = threadPoolManager.getMainExecutor();
             if(executor == null) {
                throw new IllegalStateException("无法初始化主线程池！");
             } 
        }

        IntStream.range(0, n).forEach(i -> {
            final int caseValue = cases.get(i);
            Runnable task = () -> {
                try {
                    Thread.sleep(10); // 模拟编译时间耗时
                    results[i] = nQueenSolver.run(caseValue);
                    Thread.sleep(10); // 模拟持久化时间耗时
                } catch (InterruptedException e) {
                    // 任务被中断，可能是因为超时
                    System.err.println("任务被中断 (case " + caseValue + "): 可能是因为超时");
                    Thread.currentThread().interrupt();
                } catch (Exception e) {
                    System.err.println("任务执行出错 (case " + caseValue + "): " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            };
            // 使用 ThreadPoolManager 提交带超时的任务
            threadPoolManager.submitTaskWithTimeout(task, timeoutMillis);
        });

        try {
            // 等待所有任务完成或超时
            latch.await(); 
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("等待超时线程池评测完成时被中断");
        }
        
        return results;
    }

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

