import csv
import collections
import statistics

# 确保将下面的 file_path 替换为您的 JTL 文件的实际路径
file_path = "src/main/java/com/multiplethread/jmeter/results/medium-load-results.jtl"
# 如果文件在工作区的根目录，可以直接写 "medium-load-results.jtl"

# 初始化存储结构
# results[label] = {'total': 0, 'success': 0, 'failure': 0, 'elapsed_times': [], 'latencies': [], 'timestamps': []}
results = collections.defaultdict(lambda: {'total': 0, 'success': 0, 'failure': 0, 'elapsed_times': [], 'latencies': [], 'timestamps': []})
all_timestamps = []

try:
    with open(file_path, 'r', newline='', encoding='utf-8') as csvfile:
        reader = csv.DictReader(csvfile)
        if not reader.fieldnames:
            print("错误：CSV文件为空或表头缺失。")
        else:
            required_fields = ['label', 'success', 'elapsed', 'Latency', 'timeStamp']
            if not all(field in reader.fieldnames for field in required_fields):
                print(f"错误：CSV文件缺少必要的列。需要: {', '.join(required_fields)}，实际拥有: {', '.join(reader.fieldnames)}")
            else:
                for row in reader:
                    label = row['label']
                    is_success = row['success'].lower() == 'true' # JTL success is 'true' or 'false'
                    
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
                        print(f"警告：在处理行时遇到无效的数字数据：{row}")
                        # 可以选择跳过此行或记录为错误
                        results[label]['failure'] += 1 # 如果无法解析数据，也可能视为失败
                        if results[label]['success'] > 0 and is_success: # 避免重复扣减
                             results[label]['success'] -=1
                        continue


    if not results:
        print("未从文件中解析出任何数据。")
    else:
        print("--- 测试结果分析 ---")
        
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
            print(f"总测试持续时间: {test_duration_s:.2f} 秒")
            
            total_requests_overall = sum(data['total'] for data in results.values())
            if test_duration_s > 0:
                overall_throughput = total_requests_overall / test_duration_s
                print(f"总吞吐量 (所有请求): {overall_throughput:.2f} 请求/秒")
            else:
                print("总吞吐量 (所有请求): N/A (测试时间为0或无法计算)")

        else:
            print("总测试持续时间: N/A (没有时间戳数据)")
            print("总吞吐量 (所有请求): N/A")


        for label, data in results.items():
            print(f"\n--- 请求类型: {label} ---")
            print(f"  总请求数 (Samples): {data['total']}")
            print(f"  成功数: {data['success']}")
            print(f"  失败数: {data['failure']}")
            
            if data['total'] > 0:
                error_percentage = (data['failure'] / data['total']) * 100
                print(f"  错误率: {error_percentage:.2f}%")

            if data['elapsed_times']:
                avg_elapsed = statistics.mean(data['elapsed_times'])
                min_elapsed = min(data['elapsed_times'])
                max_elapsed = max(data['elapsed_times'])
                
                print(f"  平均响应时间 (Avg Elapsed): {avg_elapsed:.2f} ms")
                print(f"  最小响应时间 (Min Elapsed): {min_elapsed} ms")
                print(f"  最大响应时间 (Max Elapsed): {max_elapsed} ms")
                
                # 计算百分位数
                data['elapsed_times'].sort()
                p90_index = int(len(data['elapsed_times']) * 0.9) -1 # -1 for 0-based index
                p95_index = int(len(data['elapsed_times']) * 0.95) -1
                p99_index = int(len(data['elapsed_times']) * 0.99) -1

                if p90_index >= 0:
                     print(f"  90%响应时间 (90th Percentile): {data['elapsed_times'][p90_index]} ms")
                if p95_index >= 0:
                     print(f"  95%响应时间 (95th Percentile): {data['elapsed_times'][p95_index]} ms")
                if p99_index >= 0:
                     print(f"  99%响应时间 (99th Percentile): {data['elapsed_times'][p99_index]} ms")

            if data['latencies']:
                avg_latency = statistics.mean(data['latencies'])
                print(f"  平均延迟 (Avg Latency): {avg_latency:.2f} ms")

            # 计算该请求类型的吞吐量
            if data['timestamps'] and test_duration_s > 0 : #  and label_duration_s > 0:
                # 使用总测试时长来计算单个请求的吞吐量，这样更符合JMeter报告的习惯
                label_throughput = data['total'] / test_duration_s
                print(f"  吞吐量 (Throughput): {label_throughput:.2f} 请求/秒")
            else:
                print(f"  吞吐量 (Throughput): N/A")
        
        print("\n注意: 响应时间单位为毫秒 (ms)。")

except FileNotFoundError:
    print(f"错误: 文件未找到 {file_path}")
except Exception as e:
    print(f"处理文件时发生错误: {e}")
