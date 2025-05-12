#!/bin/bash
# 在线判题系统JMeter测试脚本
# 按照轻负载、中负载、高负载的顺序依次运行测试
# 注意：所有测试计划由持续时间(60秒)控制，并配合预先计算的足够大的循环次数

# 检查是否提供了主机和端口参数
TARGET_HOST=$1
TARGET_PORT=$2

# 如果未提供参数，则使用默认值
if [ -z "$TARGET_HOST" ]; then
    TARGET_HOST="localhost"
    echo "未指定主机地址，使用默认值: $TARGET_HOST"
else
    echo "使用指定的主机地址: $TARGET_HOST"
fi

if [ -z "$TARGET_PORT" ]; then
    TARGET_PORT="8080"
    echo "未指定端口号，使用默认值: $TARGET_PORT"
else
    echo "使用指定的端口号: $TARGET_PORT"
fi

echo "======================================"
echo "在线判题系统性能测试开始执行"
echo "目标系统: $TARGET_HOST:$TARGET_PORT"
echo "======================================"
echo ""

# 设置JMeter路径，需要根据实际安装路径修改
JMETER_HOME=/opt/apache-jmeter-5.6.3
JMETER_BIN=$JMETER_HOME/bin
JMETER_CMD=$JMETER_BIN/jmeter.sh

# 创建结果目录
mkdir -p results/light/html
mkdir -p results/medium/html
mkdir -p results/heavy/html

echo "步骤1: 运行轻负载测试 (10-50请求/秒)"
echo "开始时间: $(date +"%T")"
echo ""

# 添加 -Jhost 和 -Jport 参数
$JMETER_CMD -n -t OJSystemLightLoad.jmx -Jhost="$TARGET_HOST" -Jport="$TARGET_PORT" -JoutputDir="results/light" -l results/light/light-load-results.jtl -j results/light/light-load-log.log
if [ $? -ne 0 ]; then
    echo "轻负载测试出错，错误码: $?"
    exit $?
fi

# 生成HTML报告
$JMETER_CMD -g results/light/light-load-results.jtl -o results/light/html
if [ $? -ne 0 ]; then
    echo "轻负载HTML报告生成错误，错误码: $?"
    exit $?
fi

echo ""
echo "轻负载测试完成"
echo "HTML报告生成在 results/light/html 目录"
echo "结束时间: $(date +"%T")"
echo ""
echo "等待系统冷却 (60秒)..."
sleep 60

echo ""
echo "步骤2: 运行中负载测试 (50-100请求/秒)"
echo "开始时间: $(date +"%T")"
echo ""

# 添加 -Jhost 和 -Jport 参数
$JMETER_CMD -n -t OJSystemMediumLoad.jmx -Jhost="$TARGET_HOST" -Jport="$TARGET_PORT" -JoutputDir="results/medium" -l results/medium/medium-load-results.jtl -j results/medium/medium-load-log.log
if [ $? -ne 0 ]; then
    echo "中负载测试出错，错误码: $?"
    exit $?
fi

# 生成HTML报告
$JMETER_CMD -g results/medium/medium-load-results.jtl -o results/medium/html
if [ $? -ne 0 ]; then
    echo "中负载HTML报告生成错误，错误码: $?"
    exit $?
fi

echo ""
echo "中负载测试完成"
echo "HTML报告生成在 results/medium/html 目录"
echo "结束时间: $(date +"%T")"
echo ""
echo "等待系统冷却 (60秒)..."
sleep 60

echo ""
echo "步骤3: 运行高负载测试 (100-150请求/秒)"
echo "开始时间: $(date +"%T")"
echo ""

# 添加 -Jhost 和 -Jport 参数
$JMETER_CMD -n -t OJSystemHeavyLoad.jmx -Jhost="$TARGET_HOST" -Jport="$TARGET_PORT" -JoutputDir="results/heavy" -l results/heavy/heavy-load-results.jtl -j results/heavy/heavy-load-log.log
if [ $? -ne 0 ]; then
    echo "高负载测试出错，错误码: $?"
    exit $?
fi

# 生成HTML报告
$JMETER_CMD -g results/heavy/heavy-load-results.jtl -o results/heavy/html
if [ $? -ne 0 ]; then
    echo "高负载HTML报告生成错误，错误码: $?"
    exit $?
fi

echo ""
echo "高负载测试完成"
echo "HTML报告生成在 results/heavy/html 目录"
echo "结束时间: $(date +"%T")"
echo ""

echo "======================================"
echo "所有测试完成!"
echo "测试目标: $TARGET_HOST:$TARGET_PORT"
echo "结果文件保存在 results 目录"
echo "======================================"

# 列出结果文件
ls -la results/*/*.jtl 