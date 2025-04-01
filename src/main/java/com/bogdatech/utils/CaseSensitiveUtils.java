package com.bogdatech.utils;


import com.bogdatech.model.service.KeywordModel;
import com.microsoft.applicationinsights.TelemetryClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class CaseSensitiveUtils {
    public static TelemetryClient appInsights = new TelemetryClient();

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
    public static String extractKeywords(String text, Map<String, String> placeholders, Map<String, String> keywordTranslationMap, Map<String, String> keyMap0, String source) {
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
        String targetText = text;
        for (KeywordModel entry : allKeywords) {
            appInsights.trackTrace("keyword: " + entry.keyword);
            String keyword = entry.keyword;
            String placeholder = "#_" + i++;
            placeholders.put(placeholder, entry.translation); // 存储翻译

            // 根据是否区分大小写选择替换方式
            if (entry.caseSensitive) {
                // 区分大小写，使用原始关键词
                targetText = text.replaceAll("\\b" + Pattern.quote(keyword) + "\\b", placeholder);
                if (targetText.equals(text)) {
                    targetText = text.replaceAll(Pattern.quote(keyword), placeholder);
                }

                appInsights.trackTrace("text1: " + text);
            } else {
                // 不区分大小写，使用 (?i) 标志
                targetText = text.replaceAll("(?i)\\b" + Pattern.quote(keyword) + "\\b", placeholder);
                if (targetText.equals(text)) {
                    targetText = text.replaceAll(Pattern.quote(keyword.toLowerCase()), placeholder);
                }

                appInsights.trackTrace("text0: " + targetText);
            }
        }

        return targetText;
    }

    // 将占位符还原为关键词
    public static String restoreKeywords(String translatedText, Map<String, String> placeholders) {
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            translatedText = translatedText.replace(entry.getKey(), entry.getValue());
        }
        return translatedText;
    }


}
