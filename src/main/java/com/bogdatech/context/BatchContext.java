package com.bogdatech.context;

import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
public class BatchContext extends TranslateContext {
    private Map<Integer, String> batchUncachedTextMap;
    private Map<Integer, String> batchTranslatedTextMap;
    private Integer cachedCount;

    public static BatchContext startBatchTranslate(Map<Integer, String> batchOriginalTextMap,
                                                   String targetLanguage) {
        BatchContext context = new BatchContext();
        context.batchOriginalTextMap = batchOriginalTextMap;
        context.batchUncachedTextMap = new HashMap<>();
        context.batchTranslatedTextMap = new HashMap<>();
        context.cachedCount = 0;

        context.setTargetLanguage(targetLanguage);
        context.setStartTime(System.currentTimeMillis());
        return context;
    }
}
