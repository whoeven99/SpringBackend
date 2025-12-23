package com.bogdatech.integration;

import com.bogdatech.utils.TimeOutUtils;
import com.google.genai.Client;
import com.google.genai.types.GenerateContentResponse;
import kotlin.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import static com.bogdatech.utils.CaseSensitiveUtils.appInsights;
import static com.bogdatech.utils.TimeOutUtils.*;
import static com.bogdatech.utils.TimeOutUtils.DEFAULT_MAX_RETRIES;

@Component
public class GeminiIntegration {
    @Autowired
    private Client client;

    public Pair<String, Integer> generateText(String model, String prompt) {
        // 发送对话请求
        try {
            GenerateContentResponse response = TimeOutUtils.callWithTimeoutAndRetry(() -> {
                        try {
                            return client.models.generateContent(
                                    model,
                                    prompt,
                                    null
                            );
                        } catch (Exception e) {
                            appInsights.trackTrace("FatalException userTranslate call errors ： " + e.getMessage() +
                                    " translateText : " + prompt);
                            appInsights.trackException(e);
                            return null;
                        }
                    },
                    DEFAULT_TIMEOUT, DEFAULT_UNIT,    // 超时时间
                    DEFAULT_MAX_RETRIES                // 最多重试3次
            );

            if (response == null){
                return new Pair<>(null, 0);
            }
            String text = response.text();
            var usage = response.usageMetadata().orElse(null);
            int inputToken = (usage != null) ? usage.promptTokenCount().orElse(0) : 0;
            int outputToken = (usage != null) ? usage.candidatesTokenCount().orElse(0) : 0;
            Integer allToken = (usage != null) ? usage.totalTokenCount().orElse(0) : 0;

            appInsights.trackTrace("Gemini 提示词： " + prompt + " 生成文本： " + text + " 请求token: " + inputToken + " 生成token: " + outputToken + " 总token: " + allToken);
            return new Pair<>(text, allToken);
        } catch (Exception e) {
            appInsights.trackTrace("FatalException userTranslate errors ： " + e.getMessage() + " translateText : " + prompt);
            appInsights.trackException(e);
            return new Pair<>(null, 0);
        }
    }
}
