package com.bogdatech.utils;

public class CalculateTokenUtils {
    // 对google计数的专门规则
    /**
     * 计算输入字符串的单词数
     * @param value 输入的字符串
     * @return 单词数量
     */
    public static int googleCalculateToken(String value){
        // 检查空值
        if (value == null || value.trim().isEmpty()) {
            return 0;
        }

        // 按空白字符分割并过滤空字符串
        String[] words = value.trim().split("\\s+");

        return words.length * 2;
    }
}
