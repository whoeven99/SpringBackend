package com.bogdatech.utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.bogdatech.constants.TranslateConstants.*;

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

    //判断字符串是否包含指定模式
    private static boolean containsPattern(String str, String patternStr) {
        Pattern pattern = Pattern.compile(patternStr);
        Matcher matcher = pattern.matcher(str);
        return matcher.find();
    }

    //判断string的类型，是否包含{{ }}，%{} ，{{xx[x].xx}}，{% %}等
    public static String judgeStringType(String str){
        // 检查 {{xx[x].xx}}
        if (containsPattern(str, "\\{\\{.*?\\[.*?\\]\\..*?\\}\\}")) {
            return CURLY_BRACKET_ARRAY;
        }
        // 检查 {{ }}
        if (containsPattern(str, "\\{\\{.*?\\}\\}")) {
            return DOUBLE_BRACES;
        }

        // 检查 %{ }
        if (containsPattern(str, "%\\{.*?\\}")) {
            return PERCENTAGE_CURLY_BRACES;
        }

        // 检查 {% %}
        if (containsPattern(str, "\\{\\%.*?\\%\\}")) {
            return DOUBLE_CURLY_BRACKET_AND_HUNDRED;
        }
        return PLAIN_TEXT;
    }
}
