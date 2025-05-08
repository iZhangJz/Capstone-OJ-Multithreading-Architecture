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
     * 对应JMeter测试的统一API接口
     * 支持快速、中等和重度计算型任务
     * @param type 任务类型（fast/medium/heavy）
     * @param size 测试用例大小
     * @return 评测结果和执行时间
     */
    @PostMapping("/api/judge")
    public Map<String, Object> judgeForJMeter(
            @RequestParam(defaultValue = "fast") String type,
            @RequestParam(defaultValue = "4") int size) {
        
        long startTime = System.currentTimeMillis();
        String threadModel = System.getProperty("oj.threadModel", "single");
        
        // 根据任务类型确定N皇后问题的规模
        int nQueenSize;
        switch(type) {
            case "medium":
                // 中等计算任务使用适中规模的N皇后
                nQueenSize = 10;
                break;
            case "heavy":
                // 重度计算任务使用较大规模的N皇后
                nQueenSize = 12;
                break;
            case "fast":
            default:
                // 快速计算任务使用小规模的N皇后
                nQueenSize = 8;
        }
        
        // 生成指定数量的测试用例，每个用例使用相应规模的N皇后问题
        List<Integer> cases = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            cases.add(nQueenSize);
        }
        
        int[] results;
        String mode;
        String monitorReport = null;
        
        // 记录任务开始执行的时间（用于计算处理性能指标）
        long executionStartTime = System.currentTimeMillis();
        
        // 根据系统属性参数选择线程处理模型
        switch(threadModel) {
            case "multiple":
                // 使用常规多线程模型
                results = judgeServer.runWithOriginalMultiThread(cases, true);
                mode = "multiple-thread";
                break;
            case "dynamic":
                // 使用动态线程池模型
                results = judgeServer.runWithDynamicThreadPool(cases);
                mode = "dynamic-pool";
                // 获取线程池监控报告
                monitorReport = threadPoolManager.getMainThreadPoolStatusReport();
                break;
            case "single":
            default:
                // 使用单线程模型
                results = judgeServer.runWithOriginalMultiThread(cases, false);
                mode = "single-thread";
        }
        
        // 记录任务结束时间
        long executionEndTime = System.currentTimeMillis();
        long executionTime = executionEndTime - startTime;
        long processingTime = executionEndTime - executionStartTime;
        
        Map<String, Object> response = new HashMap<>();
        response.put("requestId", System.currentTimeMillis()); // 提供给JMeter提取器的ID
        response.put("results", results);
        response.put("executionTime", executionTime);  // 包括请求处理的总时间
        response.put("processingTime", processingTime); // 纯计算处理时间
        response.put("mode", mode);
        response.put("type", type);
        response.put("nQueenSize", nQueenSize);
        response.put("size", size);
        response.put("threadModel", threadModel);
        
        // 添加常见指标（这些指标在任何线程模型下都可用）
        Map<String, Object> basicMetrics = new HashMap<>();
        basicMetrics.put("totalTasks", size);
        basicMetrics.put("avgTaskTime", processingTime / (size > 0 ? size : 1));
        
        // 如果使用动态线程池，添加详细的监控报告和关键性能指标
        if (monitorReport != null) {
            response.put("monitorReport", monitorReport);
            
            // 提取关键性能指标并添加到响应中
            Map<String, Object> detailedMetrics = extractMetricsFromReport(monitorReport);
            if (detailedMetrics != null && !detailedMetrics.isEmpty()) {
                // 合并基本指标和详细指标
                basicMetrics.putAll(detailedMetrics);
            }
        }
        
        // 添加性能指标到响应
        response.put("metrics", basicMetrics);
        
        return response;
    }
    
    /**
     * 从监控报告中提取关键性能指标
     * 
     * @param report 线程池监控报告
     * @return 包含关键性能指标的Map
     */
    private Map<String, Object> extractMetricsFromReport(String report) {
        if (report == null || report.isEmpty()) {
            return null;
        }
        
        Map<String, Object> metrics = new HashMap<>();
        
        try {
            // 解析监控报告中的关键指标
            String[] lines = report.split("\n");
            for (String line : lines) {
                line = line.trim();
                
                // 任务数量
                if (line.startsWith("统计周期内任务数:")) {
                    String value = line.substring(line.indexOf(":") + 1).trim();
                    metrics.put("totalTasks", Integer.parseInt(value));
                }
                
                // 平均执行时间
                else if (line.startsWith("平均执行时间:")) {
                    String value = line.substring(line.indexOf(":") + 1, line.indexOf("ms")).trim();
                    metrics.put("avgExecutionTime", Long.parseLong(value));
                }
                
                // 平均等待时间
                else if (line.startsWith("平均等待时间:")) {
                    String value = line.substring(line.indexOf(":") + 1, line.indexOf("ms")).trim();
                    metrics.put("avgWaitTime", Long.parseLong(value));
                }
                
                // 资源竞争系数
                else if (line.startsWith("资源竞争系数")) {
                    String value = line.substring(line.indexOf(":") + 1).trim();
                    metrics.put("gamma", Double.parseDouble(value));
                }
                
                // 最大执行时间
                else if (line.startsWith("最大执行时间:")) {
                    String value = line.substring(line.indexOf(":") + 1, line.indexOf("ms")).trim();
                    metrics.put("maxExecutionTime", Long.parseLong(value));
                }
                
                // 最小执行时间
                else if (line.startsWith("最小执行时间:")) {
                    String value = line.substring(line.indexOf(":") + 1, line.indexOf("ms")).trim();
                    metrics.put("minExecutionTime", Long.parseLong(value));
                }
                
                // 拒绝任务数
                else if (line.startsWith("拒绝任务数:")) {
                    String value = line.substring(line.indexOf(":") + 1).trim();
                    metrics.put("rejectedTasks", Long.parseLong(value));
                }
                
                // 失败任务数
                else if (line.startsWith("失败任务数:")) {
                    String value = line.substring(line.indexOf(":") + 1).trim();
                    metrics.put("failedTasks", Long.parseLong(value));
                }
                
                // 系统CPU利用率
                else if (line.startsWith("系统CPU利用率:")) {
                    String value = line.substring(line.indexOf(":") + 1, line.indexOf("%")).trim();
                    metrics.put("systemCpuUsage", Double.parseDouble(value));
                }
                
                // JVM进程CPU利用率
                else if (line.startsWith("JVM进程CPU利用率:")) {
                    String value = line.substring(line.indexOf(":") + 1, line.indexOf("%")).trim();
                    metrics.put("processCpuUsage", Double.parseDouble(value));
                }
                
                // 系统内存利用率
                else if (line.startsWith("系统内存利用率:")) {
                    String value = line.substring(line.indexOf(":") + 1, line.indexOf("%")).trim();
                    metrics.put("systemMemoryUsage", Double.parseDouble(value));
                }
                
                // JVM内存利用率
                else if (line.startsWith("JVM内存利用率:")) {
                    String value = line.substring(line.indexOf(":") + 1, line.indexOf("%")).trim();
                    metrics.put("jvmMemoryUsage", Double.parseDouble(value));
                }
                
                // 活跃线程数
                else if (line.startsWith("活跃线程数:")) {
                    String value = line.substring(line.indexOf(":") + 1).trim();
                    metrics.put("activeThreads", Integer.parseInt(value));
                }
                
                // 当前线程数
                else if (line.startsWith("当前线程数:")) {
                    String value = line.substring(line.indexOf(":") + 1).trim();
                    metrics.put("poolSize", Integer.parseInt(value));
                }
                
                // 核心线程数
                else if (line.startsWith("核心线程数:")) {
                    String value = line.substring(line.indexOf(":") + 1).trim();
                    metrics.put("corePoolSize", Integer.parseInt(value));
                }
                
                // 任务队列大小
                else if (line.startsWith("任务队列大小:")) {
                    String value = line.substring(line.indexOf(":") + 1).trim();
                    metrics.put("queueSize", Integer.parseInt(value));
                }
            }
        } catch (Exception e) {
            System.err.println("解析监控报告时发生错误: " + e.getMessage());
            return new HashMap<>(); // 返回空map，避免null导致的错误
        }
        
        return metrics;
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
