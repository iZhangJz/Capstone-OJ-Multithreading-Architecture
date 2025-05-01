package com.multiplethread.judge;

import com.multiplethread.model.ThreadPoolArgs;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.ArrayList;
import java.util.List;

/**
 * 超时功能的性能测试
 * 这个测试使用实际的NQueen问题来测试系统在处理超时任务时的性能
 */
class TimeoutPerformanceTest {

    private static NQueen nQueenSolver;
    private static ThreadPoolManager threadPoolManager;
    private static ThreadPoolMonitor threadPoolMonitor;
    private static JudgeServer judgeServer;

    @BeforeAll
    static void setUpClass() {
        nQueenSolver = new NQueen();
        threadPoolMonitor = new ThreadPoolMonitor();

        // 初始化SystemResourceMonitor并设置到ThreadPoolMonitor
        try {
            SystemResourceMonitor systemResourceMonitor = new SystemResourceMonitor();
            java.lang.reflect.Field srmField = ThreadPoolMonitor.class.getDeclaredField("systemResourceMonitor");
            srmField.setAccessible(true);
            srmField.set(threadPoolMonitor, systemResourceMonitor);
        } catch (Exception e) {
            System.err.println("设置systemResourceMonitor失败: " + e.getMessage());
        }

        threadPoolManager = new ThreadPoolManager();

        // 通过反射设置threadPoolMonitor
        try {
            java.lang.reflect.Field field = ThreadPoolManager.class.getDeclaredField("threadPoolMonitor");
            field.setAccessible(true);
            field.set(threadPoolManager, threadPoolMonitor);
        } catch (Exception e) {
            System.err.println("设置threadPoolMonitor失败: " + e.getMessage());
        }

        // 初始化线程池
        threadPoolManager.initializeMainExecutor(ThreadPoolArgs.DYNAMIC_INITIAL);

        // 初始化JudgeServer
        judgeServer = new JudgeServer();
        try {
            java.lang.reflect.Field tpmField = JudgeServer.class.getDeclaredField("threadPoolManager");
            tpmField.setAccessible(true);
            tpmField.set(judgeServer, threadPoolManager);

            java.lang.reflect.Field nqField = JudgeServer.class.getDeclaredField("nQueenSolver");
            nqField.setAccessible(true);
            nqField.set(judgeServer, nQueenSolver);
        } catch (Exception e) {
            System.err.println("设置JudgeServer依赖失败: " + e.getMessage());
        }
    }

    /**
     * 测试系统处理正常负载的能力
     */
    @Test
    @DisplayName("正常负载测试")
    void testNormalLoad() throws Exception {
        // 准备测试用例：N=8的N皇后问题有92个解
        List<Integer> cases = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            cases.add(8);
        }

        long startTime = System.currentTimeMillis();

        // 设置足够大的超时时间，应该所有任务都能正常完成
        int[] results = judgeServer.runWithTimeoutThreadPool(cases, 10000);

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        System.out.println("正常负载测试完成时间: " + duration + "ms");

        // 验证结果，N=8的N皇后问题有92个解
        for (int result : results) {
            assert result == 92 : "N=8的N皇后问题应该有92个解";
        }
    }

    /**
     * 测试系统处理混合负载（包含会超时的任务）的能力
     */
    @Test
    @DisplayName("混合负载测试")
    void testMixedLoad() throws Exception {
        // 准备测试用例：包含正常任务和会超时的任务
        List<Integer> cases = new ArrayList<>();

        // 添加10个正常任务 (N=8)
        for (int i = 0; i < 10; i++) {
            cases.add(8);
        }

        // 添加5个会超时的任务 (N=16，计算量非常大)
        for (int i = 0; i < 5; i++) {
            cases.add(16);
        }

        long startTime = System.currentTimeMillis();

        // 设置适中的超时时间，N=16的任务应该会超时
        int[] results = judgeServer.runWithTimeoutThreadPool(cases, 500);

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        System.out.println("混合负载测试完成时间: " + duration + "ms");

        // 验证结果
        int timeoutCount = 0;
        for (int i = 0; i < results.length; i++) {
            if (i < 10) {
                // 前10个任务应该正常完成
                assert results[i] == 92 : "N=8的N皇后问题应该有92个解，但得到了" + results[i];
            } else {
                // 后5个任务应该超时
                if (results[i] == -1) {
                    timeoutCount++;
                }
            }
        }

        // 验证超时任务数量
        assert timeoutCount == 5 : "应有5个N=16的任务超时，但实际只有" + timeoutCount + "个";

        // 验证总时间应该接近超时设置
        assert duration < 1000 : "超时处理应该限制总执行时间接近超时设置，但实际用时" + duration + "ms";
    }

    /**
     * 测试系统在大量任务中有部分超时的情况下的吞吐量
     */
    @Test
    @DisplayName("吞吐量测试")
    void testThroughput() throws Exception {
        // 准备大量测试用例
        final int normalTaskCount = 30;
        final int timeoutTaskCount = 10;
        List<Integer> cases = new ArrayList<>();

        // 添加正常任务 (N=4 到 N=8)
        for (int i = 0; i < normalTaskCount; i++) {
            cases.add(4 + (i % 5));
        }

        // 添加会超时的任务 (N=16)
        for (int i = 0; i < timeoutTaskCount; i++) {
            cases.add(16);
        }

        long startTime = System.currentTimeMillis();

        // 设置适中的超时时间
        int[] results = judgeServer.runWithTimeoutThreadPool(cases, 300);

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        System.out.println("吞吐量测试完成时间: " + duration + "ms");

        // 统计完成的任务数和超时的任务数
        int completedCount = 0;
        int timeoutCount = 0;

        for (int result : results) {
            if (result != -1) {
                completedCount++;
            } else {
                timeoutCount++;
            }
        }

        System.out.println("完成的任务数: " + completedCount);
        System.out.println("超时的任务数: " + timeoutCount);

        // 验证所有N=16的任务都应该超时
        assert timeoutCount == timeoutTaskCount : "所有N=16的任务都应该超时";
        assert completedCount == normalTaskCount : "所有正常任务都应该完成";
    }

    /**
     * 测试系统恢复能力：在处理一批包含超时任务的请求后，系统能否正常处理后续任务
     */
    @Test
    @DisplayName("系统恢复能力测试")
    void testSystemRecovery() throws Exception {
        // 第一批任务执行前先重置监控器
        threadPoolMonitor.reset();
        // 第一批：包含会超时的任务
        List<Integer> firstBatch = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            firstBatch.add(8); // 正常任务
        }
        for (int i = 0; i < 3; i++) {
            firstBatch.add(16); // 会超时的任务
        }

        // 执行第一批任务，设置较短的超时时间
        System.out.println("执行第一批任务（包含超时任务）...");
        judgeServer.runWithTimeoutThreadPool(firstBatch, 200);

        // 等待一段时间，让系统有机会恢复
        Thread.sleep(500);

        // 第二批：全部是正常任务
        List<Integer> secondBatch = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            secondBatch.add(8);
        }

        // 第二批任务执行前再次重置监控器，确保只统计第二批任务的数据
        threadPoolMonitor.reset();

        // 执行第二批任务
        System.out.println("执行第二批任务（全部正常任务）...");
        long startTime = System.currentTimeMillis();
        int[] results = judgeServer.runWithTimeoutThreadPool(secondBatch, 10000);
        long endTime = System.currentTimeMillis();

        System.out.println("第二批任务完成时间: " + (endTime - startTime) + "ms");

        // 验证第二批任务的结果
        for (int result : results) {
            assert result == 92 : "系统恢复后处理的N=8任务结果应该是92";
        }
    }
}