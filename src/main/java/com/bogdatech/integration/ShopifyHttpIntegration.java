package com.bogdatech.integration;

import com.alibaba.fastjson.JSONObject;
import com.bogdatech.exception.ClientException;
import com.bogdatech.model.controller.request.ShopifyRequest;
import com.bogdatech.requestBody.ShopifyRequestBody;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;

import static com.bogdatech.enums.ErrorEnum.SHOPIFY_CONNECT_ERROR;
import static com.bogdatech.utils.CaseSensitiveUtils.appInsights;

@Component
public class ShopifyHttpIntegration {

    // 设置头部信息
    //查询数据
    public static String sendShopifyPost(ShopifyRequest request, String  stringQuery, Map<String, Object> variables) {
        String url = "https://" + request.getShopName() + "/admin/api/" + request.getApiVersion() + "/graphql.json";
        CloseableHttpClient httpClient = HttpClients.createDefault();
        HttpPost httpPost = new HttpPost(url);
        // 设置头部信息
        httpPost.addHeader("X-Shopify-Access-Token", request.getAccessToken());
        httpPost.addHeader("Content-Type", "application/json");
        // 创建查询体
        JSONObject query = new JSONObject();
        query.put("query",
                stringQuery
        );
        if (variables != null && !variables.isEmpty()) {
            query.put("variables", new JSONObject(variables));
        }
        String responseContent = null;
        try {
            // 将查询体设置到实体中
            StringEntity input = new StringEntity(query.toString(), "UTF-8");
            httpPost.setEntity(input);
            CloseableHttpResponse response = httpClient.execute(httpPost);
            HttpEntity entity = response.getEntity();
            responseContent = EntityUtils.toString(entity, "UTF-8");
//            appInsights.trackTrace("Shopify response: " + responseContent);
            response.close();
            httpClient.close();
        } catch (IOException e) {
            throw new ClientException(SHOPIFY_CONNECT_ERROR.getErrMsg());
        }
        return responseContent;
    }

    public static JSONObject getInfoByShopify(ShopifyRequest shopifyRequest, String query) {
        String response = null;
        int maxRetries = 3;
        int attempt = 0;
        while (attempt < maxRetries) {
            response = sendShopifyPost(shopifyRequest, query, null);
            if (response != null) {
                break;
            }
            attempt++;
        }

        if (response == null) {
            appInsights.trackTrace("Failed to get response from Shopify after " + maxRetries + " attempts.");
        }

        JSONObject jsonObject = JSONObject.parseObject(response);
        return jsonObject.getJSONObject("data");
    }

    //一次存储shopify数据
    public static String registerTransaction(ShopifyRequest request, Map<String, Object> variables) {
        ShopifyRequestBody shopifyRequestBody = new ShopifyRequestBody();
        JSONObject jsonObject = null;
        int retryCount = 3;  // 最大重试次数
        int retryInterval = 2000;  // 每次重试间隔，单位：毫秒

        for (int i = 0; i < retryCount; i++) {
            try {
                String responseString = sendShopifyPost(request, shopifyRequestBody.registerTransactionQuery(), variables);
//                appInsights.trackTrace("registerTransaction response: " + responseString);
                jsonObject = JSONObject.parseObject(responseString);
                if (jsonObject != null && jsonObject.containsKey("data")) {
                    appInsights.trackTrace("registerTransaction data: " + jsonObject.getString("data"));
                    return jsonObject.getString("data");
                }
            } catch (Exception e) {
                appInsights.trackTrace("registerTransaction error: " + e.getMessage());
            }

            // 如果没有成功，等待一段时间再重试
            try {
                Thread.sleep(retryInterval);  // 延迟后进行下一次重试
            } catch (InterruptedException e) {
                appInsights.trackTrace("Thread sleep interrupted: " + e.getMessage());
            }
        }

        // 如果重试后仍然失败，返回 null
        return null;
    }


}


