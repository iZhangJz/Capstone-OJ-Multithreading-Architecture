package com.multiplethread.controller;

import com.multiplethread.judge.SystemResourceMonitor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/monitor")
public class SystemMonitorController {
    
    @Resource
    private SystemResourceMonitor systemResourceMonitor;
    
    @GetMapping("/resources")
    public Map<String, Object> getResourceUsage() {
        Map<String, Object> result = new HashMap<>();
        result.put("timestamp", System.currentTimeMillis());
        result.put("cpuUsage", systemResourceMonitor.getProcessCpuUsage());
        result.put("systemMemoryUsage", systemResourceMonitor.getSystemMemoryUsage());
        result.put("jvmMemoryUsage", systemResourceMonitor.getJvmMemoryUsage());
        return result;
    }
}