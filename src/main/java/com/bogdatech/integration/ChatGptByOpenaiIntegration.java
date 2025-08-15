package com.bogdatech.integration;


import com.bogdatech.Service.IUserPrivateTranslateService;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.ChatModel;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.util.List;

import static com.bogdatech.utils.CaseSensitiveUtils.appInsights;


@Component
public class ChatGptByOpenaiIntegration {

    private final IUserPrivateTranslateService iUserPrivateTranslateService;

    @Autowired
    public ChatGptByOpenaiIntegration(IUserPrivateTranslateService iUserPrivateTranslateService) {
        this.iUserPrivateTranslateService = iUserPrivateTranslateService;
    }

    /**
     * 初始化
     * */
    public OpenAIClient initOpenAIClient(String apiKey) {
        return OpenAIOkHttpClient.builder().apiKey(apiKey).build();
    }

    /**
     * gptSDK调用
     */
    public String chatWithGptOpenai(String prompt, String model, Long limitChars, OpenAIClient client, String shopName) {
        // 创建 OpenAI 客户端，连接 DashScope 的兼容接口
//        OpenAIClient client = OpenAIOkHttpClient.builder().apiKey("xxx").build();
        // 创建 ChatCompletion 参数
        ChatModel chatModel = chooseModel(model);
        ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
                .addUserMessage(prompt)
                .model(chatModel)
                .build();

        // 发送请求并获取响应
        ChatCompletion chatCompletion = client.chat().completions().create(params);

        // 提取并打印 content 字段内容
        List<ChatCompletion.Choice> choices = chatCompletion.choices();
        String content = null;
        if (!choices.isEmpty()) {
            //将字符数储存到数据库中
            content = choices.get(0).message().content().orElse("无响应内容");
            appInsights.trackTrace(content);
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
            case "gpt-4o-mini" -> ChatModel.GPT_4O_MINI;
            default -> ChatModel.GPT_4O;
        };
    }
}
