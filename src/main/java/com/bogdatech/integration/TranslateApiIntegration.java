package com.bogdatech.integration;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.bogdatech.model.controller.request.TranslateRequest;
import com.bogdatech.utils.ApiCodeUtils;
import com.bogdatech.utils.AppInsightsUtils;
import com.bogdatech.utils.ConfigUtils;
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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import static com.bogdatech.utils.CaseSensitiveUtils.appInsights;

@Component
public class TranslateApiIntegration {

//    @Value("${baidu.api.key}")
//    private String apiUrl;
//
//    @Value("${baidu.api.secret}")
//    private String secret;


    @Value("${microsoft.translation.endpoint}")
    private String microsoftEndpoint;

//    //百度翻译API
//    public String baiDuTranslate(TranslateRequest request) {
//        //创建URL
//        String encodedQuery = URLEncoder.encode(request.getContent(), StandardCharsets.UTF_8);
////        appInsights.trackTrace("encodedQuery: " + encodedQuery);
//        Random random = new Random();
//        String salt = String.valueOf(random.nextInt(10000));
//        String sign = DigestUtils.md5DigestAsHex((apiUrl + request.getContent() + salt + secret).getBytes());
//        String url = "https://fanyi-api.baidu.com/api/trans/vip/translate?q=" + encodedQuery
//                + "&from=" + request.getSource() + "&to=" + request.getTarget() + "&appid=" + apiUrl + "&salt=" + salt + "&sign=" + sign;
//
//        // 创建Httpclient对象
//        CloseableHttpClient httpClient = HttpClients.createDefault();
//        // 创建httpPost请求
//        HttpPost httpPost = new HttpPost(url);
//        httpPost.setHeader("Content-Type", "application/x-www-form-urlencoded");
//        String result = "";
//        // 执行请求
//        JSONObject jsonObject;
//        try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
//            // 获取响应实体并转换为JSON格式
//            jsonObject = JSONObject.parseObject(EntityUtils.toString(response.getEntity(), "UTF-8"));
//            // 获取翻译结果
//            if (jsonObject.containsKey("trans_result")) {
//                result = jsonObject.getJSONArray("trans_result").getJSONObject(0).getString("dst");
//            }
//            response.close();
//            httpClient.close();
//        } catch (IOException e) {
//            return e.toString();
//        }
//        return result;
//    }

    //谷歌翻译API
    public static String googleTranslate(TranslateRequest request) {
        String encodedQuery = URLEncoder.encode(request.getContent(), StandardCharsets.UTF_8);
        String apikey = ConfigUtils.getConfig("GOOGLE_API_KEY");
        String url = "https://translation.googleapis.com/language/translate/v2?key=" + apikey +
                "&q=" + encodedQuery +
                "&target=" + request.getTarget() +
                "&model=base";
        //                "&source=" + request.getSource() +
        String result = null;
        // 创建HttpGet请求
        HttpPost httpPost = new HttpPost(url);
        // 执行请求
        JSONObject jsonObject;
        try (CloseableHttpClient httpClient = HttpClients.createDefault();
             CloseableHttpResponse response = httpClient.execute(httpPost)) {
            // 获取响应实体并转换为字符串
            HttpEntity responseEntity = response.getEntity();
            String responseBody = EntityUtils.toString(responseEntity, StandardCharsets.UTF_8);
            jsonObject = JSONObject.parseObject(responseBody);
            // 获取翻译结果
            JSONArray translationsArray = jsonObject.getJSONObject("data").getJSONArray("translations");
            JSONObject translation = translationsArray.getJSONObject(0);
            result = translation.getString("translatedText");
            int totalToken = encodedQuery.length();
            AppInsightsUtils.printTranslateCost(totalToken, totalToken, totalToken);
        } catch (Exception e) {
            appInsights.trackTrace("信息：" + e.getMessage());
        }
        return result;
    }

    //对谷歌翻译API做重试机制
    public static String getGoogleTranslationWithRetry(TranslateRequest request) {
        int maxRetries = 3; // 最大重试次数
        int retryCount = 0; // 当前重试次数
        int baseDelay = 1000; // 初始等待时间（1秒）
        String translatedText = null;

        do {
            try {
                translatedText = googleTranslate(request);
                if (translatedText != null) {
                    return translatedText; // 成功获取翻译，直接返回
                }
            } catch (Exception e) {
                appInsights.trackTrace("翻译 API 调用失败，重试次数：" + retryCount + "，错误信息：" + e.getMessage() + " sourceText: " + request.getContent() + " target: " + request.getTarget());
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

    //微软翻译API
    public String microsoftTranslate(TranslateRequest request) {
//        String encodedQuery = URLEncoder.encode(request.getContent(), StandardCharsets.UTF_8);
        CloseableHttpClient httpClient = HttpClients.createDefault();
        String micTarget = ApiCodeUtils.microsoftTransformCode(request.getTarget());
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
