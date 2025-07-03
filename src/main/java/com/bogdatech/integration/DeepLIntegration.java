package com.bogdatech.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static com.bogdatech.constants.KlaviyoConstants.CONTENT_TYPE;
import static com.bogdatech.logic.TranslateService.OBJECT_MAPPER;

@Component
public class DeepLIntegration {
    private static final String API_KEY = "";
    private static final String FREE_API_URL = "https://api-free.deepl.com/v2/translate";
    private static final String API_URL = "https://api.deepl.com/v2/translate";

    public static String translateByDeepLWithRetry(List<String> text, String targetLang) {
        CloseableHttpClient httpClient = HttpClients.createDefault();
        HttpPost httpPost = new HttpPost(API_URL);

        //设置请求头
        httpPost.setHeader("Content-Type", CONTENT_TYPE);
        httpPost.setHeader("Authorization", "DeepL-Auth-Key " + API_KEY);

        // 设置请求体
        String requestBody = "{\n" +
                "    \"text\": " + text + ", \n" +
                "    \"target_lang\": \"" + targetLang + "\"\n" +
                "}";
        try {
            StringEntity entity = new StringEntity(requestBody);
            httpPost.setEntity(entity);

            try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                int statusCode = response.getStatusLine().getStatusCode();
                String result = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);

                if (statusCode == 200) {
                    return parseTranslatedText(result);
                } else {
                    System.out.println("Deepl API 请求失败: 状态码 = " + statusCode);
                    System.out.println("响应内容 = " + result);
                    return "翻译失败（HTTP 状态码: " + statusCode + "）";
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            return "翻译失败（IO 异常: " + e.getMessage() + "）";
        } catch (Exception e) {
            e.printStackTrace();
            return "翻译失败（未知异常: " + e.getMessage() + "）";
        } finally {
            try {
                httpClient.close();
            } catch (IOException e) {
                System.out.println("关闭 HttpClient 异常: " + e.getMessage());
            }
        }
    }

    // 解析 JSON 响应
    private static String parseTranslatedText(String json) throws IOException {
        JsonNode root = OBJECT_MAPPER.readTree(json);
        JsonNode translations = root.path("translations");
        if (translations.isArray() && !translations.isEmpty()) {
            return translations.get(0).path("text").asText();
        }
        return "翻译结果解析失败";
    }
}
