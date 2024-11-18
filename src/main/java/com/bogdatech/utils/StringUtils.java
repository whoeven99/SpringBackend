package com.bogdatech.utils;

public class StringUtils {
    /**
     * 将字符串中的空格替换为指定字符。
     *
     * @param input 原始字符串
     * @param replacement 用于替换空格的字符
     * @return 替换后的字符串
     */
    public static String replaceSpaces(String input, String replacement) {
        if (input == null) {
            return null;
        }
        return input.replaceAll("\\s+", replacement);
    }
}
