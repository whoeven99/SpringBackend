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
public class TestingEnvironmentIntegration {

    public String sendShopifyPost(ShopifyRequest request) {
        String url = "http://springbackendservice-e3hgbjgqafb9cpdh.canadacentral-01.azurewebsites.net/test123";
        CloseableHttpClient httpClient = HttpClients.createDefault();
        HttpGet httpPost = new HttpGet(url);


        String responseContent = null;
        try {
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
