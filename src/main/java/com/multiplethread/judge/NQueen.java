package com.multiplethread.judge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * N皇后问题解法
 * 相关文档: docs/modules/2023-12-21-NQueen-optimization.md
 */
@Component
public class NQueen {
    
    private static final Logger log = LoggerFactory.getLogger(NQueen.class);
    
    // 定义检查中断的频率（毎执行多少次递归检查一次中断状态）
    private static final int INTERRUPT_CHECK_FREQUENCY = 1000;
    
    // 用于计数递归调用次数
    private int recursionCounter = 0;

    /**
     * 运行N皇后算法
     * @param n 皇后数量
     * @return 解的数量，如果被中断则返回-1
     */
    public int run(int n){
        if (n < 0){
            return 0;
        }
        
        // 重置递归计数器
        recursionCounter = 0;
        
        try {
            return func(0, new int[n], n);
        } catch (InterruptedException e) {
            log.warn("N皇后算法执行被中断，n={}", n);
            // 设置中断标志，确保调用者能感知到中断
            Thread.currentThread().interrupt();
            // 返回-1表示被中断
            return -1;
        }
    }

    /**
     * N皇后递归算法，增加中断检测
     * @param row 当前行
     * @param queen 皇后放置位置
     * @param n 皇后数量
     * @return 解的数量
     * @throws InterruptedException 当线程被中断时抛出
     */
    private int func(int row, int[] queen, int n) throws InterruptedException {
        // 周期性检查中断状态
        if (++recursionCounter % INTERRUPT_CHECK_FREQUENCY == 0) {
            if (Thread.currentThread().isInterrupted()) {
                log.debug("检测到中断信号，停止N皇后计算，当前row={}, n={}", row, n);
                throw new InterruptedException("N皇后计算被中断");
            }
        }
        
        if (row == n){
            return 1;
        }
        
        int res = 0;
        for (int i = 0; i < n; i++){
            queen[row] = i;
            if (judge(queen, row)){
                res += func(row+1, queen, n);
            }
        }
        return res;
    }

    private boolean judge(int[] queen, int row){
        for (int i = 0; i < row; i++){
            if (queen[i] == queen[row] || Math.abs(queen[i] - queen[row]) == row - i){
                return false;
            }
        }
        return true;
    }
}
