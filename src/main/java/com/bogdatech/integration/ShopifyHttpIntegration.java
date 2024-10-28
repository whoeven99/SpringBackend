package com.bogdatech.integration;

import com.alibaba.fastjson.JSONObject;
import com.bogdatech.model.controller.request.ShopifyRequest;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class ShopifyHttpIntegration {

    public String sendShopifyPost(ShopifyRequest request, String StringQuery) {
        String url = "https://" + request.getShopName() + "/admin/api/" + request.getApiVersion() + "/graphql.json";
        CloseableHttpClient httpClient = HttpClients.createDefault();
        HttpPost httpPost = new HttpPost(url);
        // 设置头部信息
        httpPost.addHeader("X-Shopify-Access-Token", request.getAccessToken());
        httpPost.addHeader("Content-Type", "application/json");

        // 创建查询体
        JSONObject query = new JSONObject();
        query.put("query",
                StringQuery
        );
        String responseContent = null;
        try {
            // 将查询体设置到实体中
            StringEntity input = new StringEntity(query.toString());
            httpPost.setEntity(input);

            CloseableHttpResponse response = httpClient.execute(httpPost);
            HttpEntity entity = response.getEntity();
            responseContent = EntityUtils.toString(entity,"UTF-8");

            response.close();
            httpClient.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return responseContent;

    }

//    public String sendShopifyPost(ShopifyRequest request, String stringQuery) {
//        String url = "https://" + request.getShopName() + "/admin/api/" + request.getApiVersion() + "/graphql.json";
//
//        // 创建 RestTemplate 实例
//        RestTemplate restTemplate = new RestTemplate();
//
//        // 创建 HttpHeaders 实例
//        HttpHeaders headers = new HttpHeaders();
//        headers.set("Content-Type", "application/json");
//        headers.set("X-Shopify-Access-Token", request.getAccessToken());
//
//
//        // 创建请求体
//        JSONObject query = new JSONObject();
//        query.put("query", stringQuery);
//
//        // 创建 HttpEntity
//        HttpEntity<String> entity = new HttpEntity<>(query.toString(), headers);
//        // 发起 POST 请求
//        ResponseEntity<String> response = restTemplate.exchange(
//                url,
//                HttpMethod.POST,
//                entity,
//                String.class
//        );
//        // 获取响应内容
//        String responseContent = response.getBody();
//        return responseContent;
//    }
}


