package com.bogdatech.utils;

import java.util.regex.Pattern;

import static com.bogdatech.utils.JudgeTranslateUtils.printTranslateReason;

public class RegularJudgmentUtils {
    // 正则表达式：只包含字母、数字和标点符号
    private static final Pattern ALPHA_NUM_PUNCT_PATTERN = Pattern.compile("^[a-zA-Z0-9\\p{Punct}]+$");
    // 正则表达式：只包含数字和标点符号
    private static final Pattern NUM_PUNCT_PATTERN = Pattern.compile("^[0-9\\p{P}]+$");
    // 正则表达式：纯数字
    private static final Pattern NUM_PATTERN = Pattern.compile("^[0-9]+$");
    // 正则表达式：匹配标点符号
    private static final Pattern PUNCT_PATTERN = Pattern.compile("[\\p{Punct}]");
    // 正则表达式：JSON键值对
    private static final String JSON_KEY_VALUE_PATTERN = "\":";

    /**
     * 判断文本中是否是纯数字字母符号且有两个标点符号
     * @param input 输入的文本
     * @return 如果包含返回 false
     */
    public static boolean isValidString(String input) {
        if (input == null) {
            return false;
        }
        // 第一步：检查是否是JSON， 只包含字母、数字和标点符号. 数字和标点符号。 纯数字 。 纯标点符号
        if (input.contains("{\"") && input.contains("}")) {
            printTranslateReason("是json数据");
            return true;
        }
        if (!ALPHA_NUM_PUNCT_PATTERN.matcher(input).matches()) {
            printTranslateReason("只包含字母、数字和标点符号. 数字和标点符号");
            return false;
        }

        if (NUM_PUNCT_PATTERN.matcher(input).matches()){
            printTranslateReason("数字和标点符号");
            return true;
        }
        if(NUM_PATTERN.matcher(input).matches()) {
            printTranslateReason("纯数字");
            return true;
        }
        if (input.startsWith("#") && input.length() <= 10){
            printTranslateReason("以#开头，长度为10");
            return true;
        }
        // 第二步：统计标点符号数量
        long punctCount = PUNCT_PATTERN.matcher(input).results().count();
        return punctCount >= 2;
    }
}
