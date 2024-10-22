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

    public String sendShopifyPost(ShopifyRequest request) {
        String url = "https://" + request.getShopName() + ".myshopify.com/api/" + request.getApiVersion() + "/graphql.json";
        CloseableHttpClient httpClient = HttpClients.createDefault();
        HttpPost httpPost = new HttpPost(url);

        // 设置头部信息
        httpPost.addHeader("Content-Type", "application/json");
        httpPost.addHeader("X-Shopify-Access-Token", request.getAccessToken());

        // 创建查询体
        JSONObject query = new JSONObject();
        query.put("query",
                "query { products(first: 3) { edges { node { id title } } } }"
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
}
