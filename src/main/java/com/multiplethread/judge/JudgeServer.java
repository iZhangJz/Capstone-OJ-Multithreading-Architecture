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
     * 运行评测 (兼容单线程和原始多线程)
     * @param cases 测试用例
     * @param useMultipleThread 是否使用多线程 (非线程池)
     * @return 评测结果
     */
    public int[] runWithOriginalMultiThread(List<Integer> cases, boolean useMultipleThread) {
        int[] results = new int[cases.size()];
        
        if (useMultipleThread) {
            // 使用原始的多线程（非线程池）方式
            List<Thread> threads = IntStream.range(0, cases.size())
                .mapToObj(i -> new Thread(() -> results[i] = nQueenSolver.run(cases.get(i)))) 
                .collect(Collectors.toList());
            
            threads.forEach(Thread::start);
            threads.forEach(t -> {
                try {
                    t.join();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    System.err.println("评测线程被中断");
                }
            });
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
                    Thread.sleep(10); // 模拟编译时间耗时
                    results[i] = nQueenSolver.run(caseValue);
                    Thread.sleep(10); // 模拟持久化时间耗时
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
}
