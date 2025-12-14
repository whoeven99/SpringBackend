package com.bogdatech.intetraion;

import com.azure.ai.openai.OpenAIClient;
import com.bogdatech.integration.ChatGptIntegration;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.azure.ai.openai.models.ChatCompletions;
import com.azure.ai.openai.models.ChatCompletionsOptions;
import com.azure.ai.openai.models.ChatMessage;
import com.azure.ai.openai.models.ChatRole;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
@SpringBootTest
public class ChatGptIntegrationTest {
    @Autowired
    private ChatGptIntegration chatGptIntegration;

    @MockBean
    private OpenAIClient client;

    @Test
    public void testChatWithGptTimeout() throws Exception {
        // 模拟超时行为
//        doThrow(new TimeoutException("模拟超时")).when(client).getChatCompletions(any(String.class), any(ChatCompletionsOptions.class));
//
//        String prompt = "测试超时";
//        String sourceText = "源文本";
//        String shopName = "测试店铺";
//        String target = "目标语言";
//
//        // 调用方法并验证结果
//        var result = chatGptIntegration.chatWithGpt(prompt, sourceText, shopName, target);
//
//        assertNull(result.getFirst(), "内容应为 null");
//        assertEquals(0, result.getSecond(), "总 token 应为 0");
    }
}
