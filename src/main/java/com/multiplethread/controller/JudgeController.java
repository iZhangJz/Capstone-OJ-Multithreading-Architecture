package com.multiplethread.controller;

import com.multiplethread.judge.JudgeServer;
import com.multiplethread.judge.JudgeServer.DynamicExecutionResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * 在线判题系统的控制器类
 * 处理所有与判题相关的HTTP请求
 * 相关性能测试文档: 
 * - docs/modules/2023-10-29-JMeter测试计划-修复版本兼容性问题.md
 * - docs/modules/2023-11-21-JudgeController-ThreadPoolMonitor-优化.md
 */
@RestController
public class JudgeController {

    @Autowired
    private JudgeServer judgeServer;

    /**
     * 单线程评测
     * 适合Jmeter测试
     * @param n 测试用例大小（默认为10）
     * @return 评测结果和执行时间
     */
    @GetMapping("/judge/single")
    public Map<String, Object> judgeSingle(@RequestParam(defaultValue = "10") int n) {
        long startTime = System.currentTimeMillis();
        
        List<Integer> cases = generateTestCases(n);
        int[] results = judgeServer.runWithOriginalMultiThread(cases, false);
        
        long executionTime = System.currentTimeMillis() - startTime;
        
        Map<String, Object> response = new HashMap<>();
        response.put("results", results);
        response.put("executionTime", executionTime);
        response.put("mode", "single");
        
        return response;
    }
    
    /**
     * 多线程评测（不使用线程池）
     * 适合Jmeter测试
     * @param n 测试用例大小（默认为10）
     * @return 评测结果和执行时间
     */
    @GetMapping("/judge/multiple")
    public Map<String, Object> judgeMultiple(@RequestParam(defaultValue = "10") int n) {
        long startTime = System.currentTimeMillis();
        
        List<Integer> cases = generateTestCases(n);
        int[] results = judgeServer.runWithOriginalMultiThread(cases, true);
        
        long executionTime = System.currentTimeMillis() - startTime;
        
        Map<String, Object> response = new HashMap<>();
        response.put("results", results);
        response.put("executionTime", executionTime);
        response.put("mode", "multiple (no pool)");
        
        return response;
    }
    
    /**
     * 使用动态线程池评测 (新)
     * @param n 测试用例大小（默认为10）
     * @return 评测结果、执行时间以及监控报告
     */
    @GetMapping("/judge/dynamic")
    public Map<String, Object> judgeWithDynamicPool(@RequestParam(defaultValue = "12") int n) {
        long startTime = System.currentTimeMillis();
        
        List<Integer> cases = generateTestCases(n);
        DynamicExecutionResult dynamicResult = judgeServer.runWithDynamicThreadPool(cases);
        
        long executionTime = System.currentTimeMillis() - startTime;
        
        Map<String, Object> response = new HashMap<>();
        response.put("结果", dynamicResult.results);
        response.put("执行时间", executionTime);
        response.put("模式", "动态线程池");
        response.put("监控报告", dynamicResult.monitorReport);
        
        return response;
    }
    
    /**
     * 对应JMeter测试的统一API接口
     * 支持快速、中等和重度计算型任务
     * @param type 任务类型（fast/medium/heavy）
     * @param size 测试用例大小
     * @return 评测结果和执行时间
     */
    @PostMapping("/api/judge")
    public Map<String, Object> judgeForJMeter(
            @RequestParam(defaultValue = "fast") String type,
            @RequestParam(defaultValue = "25") int size) {
        
        long startTime = System.currentTimeMillis();
        String threadModel = System.getProperty("oj.threadModel", "single");
        
        int nQueenSize;
        switch(type) {
            case "medium":
                nQueenSize = 10;
                break;
            case "heavy":
                nQueenSize = 12;
                break;
            case "fast":
            default:
                nQueenSize = 9;
        }
        
        List<Integer> cases = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            cases.add(nQueenSize);
        }
        
        int[] results;
        String mode;
        String monitorReport = null;
        
        long executionStartTime = System.currentTimeMillis();
        
        switch(threadModel) {
            case "multiple":
                results = judgeServer.runWithOriginalMultiThread(cases, true);
                mode = "multiple-thread";
                break;
            case "dynamic":
                DynamicExecutionResult dynamicResult = judgeServer.runWithDynamicThreadPool(cases);
                results = dynamicResult.results;
                mode = "dynamic-pool";
                monitorReport = dynamicResult.monitorReport;
                break;
            case "single":
            default:
                results = judgeServer.runWithOriginalMultiThread(cases, false);
                mode = "single-thread";
        }
        
        long executionEndTime = System.currentTimeMillis();
        long executionTime = executionEndTime - startTime;
        long processingTime = executionEndTime - executionStartTime;
        
        Map<String, Object> response = new HashMap<>();
        response.put("请求ID", System.currentTimeMillis());
        response.put("结果", results);
        response.put("处理时间", processingTime);
        response.put("执行时间", executionTime);
        response.put("模式", mode);
        response.put("类型", type);
        response.put("N皇后大小", nQueenSize);
        response.put("规模", size);
        response.put("线程模型", threadModel);
        
        if (monitorReport != null) {
            response.put("监控报告", monitorReport);
        }
        
        return response;
    }
    
    /**
     * 生成测试用例
     * @param count 测试用例数量
     * @return 测试用例列表
     */
    private List<Integer> generateTestCases(int count) {
        List<Integer> cases = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            cases.add(i);
        }
        return cases;
    }
}
