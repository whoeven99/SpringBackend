package com.bogda.integration.aimodel;

import com.azure.ai.openai.OpenAIClient;
import com.azure.ai.openai.OpenAIClientBuilder;
import com.azure.ai.openai.models.ChatCompletions;
import com.azure.ai.openai.models.ChatCompletionsOptions;
import com.azure.ai.openai.models.ChatMessage;
import com.azure.ai.openai.models.ChatRole;
import com.azure.core.credential.AzureKeyCredential;
import com.bogda.common.reporter.ExceptionReporterHolder;
import com.bogda.common.reporter.TraceReporterHolder;
import com.bogda.common.utils.AppInsightsUtils;
import com.bogda.common.utils.TimeOutUtils;
import kotlin.Pair;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;

@Component
public class ChatGptIntegration {
    public static final int OPENAI_MAGNIFICATION = 3;
    private OpenAIClient client;
    public static String endpoint = "https://eastus.api.cognitive.microsoft.com/";
    public static String GPT_4 = "gpt-4.1";
    public static String GPT_4_1_MINI = "gpt-4.1-mini";
    public static String GPT_4_1_NANO = "gpt-4.1-nano";

    @Value("${azure.openai.key.vault}")
    private String gptKey;

    @PostConstruct
    public void init() {
        AppInsightsUtils.trackTrace("gptKey : " + gptKey);
        client = new OpenAIClientBuilder()
                .endpoint(endpoint)
                .credential(new AzureKeyCredential(gptKey))
                .buildClient();
        AppInsightsUtils.trackTrace("ChatGptIntegration init success");
    }

    /**
     * content - allToken，默认使用 GPT_4 模型
     */
    public Pair<String, Integer> chatWithGpt(String prompt, String target) {
        return chatWithGpt(GPT_4, prompt, target);
    }

    /**
     * content - allToken，支持指定模型名称
     */
    public Pair<String, Integer> chatWithGpt(String modelName, String prompt, String target) {
        ChatMessage userMessage = new ChatMessage(ChatRole.USER);
        userMessage.setContent(prompt);

        List<ChatMessage> prompts = new ArrayList<>();
        prompts.add(userMessage);

        // gpt-4.1-mini / gpt-4.1-nano
        ChatCompletionsOptions options = new ChatCompletionsOptions(prompts)
                .setFrequencyPenalty(0.0)
                .setPresencePenalty(0.0)
                .setStream(false);
        try {
            ChatCompletions chatCompletions = TimeOutUtils.callWithTimeoutAndRetry(() ->
                    client.getChatCompletions(modelName, options));
            String content = chatCompletions.getChoices().get(0).getMessage().getContent();
            int allToken = chatCompletions.getUsage().getTotalTokens() * OPENAI_MAGNIFICATION;
            int input = chatCompletions.getUsage().getPromptTokens();
            int output = chatCompletions.getUsage().getCompletionTokens();
            TraceReporterHolder.report("ChatGptIntegration.chatWithGpt", "ChatGptIntegration 翻译提示词： " + prompt + " model: " + modelName
                    + " token openai : " + content + " all: "
                    + allToken + " input: " + input + " output: " + output + " target: " + target + " modelName: " + modelName);
            return new Pair<>(content, allToken);
        } catch (Exception e) {
            TraceReporterHolder.report("ChatGptIntegration.chatWithGpt", "FatalException ChatGptIntegration chatWithGpt error: " + e.getMessage()
                    + " model: " + modelName + " prompt: " + prompt);
            ExceptionReporterHolder.report("ChatGptIntegration.chatWithGpt", e);
            return null;
        }
    }
}
