# 性能测试分析工具集

本目录包含用于分析和可视化 JMeter 性能测试结果的工具脚本。

## 目录结构

```
analysis/
├── analyze.py          - JTL文件分析脚本
├── visualization.py    - 资源指标可视化脚本
├── run_tools.bat       - 一体化性能测试分析工具（Windows批处理脚本）
├── charts/             - 图表输出目录
└── analysis_reports/   - 分析报告输出目录
```

## 工具说明

### 1. run_tools.bat

一体化性能测试分析工具，提供了简便的方式运行分析和可视化功能。

**功能：**
- 自动设置 UTF-8 编码，解决中文乱码问题
- 检查 Python 环境和必要的数据文件
- 自动分析 JTL 文件并生成分析报告
- 生成资源使用情况图表
- 提供选项打开图表或分析报告目录

**使用方法：**
直接双击 `run_tools.bat` 运行，或在命令行中执行：
```
run_tools.bat
```

### 2. analyze.py

JMeter 测试结果分析工具，用于自动扫描和分析所有 JTL 文件并生成详细的性能测试报告。

**功能：**
- 自动扫描 `../jmeter/results` 目录及其子目录中的所有 JTL 文件
- 针对每个 JTL 文件生成独立的分析报告
- 分析请求成功率和错误率
- 计算响应时间指标（平均、最小、最大、90/95/99百分位）
- 计算吞吐量（请求/秒）
- 提供按请求类型分组的详细数据分析

**使用方法：**
通常通过 `run_tools.bat` 调用，也可单独运行：
```
python analyze.py
```

分析结果将保存在 `analysis_reports` 目录中，每个 JTL 文件都会生成对应的文本报告文件。

### 3. visualization.py

资源指标可视化工具，用于将 CPU、系统内存、JVM内存等性能指标转换为图表。

**功能：**
- 支持轻负载、中负载、重负载三种测试场景的资源指标可视化
- 在同一图表中展示 CPU 使用率、系统内存使用率、JVM 内存使用率
- 生成高质量 PNG 格式图表

**使用方法：**
通常通过 `run_tools.bat` 调用，也可单独运行：
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

## 示例输出

分析工具会生成详细的文本报告，包括各类请求的性能指标，保存在 `analysis_reports` 目录下。

可视化工具会在 `charts` 目录下生成以下图表：
- `Light Load.png` - 轻负载测试的资源使用情况
- `Medium Load.png` - 中负载测试的资源使用情况
- `Heavy Load.png` - 重负载测试的资源使用情况

## 环境要求

- Python 3.x
- 依赖库（仅可视化工具需要）：
  - pandas
  - seaborn
  - matplotlib

可通过以下命令安装可视化工具依赖：
```
pip install pandas seaborn matplotlib
```

# 性能测试资源指标可视化工具

## 简介

这个工具用于可视化性能测试期间收集的系统资源使用情况（CPU、系统内存和JVM内存）。工具会从CSV数据文件生成图表，直观地展示不同负载情况下系统的资源消耗。

## 使用前准备

1. **安装Python**：
   - 确保已安装Python 3.6或更高版本
   - 确保Python已添加到系统环境变量PATH中

2. **安装所需的Python库**：
   ```
   pip install pandas seaborn matplotlib
   ```

3. **准备数据文件**：
   确保以下CSV文件已生成并放置在正确的位置：
   - 轻负载测试数据：`../jmeter/results/light/jmeter_resource_metrics.csv`
   - 中负载测试数据：`../jmeter/results/medium/jmeter_resource_metrics.csv`
   - 重负载测试数据：`../jmeter/results/heavy/jmeter_resource_metrics.csv`

## 使用方法

1. 双击运行`run_visualization.bat`脚本
2. 脚本会自动运行Python可视化程序并生成图表
3. 图表默认保存在`charts`目录中
4. 脚本结束时，可选择是否打开图表目录查看结果

## 图表说明

生成的图表包含三种类型的资源使用情况：
- CPU使用率（红色实线）
- 系统内存使用率（蓝色虚线）
- JVM内存使用率（绿色点划线）

针对不同负载级别会生成不同的图表：
- Light Load.png：轻负载测试结果
- Medium Load.png：中负载测试结果
- Heavy Load.png：重负载测试结果

## 故障排除

如果遇到问题：

1. **找不到Python**：
   确保Python已安装并添加到环境变量PATH中

2. **缺少依赖库**：
   运行以下命令安装所需库：
   ```
   python -m pip install pandas seaborn matplotlib
   ```

3. **找不到数据文件**：
   确保CSV数据文件已生成并放在正确的位置，或者在运行脚本时使用命令行参数指定文件位置：
   ```
   python visualization.py --light path/to/light_data.csv --medium path/to/medium_data.csv --heavy path/to/heavy_data.csv --output charts
   ``` 