package com.bogda.api.integration;

import com.bogda.api.utils.TimeOutUtils;
import com.google.genai.Client;
import com.google.genai.types.GenerateContentResponse;
import kotlin.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.google.genai.types.*;
import java.util.Base64;
import java.util.List;
import static com.bogda.api.utils.CaseSensitiveUtils.appInsights;
import static com.bogda.api.utils.TimeOutUtils.*;

@Component
public class GeminiIntegration {
    @Autowired
    private Client client;

    public static String Gemini_3_FLASH = "gemini-3-flash-preview";
    private static final int GEMINI_COEFFICIENT = 2;
    /**
     * gemini 文本调用
     */
    public Pair<String, Integer> generateText(String model, String prompt) {
        // 发送对话请求
        try {
            appInsights.trackTrace("model : " + model + " translateText : " + prompt);
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
                return null;
            }
            String text = response.text();
            var usage = response.usageMetadata().orElse(null);
            int inputToken = (usage != null) ? usage.promptTokenCount().orElse(0) : 0;
            int outputToken = (usage != null) ? usage.candidatesTokenCount().orElse(0) : 0;
            int allToken = (usage != null) ? usage.totalTokenCount().orElse(0) * GEMINI_COEFFICIENT : 0;

            appInsights.trackTrace("Gemini 提示词： " + prompt + " 生成文本： " + text + " 请求token: " + inputToken + " 生成token: " + outputToken + " 总token: " + allToken);
            return new Pair<>(text, allToken);
        } catch (Exception e) {
            appInsights.trackTrace("FatalException generateText errors ： " + e.getMessage() + " translateText : " + prompt);
            appInsights.trackException(e);
            return null;
        }
    }

    /**
     * gemini 图片调用
     */
    public Pair<String, Integer> generateImage(String model, String prompt, byte[] picBytes, String mimeType) {
        try {
            appInsights.trackTrace("model : " + model + " translateText : " + prompt + " picBytes : " + picBytes + " mimeType : " + mimeType);
            Content content = Content.fromParts(Part.fromText(prompt), Part.fromBytes(picBytes, mimeType));
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
                return null;
            }
            appInsights.trackTrace("generateImage 模型说明: " + response.toString());
            byte[] translatedBytes = new byte[0];

            for (Part part : response.parts()) {
                if (part.text().isPresent()) {
                    appInsights.trackTrace("模型说明: " + part.text().get());
                }
                if (part.inlineData().isPresent()) {
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
            appInsights.trackTrace("FatalException generateImage errors ： " + e.getMessage() + " translateText : " + prompt);
            appInsights.trackException(e);
            return null;
        }
    }
}
