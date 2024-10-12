package com.bogdatech.integration;

import com.azure.ai.openai.models.ChatCompletions;
import com.azure.ai.openai.models.ChatCompletionsOptions;
import com.azure.ai.openai.models.ChatMessage;
import com.azure.ai.openai.models.ChatRole;
import org.springframework.stereotype.Component;

import com.azure.ai.openai.OpenAIClient;
import com.azure.ai.openai.OpenAIClientBuilder;
import com.azure.core.credential.AzureKeyCredential;

import java.util.ArrayList;
import java.util.List;

@Component
public class ChatGptIntegration {

    private static String endpoint = "https://eastus.api.cognitive.microsoft.com/";
    private static String apiKey = "3892b04e4a81497db4d37d4c5c18a720";
    private static String deploymentName = "getting-started-deployment-241008214453";

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

        ChatCompletions chatCompletions = client.getChatCompletions(deploymentName, options);

        // Todo Exception
        return chatCompletions.getChoices().get(0).getMessage().getContent();
    }
}
