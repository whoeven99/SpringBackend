package com.bogda.service.logic.translate;

import com.bogda.common.reporter.TraceReporterHolder;
import com.bogda.integration.aimodel.KimiIntegration;
import com.bogda.service.integration.ALiYunTranslateIntegration;
import com.bogda.integration.aimodel.ChatGptIntegration;
import com.bogda.integration.aimodel.GeminiIntegration;
import com.bogda.integration.aimodel.GoogleMachineIntegration;
import com.bogda.common.utils.AppInsightsUtils;
import com.bogda.common.utils.JsonUtils;
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
    @Autowired
    private AiModelConfigService aiModelConfigService;
    @Autowired
    private KimiIntegration kimiIntegration;

    // ai translate
    public Pair<String, Integer> aiTranslate(String aiModel, String prompt, String target) {
        Pair<String, Integer> pair = null;
        String lowerModel = aiModel.toLowerCase();
        if (lowerModel.contains("qwen")) {
            pair = aLiYunTranslateIntegration.userTranslate(prompt, target);
        } else if (aiModel.equals(ChatGptIntegration.GPT_4_1)) {
            pair = chatGptIntegration.chatWithGpt(ChatGptIntegration.GPT_4_1, ChatGptIntegration.GPT_4_OPENAI_MAGNIFICATION
                    , prompt, target);
        } else if (lowerModel.contains("gpt") || "gpt-5-mini".equals(aiModel)) {
            pair = chatGptIntegration.chatWithGpt(
                    aiModelConfigService.getModel("gpt"),
                    aiModelConfigService.getMagnification("gpt"),
                    prompt, target);
        } else if (lowerModel.contains("gemini")) {
            pair = geminiIntegration.generateText(aiModel, prompt);
        } else if (lowerModel.contains("kimi")) {
            pair = kimiIntegration.chatWithKimi(aiModel, prompt, target, aiModelConfigService.getMagnification("kimi"));
        }
        return pair;
    }

    public Pair<String, Integer> modelTranslate(String aiModel, String prompt, String target, String sourceText) {
        Pair<String, Integer> pair = aiTranslate(aiModel, prompt, target);

        if (pair != null) {
            return pair;
        }

        // 做一个保底处理，当pair为null的时候，用google再翻译一次，如果再为null，就直接返回.
        TraceReporterHolder.report("ModelTranslateService.modelTranslate", "FatalException  " + aiModel + " 翻译失败， 数据如下，用google翻译 : " + sourceText);
        return googleMachineIntegration.googleTranslateWithSDK(sourceText, target);
    }

    public Pair<String, Integer> modelTranslate(String aiModel, String prompt, String target, Map<Integer, String> sourceMap) {
        Pair<String, Integer> pair = aiTranslate(aiModel, prompt, target);

        if (pair != null) {
            return pair;
        }

        // json批量翻译不行，翻译值会少数据，目前只能循环批量翻译
        TraceReporterHolder.report("ModelTranslateService.modelTranslate", "FatalException  " + aiModel + " 翻译失败， 数据如下，用google翻译 : " + sourceMap);

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
