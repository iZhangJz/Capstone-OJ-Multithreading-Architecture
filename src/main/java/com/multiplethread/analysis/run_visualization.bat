@echo off
REM 设置UTF-8编码，解决中文乱码
chcp 65001 > nul

echo ======================================
echo 性能测试资源指标可视化工具
echo ======================================
echo.

REM 设置Python路径，根据实际安装路径修改
SET PYTHON_CMD=D:\Python3\python.exe

REM 设置输出目录
SET OUTPUT_DIR=charts

REM 创建输出目录
if not exist "%OUTPUT_DIR%" mkdir "%OUTPUT_DIR%"

echo 正在运行可视化脚本...

%PYTHON_CMD% visualization.py --output %OUTPUT_DIR%

echo.
if %errorlevel% neq 0 (
    echo 生成图表时出错，错误代码: %errorlevel%
    exit /b %errorlevel%
) else (
    echo 图表生成成功！
    echo 所有图表已保存到 %OUTPUT_DIR% 目录
)

echo.
echo 是否打开图表输出目录？(Y/N)
set /p OPEN_DIR=

if /i "%OPEN_DIR%"=="Y" (
    start explorer %OUTPUT_DIR%
)

echo.
echo 脚本执行完毕。
echo ====================================== 