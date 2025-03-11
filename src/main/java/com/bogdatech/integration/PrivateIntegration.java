package com.bogdatech.integration;

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
    public String translateByGoogle(String text, String source, String apiKey, String target) {
        String encodedQuery = URLEncoder.encode(text, StandardCharsets.UTF_8);
        String apiValue = System.getenv(apiKey);
        String url = "https://translation.googleapis.com/language/translate/v2?key=" + apiValue +
                "&q=" + encodedQuery +
                "&source=" + source +
                "&target=" + target +
                "&model=base";
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, null, String.class);
        System.out.println("response: " + response);
        return response.getBody();
    }
}
