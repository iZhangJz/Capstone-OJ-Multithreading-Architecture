package com.multiplethread.model;

/**
 * 线程池参数配置类
 */
public class ThreadPoolArgs {
    private final int corePoolSize;
    private final int maximumPoolSize;
    private final long keepAliveTime; // 单位：秒
    private final int queueCapacity;

    public ThreadPoolArgs(int corePoolSize, int maximumPoolSize, long keepAliveTime, int queueCapacity) {
        this.corePoolSize = corePoolSize;
        this.maximumPoolSize = maximumPoolSize;
        this.keepAliveTime = keepAliveTime;
        this.queueCapacity = queueCapacity;
    }

    // Getters
    public int getCorePoolSize() { return corePoolSize; }
    public int getMaximumPoolSize() { return maximumPoolSize; }
    public long getKeepAliveTime() { return keepAliveTime; }
    public int getQueueCapacity() { return queueCapacity; }

    // 预定义配置 (保留现有，新增 DYNAMIC_INITIAL)
    public static final ThreadPoolArgs FIXED_SMALL = new ThreadPoolArgs(2, 2, 60L, 100);
    public static final ThreadPoolArgs FIXED_MEDIUM = new ThreadPoolArgs(4, 4, 60L, 100);
    public static final ThreadPoolArgs FIXED_LARGE = new ThreadPoolArgs(8, 8, 60L, 100);
    public static final ThreadPoolArgs CACHED_SMALL = new ThreadPoolArgs(0, Integer.MAX_VALUE, 60L, 0); // 注意：Cached 通常用 SynchronousQueue，这里用 0 capacity 模拟
    public static final ThreadPoolArgs CACHED_MEDIUM = new ThreadPoolArgs(0, Integer.MAX_VALUE, 60L, 0);
    public static final ThreadPoolArgs CACHED_LARGE = new ThreadPoolArgs(0, Integer.MAX_VALUE, 60L, 0);

    // 动态线程池的初始配置 (可以基于 CPU 核心数)
    private static final int CPU_CORES = Runtime.getRuntime().availableProcessors();
    // 初始核心数可以设为 CPU 的一半，最大设为 CPU 核心数，队列容量给一个初始值
    public static final ThreadPoolArgs DYNAMIC_INITIAL = new ThreadPoolArgs(
            Math.max(1, CPU_CORES / 2), // 核心数至少为 1
            CPU_CORES,                 // 最大线程数等于 CPU 核心数
            60L,// KeepAlive 时间
            100
    );
}
