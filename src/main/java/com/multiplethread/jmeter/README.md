# 在线判题系统分布式测试指南

本指南说明如何在局域网环境下对在线判题系统进行分布式测试。

## 前提条件

在开始测试前，请确保您已经：

1. 已安装 Apache JMeter 5.6.3 或更高版本
2. 已正确部署在线判题系统
3. 确保被测系统能够通过网络访问

## 测试文件说明

- `OJSystemLightLoad.jmx` - 轻负载测试计划 (20 RPS)
- `OJSystemMediumLoad.jmx` - 中负载测试计划 (60 RPS)
- `OJSystemHeavyLoad.jmx` - 高负载测试计划 (90 RPS)
- `run_tests.bat` - Windows测试执行脚本
- `run_tests.sh` - Linux/Mac测试执行脚本

## 使用方法

### 基本用法

`run_tests.bat` 脚本支持通过命令行参数指定目标主机和端口，无需手动修改JMX文件。

```batch
run_tests.bat [host] [port]
```

#### 参数说明

- `host` - 被测系统的主机名或IP地址（可选，默认：localhost）
- `port` - 被测系统的端口号（可选，默认：8080）

### 使用示例

1. 使用默认设置（localhost:8080）运行测试：

```batch
run_tests.bat
```

2. 指定主机IP地址运行测试：

```batch
run_tests.bat 192.168.120.12 8080
```

3. 只指定主机地址，使用默认端口：

```batch
run_tests.bat 192.168.120.12
```

## 测试步骤详解

执行 `run_tests.bat` 后，脚本将自动执行以下步骤：

1. **轻负载测试**：
   - 20个并发线程
   - 目标吞吐量：20 RPS
   - 测试持续时间：60秒
   - 结果保存至 `results\light\` 目录

2. **系统冷却**：
   - 等待60秒，使系统恢复稳定状态

3. **中负载测试**：
   - 50个并发线程
   - 目标吞吐量：60 RPS
   - 测试持续时间：60秒
   - 结果保存至 `results\medium\` 目录

4. **系统冷却**：
   - 再次等待60秒

5. **高负载测试**：
   - 50个并发线程
   - 目标吞吐量：90 RPS
   - 测试持续时间：60秒
   - 结果保存至 `results\heavy\` 目录

6. **生成HTML报告**：
   - 为每种负载级别生成HTML格式的测试报告

## 测试结果查看

测试完成后，结果将保存在以下目录：

- 轻负载测试结果：`results\light\light-load-results.jtl`
- 中负载测试结果：`results\medium\medium-load-results.jtl`
- 高负载测试结果：`results\heavy\heavy-load-results.jtl`

HTML报告位于相应目录的html子文件夹中：

- 轻负载HTML报告：`results\light\html\`
- 中负载HTML报告：`results\medium\html\`
- 高负载HTML报告：`results\heavy\html\`

## 资源监控

所有测试计划中都包含资源监控功能，每2秒收集一次以下指标：

- CPU使用率
- 系统内存使用率
- JVM内存使用率

监控数据保存在各自测试结果目录下的 `jmeter_resource_metrics.csv` 文件中。

## 问题排查

如果测试执行过程中遇到问题：

1. 检查JMeter路径是否正确配置
2. 确认被测系统是否可通过网络访问
3. 检查日志文件了解详细错误信息：
   - 轻负载日志：`results\light\light-load-log.log`
   - 中负载日志：`results\medium\medium-load-log.log`
   - 高负载日志：`results\heavy\heavy-load-log.log`

## 高级定制

如需进一步定制测试，可以：

1. 修改JMX文件中的测试参数
2. 调整run_tests.bat脚本中的配置
3. 为不同环境创建配置文件，例如：

```batch
@echo off
REM 保存为 prod_config.bat
SET TARGET_HOST=192.168.120.12
SET TARGET_PORT=8080
call run_tests.bat %TARGET_HOST% %TARGET_PORT%
``` 