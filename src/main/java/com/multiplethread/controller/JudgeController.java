package com.multiplethread.controller;

import com.multiplethread.judge.JudgeServer;
import com.multiplethread.judge.ThreadPoolManager;
import com.multiplethread.model.ThreadPoolArgs;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.annotation.PostConstruct;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

@RestController
public class JudgeController {

    @Autowired
    private JudgeServer judgeServer;

    @Autowired
    private ThreadPoolManager threadPoolManager;

    /**
     * 初始化主线程池
     */
    @PostConstruct
    public void initializeThreadPool() {
        System.out.println("Initializing main thread pool in JudgeController...");
        threadPoolManager.initializeMainExecutor(ThreadPoolArgs.DYNAMIC_INITIAL);
        System.out.println("Main thread pool initialized.");
    }

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
        int[] results = judgeServer.run(cases, false, false);
        
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
        int[] results = judgeServer.run(cases, true, false);
        
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
        int[] results = judgeServer.runWithDynamicThreadPool(cases);
        
        long executionTime = System.currentTimeMillis() - startTime;
        
        String report = threadPoolManager.getMainThreadPoolStatusReport();
        
        Map<String, Object> response = new HashMap<>();
        response.put("results", results);
        response.put("executionTime", executionTime);
        response.put("mode", "dynamic-pool");
        response.put("monitorReport", report);
        
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
