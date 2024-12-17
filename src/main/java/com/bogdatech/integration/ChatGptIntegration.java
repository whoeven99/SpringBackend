package com.bogdatech.integration;

import com.azure.ai.openai.models.ChatCompletions;
import com.azure.ai.openai.models.ChatCompletionsOptions;
import com.azure.ai.openai.models.ChatMessage;
import com.azure.ai.openai.models.ChatRole;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.azure.ai.openai.OpenAIClient;
import com.azure.ai.openai.OpenAIClientBuilder;
import com.azure.core.credential.AzureKeyCredential;

import java.util.ArrayList;
import java.util.List;

@Component
public class ChatGptIntegration {

    @Value("${gpt.endpoint}")
    private String endpoint;
    @Value("${gpt.apiKey}")
    private String apiKey;
    @Value("${gpt.deploymentName}")
    private String deploymentName;
    public String chatWithGpt(String prompt) {
        // 使用基于密钥的身份验证来初始化 OpenAI 客户端
        OpenAIClient client = new OpenAIClientBuilder()
                .endpoint(endpoint)
                .credential(new AzureKeyCredential(apiKey))
                .buildClient();

        // 模拟聊天交互
        ChatMessage messagereq = new ChatMessage(ChatRole.USER);
        messagereq.setContent(prompt);

        List<ChatMessage> prompts = new ArrayList<> ();
        prompts.add(messagereq);
        ChatCompletionsOptions options = new ChatCompletionsOptions(prompts)
                .setMaxTokens(800)
                .setTemperature(0.7)
                .setTopP(0.95)
                .setFrequencyPenalty(0.0)
                .setPresencePenalty(0.0)
                .setStream(false);

        String content = null;
        try {
            ChatCompletions chatCompletions = client.getChatCompletions(deploymentName, options);
            content = chatCompletions.getChoices().get(0).getMessage().getContent();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        System.out.println("Response: " + content);
        return content;
    }
}
