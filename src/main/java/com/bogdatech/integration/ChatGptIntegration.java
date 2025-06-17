package com.bogdatech.integration;

import com.azure.ai.openai.OpenAIClient;
import com.azure.ai.openai.OpenAIClientBuilder;
import com.azure.ai.openai.models.ChatCompletions;
import com.azure.ai.openai.models.ChatCompletionsOptions;
import com.azure.ai.openai.models.ChatMessage;
import com.azure.ai.openai.models.ChatRole;
import com.azure.core.credential.AzureKeyCredential;
import com.azure.core.exception.HttpResponseException;
import com.bogdatech.entity.VO.ChatgptVO;
import com.bogdatech.exception.ClientException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

import static com.bogdatech.utils.CaseSensitiveUtils.appInsights;

@Component
public class ChatGptIntegration {

    @Value("${gpt.endpoint}")
    private String endpoint;
    @Value("${gpt.apiKey}")
    private String apiKey;
    @Value("${gpt.deploymentName}")
    private String deploymentName;

    public ChatgptVO chatWithGpt(String prompt, String sourceText) {
        // 使用基于密钥的身份验证来初始化 OpenAI 客户端
        OpenAIClient client = new OpenAIClientBuilder()
                .endpoint(endpoint)
                .credential(new AzureKeyCredential(apiKey))
                .buildClient();

        // 模拟聊天交互
        ChatMessage messagereq = new ChatMessage(ChatRole.USER);
        ChatMessage messagersy= new ChatMessage(ChatRole.SYSTEM);
        messagersy.setContent(prompt);
        messagereq.setContent(sourceText);

        List<ChatMessage> prompts = new ArrayList<> ();
        prompts.add(messagereq);
        prompts.add(messagersy);
        ChatCompletionsOptions options = new ChatCompletionsOptions(prompts)
                .setMaxTokens(1000)
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
                Integer allToken = chatCompletions.getUsage().getTotalTokens();
                Integer promptToken = chatCompletions.getUsage().getPromptTokens();
                Integer completionToken = chatCompletions.getUsage().getCompletionTokens();
                if (content != null && !content.trim().isEmpty()) {
                    return new ChatgptVO(content, allToken, promptToken, completionToken);
                }
            }
            catch (HttpResponseException e) {
                retryCount++;
                if (e.getMessage().contains("400")) {
                    appInsights.trackTrace("报错的文本是： " + prompt);
                    System.out.println("报错的文本是： " + prompt);
                    if (retryCount >= 2){
                        // 如果重试次数超过2次，则修改翻译状态为4 ：翻译异常，终止翻译流程。
                        throw new ClientException("Translation exception");
                    }
                }
            }
            catch (Exception e) {
                retryCount++;
                appInsights.trackTrace("Error occurred while calling GPT: " + e.getMessage());
                System.out.println("Error occurred while calling GPT: " + e.getMessage());
                if (retryCount >= 2){
                        // 如果重试次数超过2次，则修改翻译状态为4 ：翻译异常，终止翻译流程。
                        throw new ClientException("Translation exception");
                    }
            }
        }
        return new ChatgptVO(content, 0, 0, 0);
    }


}
