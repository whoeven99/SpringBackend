package com.bogdatech.utils;


import java.util.Map;

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
    public static String extractKeywords(String text, Map<String, String> placeholders, Map<String, String> keywordTranslationMap) {
        int i = 0;
        for (Map.Entry<String, String> entry : keywordTranslationMap.entrySet()) {
            String keyword = entry.getKey();
            String placeholder = "#_" + i++;
            placeholders.put(placeholder, entry.getValue()); // 使用固定翻译
            text = text.replaceAll("\\b" + keyword + "\\b", placeholder);
        }
        return text;
    }

    // 将占位符还原为关键词
    public static String restoreKeywords(String translatedText, Map<String, String> placeholders) {
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            translatedText = translatedText.replace(entry.getKey(), entry.getValue());
        }
        return translatedText;
    }



}
