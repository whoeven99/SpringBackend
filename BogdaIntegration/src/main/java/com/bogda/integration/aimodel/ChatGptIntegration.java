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
import com.bogda.common.utils.TimeOutUtils;
import kotlin.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;

@Component
public class ChatGptIntegration {
    public static final int OPENAI_MAGNIFICATION = 3;
    public static String GPT_5 = "gpt-5-mini";
    private OpenAIClient client;
    public static String endpoint = "https://eastus.api.cognitive.microsoft.com/";

    @Value("${azure.openai.key.vault}")
    private String gptKey;

    @PostConstruct
    public void init() {
        AppInsightsUtils.trackTrace("gptKey : " + gptKey);
        client = new OpenAIClientBuilder()
                .endpoint(endpoint)
                .credential(new AzureKeyCredential(gptKey))
                .buildClient();
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
                    client.getChatCompletions(GPT_5, options));
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
}
