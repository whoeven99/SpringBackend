package com.bogdatech.utils;


import com.bogdatech.entity.VO.KeywordVO;
import com.microsoft.applicationinsights.TelemetryClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class CaseSensitiveUtils {
    public static TelemetryClient appInsights = new TelemetryClient();

    // 区分大小写
    public static boolean containsValue(String text, String value) {
        if (text == null || value == null) {
            return false;
        }
        return text.contains(value);
    }

    // 不区分大小写
    public static boolean containsValueIgnoreCase(String text, String value) {
        if (text == null || value == null) {
            return false;
        }
        return text.toLowerCase().contains(value.toLowerCase());
    }

    /**
     * 将key0和key1的值放到一个集合里面，并按长度顺序排序
     *
     * */
     public static List<KeywordVO> mergeKeywordMap(Map<String, String> keyMap0, Map<String, String> keyMap1) {
         List<KeywordVO> allKeywords = new ArrayList<>();
         for (Map.Entry<String, String> entry : keyMap1.entrySet()) {
             allKeywords.add(new KeywordVO(entry.getKey(), entry.getValue(), true));
         }
         for (Map.Entry<String, String> entry : keyMap0.entrySet()) {
             allKeywords.add(new KeywordVO(entry.getKey(), entry.getValue(), false));
         }

         // 按关键词长度从长到短排序，若长度相同则保持原有顺序
         allKeywords.sort((a, b) -> {
             int lenCompare = Integer.compare(b.keyword.length(), a.keyword.length());
             return lenCompare != 0 ? lenCompare : 0; // 长度不同时，长者优先
         });
         return allKeywords;
     }
}
