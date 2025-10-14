package com.bogdatech.integration;

import com.bogdatech.Service.ITranslationCounterService;
import com.bogdatech.utils.CharacterCountUtils;
import com.volcengine.ark.runtime.model.completion.chat.ChatCompletionRequest;
import com.volcengine.ark.runtime.model.completion.chat.ChatCompletionResult;
import com.volcengine.ark.runtime.model.completion.chat.ChatMessage;
import com.volcengine.ark.runtime.model.completion.chat.ChatMessageRole;
import com.volcengine.ark.runtime.service.ArkService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import static com.bogdatech.constants.TranslateConstants.MAGNIFICATION;
import static com.bogdatech.utils.AppInsightsUtils.printTranslateCost;
import static com.bogdatech.utils.CaseSensitiveUtils.appInsights;
import static com.bogdatech.utils.TimeOutUtils.*;
import static com.bogdatech.utils.TimeOutUtils.DEFAULT_MAX_RETRIES;

@Component
public class ArkTranslateIntegration {
    @Autowired
    private ITranslationCounterService translationCounterService;


    // 定义 ArkService 实例变量，用于与外部服务交互
    private static ArkService arkService;
    private static final String DOUBAO_API_KEY = "DOUBAO_API_KEY";
    // 初始化方法，由 @Bean 的 initMethod 调用
    // 在 Spring 容器创建 Bean 时执行，用于初始化 ArkService
    public void init() {
        // 从环境变量中获取 API Key
        try {
            String apiKey = System.getenv(DOUBAO_API_KEY);
            // 检查 API Key 是否为空，若为空则抛出异常，避免服务启动失败
            if (apiKey == null || apiKey.isEmpty()) {
                appInsights.trackTrace("豆包API Key 未设置:  " + apiKey);
            }
            // 使用建造者模式创建 ArkService 实例，并赋值给成员变量
            arkService = ArkService.builder().apiKey(apiKey).timeout(Duration.ofSeconds(120)).retryTimes(2).build();
        } catch (Exception e) {
            appInsights.trackTrace("豆包模型初始化失败！！！" + e.getMessage());
        }
    }

    // 销毁方法，由 @Bean 的 destroyMethod 调用
    // 在 Spring 容器销毁 Bean 时执行，用于清理资源
    public void shutdown() {
        // 检查 arkService 是否已初始化，若已初始化则关闭其执行器
        if (arkService != null) {
            arkService.shutdownExecutor();
            arkService = null; // 清空静态变量，避免内存泄漏
        }
    }

    // 提供获取 ArkService 实例的方法，供外部使用
    public ArkService getArkService() {
        return arkService;
    }

    /**
     * 调用豆包API
     */
    public String douBaoTranslate(String shopName, String prompt, String sourceText, CharacterCountUtils countUtils, Integer limitChars) {
        try {
            List<ChatMessage> messages = new ArrayList<>();
            ChatMessage systemMessage = ChatMessage.builder()
                    .role(ChatMessageRole.SYSTEM)
                    .content(prompt)
                    .build();
            ChatMessage userMessage = ChatMessage.builder()
                    .role(ChatMessageRole.USER)
                    .content(sourceText)
                    .build();
            messages.add(systemMessage);
            messages.add(userMessage);

            ChatCompletionRequest request = ChatCompletionRequest.builder()
                    .model("doubao-1-5-pro-256k-250115") //256k token
                    .messages(messages)
                    .build();

            StringBuilder response = new StringBuilder();
            ChatCompletionResult chatCompletion = callWithTimeoutAndRetry(() -> {
                        try {
                            return arkService.createChatCompletion(request);
                        } catch (Exception e) {
                            appInsights.trackTrace("每日须看 douBaoTranslate 豆包翻译报错信息 errors ： " + e.getMessage() + " translateText : " + sourceText + " 用户：" + shopName);
                            appInsights.trackException(e);
                            return null;
                        }
                    },
                    DEFAULT_TIMEOUT, DEFAULT_UNIT,    // 超时时间
                    DEFAULT_MAX_RETRIES                // 最多重试3次
            );
            if (chatCompletion == null) {
                return sourceText;
            }
            chatCompletion.getChoices().forEach(choice -> response.append(choice.getMessage().getContent()));
            long totalTokens = (long) (chatCompletion.getUsage().getTotalTokens() * MAGNIFICATION);
            int totalTokensInt = (int) totalTokens;
            long completionTokens = chatCompletion.getUsage().getCompletionTokens();
            long promptTokens = chatCompletion.getUsage().getPromptTokens();
            printTranslateCost(totalTokensInt, (int) promptTokens, (int) completionTokens);
            appInsights.trackTrace("clickTranslation douBaoTranslate " + shopName + " 用户 token doubao: " + sourceText + " target : " + response + " all: " + totalTokens + " input: " + promptTokens + " output: " + completionTokens);
            translationCounterService.updateAddUsedCharsByShopName(shopName, totalTokensInt, limitChars);
            countUtils.addChars(totalTokensInt);
            return response.toString();
        } catch (Exception e) {
            appInsights.trackTrace("clickTranslation " + shopName + " douBaoTranslate 豆包翻译报错信息 errors : " + e.getMessage() + " sourceText : " + sourceText);
            appInsights.trackException(e);
            return sourceText;
        }
    }

    public String douBaoPromptTranslate(String shopName, String prompt, String sourceText, CharacterCountUtils countUtils, Integer limitChars) {
        try {
            List<ChatMessage> messages = new ArrayList<>();
            appInsights.trackTrace("messages 用户 " + shopName);
            ChatMessage userMessage = ChatMessage.builder()
                    .role(ChatMessageRole.USER)
                    .content(prompt + sourceText)
                    .build();
            messages.add(userMessage);
            appInsights.trackTrace("ChatMessage 用户 " + shopName);
            ChatCompletionRequest request = ChatCompletionRequest.builder()
                    .model("doubao-1-5-pro-256k-250115") //256k token
                    .messages(messages)
                    .build();
            appInsights.trackTrace("ChatCompletionRequest 用户 " + shopName);
            StringBuilder response = new StringBuilder();
            appInsights.trackTrace("StringBuilder 用户 " + shopName);
            ChatCompletionResult chatCompletion = callWithTimeoutAndRetry(() -> {
                        try {
                            return arkService.createChatCompletion(request);
                        } catch (Exception e) {
                            appInsights.trackTrace("每日须看 douBaoPromptTranslate 豆包翻译报错信息 errors ： " + e.getMessage() + " translateText : " + sourceText + " 用户：" + shopName);
                            appInsights.trackException(e);
                            return null;
                        }
                    },
                    DEFAULT_TIMEOUT, DEFAULT_UNIT,    // 超时时间
                    DEFAULT_MAX_RETRIES                // 最多重试3次
            );
            if (chatCompletion == null) {
                return sourceText;
            }
            appInsights.trackTrace("ChatCompletionResult 用户 " + shopName);
            chatCompletion.getChoices().forEach(choice -> response.append(choice.getMessage().getContent()));
            appInsights.trackTrace("chatCompletion 用户 " + shopName);
            long totalTokens = (long) (chatCompletion.getUsage().getTotalTokens() * MAGNIFICATION);
            appInsights.trackTrace("totalTokens 用户 " + shopName);
            int totalTokensInt = (int) totalTokens;
            appInsights.trackTrace("totalTokensInt 用户 " + shopName);
            long completionTokens = chatCompletion.getUsage().getCompletionTokens();
            appInsights.trackTrace("completionTokens 用户 " + shopName);
            long promptTokens = chatCompletion.getUsage().getPromptTokens();
            appInsights.trackTrace("promptTokens 用户 " + shopName);
            printTranslateCost(totalTokensInt, (int) promptTokens, (int) completionTokens);
            appInsights.trackTrace("clickTranslation douBaoPromptTranslate " + shopName + " 用户 token doubao: " + sourceText + " target : " + response + "all: " + totalTokens + " input: " + promptTokens + " output: " + completionTokens);
            translationCounterService.updateAddUsedCharsByShopName(shopName, totalTokensInt, limitChars);
            appInsights.trackTrace("updateAddUsedCharsByShopName 用户 " + shopName);
            countUtils.addChars(totalTokensInt);
            appInsights.trackTrace("countUtils 用户 " + shopName);
            return response.toString();
        } catch (Exception e) {
            appInsights.trackTrace("clickTranslation " + shopName + " douBaoPromptTranslate 豆包翻译报错信息 errors : " + e.getMessage() + " sourceText : " + sourceText);
            appInsights.trackException(e);
            return sourceText;
        }
    }
}
