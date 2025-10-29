package com.bogdatech.integration;

import com.azure.ai.openai.OpenAIClient;
import com.azure.ai.openai.OpenAIClientBuilder;
import com.azure.ai.openai.models.ChatCompletions;
import com.azure.ai.openai.models.ChatCompletionsOptions;
import com.azure.ai.openai.models.ChatMessage;
import com.azure.ai.openai.models.ChatRole;
import com.azure.core.credential.AzureKeyCredential;
import com.bogdatech.Service.ITranslationCounterService;
import com.bogdatech.logic.redis.TranslationCounterRedisService;
import com.bogdatech.logic.redis.TranslationParametersRedisService;
import com.bogdatech.model.controller.request.TranslateRequest;
import com.bogdatech.utils.CharacterCountUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.List;
import static com.bogdatech.constants.TranslateConstants.OPENAI_MAGNIFICATION;
import static com.bogdatech.utils.AppInsightsUtils.printTranslateCost;
import static com.bogdatech.utils.CaseSensitiveUtils.appInsights;
import static com.bogdatech.utils.RedisKeyUtils.generateProcessKey;
import static com.bogdatech.utils.TimeOutUtils.*;
import static com.bogdatech.utils.TimeOutUtils.DEFAULT_MAX_RETRIES;

@Component
public class ChatGptIntegration {

    @Value("${gpt.endpoint}")
    private String endpoint;
    @Value("${gpt.deploymentName}")
    private String deploymentName;
    @Autowired
    private ITranslationCounterService translationCounterService;
    @Autowired
    private TranslationCounterRedisService translationCounterRedisService;

    /**
     * Azure服务器调用gpt
     */
    public String chatWithGpt(String prompt, String sourceText, String shopName, String target, CharacterCountUtils counter, Integer limitChars, boolean isSingleFlag) {
        // 使用基于密钥的身份验证来初始化 OpenAI 客户端
        OpenAIClient client = new OpenAIClientBuilder()
                .endpoint(endpoint)
                .credential(new AzureKeyCredential(System.getenv("Gpt_ApiKey")))
                .buildClient();

        // 模拟聊天交互
        ChatMessage userMessage = new ChatMessage(ChatRole.USER);
        userMessage.setContent(prompt + "\n" + sourceText); // 合并 prompt 和 sourceText
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
            ChatCompletions chatCompletions = callWithTimeoutAndRetry(() -> {
                        try {
                            return client.getChatCompletions(deploymentName, options);
                        } catch (Exception e) {
                            appInsights.trackTrace("每日须看 chatWithGpt gpt翻译报错信息 errors ： " + e.getMessage() + " translateText : " + sourceText + " 用户：" + shopName);
                            appInsights.trackException(e);
                            return null;
                        }
                    },
                    DEFAULT_TIMEOUT, DEFAULT_UNIT,    // 超时时间
                    DEFAULT_MAX_RETRIES                // 最多重试3次
            );
            if (chatCompletions == null) {
                return sourceText;
            }

            content = chatCompletions.getChoices().get(0).getMessage().getContent();
            int allToken = chatCompletions.getUsage().getTotalTokens() * OPENAI_MAGNIFICATION;
            int promptToken = chatCompletions.getUsage().getPromptTokens();
            int completionToken = chatCompletions.getUsage().getCompletionTokens();
            printTranslateCost(allToken, promptToken, completionToken);
            appInsights.trackTrace("clickTranslation chatWithGpt 用户： " + shopName + " 翻译的文本： " + sourceText + " token openai : " + target + " all: " + allToken + " input : " + promptToken + " output : " + completionToken);
            if (isSingleFlag) {
                translationCounterService.updateAddUsedCharsByShopName(shopName, allToken, limitChars);
            } else {
                translationCounterService.updateAddUsedCharsByShopName(shopName, allToken, limitChars);
                translationCounterRedisService.increaseLanguage(generateProcessKey(shopName, target), allToken);
            }
            counter.addChars(allToken);
            return content;
        } catch (Exception e) {
            appInsights.trackTrace("clickTranslation " + shopName + " chatWithGpt gpt翻译报错信息 Error occurred while calling GPT: " + e.getMessage() + " sourceText: " + sourceText + " prompt: " + prompt);
            appInsights.trackException(e);
            // 如果重试次数超过2次，则修改翻译状态为4 ：翻译异常，终止翻译流程。
            return null;
        }
    }

}
