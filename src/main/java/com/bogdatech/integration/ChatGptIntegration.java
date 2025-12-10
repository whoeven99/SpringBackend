package com.bogdatech.integration;

import com.azure.ai.openai.OpenAIClient;
import com.azure.ai.openai.OpenAIClientBuilder;
import com.azure.ai.openai.models.ChatCompletions;
import com.azure.ai.openai.models.ChatCompletionsOptions;
import com.azure.ai.openai.models.ChatMessage;
import com.azure.ai.openai.models.ChatRole;
import com.azure.core.credential.AzureKeyCredential;
import com.bogdatech.utils.ConfigUtils;
import com.bogdatech.utils.TimeOutUtils;
import kotlin.Pair;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

import static com.bogdatech.utils.CaseSensitiveUtils.appInsights;

@Component
public class ChatGptIntegration {

    @Value("${gpt.endpoint}")
    private String endpoint;
    @Value("${gpt.deploymentName}")
    private String deploymentName;
    public static final int OPENAI_MAGNIFICATION = 3;

    // content - allToken
    public Pair<String, Integer> chatWithGpt(String prompt, String sourceText, String shopName, String target, String privateKey) {
        // 使用基于密钥的身份验证来初始化 OpenAI 客户端
        OpenAIClient client = new OpenAIClientBuilder()
                .endpoint(endpoint)
                .credential(privateKey != null ? privateKey : new AzureKeyCredential(ConfigUtils.getConfig("Gpt_ApiKey")))
                .buildClient();

        // 模拟聊天交互
        ChatMessage userMessage = new ChatMessage(ChatRole.USER);
        if (sourceText != null) {
            userMessage.setContent(prompt + "\n" + sourceText); // 合并 prompt 和 sourceText
        } else {
            userMessage.setContent(prompt);
        }

        List<ChatMessage> prompts = new ArrayList<>();
        prompts.add(userMessage);

        ChatCompletionsOptions options = new ChatCompletionsOptions(prompts)
                .setMaxTokens(16000)
                .setTemperature(0.7)
                .setTopP(0.95)
                .setFrequencyPenalty(0.0)
                .setPresencePenalty(0.0)
                .setStream(false);

        String content;
        try {
            ChatCompletions chatCompletions = TimeOutUtils.callWithTimeoutAndRetry(() -> {
                        try {
                            return client.getChatCompletions(deploymentName, options);
                        } catch (Exception e) {
                            appInsights.trackTrace("FatalException 每日须看 chatWithGpt gpt翻译报错信息 errors ： " + e.getMessage() + " translateText : " + sourceText + " 用户：" + shopName);
                            appInsights.trackException(e);
                            return null;
                        }
                    },
                    TimeOutUtils.DEFAULT_TIMEOUT, TimeOutUtils.DEFAULT_UNIT,    // 超时时间
                    TimeOutUtils.DEFAULT_MAX_RETRIES                // 最多重试3次
            );
            if (chatCompletions == null) {
                appInsights.trackTrace("FatalException chatWithGpt chatCompletions is null prompt : " +
                        prompt + "\n " + sourceText + " 用户：" + shopName);
                return new Pair<>(null, 0);
            }

            content = chatCompletions.getChoices().get(0).getMessage().getContent();
            int allToken = chatCompletions.getUsage().getTotalTokens() * OPENAI_MAGNIFICATION;
            int promptToken = chatCompletions.getUsage().getPromptTokens();
            int completionToken = chatCompletions.getUsage().getCompletionTokens();
            appInsights.trackTrace("clickTranslation chatWithGpt 用户： " + shopName + " 翻译语言： " + target + " 翻译的文本： " +
                    sourceText + " token openai : " + content + " all: " + allToken + " input : " + promptToken +
                    " output : " + completionToken);
            return new Pair<>(content, allToken);
        } catch (Exception e) {
            appInsights.trackTrace("clickTranslation " + shopName + " chatWithGpt gpt翻译报错信息 Error occurred while calling GPT: " + e.getMessage() + " sourceText: " + sourceText + " prompt: " + prompt);
            appInsights.trackException(e);
            // 如果重试次数超过2次，则修改翻译状态为4 ：翻译异常，终止翻译流程。
            return new Pair<>(null, 0);
        }
    }
}
