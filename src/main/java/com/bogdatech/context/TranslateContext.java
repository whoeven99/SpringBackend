package com.bogdatech.context;

import com.bogdatech.entity.DO.GlossaryDO;
import lombok.Data;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.TextNode;

import java.util.HashMap;
import java.util.Map;

@Data
public class TranslateContext {
    // Start
    private String content;

    private String shopifyTextType;
    private String shopifyTextKey;
    private String targetLanguage;
    private Long startTime;

    // Calculate
    private Map<String, GlossaryDO> glossaryMap;
    private boolean isCached;
    private String strategy; // 系统内判断的翻译类型
    private String prompt;

    // Batch
    private Map<Integer, String> originalTextMap = new HashMap<>();
    private Map<Integer, String> glossaryTextMap = new HashMap<>();
    private Map<String, GlossaryDO> usedGlossaryMap = new HashMap<>();
    private Map<Integer, String> uncachedTextMap = new HashMap<>();
    private Map<Integer, String> translatedTextMap = new HashMap<>();
    private int cachedCount;
    private int glossaryCount;

    // Finish
    private Long endTime;
    private int usedToken;
    private int translatedChars;
    private String translatedContent;

    private Long translatedTime;

    private Map<String, String> translateVariables;

    public static TranslateContext startNewTranslate(String content, String targetLanguage, String type, String key) {
        TranslateContext context = new TranslateContext();
        context.content = content;
        context.targetLanguage = targetLanguage;
        context.shopifyTextType = type;
        context.shopifyTextKey = key;
        context.startTime = System.currentTimeMillis();
        context.translatedChars = content.length();
        return context;
    }

    public static TranslateContext startBatchTranslate(Map<Integer, String> batchOriginalTextMap,
                                                   String targetLanguage) {
        TranslateContext context = new TranslateContext();
        context.originalTextMap = batchOriginalTextMap;
        context.cachedCount = 0;

        int totalChars = 0;
        for (String value : batchOriginalTextMap.values()) {
            if (value != null) {
                totalChars += value.length();
            }
        }
        context.setTranslatedChars(totalChars);

        context.setTargetLanguage(targetLanguage);
        context.setStartTime(System.currentTimeMillis());
        return context;
    }

    private Document doc;
    boolean hasHtmlTag;
    private Map<Integer, TextNode> nodeMap = new HashMap<>();

    public void finish() {
        this.endTime = System.currentTimeMillis();
        this.translatedTime = this.getTranslateTime();
    }

    public long getTranslateTime() {
        if (startTime == null) return 0;
        long end = endTime != null ? endTime : System.currentTimeMillis();
        return (end - startTime) / 1000;
    }

    public void incrementCachedCount() {
        this.cachedCount++;
    }

    public void incrementGlossaryCount() {
        this.glossaryCount++;
    }

    public void incrementUsedTokenCount(int count) {
        this.usedToken += count;
    }
}
