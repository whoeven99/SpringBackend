package com.bogda.integration.aimodel;

import com.azure.ai.openai.OpenAIClient;
import com.azure.ai.openai.OpenAIClientBuilder;
import com.azure.ai.openai.models.ChatCompletions;
import com.azure.ai.openai.models.ChatCompletionsOptions;
import com.azure.ai.openai.models.ChatMessage;
import com.azure.ai.openai.models.ChatRole;
import com.azure.core.credential.AzureKeyCredential;
import com.bogda.common.utils.AppInsightsUtils;
import com.bogda.common.utils.ConfigUtils;
import com.bogda.common.utils.JsonUtils;
import com.bogda.common.utils.ModuleCodeUtils;
import com.bogda.common.utils.TimeOutUtils;
import com.bogda.integration.http.BaseHttpIntegration;
import kotlin.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class ChatGptIntegration {
    public static final int OPENAI_MAGNIFICATION = 3;
    private OpenAIClient client;
    public static String endpoint = "https://eastus.api.cognitive.microsoft.com/";
    public static String GPT_4 = "gpt-4.1";

    @Value("${azure.openai.key.vault}")
    private String gptKey;
    @Autowired
    private BaseHttpIntegration baseHttpIntegration;

    @PostConstruct
    public void init() {
        AppInsightsUtils.trackTrace("gptKey : " + gptKey);
        client = new OpenAIClientBuilder()
                .endpoint(endpoint)
                .credential(new AzureKeyCredential(gptKey))
                .buildClient();
        AppInsightsUtils.trackTrace("ChatGptIntegration init success");
    }

    // content - allToken
    public Pair<String, Integer> chatWithGpt(String prompt, String target) {
        ChatMessage userMessage = new ChatMessage(ChatRole.USER);
        userMessage.setContent(prompt);

        List<ChatMessage> prompts = new ArrayList<>();
        prompts.add(userMessage);

        // gpt-5-mini 不支持 max_tokens， Temperature， TopP
        // 故不设置 maxTokens，由服务端使用默认 max_completion_tokens，避免 400 unsupported_parameter 错误
        ChatCompletionsOptions options = new ChatCompletionsOptions(prompts)
                .setFrequencyPenalty(0.0)
                .setPresencePenalty(0.0)
                .setStream(false);
        try {
            ChatCompletions chatCompletions = TimeOutUtils.callWithTimeoutAndRetry(() ->
                    client.getChatCompletions(GPT_4, options));
            String content = chatCompletions.getChoices().get(0).getMessage().getContent();
            int allToken = chatCompletions.getUsage().getTotalTokens() * OPENAI_MAGNIFICATION;
            int input = chatCompletions.getUsage().getPromptTokens();
            int output = chatCompletions.getUsage().getCompletionTokens();
            AppInsightsUtils.trackTrace("ChatGptIntegration 翻译提示词： " + prompt + " token openai : " + content + " all: "
                    + allToken + " input: " + input + " output: " + output + " target: " + target);
            return new Pair<>(content, allToken);
        } catch (Exception e) {
            AppInsightsUtils.trackTrace("FatalException ChatGptIntegration chatWithGpt error: " + e.getMessage() + " prompt: " + prompt);
            AppInsightsUtils.trackException(e);
            return null;
        }
    }

    /**
     * 使用用户私有 key 通过 OpenAI Chat Completions API 调用 GPT。
     */
    public Pair<String, Integer> chatWithGptByApiKey(String prompt, String target, String privateApiKey, String model) {
        if (prompt == null || privateApiKey == null || privateApiKey.isBlank()) {
            return null;
        }
        String finalModel = (model == null || model.isBlank()) ? ModuleCodeUtils.GPT_5 : model;
        String endpoint = ConfigUtils.getConfig("PRIVATE_OPENAI_CHAT_ENDPOINT");
        if (endpoint == null || endpoint.isBlank()) {
            endpoint = "https://api.openai.com/v1/chat/completions";
        }
        try {
            Map<String, String> headers = new HashMap<>();
            headers.put("Authorization", "Bearer " + privateApiKey);
            String body = JsonUtils.objectToJson(Map.of(
                    "model", finalModel,
                    "stream", false,
                    "messages", List.of(Map.of("role", "user", "content", prompt))
            ));
            String response = baseHttpIntegration.httpPost(endpoint, body, headers);
            if (response == null) {
                return null;
            }
            var root = JsonUtils.readTree(response);
            if (root == null) {
                return null;
            }
            String content = root.path("choices").path(0).path("message").path("content").asText(null);
            if (content == null) {
                AppInsightsUtils.trackTrace("FatalException chatWithGptByApiKey invalid response: " + response);
                return null;
            }
            int totalTokens = root.path("usage").path("total_tokens").asInt(0);
            if (totalTokens <= 0) {
                totalTokens = Math.max(1, prompt.length() / 3);
            }
            totalTokens = totalTokens * OPENAI_MAGNIFICATION;
            AppInsightsUtils.trackTrace("chatWithGptByApiKey model: " + finalModel + " target: " + target
                    + " token: " + totalTokens);
            return new Pair<>(content, totalTokens);
        } catch (Exception e) {
            AppInsightsUtils.trackTrace("FatalException chatWithGptByApiKey error: " + e.getMessage());
            AppInsightsUtils.trackException(e);
            return null;
        }
    }
}
