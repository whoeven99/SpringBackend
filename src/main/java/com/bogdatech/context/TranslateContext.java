package com.bogdatech.context;

import com.bogdatech.entity.DO.GlossaryDO;
import com.bogdatech.utils.JsonUtils;

import java.util.HashMap;
import java.util.Map;

@Data
public class TranslateContext {
    // Start
    private String content;
    private String targetLanguage;
    private Long startTime;

    // Calculate
    private Map<String, GlossaryDO> glossaryMap;
    private boolean hasGlossary;
    private boolean isCached;
    private ContentTypeEnum contentType; // 系统内判断的翻译类型
    private HtmlContext htmlContext = null;
    private String prompt;

    // Finish
    private Long endTime;
    private Integer usedToken;
    private String translatedContent;

    private Long translatedTime;

    public class HtmlContext {
        private Map<Integer, String> originalTextMap;
        private Document doc;
        private Map<Integer, TextNode> nodeMap = new HashMap<>();
        private Map<Integer, String> translatedTextMap;
//        private String replaceBackContent;
    }

    // 批量翻译的数据
    private Map<Integer, String> batchOriginalTextMap;
    private Map<Integer, String> batchUncachedTextMap;
    private Map<Integer, String> batchTranslatedTextMap;

    public TranslateContext startNewTranslate(String content, String targetLanguage) {
        TranslateContext context = new TranslateContext();
        context.content = content;
        context.targetLanguage = targetLanguage;
        context.startTime = System.currentTimeMillis();
        return context;
    }

    public TranslateContext startBatchTranslate(Map<Integer, String> batchOriginalTextMap, String targetLanguage) {
        TranslateContext context = new TranslateContext();
        context.batchOriginalTextMap = batchOriginalTextMap;
        context.batchUncachedTextMap = new HashMap<>();
        context.batchTranslatedTextMap = new HashMap<>();
        context.targetLanguage = targetLanguage;
        context.startTime = System.currentTimeMillis();
        return context;
    }

    public String getJsonRecord() {
        TranslateContext context = new TranslateContext();
        context.isCached = this.isCached;
        context.usedToken = this.usedToken;
        context.translatedTime = this.getTranslateTime();
        context.hasGlossary = this.hasGlossary;
        return JsonUtils.objectToJson(context);
    }

    public long getTranslateTime() {
        if (startTime == null) return 0;
        long end = endTime != null ? endTime : System.currentTimeMillis();
        return (end - startTime) / 1000;
    }

    public enum ContentTypeEnum {
        STARTED("20字符以内"),
        RUNNING("handle字段"),
        COMPLETED("html"),
        FAILED(""),
        CANCELLED,
        SUSPENDED
    }
}


