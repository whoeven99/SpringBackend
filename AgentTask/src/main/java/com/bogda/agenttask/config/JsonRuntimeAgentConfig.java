package com.bogda.agenttask.config;

import com.bogda.agenttask.ApplicationLocalPropertiesBootstrap;
import com.bogda.agenttask.agent.JsonRuntimeAgent;
import com.bogda.agenttask.agent.JsonRuntimeAgentTools;
import com.bogda.common.utils.ConfigUtils;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JsonRuntimeAgentConfig {

    /**
     * 保证先执行 {@link ApplicationLocalPropertiesBootstrap}，
     * 再创建 {@link ChatModel}，否则 {@link ConfigUtils#getConfig(String)} 读不到 application.local.properties。
     */
    public JsonRuntimeAgentConfig(
            @SuppressWarnings("unused") ApplicationLocalPropertiesBootstrap localPropsLoaded) {
    }

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
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalStateException(
                    "未配置 OpenAI 兼容 API 密钥：请设置 spring 属性 langchain4j.openai.api-key，"
                            + "或环境变量 DEEPSEEK_API_KEY / deepseek，"
                            + "或在本机将 deepseek=sk-... 写入 application.local.properties 并通过 ApplicationLocalPropertiesBootstrap 加载。");
        }
        return OpenAiChatModel.builder()
                .apiKey(apiKey)
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
