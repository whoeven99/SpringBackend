package com.bogdatech.integration;

import com.bogdatech.utils.TimeOutUtils;
import com.google.genai.Client;
import com.google.genai.types.*;
import kotlin.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Base64;
import java.util.List;

import static com.bogdatech.utils.CaseSensitiveUtils.appInsights;
import static com.bogdatech.utils.TimeOutUtils.*;
import static com.bogdatech.utils.TimeOutUtils.DEFAULT_MAX_RETRIES;

@Component
public class GeminiIntegration {
    @Autowired
    private Client client;

    /**
     * gemini 文本调用
     */
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

            if (response == null) {
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

    /**
     * gemini 图片调用
     */
    public Pair<String, Integer> generateImage(String model, String prompt, byte[] picBytes, String picType) {
        try {
            Content content = Content.fromParts(Part.fromText(prompt), Part.fromBytes(picBytes, picType));
            GenerateContentConfig config = GenerateContentConfig.builder().responseModalities(List.of("IMAGE")).build();// 关键：指定输出图片
            GenerateContentResponse response = TimeOutUtils.callWithTimeoutAndRetry(() -> {
                        try {
                            return client.models.generateContent(
                                    model,
                                    content,
                                    config
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

            if (response == null) {
                return new Pair<>(null, 0);
            }

            byte[] translatedBytes = new byte[0];

            for (Part part : response.parts()) {
                if (part.text().isPresent()) {
                    appInsights.trackTrace("模型说明: " + part.text().get());
                } else if (part.inlineData().isPresent()) {
                    var blob = part.inlineData().get();
                    if (blob.data().isPresent()) {
                        translatedBytes = blob.data().get();
                        appInsights.trackTrace("翻译后的图片已保存为: " + Base64.getEncoder().encodeToString(blob.data().get()));
                    }
                }
            }


            var usage = response.usageMetadata().orElse(null);
            int inputToken = (usage != null) ? usage.promptTokenCount().orElse(0) : 0;
            int outputToken = (usage != null) ? usage.candidatesTokenCount().orElse(0) : 0;
            Integer allToken = (usage != null) ? usage.totalTokenCount().orElse(0) : 0;

            appInsights.trackTrace("Gemini 提示词： " + prompt + " 生成文本： " + Base64.getEncoder().encodeToString(translatedBytes) + " 请求token: " + inputToken + " 生成token: " + outputToken + " 总token: " + allToken);
            return new Pair<>(Base64.getEncoder().encodeToString(translatedBytes), allToken);
        } catch (Exception e) {
            appInsights.trackTrace("FatalException userTranslate errors ： " + e.getMessage() + " translateText : " + prompt);
            appInsights.trackException(e);
            return new Pair<>(null, 0);
        }
    }
}
