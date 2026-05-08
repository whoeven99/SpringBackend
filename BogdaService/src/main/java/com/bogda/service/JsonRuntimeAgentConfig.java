package com.bogda.service;

import com.bogda.common.utils.ConfigUtils;
import com.bogda.service.agent.JsonRuntimeAgent;
import com.bogda.service.agent.JsonRuntimeAgentTools;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JsonRuntimeAgentConfig {
    @Value("${langchain4j.openai.api-key:}")
    private String apiKeyFromProperties;

    @Value("${langchain4j.openai.base-url:https://api.deepseek.com/v1}")
    private String baseUrlFromProperties;

    @Value("${langchain4j.openai.model-name:deepseek-chat}")
    private String modelNameFromProperties;

    @Bean
    public ChatModel jsonRuntimeChatLanguageModel() {
        String apiKey = firstNonEmpty(
                apiKeyFromProperties,
                ConfigUtils.getConfig("langchain4j.openai.api-key"),
                ConfigUtils.getConfig("DEEPSEEK_API_KEY"),
                ConfigUtils.getConfig("deepseek")
        );
        String baseUrl = firstNonEmpty(
                baseUrlFromProperties,
                ConfigUtils.getConfig("langchain4j.openai.base-url"),
                "https://api.deepseek.com/v1"
        );
        String modelName = firstNonEmpty(
                modelNameFromProperties,
                ConfigUtils.getConfig("langchain4j.openai.model-name"),
                "deepseek-chat"
        );
        return OpenAiChatModel.builder()
                .apiKey(apiKey == null ? "" : apiKey)
                .baseUrl(baseUrl)
                .modelName(modelName)
                .build();
    }

    @Bean
    public JsonRuntimeAgent jsonRuntimeAgent(ChatModel jsonRuntimeChatLanguageModel,
                                             JsonRuntimeAgentTools jsonRuntimeAgentTools) {
        return AiServices.builder(JsonRuntimeAgent.class)
                .chatModel(jsonRuntimeChatLanguageModel)
                .tools(jsonRuntimeAgentTools)
                .build();
    }

    private static String firstNonEmpty(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return "";
    }
}
