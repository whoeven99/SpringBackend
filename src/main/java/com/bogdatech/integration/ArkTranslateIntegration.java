package com.bogdatech.integration;

import com.volcengine.ark.runtime.model.completion.chat.ChatCompletionRequest;
import com.volcengine.ark.runtime.model.completion.chat.ChatMessage;
import com.volcengine.ark.runtime.model.completion.chat.ChatMessageRole;
import com.volcengine.ark.runtime.service.ArkService;

import java.util.ArrayList;
import java.util.List;

import static com.bogdatech.integration.ALiYunTranslateIntegration.cueWordSingle;

public class ArkTranslateIntegration {
    private ArkTranslateIntegration() {
        // 防止反射创建实例
    }

    // 静态内部类，只有在调用时才会加载
    private static class SingletonHolder {
        private static final ArkService INSTANCE;
        static {
            String apiKey = System.getenv("ARK_API_KEY");
            INSTANCE = ArkService.builder().apiKey(apiKey).build();
        }
    }

    //创建实例
    public static ArkService getInstance() {
        return SingletonHolder.INSTANCE;
    }

    //关闭实例
    public static void shutdown() {
        SingletonHolder.INSTANCE.shutdownExecutor();
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
                    .model("<Model>")
                    .messages(messages)
                    .build();

            StringBuilder response = new StringBuilder();
            getInstance().createChatCompletion(request)
                    .getChoices()
                    .forEach(choice -> response.append(choice.getMessage().getContent()));

            return response.toString();
        } catch (Exception e) {
            throw new RuntimeException("Chat completion failed", e);
        }
    }
}
