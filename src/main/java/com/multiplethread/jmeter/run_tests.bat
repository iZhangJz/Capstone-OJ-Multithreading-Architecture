@echo off
REM 设置UTF-8编码，解决中文乱码
chcp 65001 > nul

REM JMeter Test Script for Online Judge System
REM Run tests in order of light load, medium load, and heavy load
REM Note: All test plans are controlled by duration (60 seconds) with pre-calculated sufficient loop counts

REM 检查是否提供了主机和端口参数
SET TARGET_HOST=%1
SET TARGET_PORT=%2

REM 如果未提供参数，则使用默认值
IF "%TARGET_HOST%"=="" (
    SET TARGET_HOST=localhost
    echo 未指定主机地址，使用默认值: %TARGET_HOST%
) ELSE (
    echo 使用指定的主机地址: %TARGET_HOST%
)

IF "%TARGET_PORT%"=="" (
    SET TARGET_PORT=8080
    echo 未指定端口号，使用默认值: %TARGET_PORT%
) ELSE (
    echo 使用指定的端口号: %TARGET_PORT%
)

echo ======================================
echo Performance Test for Online Judge System Starting
echo 目标系统: %TARGET_HOST%:%TARGET_PORT%
echo ======================================
echo.

REM Set JMeter path, modify according to actual installation path
SET JMETER_HOME=D:\apache-jmeter-5.6.3\apache-jmeter-5.6.3
SET JMETER_JAR=%JMETER_HOME%\bin\ApacheJMeter.jar
SET JMETER_CMD=%JMETER_HOME%\bin\jmeter.bat

REM 验证JMeter路径是否正确
if not exist "%JMETER_HOME%" (
    echo 错误: JMeter目录未找到: %JMETER_HOME%
    echo 请检查JMeter安装路径并修改JMETER_HOME变量
    exit /b 1
)

REM Create results directory structure
if not exist "results" mkdir results
if not exist "results\light" mkdir results\light
if not exist "results\light\html" mkdir results\light\html
if not exist "results\medium" mkdir results\medium
if not exist "results\medium\html" mkdir results\medium\html
if not exist "results\heavy" mkdir results\heavy
if not exist "results\heavy\html" mkdir results\heavy\html

echo Step 1: Running Light Load Test (10-50 requests/second)
echo Start Time: %time%
echo.

REM 注意这里添加了 -Jhost 和 -Jport 参数
call "%JMETER_CMD%" -n -t "OJSystemLightLoad.jmx" -Jhost="%TARGET_HOST%" -Jport="%TARGET_PORT%" -JoutputDir="results\light" -l "results\light\light-load-results.jtl" -j "results\light\light-load-log.log"
if %errorlevel% neq 0 (
    echo Light load test error, error code: %errorlevel%
    exit /b %errorlevel%
)

REM Generate HTML report for light load test
if exist "results\light\html" (
    rd /s /q "results\light\html"
)
mkdir "results\light\html"
call "%JMETER_CMD%" -g "results\light\light-load-results.jtl" -o "results\light\html"
if %errorlevel% neq 0 (
    echo Light load HTML report generation error, error code: %errorlevel%
    exit /b %errorlevel%
)

echo.
echo Light load test completed
echo HTML report generated in results\light\html
echo End Time: %time%
echo.
echo Waiting for system cooldown (60 seconds)...
timeout /t 60 /nobreak > nul

echo.
echo Step 2: Running Medium Load Test (50-100 requests/second)
echo Start Time: %time%
echo.

REM 添加 -Jhost 和 -Jport 参数
call "%JMETER_CMD%" -n -t "OJSystemMediumLoad.jmx" -Jhost="%TARGET_HOST%" -Jport="%TARGET_PORT%" -JoutputDir="results\medium" -l "results\medium\medium-load-results.jtl" -j "results\medium\medium-load-log.log"
if %errorlevel% neq 0 (
    echo Medium load test error, error code: %errorlevel%
    exit /b %errorlevel%
)

REM Generate HTML report for medium load test
if exist "results\medium\html" (
    rd /s /q "results\medium\html"
)
mkdir "results\medium\html"
call "%JMETER_CMD%" -g "results\medium\medium-load-results.jtl" -o "results\medium\html"
if %errorlevel% neq 0 (
    echo Medium load HTML report generation error, error code: %errorlevel%
    exit /b %errorlevel%
)

echo.
echo Medium load test completed
echo HTML report generated in results\medium\html
echo End Time: %time%
echo.
echo Waiting for system cooldown (60 seconds)...
timeout /t 60 /nobreak > nul

echo.
echo Step 3: Running Heavy Load Test (100-150 requests/second)
echo Start Time: %time%
echo.

REM 添加 -Jhost 和 -Jport 参数
call "%JMETER_CMD%" -n -t "OJSystemHeavyLoad.jmx" -Jhost="%TARGET_HOST%" -Jport="%TARGET_PORT%" -JoutputDir="results\heavy" -l "results\heavy\heavy-load-results.jtl" -j "results\heavy\heavy-load-log.log"
if %errorlevel% neq 0 (
    echo Heavy load test error, error code: %errorlevel%
    exit /b %errorlevel%
)

REM Generate HTML report for heavy load test
if exist "results\heavy\html" (
    rd /s /q "results\heavy\html"
)
mkdir "results\heavy\html"
call "%JMETER_CMD%" -g "results\heavy\heavy-load-results.jtl" -o "results\heavy\html"
if %errorlevel% neq 0 (
    echo Heavy load HTML report generation error, error code: %errorlevel%
    exit /b %errorlevel%
)

echo.
echo Heavy load test completed
echo HTML report generated in results\heavy\html
echo End Time: %time%
echo.

echo ======================================
echo All tests completed!
echo 测试目标: %TARGET_HOST%:%TARGET_PORT%
echo Results saved in results directory
echo HTML reports available in respective html folders
echo ======================================

REM List result files
dir results\*\*.jtl 