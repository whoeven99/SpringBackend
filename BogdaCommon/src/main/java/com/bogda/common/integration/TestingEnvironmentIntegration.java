package com.bogda.common.integration;

import com.bogda.common.enums.ErrorEnum;
import com.bogda.common.exception.ClientException;
import com.bogda.common.model.controller.request.CloudServiceRequest;
import com.bogda.common.utils.CaseSensitiveUtils;
import com.bogda.common.utils.JsonUtils;
import com.bogda.common.utils.TimeOutUtils;
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
public class TestingEnvironmentIntegration {

    public String sendShopifyPost(String api, String shopName, String accessToken, String apiVersion, String query) {
        CloudServiceRequest cloudServiceRequest = new CloudServiceRequest();
        cloudServiceRequest.setShopName(shopName);
        cloudServiceRequest.setAccessToken(accessToken);
        cloudServiceRequest.setTarget(apiVersion);
        cloudServiceRequest.setBody(query);

        String requestBody = JsonUtils.objectToJson(cloudServiceRequest);
        return sendShopifyPost(api, requestBody);
    }

    /**
     * 本地调用test环境
     */
    public static String sendShopifyPost(String api, String body) {
        String url = "https://springbackendservice-e3hgbjgqafb9cpdh.canadacentral-01.azurewebsites.net/" + api;
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
                CloseableHttpResponse response = TimeOutUtils.callWithTimeoutAndRetry(() -> {
                            try {
                                return httpClient.execute(httpPost);
                            } catch (Exception e) {
                                CaseSensitiveUtils.appInsights.trackTrace("每日须看 sendShopifyPost api报错信息 errors ： " + e.getMessage() + " url : " + url + " api：" + api);
                                CaseSensitiveUtils.appInsights.trackException(e);
                                return null;
                            }
                        },
                        TimeOutUtils.DEFAULT_TIMEOUT, TimeOutUtils.DEFAULT_UNIT,    // 超时时间
                        TimeOutUtils.DEFAULT_MAX_RETRIES                // 最多重试3次
                );

                try (response) {
                    if (response == null) {
                        return null;
                    }
                    HttpEntity entity = response.getEntity();
                    responseContent = EntityUtils.toString(entity);
                    if (response.getStatusLine().getStatusCode() >= 200 && response.getStatusLine().getStatusCode() < 300) {
                        break;
                    }
                }
            } catch (Exception e) {
                attempt++;
                if (attempt >= maxRetries) {
                    throw new ClientException(ErrorEnum.SHOPIFY_CONNECT_ERROR.getErrMsg() + " after " + attempt + " attempts.");
                }

                try {
                    long delay = (long) Math.pow(2, attempt) * 1000L; // milliseconds
                    Thread.sleep(delay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt(); // Restore interrupt status
                    throw new RuntimeException("sendShopifyPost 指数退避失败： Retry interrupted. " + ie.getMessage());
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
