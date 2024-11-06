package com.bogdatech.integration;

import com.bogdatech.model.controller.request.ShopifyRequest;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class TestingEnvironmentIntegration {

    public String sendShopifyGet(ShopifyRequest request, String api) {
        String url = "http://springbackendservice-e3hgbjgqafb9cpdh.canadacentral-01.azurewebsites.net/"+ api;
        CloseableHttpClient httpClient = HttpClients.createDefault();
        HttpGet httpGet = new HttpGet(url);

        String responseContent = null;
        try {
            CloseableHttpResponse response = httpClient.execute(httpGet);
            HttpEntity entity = response.getEntity();
            responseContent = EntityUtils.toString(entity);
            response.close();
            httpClient.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return responseContent;
    }

    public String sendShopifyPost(ShopifyRequest request, String api, String body) {
        String url = "http://springbackendservice-e3hgbjgqafb9cpdh.canadacentral-01.azurewebsites.net/"+ api;
        CloseableHttpClient httpClient = HttpClients.createDefault();
        HttpPost httpPost = new HttpPost(url);

        String responseContent = null;
        try {
            StringEntity input = new StringEntity(body.toString(),"UTF-8");
            httpPost.setEntity(input);
            CloseableHttpResponse response = httpClient.execute(httpPost);
            HttpEntity entity = response.getEntity();
            responseContent = EntityUtils.toString(entity);
            response.close();
            httpClient.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return responseContent;
    }
}
