package com.bogda.service.logic.translate;

import com.bogda.common.entity.DO.TranslateResourceDTO;
import com.bogda.common.reporter.ExceptionReporterHolder;
import com.bogda.common.utils.JsonUtils;
import com.bogda.common.utils.PictureUtils;
import com.bogda.integration.aimodel.ChatGptIntegration;
import com.bogda.integration.aimodel.GeminiIntegration;
import com.bogda.integration.aimodel.KimiIntegration;
import com.bogda.service.integration.ALiYunTranslateIntegration;
import com.fasterxml.jackson.core.type.TypeReference;
import kotlin.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * v2 批量翻译主路径已下线（Spark worker v4 接管）。
 * 保留：Monitor 调试翻译、资源排序工具。
 */
@Component
public class TranslateV2Service {
    @Autowired
    private ALiYunTranslateIntegration aLiYunTranslateIntegration;
    @Autowired
    private GeminiIntegration geminiIntegration;
    @Autowired
    private ChatGptIntegration chatGptIntegration;
    @Autowired
    private KimiIntegration kimiIntegration;
    @Autowired
    private AiModelConfigService aiModelConfigService;

    /** For MonitorController promptTest */
    public Map<String, Object> testTranslate(Map<String, Object> map) {
        String model = String.valueOf(map.getOrDefault("model", ""));
        String prompt = String.valueOf(map.getOrDefault("prompt", ""));
        String target = String.valueOf(map.getOrDefault("target", ""));
        String picUrl = (map.get("picUrl") != null) ? map.get("picUrl").toString() : null;

        try {
            String jsonStr = String.valueOf(map.getOrDefault("json", "{}"));
            Map<Integer, String> languageMap = JsonUtils.jsonToObject(jsonStr, new TypeReference<Map<Integer, String>>() {
            });
            if (CollectionUtils.isEmpty(languageMap)) {
                return defaultNullMap();
            }
            prompt = prompt.replace("{{SOURCE_LANGUAGE_LIST}}", languageMap.toString())
                    .replace("{{TARGET_LANGUAGE}}", target);
        } catch (Exception e) {
            ExceptionReporterHolder.report("TranslateV2Service.testTranslate", e);
            return defaultNullMap();
        }

        try {
            if (model.contains("qwen")) {
                return handleAliYun(prompt, target);
            } else if (model.contains("gemini")) {
                return handleGemini(model, prompt, picUrl);
            } else if (model.contains("gpt")) {
                return handleGpt(model, prompt, target);
            } else if (model.contains("kimi")) {
                return handleKimi(prompt, target);
            }
        } catch (Exception e) {
            ExceptionReporterHolder.report("TranslateV2Service.testTranslate", e);
        }

        return defaultNullMap();
    }

    private Map<String, Object> handleKimi(String prompt, String target) {
        Pair<String, Integer> pair = kimiIntegration.chat(prompt, target);
        if (pair == null) {
            return defaultNullMap();
        }
        return buildResponse(pair.getFirst(), pair.getSecond(), "text");
    }

    private Map<String, Object> handleGpt(String modelName, String prompt, String target) {
        Pair<String, Integer> pair = chatGptIntegration.chatWithGpt(modelName, prompt, target);
        if (pair == null) {
            return defaultNullMap();
        }
        return buildResponse(pair.getFirst(), pair.getSecond(), "text");
    }

    private Map<String, Object> handleAliYun(String prompt, String target) {
        Pair<String, Integer> pair = aLiYunTranslateIntegration.userTranslate(prompt, target, aiModelConfigService.getMagnification("qwen"));
        if (pair == null) {
            return defaultNullMap();
        }
        return buildResponse(pair.getFirst(), pair.getSecond(), "text");
    }

    private Map<String, Object> handleGemini(String model, String prompt, String picUrl) throws Exception {
        if (picUrl == null) {
            Pair<String, Integer> pair = geminiIntegration.generateText(model, prompt, aiModelConfigService.getMagnification("gemini"));
            if (pair == null) {
                return defaultNullMap();
            }
            return buildResponse(pair.getFirst(), pair.getSecond(), "text");
        }

        String picType = PictureUtils.getExtensionFromUrl(picUrl);
        String mimeType = (picType != null) ? PictureUtils.IMAGE_MIME_MAP.get(picType.toLowerCase()) : null;
        if (mimeType == null) {
            return defaultNullMap();
        }

        try (InputStream in = new URL(picUrl).openStream()) {
            byte[] imageBytes = in.readAllBytes();
            Pair<String, Integer> pair = geminiIntegration.generateImage(model, prompt, imageBytes, mimeType);
            if (pair == null) {
                return defaultNullMap();
            }
            String dataUrl = "data:" + mimeType + ";base64," + pair.getFirst();
            return buildResponse(dataUrl, pair.getSecond(), "pic");
        }
    }

    private Map<String, Object> buildResponse(Object content, Integer tokens, String translateModel) {
        Map<String, Object> ans = new HashMap<>();
        ans.put("content", content);
        ans.put("allToken", tokens);
        ans.put("translateModel", translateModel);
        return ans;
    }

    private Map<String, Object> defaultNullMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("content", "error");
        map.put("allToken", 0);
        return map;
    }

    public static List<String> sortTranslateData(List<String> list) {
        List<String> orderList = TranslateResourceDTO.ALL_RESOURCES.stream()
                .map(TranslateResourceDTO::getResourceType)
                .toList();

        Map<String, Integer> orderMap = new HashMap<>();
        for (int i = 0; i < orderList.size(); i++) {
            orderMap.put(orderList.get(i), i);
        }

        List<String> sortedList = new ArrayList<>(list);
        sortedList.sort(Comparator.comparingInt(name -> orderMap.getOrDefault(name, Integer.MAX_VALUE)));
        return sortedList;
    }
}
