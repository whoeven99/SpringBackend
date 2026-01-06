package com.bogda.api.logic.translate;

import com.bogda.api.integration.ALiYunTranslateIntegration;
import com.bogda.api.integration.ChatGptIntegration;
import com.bogda.api.integration.GeminiIntegration;
import com.bogda.api.integration.GoogleMachineIntegration;
import com.bogda.api.utils.CaseSensitiveUtils;
import com.bogda.api.utils.JsonUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import kotlin.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class ModelTranslateService {
    @Autowired
    private ALiYunTranslateIntegration aLiYunTranslateIntegration;
    @Autowired
    private GeminiIntegration geminiIntegration;
    @Autowired
    private ChatGptIntegration chatGptIntegration;
    @Autowired
    private GoogleMachineIntegration googleMachineIntegration;

    public Pair<String, Integer> modelTranslate(String aiModel, String prompt, String target, String sourceText, String translateType) {
        Pair<String, Integer> pair = null;
        if (ALiYunTranslateIntegration.QWEN_MAX.equals(aiModel)) {
            pair = aLiYunTranslateIntegration.userTranslate(prompt, target);
        } else if (ChatGptIntegration.GPT_4.equals(aiModel)) {
            pair = chatGptIntegration.chatWithGpt(prompt, target);
        } else if (GeminiIntegration.Gemini_3_FLASH.equals(aiModel)) {
            pair = geminiIntegration.generateText(prompt, target);
        }

        if (pair != null) {
            return pair;
        }

        // 做一个保底处理，当pair为null的时候，用google再翻译一次，如果再为null，就直接返回.
        // json批量翻译不行，翻译值会少数据，目前只能循环批量翻译
        CaseSensitiveUtils.appInsights.trackTrace("FatalException  " + aiModel + " 翻译失败， 数据如下，用google翻译 : " + sourceText);
        if ("SINGLE".equals(translateType)) {
            return googleMachineIntegration.googleTranslateWithSDK(sourceText, target);
        }

        // 将文本转为Map<Integer, String>, 循环翻译
        Map<Integer, String> sourceMap;
        try {
            sourceMap = JsonUtils.jsonToObject(
                    sourceText, new TypeReference<Map<Integer, String>>() {});
        } catch (Exception e) {
            CaseSensitiveUtils.appInsights.trackException(e);
            CaseSensitiveUtils.appInsights.trackTrace("FatalException 数据转化失败：" + sourceText);
            return null;
        }

        if (sourceMap == null || sourceMap.isEmpty()) {
            return null;
        }

        Map<Integer, String> resultMap = new LinkedHashMap<>();
        int totalCount = 0;

        for (Map.Entry<Integer, String> entry : sourceMap.entrySet()) {
            Integer key = entry.getKey();
            String value = entry.getValue();

            if (value == null || value.isBlank()) {
                resultMap.put(key, value);
                continue;
            }

            try {
                Pair<String, Integer> translated =
                        googleMachineIntegration.googleTranslateWithSDK(value, target);

                if (translated != null) {
                    resultMap.put(key, translated.getFirst());
                    totalCount += translated.getSecond();
                } else {
                    resultMap.put(key, value);
                }
            } catch (Exception e) {
                CaseSensitiveUtils.appInsights.trackException(e);
                CaseSensitiveUtils.appInsights.trackTrace("FatalException google机器翻译失败：" + value + " key: " + key);
                resultMap.put(key, value);
            }
        }

        return new Pair<>(JsonUtils.objectToJson(resultMap), totalCount);

    }
}
