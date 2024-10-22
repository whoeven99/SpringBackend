package com.bogdatech.integration;

import com.bogdatech.model.controller.request.ShopifyRequest;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class ShopifyHttpIntegration {

    public String sendShopifyGet(ShopifyRequest request) {
        CloseableHttpClient httpClient = HttpClients.createDefault();
        HttpGet httpGet = new HttpGet(request.getUrl());
        httpGet.addHeader("Content-Type", "application/json");
        httpGet.addHeader("X-Shopify-Access-Token", request.getAccessToken());
        String responseContent = null;
        try {
            CloseableHttpResponse response = httpClient.execute(httpGet);
            HttpEntity entity = response.getEntity();
            responseContent = EntityUtils.toString(entity, "UTF-8");

            response.close();
            httpClient.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return responseContent;

    }
}
