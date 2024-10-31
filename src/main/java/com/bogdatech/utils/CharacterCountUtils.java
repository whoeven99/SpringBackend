package com.bogdatech.utils;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

@Component
public class CharacterCountUtils {
    //计数器
    private AtomicInteger totalChars = new AtomicInteger(0); // 初始化为0

    public void addChars(int chars) {
        totalChars.addAndGet(chars); // 原子性地增加字符数
    }
    //原子性地减少字符数
    public void subtractChars(int chars) {
        totalChars.addAndGet(-chars); // 原子性地减少字符数
    }
    public int getTotalChars() {
        return totalChars.get(); // 获取当前总的字符数
    }

    public void reset() {
        totalChars.set(0); // 重置计数器
    }


}
