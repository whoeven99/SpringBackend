package com.bogdatech.utils;


import java.util.Map;
import java.util.regex.Pattern;

public class CaseSensitiveUtils {

    //区分大小写
    public static boolean containsValue(String text, String value) {
        if (text == null || value == null) {
            return false;
        }
        return text.contains(value);
    }

    //不区分大小写
    public static boolean containsValueIgnoreCase(String text, String value) {
        if (text == null || value == null) {
            return false;
        }
        return text.toLowerCase().contains(value.toLowerCase());
    }

    // 替换关键词为占位符
    public static String extractKeywords(String text, Map<String, String> placeholders, Map<String, String> keywordTranslationMap, Map<String, String> keyMap0) {
        int i = 0;
        for (Map.Entry<String, String> entry : keywordTranslationMap.entrySet()) {
            String keyword = entry.getKey();
            String placeholder = "#_" + i++;
            placeholders.put(placeholder, entry.getValue()); // 使用固定翻译
            text = text.replaceAll("\\b" + keyword + "\\b", placeholder);
        }
        for (Map.Entry<String, String> entry : keyMap0.entrySet()) {
            String keyword = entry.getKey();
            String placeholder = "#_" + i++;
            placeholders.put(placeholder, entry.getValue()); // 使用固定翻译
            // 使用 (?i) 标志来进行不区分大小写的替换
            text = text.replaceAll("(?i)\\b" + Pattern.quote(keyword) + "\\b", placeholder);
        }
        System.out.println("text: " + text);
        return text;
    }

    // 将占位符还原为关键词
    public static String restoreKeywords(String translatedText, Map<String, String> placeholders) {
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            translatedText = translatedText.replace(entry.getKey(), entry.getValue());
            System.out.println("translatedText: " + translatedText);
        }
        return translatedText;
    }



}
