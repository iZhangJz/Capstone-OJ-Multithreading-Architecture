import csv
import collections
import statistics
import os
import sys
from datetime import datetime

# 定义基本目录
BASE_RESULTS_DIR = "../jmeter/results"  # 相对于脚本位置的相对路径
OUTPUT_DIR = "analysis_reports"  # 输出目录


def find_jtl_files(base_dir):
    """
    在指定目录及其子目录中查找所有 JTL 文件
    """
    jtl_files = []
    
    # 检查目录是否存在
    if not os.path.exists(base_dir):
        print(f"警告: 目录不存在 - {base_dir}")
        return jtl_files
    
    # 遍历目录
    for root, dirs, files in os.walk(base_dir):
        for file in files:
            if file.endswith('.jtl'):
                jtl_files.append(os.path.join(root, file))
    
    return jtl_files


def analyze_jtl_file(file_path, output_file=None):
    """
    分析 JTL 文件并将结果写入指定的输出文件
    
    :param file_path: JTL 文件路径
    :param output_file: 输出文件路径，如果为 None，则输出到控制台
    :return: 分析是否成功
    """
    # 初始化存储结构
    # results[label] = {'total': 0, 'success': 0, 'failure': 0, 'elapsed_times': [], 'latencies': [], 'timestamps': []}
    results = collections.defaultdict(lambda: {'total': 0, 'success': 0, 'failure': 0, 'elapsed_times': [], 'latencies': [], 'timestamps': []})
    all_timestamps = []
    
    # 确定输出流
    if output_file:
        try:
            # 确保输出目录存在
            os.makedirs(os.path.dirname(output_file), exist_ok=True)
            output_stream = open(output_file, 'w', encoding='utf-8')
            print(f"正在将分析结果写入: {output_file}")
        except Exception as e:
            print(f"无法创建输出文件 {output_file}: {e}")
            return False
    else:
        output_stream = sys.stdout  # 使用标准输出
    
    try:
        # 分析文件
        with open(file_path, 'r', newline='', encoding='utf-8') as csvfile:
            reader = csv.DictReader(csvfile)
            if not reader.fieldnames:
                print(f"错误：CSV文件为空或表头缺失 - {file_path}", file=output_stream)
                if output_file:
                    output_stream.close()
                return False
            else:
                required_fields = ['label', 'success', 'elapsed', 'Latency', 'timeStamp']
                if not all(field in reader.fieldnames for field in required_fields):
                    print(f"错误：CSV文件缺少必要的列。需要: {', '.join(required_fields)}，实际拥有: {', '.join(reader.fieldnames)}", file=output_stream)
                    if output_file:
                        output_stream.close()
                    return False
                else:
                    for row in reader:
                        label = row['label']
                        is_success = row['success'].lower() == 'true'  # JTL success is 'true' or 'false'
                        
                        results[label]['total'] += 1
                        if is_success:
                            results[label]['success'] += 1
                        else:
                            results[label]['failure'] += 1
                        
                        try:
                            elapsed_time = int(row['elapsed'])
                            latency_time = int(row['Latency'])
                            timestamp = int(row['timeStamp'])
                            
                            results[label]['elapsed_times'].append(elapsed_time)
                            results[label]['latencies'].append(latency_time)
                            results[label]['timestamps'].append(timestamp)
                            all_timestamps.append(timestamp)
                        except ValueError:
                            print(f"警告：在处理行时遇到无效的数字数据：{row}", file=output_stream)
                            # 可以选择跳过此行或记录为错误
                            results[label]['failure'] += 1  # 如果无法解析数据，也可能视为失败
                            if results[label]['success'] > 0 and is_success:  # 避免重复扣减
                                results[label]['success'] -= 1
                            continue

        # 输出分析结果
        if not results:
            print("未从文件中解析出任何数据。", file=output_stream)
            if output_file:
                output_stream.close()
            return False
        else:
            print(f"--- 测试结果分析: {os.path.basename(file_path)} ---", file=output_stream)
            print(f"分析时间: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}", file=output_stream)
            print(f"JTL文件: {file_path}", file=output_stream)
            print("", file=output_stream)
            
            # 计算总测试持续时间
            if all_timestamps:
                min_timestamp = min(all_timestamps)
                max_timestamp = max(all_timestamps)
                # 找到最后一个请求的结束时间，需要 elapsed time
                last_request_end_time = 0
                for label_data in results.values():
                    if label_data['timestamps'] and label_data['elapsed_times']:
                        # 找到每个label的最后一个请求的结束时间
                        last_ts_index = label_data['timestamps'].index(max(label_data['timestamps']))
                        current_end_time = label_data['timestamps'][last_ts_index] + label_data['elapsed_times'][last_ts_index]
                        if current_end_time > last_request_end_time:
                            last_request_end_time = current_end_time
                
                test_duration_ms = last_request_end_time - min_timestamp
                test_duration_s = test_duration_ms / 1000.0
                print(f"总测试持续时间: {test_duration_s:.2f} 秒", file=output_stream)
                
                total_requests_overall = sum(data['total'] for data in results.values())
                if test_duration_s > 0:
                    overall_throughput = total_requests_overall / test_duration_s
                    print(f"总吞吐量 (所有请求): {overall_throughput:.2f} 请求/秒", file=output_stream)
                else:
                    print("总吞吐量 (所有请求): N/A (测试时间为0或无法计算)", file=output_stream)
            else:
                print("总测试持续时间: N/A (没有时间戳数据)", file=output_stream)
                print("总吞吐量 (所有请求): N/A", file=output_stream)

            for label, data in results.items():
                print(f"\n--- 请求类型: {label} ---", file=output_stream)
                print(f"  总请求数 (Samples): {data['total']}", file=output_stream)
                print(f"  成功数: {data['success']}", file=output_stream)
                print(f"  失败数: {data['failure']}", file=output_stream)
                
                if data['total'] > 0:
                    error_percentage = (data['failure'] / data['total']) * 100
                    print(f"  错误率: {error_percentage:.2f}%", file=output_stream)

                if data['elapsed_times']:
                    avg_elapsed = statistics.mean(data['elapsed_times'])
                    min_elapsed = min(data['elapsed_times'])
                    max_elapsed = max(data['elapsed_times'])
                    
                    print(f"  平均响应时间 (Avg Elapsed): {avg_elapsed:.2f} ms", file=output_stream)
                    print(f"  最小响应时间 (Min Elapsed): {min_elapsed} ms", file=output_stream)
                    print(f"  最大响应时间 (Max Elapsed): {max_elapsed} ms", file=output_stream)
                    
                    # 计算百分位数
                    data['elapsed_times'].sort()
                    p90_index = int(len(data['elapsed_times']) * 0.9) - 1  # -1 for 0-based index
                    p95_index = int(len(data['elapsed_times']) * 0.95) - 1
                    p99_index = int(len(data['elapsed_times']) * 0.99) - 1

                    if p90_index >= 0:
                        print(f"  90%响应时间 (90th Percentile): {data['elapsed_times'][p90_index]} ms", file=output_stream)
                    if p95_index >= 0:
                        print(f"  95%响应时间 (95th Percentile): {data['elapsed_times'][p95_index]} ms", file=output_stream)
                    if p99_index >= 0:
                        print(f"  99%响应时间 (99th Percentile): {data['elapsed_times'][p99_index]} ms", file=output_stream)

                if data['latencies']:
                    avg_latency = statistics.mean(data['latencies'])
                    print(f"  平均延迟 (Avg Latency): {avg_latency:.2f} ms", file=output_stream)

                # 计算该请求类型的吞吐量
                if data['timestamps'] and test_duration_s > 0:  # and label_duration_s > 0:
                    # 使用总测试时长来计算单个请求的吞吐量，这样更符合JMeter报告的习惯
                    label_throughput = data['total'] / test_duration_s
                    print(f"  吞吐量 (Throughput): {label_throughput:.2f} 请求/秒", file=output_stream)
                else:
                    print(f"  吞吐量 (Throughput): N/A", file=output_stream)
            
            print("\n注意: 响应时间单位为毫秒 (ms)。", file=output_stream)
            print(f"\n分析完成: {os.path.basename(file_path)}", file=output_stream)
            
            if output_file:
                output_stream.close()
            
            return True

    except FileNotFoundError:
        print(f"错误: 文件未找到 {file_path}", file=output_stream)
        if output_file:
            output_stream.close()
        return False
    except Exception as e:
        print(f"处理文件时发生错误: {e}", file=output_stream)
        if output_file:
            output_stream.close()
        return False


def main():
    """
    主函数
    """
    # 确定 results 目录的绝对路径（相对于脚本位置）
    script_dir = os.path.dirname(os.path.abspath(__file__))
    results_dir = os.path.abspath(os.path.join(script_dir, BASE_RESULTS_DIR))
    
    print(f"正在扫描 JTL 文件: {results_dir}")
    
    # 查找所有 JTL 文件
    jtl_files = find_jtl_files(results_dir)
    
    if not jtl_files:
        print("未找到任何 JTL 文件！")
        return
    
    print(f"找到 {len(jtl_files)} 个 JTL 文件。")
    
    # 创建输出目录
    output_dir = os.path.join(script_dir, OUTPUT_DIR)
    os.makedirs(output_dir, exist_ok=True)
    
    # 分析每个 JTL 文件
    success_count = 0
    for jtl_file in jtl_files:
        # 确定输出文件名
        # 从 JTL 文件路径提取负载类型（light/medium/heavy）
        load_type = None
        for load in ['light', 'medium', 'heavy']:
            if load in jtl_file.lower():
                load_type = load
                break
        
        # 如果无法确定负载类型，则使用文件名作为输出文件名前缀
        if not load_type:
            load_type = os.path.splitext(os.path.basename(jtl_file))[0]
        
        # 生成输出文件名
        output_file = os.path.join(output_dir, f"{load_type}_analysis_report.txt")
        
        # 分析文件并写入结果
        print(f"正在分析: {jtl_file}")
        if analyze_jtl_file(jtl_file, output_file):
            success_count += 1
    
    print(f"\n分析完成! 共分析 {len(jtl_files)} 个文件，成功 {success_count} 个。")
    print(f"分析报告已保存到目录: {output_dir}")


if __name__ == "__main__":
    main()
