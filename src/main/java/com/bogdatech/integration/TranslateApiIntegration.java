package com.bogdatech.integration;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.bogdatech.model.controller.request.TranslateRequest;
import com.bogdatech.utils.ApiCodeUtils;
import com.microsoft.applicationinsights.TelemetryClient;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.DigestUtils;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Random;

@Component
public class TranslateApiIntegration {

    @Autowired
    private BaseHttpIntegration baseHttpIntegration;

    private TelemetryClient appInsights = new TelemetryClient();

    @Value("${baidu.api.key}")
    private String apiUrl;

    @Value("${baidu.api.secret}")
    private String secret;

    @Value("${google.api.key}")
    private String apiKey;

    @Value("${microsoft.translation.key}")
    private String microsoftKey;

    @Value("${microsoft.translation.endpoint}")
    private String microsoftEndpoint;

    public String translateText(String text) {
        try {
//            var ans = baseHttpIntegration.sendHttpGet("/google/translate");
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "";
    }

    //百度翻译API
    public String baiDuTranslate(TranslateRequest request) {
        //创建URL
        String encodedQuery = URLEncoder.encode(request.getContent(), StandardCharsets.UTF_8);
//        System.out.println("encodedQuery: " + encodedQuery);
        Random random = new Random();
        String salt = String.valueOf(random.nextInt(10000));
        String sign = DigestUtils.md5DigestAsHex((apiUrl + request.getContent() + salt + secret).getBytes());
        String url = "https://fanyi-api.baidu.com/api/trans/vip/translate?q=" + encodedQuery
                + "&from=" + request.getSource() + "&to=" + request.getTarget() + "&appid=" + apiUrl + "&salt=" + salt + "&sign=" + sign;

        // 创建Httpclient对象
        CloseableHttpClient httpClient = HttpClients.createDefault();
        // 创建httpPost请求
        HttpPost httpPost = new HttpPost(url);
        httpPost.setHeader("Content-Type", "application/x-www-form-urlencoded");
        String result = "";
        // 执行请求
        JSONObject jsonObject;
        try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
            // 获取响应实体并转换为JSON格式
            jsonObject = JSONObject.parseObject(EntityUtils.toString(response.getEntity(), "UTF-8"));
//            appInsights.trackTrace("翻译结果：" + jsonObject);
            // 获取翻译结果
            System.out.println("jsonObject: " + jsonObject);
            if (jsonObject.containsKey("trans_result")) {
                result = jsonObject.getJSONArray("trans_result").getJSONObject(0).getString("dst");
                System.out.println("result: " + result);
            }
            response.close();
            httpClient.close();
        } catch (IOException e) {
            return e.toString();
        }
        return result;
    }

    //谷歌翻译API
    public String googleTranslate(TranslateRequest request) {
        String encodedQuery = URLEncoder.encode(request.getContent(), StandardCharsets.UTF_8);
        String url = "https://translation.googleapis.com/language/translate/v2?key=" + apiKey +
                "&q=" + encodedQuery +
                "&source=" + request.getSource() +
                "&target=" + request.getTarget();
        String result = null;
        CloseableHttpClient httpClient = HttpClients.createDefault();
        // 创建HttpGet请求
        HttpPost httpPost = new HttpPost(url);
        // 执行请求
        JSONObject jsonObject;
        try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
            if (response.getStatusLine().getStatusCode() == 200) {
                // 获取响应实体并转换为字符串
                jsonObject = JSONObject.parseObject(EntityUtils.toString(response.getEntity(), "UTF-8"));
//                appInsights.trackTrace("翻译结果：" + jsonObject);
                // 获取翻译结果
                JSONArray translationsArray = jsonObject.getJSONObject("data").getJSONArray("translations");
                JSONObject translation = translationsArray.getJSONObject(0);
                result = translation.getString("translatedText");
            }
            response.close();
            httpClient.close();
        } catch (IOException e) {
            return e.toString();
        }
        return result;
    }

    //微软翻译API
    public String microsoftTranslate(TranslateRequest request) {
//        String encodedQuery = URLEncoder.encode(request.getContent(), StandardCharsets.UTF_8);
        CloseableHttpClient httpClient = HttpClients.createDefault();
        String micTarget = ApiCodeUtils.microsoftTransformCode(request.getTarget());
        HttpPost httpPost = new HttpPost(microsoftEndpoint + micTarget);

        // 随机生成一个 32 位的 Guid设置请求头
        httpPost.setHeader("Ocp-Apim-Subscription-Key", microsoftKey);
        httpPost.setHeader("Content-Type", "application/json");
        httpPost.setHeader("Ocp-Apim-Subscription-Region", "eastus");

        // 设置请求体
        String requestBody = "[{\n" +
                "    \"Text\": \"" + request.getContent() + "\"\n" +
                "}]";
        System.out.println("requestBody" + requestBody);
        // 发送请求
        String responseContent = null;
        String result = null;
        try {
            StringEntity entity = new StringEntity(requestBody);
            httpPost.setEntity(entity);
            CloseableHttpResponse response = httpClient.execute(httpPost);
            HttpEntity responseEntity = response.getEntity();
            responseContent = EntityUtils.toString(responseEntity);
            // 获取翻译结果
//            appInsights.trackTrace("翻译错误信息：" + JSON.parseArray(responseContent));
//            System.out.println("翻译错误信息：" + responseContent);
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

//        System.out.println("responseContent: " + result);
        return result;
    }
}
