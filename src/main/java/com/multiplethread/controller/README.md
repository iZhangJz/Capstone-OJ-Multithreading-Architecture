# 线程模型性能对比测试指南

本文档提供了如何正确执行在线判题系统的线程模型性能对比测试的说明。

## 问题修正

在原有实现中，我们发现了一个实验设计上的缺陷：`judgeForJMeter`方法根据任务类型自动选择不同的线程处理模型，这导致无法有效比较线程模型本身的性能差异。具体来说：

- 快速计算型任务（fast）使用单线程模型
- 中等计算型任务（medium）使用常规多线程模型
- 重度计算型任务（heavy）使用动态线程池模型

这种设计无法公平比较线程模型的优劣，因为：
1. 任务类型与线程模型绑定，混淆了影响因素
2. 不能对相同的任务负载测试不同的线程模型
3. 违反了科学实验中的控制变量原则

## 优化后的实现

我们对设计进行了优化，将线程模型选择与任务类型解耦，通过系统属性参数控制使用的线程模型：

```java
String threadModel = System.getProperty("oj.threadModel", "single");

// 根据系统属性参数选择线程处理模型
switch(threadModel) {
    case "multiple":
        // 使用常规多线程模型
        results = judgeServer.runWithOriginalMultiThread(cases, true);
        break;
    case "dynamic":
        // 使用动态线程池模型
        results = judgeServer.runWithDynamicThreadPool(cases);
        break;
    case "single":
    default:
        // 使用单线程模型
        results = judgeServer.runWithOriginalMultiThread(cases, false);
}
```

同时，我们利用N皇后问题的不同规模来反映任务的复杂度差异，而不是使用人工延迟：

```java
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
```

N皇后问题的计算复杂度随N值呈指数级增长，通过调整N的值，我们可以真实模拟不同复杂度的计算任务：
- N=8: 约92个解，计算量适中，适合快速任务
- N=10: 约724个解，计算量较大，适合中等任务
- N=12: 约14200个解，计算量巨大，适合重度任务

## 如何正确执行测试

为确保实验结果有效，应按照如下步骤进行测试：

### 1. 单线程模式测试
```bash
# 启动系统，指定使用单线程模型
java -Doj.threadModel=single -jar online-judge-system.jar

# 在另一个终端运行JMeter测试
jmeter -n -t src/main/resources/jmeter/OJSystemTestPlan.jmx -l results-single.jtl
```

### 2. 传统多线程模式测试
```bash
# 重启系统，指定使用多线程模型
java -Doj.threadModel=multiple -jar online-judge-system.jar

# 运行相同的JMeter测试
jmeter -n -t src/main/resources/jmeter/OJSystemTestPlan.jmx -l results-multiple.jtl
```

### 3. 动态线程池模式测试
```bash
# 重启系统，指定使用动态线程池模型
java -Doj.threadModel=dynamic -jar online-judge-system.jar

# 运行相同的JMeter测试
jmeter -n -t src/main/resources/jmeter/OJSystemTestPlan.jmx -l results-dynamic.jtl
```

### 结果分析

收集完三组测试结果后，可以使用JMeter的比较报告功能或其他数据分析工具对结果进行对比分析。主要关注：

1. 吞吐量 - 每秒处理的请求数（Throughput）
2. 响应时间 - 平均响应时间（Average）、90%响应时间（90% Line）、最大响应时间（Max）
3. 错误率 - 处理失败的请求百分比（Error %）

## 数据可视化

推荐使用以下工具进行测试结果的可视化分析：

1. JMeter HTML报告生成器：
```bash
jmeter -g results-*.jtl -o report-directory
```

2. 使用Python+Matplotlib创建自定义图表：
```python
import pandas as pd
import matplotlib.pyplot as plt

# 读取测试结果
df_single = pd.read_csv('results-single.jtl')
df_multiple = pd.read_csv('results-multiple.jtl')
df_dynamic = pd.read_csv('results-dynamic.jtl')

# 创建性能对比图表
# ...
``` 