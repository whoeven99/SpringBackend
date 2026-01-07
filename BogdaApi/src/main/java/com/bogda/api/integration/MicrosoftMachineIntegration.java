package com.bogda.api.integration;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.bogda.api.model.controller.request.TranslateRequest;
import com.bogda.api.utils.ConfigUtils;
import com.bogda.api.utils.ModuleCodeUtils;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static com.bogda.api.utils.CaseSensitiveUtils.appInsights;

@Component
public class MicrosoftMachineIntegration {
    @Value("${microsoft.translation.endpoint}")
    private String microsoftEndpoint;

    //微软机器翻译API
    public String microsoftTranslate(TranslateRequest request) {
//        String encodedQuery = URLEncoder.encode(request.getContent(), StandardCharsets.UTF_8);
        CloseableHttpClient httpClient = HttpClients.createDefault();
        String micTarget = ModuleCodeUtils.microsoftTransformCode(request.getTarget());
        HttpPost httpPost = new HttpPost(microsoftEndpoint + micTarget);

        // 随机生成一个 32 位的 Guid设置请求头
        httpPost.setHeader("Ocp-Apim-Subscription-Key", ConfigUtils.getConfig("Microsoft.Translation.Key"));
        httpPost.setHeader("Content-Type", "application/json");
        httpPost.setHeader("Ocp-Apim-Subscription-Region", "eastus");

        // 设置请求体
        String requestBody = "[{\n" +
                "    \"Text\": \"" + request.getContent() + "\"\n" +
                "}]";
//        appInsights.trackTrace("requestBody" + requestBody);
        // 发送请求
        String responseContent = null;
        String result = null;
        try {
            StringEntity entity = new StringEntity(requestBody);
            httpPost.setEntity(entity);
            CloseableHttpResponse response = httpClient.execute(httpPost);
            HttpEntity responseEntity = response.getEntity();
            responseContent = EntityUtils.toString(responseEntity, StandardCharsets.UTF_8);
            // 获取翻译结果
            appInsights.trackTrace("翻译错误信息：" + JSON.parseArray(responseContent));
//            appInsights.trackTrace("翻译错误信息：" + responseContent);
            JSONArray jsonArray = JSON.parseArray(responseContent);
            for (int i = 0; i < jsonArray.size(); i++) {
                // 获取当前的 JSONObject
                JSONObject jsonObject = jsonArray.getJSONObject(i);
                // 获取 translations 数组
                JSONArray translations = jsonObject.getJSONArray("translations");
                // 遍历 translations 数组
                for (int j = 0; j < translations.size(); j++) {
                    // 获取当前的翻译对象
                    JSONObject translation = translations.getJSONObject(j);
                    // 获取 text 的值
                    result = translation.getString("text");
                }
            }
            response.close();
            httpClient.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return result;
    }
}
