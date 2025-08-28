package com.bogdatech.integration;

import com.bogdatech.Service.ITranslationCounterService;
import com.bogdatech.utils.CharacterCountUtils;
import com.volcengine.ark.runtime.model.completion.chat.ChatCompletionRequest;
import com.volcengine.ark.runtime.model.completion.chat.ChatCompletionResult;
import com.volcengine.ark.runtime.model.completion.chat.ChatMessage;
import com.volcengine.ark.runtime.model.completion.chat.ChatMessageRole;
import com.volcengine.ark.runtime.service.ArkService;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.bogdatech.constants.TranslateConstants.MAGNIFICATION;
import static com.bogdatech.logic.TranslateService.userTranslate;
import static com.bogdatech.utils.AppInsightsUtils.printTranslateCost;
import static com.bogdatech.utils.CaseSensitiveUtils.appInsights;
import static com.bogdatech.utils.MapUtils.getTranslationStatusMap;

@Component
public class ArkTranslateIntegration {

    private final ITranslationCounterService translationCounterService;


    // 定义 ArkService 实例变量，用于与外部服务交互
    private static ArkService arkService;
    private static final String DOUBAO_API_KEY = "DOUBAO_API_KEY";

    public ArkTranslateIntegration(ITranslationCounterService translationCounterService) {
        this.translationCounterService = translationCounterService;
    }
    // 私有构造方法，防止外部通过 new 创建实例，确保单例性


    // 初始化方法，由 @Bean 的 initMethod 调用
    // 在 Spring 容器创建 Bean 时执行，用于初始化 ArkService
    public void init() {
        // 从环境变量中获取 API Key
        try {
            String apiKey = System.getenv(DOUBAO_API_KEY);
            // 检查 API Key 是否为空，若为空则抛出异常，避免服务启动失败
            if (apiKey == null || apiKey.isEmpty()) {
                appInsights.trackTrace("豆包API Key 未设置:  " + apiKey);
                //            throw new IllegalStateException("环境变量 ARK_API_KEY 未设置");
            }
            // 使用建造者模式创建 ArkService 实例，并赋值给成员变量
            arkService = ArkService.builder().apiKey(apiKey).timeout(Duration.ofSeconds(120)).retryTimes(2).build();
        } catch (Exception e) {
//            appInsights.trackTrace("豆包模型初始化失败！！！" + e.getMessage());
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
            ChatCompletionResult chatCompletion = arkService.createChatCompletion(request);
            chatCompletion.getChoices().forEach(choice -> response.append(choice.getMessage().getContent()));
            long totalTokens = (long) (chatCompletion.getUsage().getTotalTokens() * MAGNIFICATION);
            int totalTokensInt = (int) totalTokens;
            Map<String, Object> translationStatusMap = getTranslationStatusMap(sourceText, 2);
            userTranslate.put(shopName, translationStatusMap);
            long completionTokens = chatCompletion.getUsage().getCompletionTokens();
            long promptTokens = chatCompletion.getUsage().getPromptTokens();
            printTranslateCost(totalTokensInt, (int) promptTokens, (int)completionTokens);
            appInsights.trackTrace("clickTranslation douBaoTranslate " + shopName + " 用户 token doubao: " + sourceText + " target : " + response + " all: " + totalTokens + " input: " + promptTokens + " output: " + completionTokens);
            translationCounterService.updateAddUsedCharsByShopName(shopName, totalTokensInt, limitChars);
            countUtils.addChars(totalTokensInt);
            return response.toString();
        } catch (Exception e) {
            appInsights.trackTrace("clickTranslation " + shopName + " douBaoTranslate 豆包翻译失败 errors : " + e.getMessage());
            appInsights.trackException(e);
            return sourceText;
        }
    }

    public static String douBaoPromptTranslate(String prompt, String sourceText, CharacterCountUtils countUtils) {
        try {
            List<ChatMessage> messages = new ArrayList<>();
            ChatMessage userMessage = ChatMessage.builder()
                    .role(ChatMessageRole.USER)
                    .content(prompt)
                    .build();
            messages.add(userMessage);

            ChatCompletionRequest request = ChatCompletionRequest.builder()
                    .model("doubao-1-5-pro-256k-250115") //256k token
                    .messages(messages)
                    .build();

            StringBuilder response = new StringBuilder();
            ChatCompletionResult chatCompletion = arkService.createChatCompletion(request);
            chatCompletion.getChoices().forEach(choice -> response.append(choice.getMessage().getContent()));
            long totalTokens = (long) (chatCompletion.getUsage().getTotalTokens() * MAGNIFICATION);
            int totalTokensInt = (int) totalTokens;
            long completionTokens = chatCompletion.getUsage().getCompletionTokens();
            long promptTokens = chatCompletion.getUsage().getPromptTokens();
            printTranslateCost(totalTokensInt, (int) promptTokens, (int)completionTokens);
            appInsights.trackTrace("token doubao: " + sourceText + "all: " + totalTokens + " input: " + promptTokens + " output: " + completionTokens);
            countUtils.addChars(totalTokensInt);
            return response.toString();
        } catch (Exception e) {
            appInsights.trackTrace("豆包翻译失败 errors : " + e.getMessage());
            return sourceText;
        }
    }
}
