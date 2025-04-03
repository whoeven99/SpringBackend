package com.bogdatech.integration;

import com.volcengine.ark.runtime.model.completion.chat.ChatCompletionRequest;
import com.volcengine.ark.runtime.model.completion.chat.ChatMessage;
import com.volcengine.ark.runtime.model.completion.chat.ChatMessageRole;
import com.volcengine.ark.runtime.service.ArkService;

import java.util.ArrayList;
import java.util.List;

import static com.bogdatech.integration.ALiYunTranslateIntegration.cueWordSingle;

public class ArkTranslateIntegration {

    // 定义 ArkService 实例变量，用于与外部服务交互
    private static ArkService arkService;

    // 私有构造方法，防止外部通过 new 创建实例，确保单例性
    public ArkTranslateIntegration() {
    }

    // 初始化方法，由 @Bean 的 initMethod 调用
    // 在 Spring 容器创建 Bean 时执行，用于初始化 ArkService
    public void init() {
        // 从环境变量中获取 API Key
//        String apiKey = System.getenv("ARK_API_KEY");
        String apiKey = "dcb8dfb3-6030-4c62-a04d-1b877eaa06d1";
        // 检查 API Key 是否为空，若为空则抛出异常，避免服务启动失败
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalStateException("环境变量 ARK_API_KEY 未设置");
        }
        // 使用建造者模式创建 ArkService 实例，并赋值给成员变量
        arkService = ArkService.builder().apiKey(apiKey).build();
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

    // 调用豆包API
    public static String getChatResponse(String targetCode, String type, String sourceCode) {
        try {
            List<ChatMessage> messages = new ArrayList<>();
            ChatMessage systemMessage = ChatMessage.builder()
                    .role(ChatMessageRole.SYSTEM)
                    .content(cueWordSingle(targetCode, type))
                    .build();
            ChatMessage userMessage = ChatMessage.builder()
                    .role(ChatMessageRole.USER)
                    .content(sourceCode)
                    .build();
            messages.add(systemMessage);
            messages.add(userMessage);

            ChatCompletionRequest request = ChatCompletionRequest.builder()
                    .model("doubao-1-5-pro-256k-250115")
                    .messages(messages)
                    .build();

            StringBuilder response = new StringBuilder();
            arkService.createChatCompletion(request)
                    .getChoices()
                    .forEach(choice -> response.append(choice.getMessage().getContent()));

            return response.toString();
        } catch (Exception e) {
            throw new RuntimeException("Chat completion failed", e);
        }
    }
}
