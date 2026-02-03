package com.bogda.service.logic.translate;

import com.bogda.common.model.AiTranslateResult;
import com.bogda.common.utils.ModuleCodeUtils;
import com.bogda.service.integration.ALiYunTranslateIntegration;
import com.bogda.integration.aimodel.ChatGptIntegration;
import com.bogda.integration.aimodel.GeminiIntegration;
import com.bogda.integration.aimodel.GoogleMachineIntegration;
import com.bogda.common.utils.AppInsightsUtils;
import com.bogda.common.utils.JsonUtils;
import kotlin.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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

    /**
     * 根据用户选择的模型得到链式顺序：首选用户选的，再轮换另外两个。
     * GPT -> [GPT, Gemini, Qwen]; Gemini -> [Gemini, GPT, Qwen]; Qwen -> [Qwen, GPT, Gemini]
     */
    private List<String> getModelChain(String userSelectedModel) {
        List<String> chain = new ArrayList<>(3);
        if (ModuleCodeUtils.GPT_5.equals(userSelectedModel)) {
            chain.add(ModuleCodeUtils.GPT_5);
            chain.add(GeminiIntegration.GEMINI_3_FLASH);
            chain.add(ALiYunTranslateIntegration.QWEN_MAX);
        } else if (GeminiIntegration.GEMINI_3_FLASH.equals(userSelectedModel)) {
            chain.add(GeminiIntegration.GEMINI_3_FLASH);
            chain.add(ModuleCodeUtils.GPT_5);
            chain.add(ALiYunTranslateIntegration.QWEN_MAX);
        } else {
            chain.add(ALiYunTranslateIntegration.QWEN_MAX);
            chain.add(ModuleCodeUtils.GPT_5);
            chain.add(GeminiIntegration.GEMINI_3_FLASH);
        }
        return chain;
    }

    /**
     * 单模型 AI 翻译，带错误码（400 表示直接走 Google）。
     */
    private AiTranslateResult aiTranslateWithResult(String aiModel, String prompt, String target) {
        if (ALiYunTranslateIntegration.QWEN_MAX.equals(aiModel)) {
            return aLiYunTranslateIntegration.userTranslateWithResult(prompt, target);
        }
        if (ModuleCodeUtils.GPT_5.equals(aiModel)) {
            return chatGptIntegration.chatWithGptWithResult(prompt, target);
        }
        if (GeminiIntegration.GEMINI_3_FLASH.equals(aiModel)) {
            return geminiIntegration.generateTextWithResult(aiModel, prompt);
        }
        return AiTranslateResult.fail(0);
    }

    // ai translate（保留原接口，供未走链式的调用）
    public Pair<String, Integer> aiTranslate(String aiModel, String prompt, String target) {
        AiTranslateResult result = aiTranslateWithResult(aiModel, prompt, target);
        return result.isSuccess() ? new Pair<>(result.getContent(), result.getTokenCount()) : null;
    }

    public Pair<String, Integer> modelTranslate(String aiModel, String prompt, String target, String sourceText) {
        List<String> chain = getModelChain(aiModel);
        for (String model : chain) {
            AiTranslateResult result = aiTranslateWithResult(model, prompt, target);
            if (result == null) {
                result = AiTranslateResult.fail(0);
            }
            if (result.isSuccess()) {
                return new Pair<>(result.getContent(), result.getTokenCount());
            }
            if (result.isBadRequest()) {
                AppInsightsUtils.trackTrace("FatalException " + model + " 返回 400，直接走 Google : " + sourceText);
                break;
            }
            AppInsightsUtils.trackTrace("FatalException " + model + " 翻译失败，尝试下一模型 : " + sourceText);
        }
        return googleMachineIntegration.googleTranslateWithSDK(sourceText, target);
    }

    public Pair<String, Integer> modelTranslate(String aiModel, String prompt, String target, Map<Integer, String> sourceMap) {
        List<String> chain = getModelChain(aiModel);
        Pair<String, Integer> pair = null;
        boolean skipToGoogle = false;
        for (String model : chain) {
            AiTranslateResult result = aiTranslateWithResult(model, prompt, target);
            if (result == null) {
                result = AiTranslateResult.fail(0);
            }
            if (result.isSuccess()) {
                pair = new Pair<>(result.getContent(), result.getTokenCount());
                break;
            }
            if (result.isBadRequest()) {
                AppInsightsUtils.trackTrace("FatalException " + model + " 返回 400，直接走 Google : " + sourceMap);
                skipToGoogle = true;
                break;
            }
            AppInsightsUtils.trackTrace("FatalException " + model + " 翻译失败，尝试下一模型 : " + sourceMap);
        }

        if (pair != null) {
            return pair;
        }
        if (!skipToGoogle) {
            AppInsightsUtils.trackTrace("FatalException  " + aiModel + " 链式翻译均失败，用google翻译 : " + sourceMap);
        }

        // 将文本转为Map<Integer, String>, 循环翻译
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
                AppInsightsUtils.trackException(e);
                AppInsightsUtils.trackTrace("FatalException google机器翻译失败：" + value + " key: " + key);
                resultMap.put(key, value);
            }
        }

        return new Pair<String, Integer>(JsonUtils.objectToJson(resultMap), totalCount);

    }
}
