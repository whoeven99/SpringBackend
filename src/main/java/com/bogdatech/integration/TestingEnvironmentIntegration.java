package com.bogdatech.integration;

import com.bogdatech.exception.ClientException;
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

import static com.bogdatech.enums.ErrorEnum.SHOPIFY_CONNECT_ERROR;

@Component
public class TestingEnvironmentIntegration {

    public String sendShopifyGet(ShopifyRequest request, String api) {
        String url = "https://springbackendservice-e3hgbjgqafb9cpdh.canadacentral-01.azurewebsites.net/"+ api;
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
            throw new ClientException(SHOPIFY_CONNECT_ERROR.getErrMsg());
        }
        return responseContent;
    }

    public static String sendShopifyPost(String api, String body) {
        String url = "https://springbackendservice-e3hgbjgqafb9cpdh.canadacentral-01.azurewebsites.net/"+ api;
        CloseableHttpClient httpClient = HttpClients.createDefault();
        HttpPost httpPost = new HttpPost(url);

        String responseContent = null;
        int maxRetries = 3;  // Maximum number of retry attempts
        int attempt = 0;

        while (attempt < maxRetries) {
            try {
                StringEntity input = new StringEntity(body, "UTF-8");
                httpPost.setEntity(input);
                httpPost.setHeader("Content-Type", "application/json"); // Set header to JSON

                CloseableHttpResponse response = httpClient.execute(httpPost);
                try {
                    HttpEntity entity = response.getEntity();
                    responseContent = EntityUtils.toString(entity);

                    if (response.getStatusLine().getStatusCode() >= 200 && response.getStatusLine().getStatusCode() < 300) {
                        break;  // Exit loop if successful
                    }
                } finally {
                    response.close();
                }
            } catch (IOException e) {
                attempt++;
                if (attempt >= maxRetries) {
                    throw new ClientException(SHOPIFY_CONNECT_ERROR.getErrMsg() + " after " + attempt + " attempts.");
                }

                try {
                    long delay = (long) Math.pow(2, attempt) * 1000L; // milliseconds
                    Thread.sleep(delay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt(); // Restore interrupt status
                    throw new RuntimeException("指数退避失败： Retry interrupted. " + ie.getMessage());
                }
            }
        }

        try {
            httpClient.close();
        } catch (IOException e) {
            throw new ClientException("Failed to close HttpClient.");
        }

        return responseContent;
    }
}
