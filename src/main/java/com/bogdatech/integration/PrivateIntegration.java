package com.bogdatech.integration;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.microsoft.applicationinsights.TelemetryClient;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Component
public class PrivateIntegration {
    private final RestTemplate restTemplate;
    static TelemetryClient appInsights = new TelemetryClient();
    public PrivateIntegration(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * 调用 OpenAI ChatGPT 接口进行对话，并返回 GPT 的回复文本
     *
     * @param prompt 用户输入的对话内容
     * @param model GPT 模型名称
     * @param apiKey OpenAI API 密钥
     * @param systemPrompt 系统提示文本
     * @return GPT 回复的文本
     */
    public String translateByGpt(String prompt, String model, String apiKey, String systemPrompt) {
        // 创建 OpenAI 客户端
        String url = "https://api.openai.com/v1/chat/completions";
        String apiValue = System.getenv(apiKey);
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + apiValue);
        headers.set("Content-Type", "application/json");

        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("messages", new Object[]{
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", prompt)
        });

        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(body, headers);
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, requestEntity, String.class);

        return response.getBody();
    }

    /**
     * 调用google接口进行对话，并返回回复文本
     *
     * @param text 用户的文本信息
     * @param source 源语言
     * @param apiKey google 密钥
     * @param target 目标语言
     * @return GPT 回复的文本
     */
    public static String googleTranslate(String text, String source, String apiKey, String target) {
        String encodedQuery = URLEncoder.encode(text, StandardCharsets.UTF_8);
        String url = "https://translation.googleapis.com/language/translate/v2?key=" + apiKey +
                "&q=" + encodedQuery +
                "&source=" + source +
                "&target=" + target +
                "&model=base";
        String result = null;
        // 创建HttpGet请求
        HttpPost httpPost = new HttpPost(url);
        // 执行请求
        JSONObject jsonObject;
        try (CloseableHttpClient httpClient = HttpClients.createDefault();
             CloseableHttpResponse response = httpClient.execute(httpPost)) {
            // 获取响应实体并转换为字符串
            org.apache.http.HttpEntity responseEntity = response.getEntity();
            String responseBody = EntityUtils.toString(responseEntity, StandardCharsets.UTF_8);
            jsonObject = JSONObject.parseObject(responseBody);
            // 获取翻译结果
            JSONArray translationsArray = jsonObject.getJSONObject("data").getJSONArray("translations");
            JSONObject translation = translationsArray.getJSONObject(0);
            result = translation.getString("translatedText");
        } catch (Exception e) {
            appInsights.trackTrace("信息：" + e.getMessage());
        }
        return result;
    }

    //对谷歌翻译API做重试机制
    public static String getGoogleTranslationWithRetry(String text, String source, String apiKey, String target) {
        int maxRetries = 3; // 最大重试次数
        int retryCount = 0; // 当前重试次数
        int baseDelay = 1000; // 初始等待时间（1秒）
        String translatedText = null;

        do {
            try {
                translatedText = googleTranslate(text, source, apiKey, target);
                if (translatedText != null) {
                    return translatedText; // 成功获取翻译，直接返回
                }
            } catch (Exception e) {
                appInsights.trackTrace("翻译 API 调用失败，重试次数：" + retryCount + "，错误信息：" + e.getMessage());
            }

            try {
                Thread.sleep(baseDelay * (long) Math.pow(2, retryCount)); // 指数退避
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // 恢复中断状态
            }

            retryCount++;
        } while (retryCount < maxRetries);

        return null; // 重试后仍然失败，返回 null
    }
}
