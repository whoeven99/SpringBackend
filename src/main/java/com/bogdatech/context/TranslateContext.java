package com.bogdatech.context;

import com.bogdatech.entity.DO.GlossaryDO;
import com.bogdatech.utils.JsonUtils;
import lombok.Data;

import java.util.Map;

@Data
public class TranslateContext {
    // Start
    private String content;
    private Map<Integer, String> batchOriginalTextMap;

    private String shopifyTextType;
    private String shopifyTextKey;
    private String targetLanguage;
    private Long startTime;

    // Calculate
    private Map<String, GlossaryDO> glossaryMap;
    private boolean hasGlossary;
    private String glossaryReplaceContent;
    private String glossaryReplaceBackContent;
    private boolean isCached;
    private String strategy; // 系统内判断的翻译类型
    private String prompt;

    // Finish
    private Long endTime;
    private Integer usedToken;
    private Integer translatedChars;
    private String translatedContent;

    private Long translatedTime;

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

    public void finish() {
        this.endTime = System.currentTimeMillis();
        this.translatedTime = this.getTranslateTime();
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

//    public enum TranslateStrategyEnum {
//        PLAIN_TEXT("小于20个字符"),
//        TITLE("大于20个字符，纯文本"),
//        META_TITLE("html"),
//        LOWERCASE_HANDLE("json"),
//        LIST_SINGLE("handle"),
//        ;
//
//        private final String desc;
//
//        TranslateStrategyEnum(String desc) {
//            this.desc = desc;
//        }
//    }
}


