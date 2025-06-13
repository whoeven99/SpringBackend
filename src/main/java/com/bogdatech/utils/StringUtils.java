package com.bogdatech.utils;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
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

    //对prompt_word的文本进行处理，将第一个{{Chinese}}替换成target
    public static String replaceLanguage(String prompt, String target, String translateResourceType, String industry) {
        prompt = prompt.replaceFirst("\\{\\{apparel\\}\\}", industry);
        prompt = prompt.replaceFirst("\\{\\{product\\}\\}", translateResourceType);
        return prompt.replaceFirst("\\{\\{Chinese\\}\\}", target);
    }

    //计算一段文本中的单词数
    public static int countWords(String text) {
        if (text == null || text.trim().isEmpty()) {
            return 0;
        }
        // 用正则表达式匹配单词
        String[] words = text.trim().split("[\\s\\p{Punct}]+");
//        System.out.println("words: " + Arrays.toString(words));
        return words.length;
    }

    //将传入的文本的.替换成-
     public static String replaceDot(String text) {
        return text.replace(".", "-");
     }


     //判断字符串是否为纯数字,小数或负数
     public static boolean isNumber(String value) {
         if (value == null) {
             return false;
         }
         // 正则表达式支持：正整数、负整数、零、正小数、负小数
         return value.matches("-?\\d+(\\.\\d+)?");
     }

    /**
     * 解析shopName的数据，去掉后缀
     * @param shopName
     * @return parseShopName 去掉后缀的parseShopName
     * */
    public static String parseShopName(String shopName) {
        // 定义要移除的后缀
        String suffix = ".myshopify.com";
        return  shopName.substring(0, shopName.length() - suffix.length());
    }


    /**
     * 判断白名单内的语言代码是否有大小写，且大小写状态是什么，返回Boolean值
     * */
    public static Boolean judgeLanguageUpperCaseCode(String languageCode, String text) {
        //白名单判断
        if (!languageCodeSet.contains(languageCode)) {
            return false;
        }

        return text.equals(text.toUpperCase());
    }

    // 名单内的语言代码
    private static final Set<String> languageCodeSet = new HashSet<>(Arrays.asList(
            "en", "fr", "es", "it", "pt", "nl", "cs", "pl", "sv", "no", "da", "fi", "vi", "tr", "ro"
    ));


    /**
     * 将判断要大写的用户， 将小写转换成大写， de 和 tr 要单独处理
     * */
    public static String convertLanguageCodeToUpperCase(String languageCode, String text) {
        if (languageCode.equals("tr")) {
            return text.toUpperCase(new Locale("tr", "TR"));
        }

        return text.toUpperCase();
    }

    /**
     * 将string类型数据的换行符等改为空格
     * */
    public static String normalizeHtml(String html) {
        if (html == null) {
            return null;
        }
        // 1. 替换换行符为空格
        html = html.replaceAll("\\r?\\n", " ");

        // 2. 将多个连续空格替换为单个空格
        html = html.replaceAll("\\s{2,}", " ");

        return html.trim();
    }

    /**
     * 将-替换为空格
     * */
    public static String replaceHyphensWithSpaces(String input) {
        if (input == null) {
            return null;
        }
        return input.replace("-", " ");
    }
}
