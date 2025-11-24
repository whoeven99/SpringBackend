package com.bogdatech.context;

import java.util.HashMap;
import java.util.Map;

public class TranslateTaskContext {
    // Start
    private Integer initialTaskId;
    private String shopName;
    private String targetLanguage;
    private Long startTime;

    // Reading shopify
    private Integer taskCount;
    private Long initTaskEndTime;

    // Translating
    private Integer translatedTaskCount;
    private Long translatedTaskEndTime;

    // Saving shopify
    private Integer savingTaskCount;
    private Long savingTaskEndTime;

    private boolean sendEmail;

    // Calculate
    private Integer status;
    private Integer usedToken;
    private Long translatedChars;
    private Integer cachedCount;

    public Map<Integer, Integer> getTranslateProgress() {
        Map<Integer, Integer> translateProgress = new HashMap<>();
        translateProgress.put(translatedTaskCount, taskCount);
        return translateProgress;
    }

    public Map<Integer, Integer> getSavingShopifyProgress() {
        Map<Integer, Integer> map = new HashMap<>();
        map.put(savingTaskCount, taskCount);
        return map;
    }

    public TranslateTaskContext startNewTranslate(Integer initialTaskId) {
        TranslateTaskContext context = new TranslateTaskContext();
        context.initialTaskId = initialTaskId;
        context.startTime = System.currentTimeMillis();
        return context;
    }
}
