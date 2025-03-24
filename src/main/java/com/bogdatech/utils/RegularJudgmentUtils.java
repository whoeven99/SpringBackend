package com.bogdatech.utils;

import java.util.regex.Pattern;

public class RegularJudgmentUtils {
    // 正则表达式：只包含字母、数字和标点符号
    private static final Pattern ALPHA_NUM_PUNCT_PATTERN = Pattern.compile("^[a-zA-Z0-9\\p{Punct}]+$");
    // 正则表达式：匹配标点符号
    private static final Pattern PUNCT_PATTERN = Pattern.compile("[\\p{Punct}]");

    /**
     * 判断文本中是否是纯数字字母符号且有两个标点符号
     * @param input 输入的文本
     * @return 如果包含返回 false
     */
    public static boolean isValidString(String input) {
        if (input == null) {
            return false;
        }
        // 第一步：检查是否只包含字母、数字和标点符号
        if (!ALPHA_NUM_PUNCT_PATTERN.matcher(input).matches()) {
            return false;
        }
        // 第二步：统计标点符号数量
        long punctCount = PUNCT_PATTERN.matcher(input).results().count();
        return punctCount >= 2;
    }
}
