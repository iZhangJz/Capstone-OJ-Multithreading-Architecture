import pandas as pd
import seaborn as sns
import matplotlib.pyplot as plt
import matplotlib.dates as mdates
from datetime import datetime
import os
import argparse
import matplotlib
import numpy as np
from scipy.interpolate import interp1d

# 设置中文字体支持
matplotlib.rcParams['font.sans-serif'] = ['Microsoft YaHei', 'SimHei', 'SimSun', 'DejaVu Sans', 'Arial Unicode MS', 'sans-serif']
matplotlib.rcParams['axes.unicode_minus'] = False  # 用来正常显示负号

def load_csv_data(csv_file_path):
    """
    加载CSV文件并返回DataFrame，并对cpuUsage进行平滑处理
    """
    try:
        # 读取CSV文件
        df = pd.read_csv(csv_file_path)
        
        # 将时间戳转换为日期时间格式
        df['datetime'] = pd.to_datetime(df['timestamp'], unit='ms')
        
        # 对cpuUsage进行移动中位数平滑处理，窗口大小为5
        # center=True 会在窗口的中心计算中位数
        # min_periods=1 确保即使窗口未满也能计算
        if 'cpuUsage' in df.columns:
            df['cpuUsage'] = df['cpuUsage'].rolling(window=5, center=True, min_periods=1).median()
            # 填充可能产生的NaN值：先用后面的有效值填充 (bfill)，再用前面的有效值填充 (ffill)
            # 这样可以确保首尾的数据点也被合理处理
            df['cpuUsage'] = df['cpuUsage'].fillna(method='bfill').fillna(method='ffill')
        
        return df
    except Exception as e:
        print(f"加载CSV文件时出错: {e}")
        return None

def visualize_metrics(df, title, output_dir):
    """
    创建资源指标的可视化图表 - 所有指标在同一张图表中显示
    """
    # 设置Seaborn样式
    sns.set(style="darkgrid")
    
    # 创建一个图表
    plt.figure(figsize=(12, 8))
    plt.title(f"{title} Performance Test Resource Metrics", fontsize=16)
    
    # 将 datetime 转换为 matplotlib 可识别的数值格式
    df['datetime_num'] = mdates.date2num(df['datetime'])

    # 定义一个辅助函数来绘制平滑曲线
    def plot_smooth(x, y, label, color, linestyle):
        # 确保数据点足够进行插值
        if len(x) > 3:  # 三次样条至少需要4个点
            f_interp = interp1d(x, y, kind='cubic', fill_value="extrapolate")
            x_smooth = np.linspace(x.min(), x.max(), 300) # 生成更密集的点
            y_smooth = f_interp(x_smooth)
            plt.plot(x_smooth, y_smooth, label=label, color=color, linestyle=linestyle)
        else: # 如果点太少，则直接画线
            plt.plot(x, y, label=label, color=color, linestyle=linestyle)

    # 在同一张图表上绘制三个指标，使用不同线型以便黑白打印区分
    # 使用原始的 df['datetime'] (会被自动转换为数值) 或 df['datetime_num']
    plot_smooth(df['datetime_num'], df['cpuUsage'], label='CPU Usage', color='red', linestyle='-')
    plot_smooth(df['datetime_num'], df['systemMemoryUsage'], label='System Memory Usage', color='blue', linestyle='--')
    plot_smooth(df['datetime_num'], df['jvmMemoryUsage'], label='JVM Memory Usage', color='green', linestyle='-.')
    
    # 设置图表属性
    plt.ylabel('Usage (0-1)')
    plt.ylim(0, 1.1)
    plt.xlabel('Time')
    plt.legend()
    plt.grid(True, linestyle='--', alpha=0.7)
    
    # 配置X轴为时间格式
    plt.gca().xaxis.set_major_formatter(mdates.DateFormatter('%H:%M:%S'))
    
    # 调整布局
    plt.tight_layout()
    
    # 保存图表
    save_charts(plt.gcf(), title, output_dir)
    
    return plt.gcf()

def save_charts(fig, title, output_dir):
    """
    保存图表为PNG格式
    """
    # 创建输出目录（如果不存在）
    os.makedirs(output_dir, exist_ok=True)
    
    # 设置文件名（不含时间戳）
    filename = f"{title}.png"
    filepath = os.path.join(output_dir, filename)
    
    # 如果文件已存在，则先删除
    if os.path.exists(filepath):
        os.remove(filepath)
        print(f"已删除旧文件: {filepath}")
    
    # 保存为PNG（适合网页和演示）
    fig.savefig(filepath, dpi=300, bbox_inches='tight')
    
    print(f"已保存图表到: {filepath}")

def main():
    # 解析命令行参数
    parser = argparse.ArgumentParser(description='JMeter Resource Metrics Visualization Tool')
    parser.add_argument('--light', type=str, help='轻负载CSV文件路径')
    parser.add_argument('--medium', type=str, help='中负载CSV文件路径')
    parser.add_argument('--heavy', type=str, help='重负载CSV文件路径')
    parser.add_argument('--output', type=str, default='charts', help='图表输出目录')
    
    args = parser.parse_args()
    
    # 设置默认路径
    # 旧的 base_dir，当脚本在 analysis 目录执行时，此路径会解析错误
    # base_dir = 'src/main/java/com/multiplethread/jmeter/results'
    # 新的 base_dir, 相对于 analysis 目录，向上到 com/multiplethread，然后进入 jmeter/results
    base_dir = '../jmeter/results' 
    output_dir = args.output
    
    # 如果未提供CSV文件路径，使用默认路径
    light_csv = args.light or f"{base_dir}/light/jmeter_resource_metrics.csv"
    medium_csv = args.medium or f"{base_dir}/medium/jmeter_resource_metrics.csv"
    heavy_csv = args.heavy or f"{base_dir}/heavy/jmeter_resource_metrics.csv"
    
    # 创建输出目录
    os.makedirs(output_dir, exist_ok=True)
    
    # 加载数据
    light_df = load_csv_data(light_csv) if os.path.exists(light_csv) else None
    medium_df = load_csv_data(medium_csv) if os.path.exists(medium_csv) else None
    heavy_df = load_csv_data(heavy_csv) if os.path.exists(heavy_csv) else None
    
    # 生成各种负载的单独图表
    if light_df is not None:
        visualize_metrics(light_df, "Light Load", output_dir)
        print(f"Light Load chart generated")
    
    if medium_df is not None:
        visualize_metrics(medium_df, "Medium Load", output_dir)
        print(f"Medium Load chart generated")
    
    if heavy_df is not None:
        visualize_metrics(heavy_df, "Heavy Load", output_dir)
        print(f"Heavy Load chart generated")
    
    print(f"All charts have been generated to directory: {output_dir}")

if __name__ == "__main__":
    main() 