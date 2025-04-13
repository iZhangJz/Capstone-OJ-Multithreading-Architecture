# OJ系统多线程机制研究

本项目是一个在线算法评测系统（OJ系统）的多线程机制研究，主要研究不同线程处理模式下的性能表现和线程安全问题。

## 系统架构

系统主要包含以下几个模块：

1. **控制器层**：处理HTTP请求，提供API接口
   - `JudgeController`：提供评测接口
   - `ThreadPoolTestController`：提供线程池测试接口

2. **评测引擎**：执行算法评测
   - `JudgeServer`：评测服务
   - `NQueen`：N皇后算法实现（示例算法）

3. **线程池管理**：管理和监控线程池
   - `ThreadPoolManager`：线程池管理
   - `ThreadPoolMonitor`：线程池监控
   - `ThreadPoolTester`：线程池测试

4. **数据模型**：
   - `ThreadPoolArgs`：线程池参数配置

## 评测模式

系统支持三种评测模式：

1. **单线程模式**：使用单线程顺序执行评测任务
2. **多线程模式**：为每组任务创建新线程执行
3. **线程池模式**：使用线程池管理线程资源，支持不同配置

## 线程池配置

系统提供了多种线程池配置，分为两类：

1. **固定大小线程池**：
   - `FIXED_SMALL`：核心线程数=2，最大线程数=2
   - `FIXED_MEDIUM`：核心线程数=5，最大线程数=5
   - `FIXED_LARGE`：核心线程数=10，最大线程数=10

2. **可伸缩线程池**：
   - `CACHED_SMALL`：核心线程数=2，最大线程数=5
   - `CACHED_MEDIUM`：核心线程数=5，最大线程数=10
   - `CACHED_LARGE`：核心线程数=10，最大线程数=20

## API接口

### 评测接口

1. **单线程评测**：
   ```
   GET /judge/single?n=10
   ```

2. **多线程评测**：
   ```
   GET /judge/multiple?n=10
   ```

3. **线程池评测**：
   ```
   GET /judge/threadpool?n=10&poolType=fixed&size=medium
   ```
   参数说明：
   - `n`：测试用例大小
   - `poolType`：线程池类型（fixed或cached）
   - `size`：线程池大小（small、medium或large）

### 线程池管理接口

1. **创建线程池**：
   ```
   GET /test/pool/create/{poolType}/{size}
   ```

2. **获取线程池状态**：
   ```
   GET /test/pool/status/{poolId}
   ```

3. **关闭线程池**：
   ```
   GET /test/pool/shutdown/{poolId}
   ```

4. **获取线程池监控报告**：
   ```
   GET /test/pool/report/{poolId}
   ```

5. **记录测试结果**：
   ```
   POST /test/record?poolId={poolId}&executionTime={executionTime}&taskCount={taskCount}
   ```

6. **获取测试报告**：
   ```
   GET /test/report
   ```

7. **清除测试结果**：
   ```
   GET /test/clear
   ```

8. **关闭所有线程池**：
   ```
   GET /test/shutdown/all
   ```

## 性能测试

系统提供了JMeter测试计划，位于`src/main/resources/jmeter/OJ_Performance_Test_Plan.jmx`，可以用于测试不同评测模式的性能。

### 测试步骤

1. 启动应用程序
2. 使用JMeter打开测试计划
3. 配置测试参数（并发用户数、循环次数等）
4. 运行测试
5. 分析测试结果

### 测试场景

1. **低负载测试**：20个并发用户，每个用户发送10个请求
2. **中负载测试**：50个并发用户，每个用户发送10个请求
3. **高负载测试**：100个并发用户，每个用户发送10个请求

## 线程安全问题研究

在系统中，主要研究以下线程安全问题：

1. **共享资源访问**：多个线程同时访问结果集
2. **线程池任务调度**：任务提交和执行的线程安全
3. **资源竞争**：多个线程竞争CPU和内存资源
4. **死锁和活锁**：线程间的相互等待和资源竞争

## 性能指标

系统收集以下性能指标：

1. **响应时间**：评测任务的执行时间
2. **吞吐量**：单位时间内完成的评测任务数
3. **线程池状态**：活跃线程数、队列大小等
4. **资源利用率**：CPU和内存使用情况

## 运行环境

- Java 8+
- Spring Boot 2.x
- JMeter 5.x（用于性能测试） 