# 超时中断功能测试说明

本测试模块用于验证判题系统中的任务超时中断功能。超时中断功能允许系统在任务执行时间超过预设阈值时自动中断任务，防止恶意代码或无限循环导致系统资源耗尽。

## 测试类说明

### 1. TimedTaskTest.java

这是对`TimedTask`类的单元测试，验证任务超时中断的基本功能是否正常工作。

测试内容包括：
- 测试任务在超时前正常完成
- 测试任务超时被中断
- 测试多个任务同时执行时的超时处理

### 2. JudgeServerTimeoutTest.java

这是对`JudgeServer`和`ThreadPoolManager`集成的测试，验证超时功能在实际业务场景中的表现。

测试内容包括：
- 测试所有任务在超时前完成
- 测试部分任务超时
- 测试所有任务都超时
- 测试高并发情况下的超时处理

### 3. TimeoutPerformanceTest.java

这是对超时功能在实际N皇后问题上的性能测试，验证系统在处理正常和超时任务时的性能表现。

测试内容包括：
- 正常负载测试（所有任务都能完成）
- 混合负载测试（部分任务会超时）
- 吞吐量测试（大量任务中部分会超时）
- 系统恢复能力测试（处理超时任务后能否恢复正常）

## 运行测试

### 配置依赖

在运行测试前，确保项目的`pom.xml`中包含以下依赖：

```xml
<!-- JUnit 5 -->
<dependency>
    <groupId>org.junit.jupiter</groupId>
    <artifactId>junit-jupiter-api</artifactId>
    <version>5.8.2</version>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.junit.jupiter</groupId>
    <artifactId>junit-jupiter-engine</artifactId>
    <version>5.8.2</version>
    <scope>test</scope>
</dependency>

<!-- Mockito -->
<dependency>
    <groupId>org.mockito</groupId>
    <artifactId>mockito-core</artifactId>
    <version>4.5.1</version>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.mockito</groupId>
    <artifactId>mockito-junit-jupiter</artifactId>
    <version>4.5.1</version>
    <scope>test</scope>
</dependency>
```

### 使用Maven运行测试

```bash
# 运行所有测试
mvn test

# 运行特定测试类
mvn test -Dtest=TimedTaskTest
mvn test -Dtest=JudgeServerTimeoutTest
mvn test -Dtest=TimeoutPerformanceTest
```

### 使用IDE运行测试

在大多数IDE（如IntelliJ IDEA、Eclipse）中，你可以：
1. 打开相应的测试类
2. 右键点击测试类或具体的测试方法
3. 选择"运行"或"调试"

## 在实际代码中使用超时功能

如果你需要在代码中使用超时功能，可以通过`ThreadPoolManager`的`submitTaskWithTimeout`方法提交带超时的任务：

```java
// 获取ThreadPoolManager实例
@Resource
private ThreadPoolManager threadPoolManager;

// 提交带超时的任务
Runnable task = () -> {
    // 任务代码
};
threadPoolManager.submitTaskWithTimeout(task, 1000); // 1000毫秒超时
```

或者通过`JudgeServer`的`runWithTimeoutThreadPool`方法执行一批带超时的评测任务：

```java
// 获取JudgeServer实例
@Resource
private JudgeServer judgeServer;

// 执行一批带超时的任务
List<Integer> cases = Arrays.asList(5, 8, 12, 15);
int[] results = judgeServer.runWithTimeoutThreadPool(cases, 2000); // 2000毫秒超时
``` 