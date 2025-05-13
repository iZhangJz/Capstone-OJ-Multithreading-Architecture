@echo off
REM 设置UTF-8编码，解决中文乱码
chcp 65001 > nul

echo ======================================
echo 性能测试分析与可视化工具
echo ======================================
echo.

REM 检查Python是否在环境变量中
where python >nul 2>nul
if %errorlevel% neq 0 (
    echo 错误: 未找到Python! 请确保Python已安装并添加到环境变量PATH中。
    echo 您可以从 https://www.python.org/downloads/ 下载Python并安装。
    pause
    exit /b 1
)

REM 设置Python命令为环境变量中的python
SET PYTHON_CMD=python

REM 检查必要的CSV文件是否存在
SET CSV_BASE_DIR=..\jmeter\results
if not exist "%CSV_BASE_DIR%\light\jmeter_resource_metrics.csv" (
    echo 警告: 未找到轻负载CSV文件 "%CSV_BASE_DIR%\light\jmeter_resource_metrics.csv"
)
if not exist "%CSV_BASE_DIR%\medium\jmeter_resource_metrics.csv" (
    echo 警告: 未找到中负载CSV文件 "%CSV_BASE_DIR%\medium\jmeter_resource_metrics.csv"
)
if not exist "%CSV_BASE_DIR%\heavy\jmeter_resource_metrics.csv" (
    echo 警告: 未找到重负载CSV文件 "%CSV_BASE_DIR%\heavy\jmeter_resource_metrics.csv"
)

REM 设置输出目录
SET CHARTS_DIR=charts
SET ANALYSIS_DIR=analysis_reports

REM 创建输出目录
if not exist "%CHARTS_DIR%" mkdir "%CHARTS_DIR%"
if not exist "%ANALYSIS_DIR%" mkdir "%ANALYSIS_DIR%"

echo.
echo ===== 第1步：分析JTL测试结果 =====
echo 正在分析 results 目录下的JTL文件...

%PYTHON_CMD% analyze.py

if %errorlevel% neq 0 (
    echo 分析JTL文件时出错，错误代码: %errorlevel%
    echo 请检查Python是否正确安装。
    pause
    exit /b %errorlevel%
)

echo.
echo ===== 第2步：生成资源使用图表 =====
echo 正在运行可视化脚本...

%PYTHON_CMD% visualization.py --output %CHARTS_DIR%

if %errorlevel% neq 0 (
    echo 生成图表时出错，错误代码: %errorlevel%
    echo 请检查Python是否正确安装，以及必要的库是否已安装。
    echo 您可能需要运行以下命令安装所需库:
    echo %PYTHON_CMD% -m pip install pandas seaborn matplotlib
    pause
    exit /b %errorlevel%
) else (
    echo 图表生成成功！
    echo 所有图表已保存到 %CHARTS_DIR% 目录
    echo 所有分析报告已保存到 %ANALYSIS_DIR% 目录
)

echo.
echo 请选择要查看的输出:
echo 1. 图表目录 (Charts)
echo 2. 分析报告目录 (Analysis Reports)
echo 3. 两者都不查看
echo.
set /p VIEW_OPTION=请输入选项 (1/2/3): 

if "%VIEW_OPTION%"=="1" (
    start explorer %CHARTS_DIR%
) else if "%VIEW_OPTION%"=="2" (
    start explorer %ANALYSIS_DIR%
)

echo.
echo 脚本执行完毕。
echo ====================================== 