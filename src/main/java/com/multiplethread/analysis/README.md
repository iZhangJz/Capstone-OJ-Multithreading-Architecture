# 性能测试分析工具集

本目录包含用于分析和可视化 JMeter 性能测试结果的工具脚本。

## 目录结构

```
analysis/
├── analyze.py           - JMeter 测试结果分析脚本
├── visualization.py     - 资源指标可视化脚本
├── run_visualization.bat - Windows 批处理脚本，用于运行可视化工具
└── charts/              - 图表输出目录
```

## 工具说明

### 1. analyze.py

JMeter 测试结果分析工具，用于解析 JTL 文件并生成详细的性能测试报告。

**功能：**
- 分析请求成功率和错误率
- 计算响应时间指标（平均、最小、最大、90/95/99百分位）
- 计算吞吐量（请求/秒）
- 提供按请求类型分组的详细数据分析

**使用方法：**
```
python analyze.py
```

默认分析文件路径为 `src/main/java/com/multiplethread/jmeter/results/medium-load-results.jtl`，可在脚本内修改 `file_path` 变量更改目标文件。

### 2. visualization.py

资源指标可视化工具，用于将 CPU、系统内存、JVM内存等性能指标转换为图表。

**功能：**
- 支持轻负载、中负载、重负载三种测试场景的资源指标可视化
- 在同一图表中展示 CPU 使用率、系统内存使用率、JVM 内存使用率
- 生成高质量 PNG 格式图表

**使用方法：**
```
python visualization.py [选项]
```

**参数：**
- `--light` - 轻负载 CSV 文件路径
- `--medium` - 中负载 CSV 文件路径
- `--heavy` - 重负载 CSV 文件路径
- `--output` - 图表输出目录，默认为 `charts`

如不指定文件路径，则使用默认路径：
- 轻负载：`../jmeter/results/light/jmeter_resource_metrics.csv`
- 中负载：`../jmeter/results/medium/jmeter_resource_metrics.csv`
- 重负载：`../jmeter/results/heavy/jmeter_resource_metrics.csv`

### 3. run_visualization.bat

Windows 批处理脚本，提供了可视化工具的简易启动方式。

**功能：**
- 自动设置 UTF-8 编码，解决中文乱码问题
- 创建图表输出目录
- 运行可视化脚本
- 提供选项打开图表输出目录

**使用方法：**
直接双击 `run_visualization.bat` 运行，或在命令行中执行：
```
run_visualization.bat
```

**注意：** 使用前请在脚本中设置正确的 Python 路径（`PYTHON_CMD` 变量）。

## 示例输出

分析工具会生成详细的文本报告，包括各类请求的性能指标。

可视化工具会在 `charts` 目录下生成以下图表：
- `Light Load.png` - 轻负载测试的资源使用情况
- `Medium Load.png` - 中负载测试的资源使用情况
- `Heavy Load.png` - 重负载测试的资源使用情况

## 环境要求

- Python 3.x
- 依赖库：
  - pandas
  - seaborn
  - matplotlib
  - numpy

可通过以下命令安装依赖：
```
pip install pandas seaborn matplotlib numpy
``` 