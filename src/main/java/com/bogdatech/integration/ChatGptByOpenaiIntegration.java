package com.bogdatech.integration;

import com.azure.security.keyvault.secrets.SecretClient;
import com.azure.security.keyvault.secrets.models.KeyVaultSecret;
import com.bogdatech.utils.CharacterCountUtils;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.ChatModel;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.bogdatech.utils.UserPrivateUtils.getApiKey;

@Component
public class ChatGptByOpenaiIntegration {

    /**
     * 初始化
     * */
    public OpenAIClient initOpenAIClient(String apiKey) {
        return OpenAIOkHttpClient.builder().apiKey(apiKey).build();
    }

    /**
     * gptSDK调用
     */
    public String chatWithGptOpenai(String prompt, String model, CharacterCountUtils counter, Long limitChars, OpenAIClient client, String shopName) {
        // 创建 OpenAI 客户端，连接 DashScope 的兼容接口
//        OpenAIClient client = OpenAIOkHttpClient.builder().apiKey("xxx").build();
        // 创建 ChatCompletion 参数
        ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
                .addUserMessage(prompt)
                .model(chooseModel(model))
                .build();

        // 发送请求并获取响应
        ChatCompletion chatCompletion = client.chat().completions().create(params);

        // 提取并打印 content 字段内容
        List<ChatCompletion.Choice> choices = chatCompletion.choices();
        String content = null;
        if (!choices.isEmpty()) {
            content = choices.get(0).message().content().orElse("无响应内容");
            System.out.println(content);
        }
        return content;
    }

    /**
     * 根据传入的值选择对应的模型
     */
    public ChatModel chooseModel(String model) {
        return switch (model) {
            // 4o,4.1
            case "gpt-4o" -> ChatModel.GPT_4O;
            case "gpt-4.1" -> ChatModel.GPT_4_1;
            default -> ChatModel.GPT_4O;
        };
    }
}
