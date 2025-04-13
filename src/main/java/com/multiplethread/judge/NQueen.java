package com.multiplethread.judge;

import org.springframework.stereotype.Component;

@Component
public class NQueen {

    public int run(int n){
        if (n < 0){
            return 0;
        }
        return func(0,new int[n],n);
    }

    private int func(int row,int[] queen,int n){
        if (row == n){
            return 1;
        }
        int res = 0;
        for (int i = 0; i < n; i++){
            queen[row] = i;
            if (judge(queen,row)){
                res += func(row+1,queen,n);
            }
        }
        return res;
    }

    private boolean judge(int[] queen,int row){
        for (int i = 0; i < row; i++){
            if (queen[i] == queen[row] || Math.abs(queen[i] - queen[row]) == row - i){
                return false;
            }
        }
        return true;
    }
}
