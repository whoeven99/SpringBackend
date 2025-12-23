package com.bogda.common.integration;

import com.azure.ai.openai.OpenAIClient;
import com.azure.ai.openai.OpenAIClientBuilder;
import com.azure.ai.openai.models.ChatCompletions;
import com.azure.ai.openai.models.ChatCompletionsOptions;
import com.azure.ai.openai.models.ChatMessage;
import com.azure.ai.openai.models.ChatRole;
import com.azure.core.credential.AzureKeyCredential;
import com.bogda.common.utils.ConfigUtils;
import com.bogda.common.utils.TimeOutUtils;
import kotlin.Pair;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

import static com.bogda.common.utils.CaseSensitiveUtils.appInsights;

@Component
public class ChatGptIntegration {

    @Value("${gpt.endpoint}")
    private String endpoint;
    @Value("${gpt.deploymentName}")
    private String deploymentName;
    public static final int OPENAI_MAGNIFICATION = 3;

//    private final OpenAIClient client;

    public ChatGptIntegration() {
//        client = new OpenAIClientBuilder()
//                .endpoint(endpoint)
//                .credential(new AzureKeyCredential(ConfigUtils.getConfig("Gpt_ApiKey")))
//                .buildClient();
    }

    // content - allToken
    public Pair<String, Integer> chatWithGpt(String prompt, String sourceText, String shopName, String target) {
        OpenAIClient client = new OpenAIClientBuilder()
                .endpoint(endpoint)
                .credential(new AzureKeyCredential(ConfigUtils.getConfig("Gpt_ApiKey")))
                .buildClient();
        ChatMessage userMessage = new ChatMessage(ChatRole.USER);
        userMessage.setContent(prompt + (sourceText != null ? "\n" + sourceText : ""));

        List<ChatMessage> prompts = new ArrayList<>();
        prompts.add(userMessage);

        ChatCompletionsOptions options = new ChatCompletionsOptions(prompts)
                .setMaxTokens(16000)
                .setTemperature(0.7)
                .setTopP(0.95)
                .setFrequencyPenalty(0.0)
                .setPresencePenalty(0.0)
                .setStream(false);
        try {
            ChatCompletions chatCompletions = TimeOutUtils.callWithTimeoutAndRetry(() ->
                    client.getChatCompletions(deploymentName, options), TimeOutUtils.rateLimiter1);
            String content = chatCompletions.getChoices().get(0).getMessage().getContent();
            int allToken = chatCompletions.getUsage().getTotalTokens() * OPENAI_MAGNIFICATION;
            appInsights.trackTrace("ChatGptIntegration 用户： " + shopName + " 翻译语言： " + target + " 翻译的文本： " +
                    sourceText + " token openai : " + content + " all: " + allToken);
            return new Pair<>(content, allToken);
        } catch (Exception e) {
            appInsights.trackTrace("ChatGptIntegration " + shopName + " chatWithGpt error: " + e.getMessage() + " sourceText: " + sourceText + " prompt: " + prompt);
            appInsights.trackException(e);
            return new Pair<>(null, 0);
        }
    }
}
