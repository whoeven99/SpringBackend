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
//        ChatMessage messagersy= new ChatMessage(ChatRole.SYSTEM);
        messagereq.setContent(prompt);
//        messagersy.setContent("Please translate the following English text into Chinese, following the rules: \n" +
//                "never translate:\"Shoes\" → \"Shoes\" \n" +
//                "always translate:\"Girls\" → \"女生\" ");

        List<ChatMessage> prompts = new ArrayList<> ();
        prompts.add(messagereq);
//        prompts.add(messagersy);
        ChatCompletionsOptions options = new ChatCompletionsOptions(prompts)
                .setMaxTokens(800)
                .setTemperature(0.7)
                .setTopP(0.95)
                .setFrequencyPenalty(0.0)
                .setPresencePenalty(0.0)
                .setStream(false);

        String content = null;
        int retryCount = 0;
        final int maxRetries = 3;
        while (retryCount < maxRetries) {
            try {
                ChatCompletions chatCompletions = client.getChatCompletions(deploymentName, options);
                content = chatCompletions.getChoices().get(0).getMessage().getContent();

                if (content != null && !content.trim().isEmpty()) {
                    return content;
                }
            } catch (Exception e) {
                retryCount++;
                System.err.println("Attempt " + retryCount + " failed: " + e.getMessage());
            }
        }

        System.out.println("Response: " + content);
        return content;
    }
}
