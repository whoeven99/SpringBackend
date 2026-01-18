package com.bogda.common;

import com.bogda.common.entity.DO.GlossaryDO;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.TextNode;

import java.util.HashMap;
import java.util.Map;

@Data
@NoArgsConstructor
public class TranslateContext {
    // Start
    private String content;

    private String shopifyTextType;
    private String shopifyTextKey;
    private String targetLanguage;
    private Long startTime;

    // Calculate
    private Map<String, GlossaryDO> glossaryMap = new HashMap<>();
    private boolean isCached;
    private String strategy; // 系统内判断的翻译类型
    private String prompt;

    // Translate Model
    private String aiModel;

    // Batch
    private Map<Integer, String> originalTextMap = new HashMap<>();
    private Map<Integer, String> glossaryTextMap = new HashMap<>();
    private Map<String, GlossaryDO> usedGlossaryMap = new HashMap<>();
    private Map<Integer, String> uncachedTextMap = new HashMap<>();
    private Map<Integer, String> translatedTextMap = new HashMap<>();
    private int cachedCount;
    private int glossaryCount;

    // Finish
    private int usedToken;
    private int translatedChars;
    private String translatedContent;

    private Long translatedTime;

    private Map<String, String> translateVariables = new HashMap<>();

    // For html
    public TranslateContext(String content, String targetLanguage, Map<String, GlossaryDO> glossaryMap, String aiModel) {
        this.content = content;
        this.targetLanguage = targetLanguage;
        this.glossaryMap = glossaryMap;

        this.startTime = System.currentTimeMillis();
        this.translatedChars = content.length();
        this.aiModel = aiModel;
    }

    // For single
    public TranslateContext(String content, String targetLanguage, String type, String key,
                            Map<String, GlossaryDO> glossaryMap, String aiModel) {
        this.content = content;
        this.targetLanguage = targetLanguage;
        this.shopifyTextType = type;
        this.shopifyTextKey = key;
        this.glossaryMap = glossaryMap;
        this.startTime = System.currentTimeMillis();
        this.translatedChars = content.length();
        this.aiModel = aiModel;
    }

    // For batch
    public TranslateContext(Map<Integer, String> batchOriginalTextMap,
                            String targetLanguage, Map<String, GlossaryDO> glossaryMap, String aiModel) {
        this.originalTextMap = batchOriginalTextMap;
        this.targetLanguage = targetLanguage;
        this.glossaryMap = glossaryMap;

        int totalChars = 0;
        for (String value : batchOriginalTextMap.values()) {
            if (value != null) {
                totalChars += value.length();
            }
        }
        this.translatedChars = totalChars;
        this.startTime = System.currentTimeMillis();
        this.aiModel = aiModel;
    }

    private Document doc;
    boolean hasHtmlTag;
    private Map<Integer, TextNode> nodeMap = new HashMap<>();

    public void finish() {
        this.translatedTime = (System.currentTimeMillis() - startTime) / 1000;
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
