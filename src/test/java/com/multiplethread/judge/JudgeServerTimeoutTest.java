package com.multiplethread.judge;

import com.multiplethread.model.ThreadPoolArgs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * JudgeServer超时功能的集成测试
 */
class JudgeServerTimeoutTest {

    @Mock
    private ThreadPoolMonitor threadPoolMonitor;

    @Spy
    private ThreadPoolManager threadPoolManager;

    @Mock
    private NQueen nQueenSolver;
    
    @InjectMocks
    private JudgeServer judgeServer;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        // 初始化线程池
        threadPoolManager.initializeMainExecutor(ThreadPoolArgs.DYNAMIC_INITIAL);
        
        // 模拟NQueen求解器的行为
        when(nQueenSolver.run(anyInt())).thenAnswer(invocation -> {
            int n = invocation.getArgument(0);
            // 模拟执行时间，n越大执行时间越长
            if (n > 10) {
                Thread.sleep(300); // 大规模问题耗时长
            } else {
                Thread.sleep(50);  // 小规模问题耗时短
            }
            return n * n; // 返回一个假的结果
        });
    }
    
    /**
     * 测试所有任务在超时前完成
     */
    @Test
    @DisplayName("测试所有任务在超时前完成")
    void testAllTasksCompleteBeforeTimeout() throws Exception {
        // 准备只包含小规模问题的测试用例
        List<Integer> cases = Arrays.asList(5, 6, 7, 8);
        
        // 设置超时时间为200ms，足够所有任务完成
        int[] results = judgeServer.runWithTimeoutThreadPool(cases, 200);
        
        // 验证所有任务都成功完成
        for (int i = 0; i < results.length; i++) {
            assertEquals(cases.get(i) * cases.get(i), results[i], 
                    "任务 " + i + " 应该正常完成，返回 " + cases.get(i) * cases.get(i));
        }
    }
    
    /**
     * 测试部分任务超时
     */
    @Test
    @DisplayName("测试部分任务超时")
    void testSomeTasksTimeout() throws Exception {
        // 准备混合大小规模的测试用例
        List<Integer> cases = Arrays.asList(5, 15, 7, 20);
        
        // 设置超时时间为100ms，大规模任务会超时
        int[] results = judgeServer.runWithTimeoutThreadPool(cases, 100);
        
        // 验证小规模任务完成，大规模任务超时
        assertEquals(5 * 5, results[0], "小规模任务应该完成");
        assertEquals(-1, results[1], "大规模任务应该超时");
        assertEquals(7 * 7, results[2], "小规模任务应该完成");
        assertEquals(-1, results[3], "大规模任务应该超时");
        
        // 验证提交超时任务的方法被调用了正确的次数
        verify(threadPoolManager, times(cases.size())).submitTaskWithTimeout(any(), eq(100L));
    }

    /**
     * 测试所有任务都超时
     */
    @Test
    @DisplayName("测试所有任务都超时")
    void testAllTasksTimeout() throws Exception {
        // 准备只包含大规模问题的测试用例
        List<Integer> cases = Arrays.asList(15, 20, 25, 30);
        
        // 设置极短的超时时间，所有任务都会超时
        int[] results = judgeServer.runWithTimeoutThreadPool(cases, 10);
        
        // 验证所有任务都超时
        for (int result : results) {
            assertEquals(-1, result, "所有任务都应该超时");
        }
    }
    
    /**
     * 测试高并发情况下的超时处理
     */
    @Test
    @DisplayName("测试高并发情况下的超时处理")
    void testHighConcurrencyTimeout() throws Exception {
        // 准备大量测试用例
        final int caseCount = 20;
        Integer[] casesArray = new Integer[caseCount];
        
        // 偶数索引的用例是小规模问题，奇数索引的用例是大规模问题
        for (int i = 0; i < caseCount; i++) {
            casesArray[i] = (i % 2 == 0) ? 5 : 15;
        }
        List<Integer> cases = Arrays.asList(casesArray);
        
        // 设置中等超时时间，只有大规模问题会超时
        int[] results = judgeServer.runWithTimeoutThreadPool(cases, 100);
        
        // 验证结果
        int completedCount = 0;
        int timeoutCount = 0;
        
        for (int i = 0; i < results.length; i++) {
            if (i % 2 == 0) {
                // 偶数索引应该完成
                assertEquals(5 * 5, results[i], "小规模任务应该完成");
                completedCount++;
            } else {
                // 奇数索引应该超时
                assertEquals(-1, results[i], "大规模任务应该超时");
                timeoutCount++;
            }
        }
        
        assertEquals(caseCount / 2, completedCount, "应该有一半的任务完成");
        assertEquals(caseCount / 2, timeoutCount, "应该有一半的任务超时");
    }
} 