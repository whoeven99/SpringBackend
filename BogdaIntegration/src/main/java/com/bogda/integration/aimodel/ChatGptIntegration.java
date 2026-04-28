package com.bogda.integration.aimodel;

import com.azure.ai.openai.OpenAIClient;
import com.azure.ai.openai.OpenAIClientBuilder;
import com.azure.ai.openai.models.*;
import com.azure.core.credential.AzureKeyCredential;
import com.bogda.common.reporter.ExceptionReporterHolder;
import com.bogda.common.reporter.TraceReporterHolder;
import com.bogda.common.utils.TimeOutUtils;
import com.bogda.integration.feishu.FeiShuRobotIntegration;
import kotlin.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class ChatGptIntegration {
    public static final int GPT_4_OPENAI_MAGNIFICATION = 3;
    public static final double GPT_4_1_NANO_OPENAI_MAGNIFICATION = 1.5;
    private OpenAIClient client;
    public static String endpoint = "https://eastus.api.cognitive.microsoft.com/";
    public static String GPT_4_1 = "gpt-4.1";
    public static String GPT_4_1_MINI = "gpt-4.1-mini";
    public static String GPT_4_1_NANO = "gpt-4.1-nano";

    @Value("${azure.openai.key.vault}")
    private String gptKey;

    @Autowired
    private FeiShuRobotIntegration feiShuRobotIntegration;

    @PostConstruct
    public void init() {
        client = new OpenAIClientBuilder()
                .endpoint(endpoint)
                .credential(new AzureKeyCredential(gptKey))
                .buildClient();
    }

    /**
     * content - allToken，支持指定模型名称，使用默认系数
     */
    public Pair<String, Integer> chatWithGpt(String modelName, String prompt, String target) {
        return chatWithGpt(modelName, GPT_4_OPENAI_MAGNIFICATION, prompt, target);
    }

    /**
     * content - allToken，支持指定模型名称和系数（配置化入口）
     */
    public Pair<String, Integer> chatWithGpt(String modelName, double magnification, String prompt, String target) {
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "user", "content", prompt));
        return chatWithGpt(modelName, magnification, messages, target, null);
    }

    public Pair<String, Integer> chatWithGpt(String modelName, double magnification,
                                             List<Map<String, String>> messages,
                                             String target, String sessionId) {
        List<ChatMessage> prompts = new ArrayList<>();
        for (Map<String, String> message : messages) {
            if (message == null) {
                continue;
            }
            String role = message.get("role");
            String content = message.get("content");
            if (content == null || content.isEmpty()) {
                continue;
            }
            ChatRole chatRole = "assistant".equalsIgnoreCase(role) ? ChatRole.ASSISTANT : ChatRole.USER;
            ChatMessage chatMessage = new ChatMessage(chatRole);
            chatMessage.setContent(content);
            prompts.add(chatMessage);
        }
        if (prompts.isEmpty()) {
            return null;
        }

        ChatCompletionsOptions options = new ChatCompletionsOptions(prompts)
                .setFrequencyPenalty(0.0)
                .setPresencePenalty(0.0)
                .setTemperature(0.1)
                .setStream(false);
        try {
            ChatCompletions chatCompletions = TimeOutUtils.callWithTimeoutAndRetry(() ->
                    client.getChatCompletions(modelName, options));
            String content = chatCompletions.getChoices().get(0).getMessage().getContent();
            int allToken = (int) Math.ceil(chatCompletions.getUsage().getTotalTokens() * magnification);
            int input = chatCompletions.getUsage().getPromptTokens();
            int output = chatCompletions.getUsage().getCompletionTokens();
            TraceReporterHolder.report("ChatGptIntegration.chatWithGpt", "ChatGptIntegration 翻译 modelName: " + modelName
                    + " magnification: " + magnification + " token openai : " + content + " all: "
                    + allToken + " input: " + input + " output: " + output + " target: " + target
                    + " sessionId: " + sessionId + " messagesSize: " + prompts.size());

            if (content == null) {
                CompletionsFinishReason finishReason = chatCompletions.getChoices().get(0).getFinishReason();
                ChatMessage message = chatCompletions.getChoices().get(0).getMessage();
                TraceReporterHolder.report("ChatGptIntegration.chatWithGpt", "FatalException 飞书机器人报错 ChatGptIntegration chatWithGpt error: "
                        + " model: " + modelName + " sessionId: " + sessionId + " finishReason : " + finishReason + " message : " + message);
                feiShuRobotIntegration.sendMessage("FatalException ChatGptIntegration chatWithGpt error: " + " model: "
                        + modelName + " sessionId: " + sessionId + " finishReason : " + finishReason + " message : " + message);
            }

            return new Pair<>(content, allToken);
        } catch (Exception e) {
            TraceReporterHolder.report("ChatGptIntegration.chatWithGpt", "FatalException 飞书机器人报错 ChatGptIntegration chatWithGpt error: " + e.getMessage()
                    + " model: " + modelName + " sessionId: " + sessionId);
            ExceptionReporterHolder.report("ChatGptIntegration.chatWithGpt", e);
            feiShuRobotIntegration.sendMessage("FatalException ChatGptIntegration chatWithGpt error: " + e.getMessage() + " model: " + modelName + " sessionId: " + sessionId);
            return null;
        }
    }
}
