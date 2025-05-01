# 为判题系统添加任务超时中断功能

- **日期**: 2025-05-01
- **作者**: ZhangJz
- **变更类型**: 新增

## 相关文件
- src/main/java/com/multiplethread/judge/MonitoredTask.java
- src/main/java/com/multiplethread/judge/TimedTask.java
- src/main/java/com/multiplethread/judge/ThreadPoolManager.java
- src/main/java/com/multiplethread/judge/JudgeServer.java

## 变更描述
为判题系统添加了任务超时中断功能，使系统能够在任务执行时间超过预设阈值时自动中断任务。具体实现包括：

1. 在`MonitoredTask`类中添加了`getActualTask()`方法以便子类访问实际任务
2. 新增`TimedTask`类，继承自`MonitoredTask`，通过调度器实现超时中断
3. 在`ThreadPoolManager`中添加超时检查线程池和提交带超时任务的方法
4. 在`JudgeServer`中添加了支持超时任务的新方法`runWithTimeoutThreadPool`

## 变更原因
在线判题系统需要防止恶意代码或无限循环导致的资源耗尽问题。通过为任务添加超时中断功能，系统可以更加健壮地处理各种提交的代码，避免因单个任务卡住而影响整个系统的性能。

## 设计决策
1. **继承而非组合**：选择让`TimedTask`继承自`MonitoredTask`而不是完全重写，以保持代码逻辑的一致性并最小化代码改动。
2. **专用超时检查线程池**：使用单独的ScheduledExecutorService来处理超时检查，避免主线程池资源被占用。
3. **非侵入式设计**：保持原有API不变，通过添加新方法的方式支持超时功能，确保向后兼容性。
4. **默认错误值**：超时任务的结果默认为-1，便于区分正常完成和超时中断的任务。

## 测试方法
1. 单元测试：编写针对`TimedTask`的单元测试，验证超时中断功能是否正常工作
2. 集成测试：使用具有不同执行时间的测试用例，验证系统是否能正确处理超时情况
3. 压力测试：同时提交多个会超时的任务，验证系统稳定性和资源释放情况

## 未来工作
1. 添加更细粒度的超时控制，如不同类型任务设置不同的超时时间
2. 实现超时任务的重试机制
3. 增加超时统计和监控功能，收集超时数据用于系统优化
4. 考虑使用更高级的中断机制，如JVM安全点技术，更彻底地处理资源释放问题 