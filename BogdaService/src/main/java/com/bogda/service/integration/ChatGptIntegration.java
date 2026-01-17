package com.bogda.service.integration;

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
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;



@Component
public class ChatGptIntegration {
    public static final int OPENAI_MAGNIFICATION = 3;
    public static String GPT_4 = "gpt-4.1";
    private final OpenAIClient client;
    public static String endpoint = "https://eastus.api.cognitive.microsoft.com/";

    @Autowired
    public ChatGptIntegration() {
        client = new OpenAIClientBuilder()
                .endpoint(endpoint)
                .credential(new AzureKeyCredential(ConfigUtils.getConfig("Gpt_ApiKey")))
                .buildClient();
    }

    // content - allToken
    public Pair<String, Integer> chatWithGpt(String prompt, String target) {
        ChatMessage userMessage = new ChatMessage(ChatRole.USER);
        userMessage.setContent(prompt);

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
}
