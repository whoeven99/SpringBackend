package com.bogda.service.logic.translate;

import com.bogda.common.utils.JsonUtils;
import com.bogda.common.utils.ModuleCodeUtils;
import com.bogda.integration.aimodel.ChatGptIntegration;
import com.bogda.integration.aimodel.GeminiIntegration;
import com.bogda.service.integration.ALiYunTranslateIntegration;
import com.bogda.service.logic.redis.ConfigRedisRepo;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class AiModelConfigService {

    private static final String AI_MODEL_CONFIG_KEY = "ai_model_config";
    private static final String SINGLE_TRANSLATE_MODEL_KEY = "single_translate_model";

    @Autowired
    private ConfigRedisRepo configRedisRepo;

    /**
     * 从Redis统一配置中获取指定类型的AI模型配置。
     * Redis数据格式: {"gpt":{"model":"gpt-4.1","magnification":"3"},"qwen":{"model":"qwen-max","magnification":"2"}}
     */
    private Map<String, String> getTypeConfig(String type) {
        String json = configRedisRepo.getConfig(AI_MODEL_CONFIG_KEY);
        if (json == null || json.trim().isEmpty()) {
            return null;
        }
        Map<String, Map<String, String>> configMap = JsonUtils.jsonToObject(
                json, new TypeReference<Map<String, Map<String, String>>>() {});
        return configMap != null ? configMap.get(type) : null;
    }

    public String getModel(String type) {
        Map<String, String> config = getTypeConfig(type);
        if (config != null) {
            String model = config.get("model");
            if (model != null && !model.trim().isEmpty()) {
                return model.trim();
            }
        }
        return switch (type) {
            case "gpt" -> ChatGptIntegration.GPT_4;
            case "qwen" -> ALiYunTranslateIntegration.QWEN_MAX;
            case "gemini" -> GeminiIntegration.GEMINI_3_FLASH;
            default -> null;
        };
    }

    public double getMagnification(String type) {
        Map<String, String> config = getTypeConfig(type);
        if (config != null) {
            String value = config.get("magnification");
            if (value != null && !value.trim().isEmpty()) {
                try {
                    return Double.parseDouble(value.trim());
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return switch (type) {
            case "gpt" -> ChatGptIntegration.GPT_4_OPENAI_MAGNIFICATION;
            default -> 2.0;
        };
    }

    /**
     * 获取单条翻译使用的AI模型，通过Redis配置动态切换。
     * 配置值映射: "qwen" → QWEN_MAX, "gpt" → GPT_5, "gemini" → GEMINI_3_FLASH
     */
    public String getSingleTranslateModel() {
        String config = configRedisRepo.getConfig(SINGLE_TRANSLATE_MODEL_KEY);
        if (config == null || config.trim().isEmpty()) {
            return ALiYunTranslateIntegration.QWEN_MAX;
        }
        return switch (config.trim().toLowerCase()) {
            case "gpt" -> ModuleCodeUtils.GPT_5;
            case "gemini" -> GeminiIntegration.GEMINI_3_FLASH;
            default -> ALiYunTranslateIntegration.QWEN_MAX;
        };
    }
}
