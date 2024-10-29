package com.bogdatech.integration;

import com.alibaba.fastjson.JSONObject;
import com.bogdatech.model.controller.request.TranslateRequest;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.DigestUtils;

import java.io.IOException;
import java.util.Random;

@Component
public class TranslateApiIntegration {

    @Autowired
    private BaseHttpIntegration baseHttpIntegration;

    @Value("${baidu.api.key}")
    private String apiUrl;

    @Value("${baidu.api.secret}")
    private String secret;

    @Value("${google.api.key}")
    private String apiKey;

    public String translateText(String text) {
        try {
//            var ans = baseHttpIntegration.sendHttpGet("/google/translate");
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "";
    }

    public String baiDuTranslate(TranslateRequest request) {
        //创建URL
        Random random = new Random();
        String salt = String.valueOf(random.nextInt(10000));
        String sign = DigestUtils.md5DigestAsHex((apiUrl + request.getContent() + salt + secret).getBytes());
        String url = "https://fanyi-api.baidu.com/api/trans/vip/translate?q=" + request.getContent()
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
            // 获取翻译结果
            result = jsonObject.getJSONArray("trans_result").getJSONObject(0).getString("dst");
        } catch (IOException e) {
            return e.toString();
        }
        return result;
    }

    public String googleTranslate(TranslateRequest request) {
        String url = "https://translation.googleapis.com/language/translate/v2?key=" + apiKey +
                "&q=" + request.getContent() +
                "&source=" + request.getSource() +
                "&target=" + request.getTarget();
        String result = null;
        CloseableHttpClient httpClient = HttpClients.createDefault();
        // 创建HttpGet请求
        HttpPost httpPost = new HttpPost(url);
        // 执行请求
        try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
            if (response.getStatusLine().getStatusCode() == 200) {
                // 获取响应实体并转换为字符串
                result = EntityUtils.toString(response.getEntity());
            }
        } catch (IOException e) {
            return e.toString();
        }
        return result;
    }
}
