package com.bogdatech.utils;


import com.bogdatech.model.service.KeywordModel;

import java.util.ArrayList;
import java.util.List;
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
        List<KeywordModel> allKeywords = new ArrayList<>();
        for (Map.Entry<String, String> entry : keywordTranslationMap.entrySet()) {
            allKeywords.add(new KeywordModel(entry.getKey(), entry.getValue(), true));
        }
        for (Map.Entry<String, String> entry : keyMap0.entrySet()) {
            allKeywords.add(new KeywordModel(entry.getKey(), entry.getValue(), false));
        }

        // 按关键词长度从长到短排序，若长度相同则保持原有顺序
        allKeywords.sort((a, b) -> {
            int lenCompare = Integer.compare(b.keyword.length(), a.keyword.length());
            return lenCompare != 0 ? lenCompare : 0; // 长度不同时，长者优先
        });

        // 依次替换关键词
        int i = 0;
        for (KeywordModel entry : allKeywords) {
            String keyword = entry.keyword;
            String placeholder = "[x" + i++ + "]";
            placeholders.put(placeholder, entry.translation); // 存储翻译

            // 根据是否区分大小写选择替换方式
            if (entry.caseSensitive) {
                // 区分大小写，使用原始关键词
                text = text.replaceAll("\\b" + Pattern.quote(keyword) + "\\b", placeholder);
            } else {
                // 不区分大小写，使用 (?i) 标志
                text = text.replaceAll("(?i)\\b" + Pattern.quote(keyword) + "\\b", placeholder);
            }
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
