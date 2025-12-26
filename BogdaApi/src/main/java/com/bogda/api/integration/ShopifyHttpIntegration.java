package com.bogda.api.integration;

import com.alibaba.fastjson.JSONObject;
import com.bogda.api.integration.model.ShopifyGraphResponse;
import com.bogda.api.model.controller.request.ShopifyRequest;
import com.bogda.api.requestBody.ShopifyRequestBody;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.springframework.stereotype.Component;

import java.util.Map;

import static com.bogda.api.utils.CaseSensitiveUtils.appInsights;
import static com.bogda.api.utils.TimeOutUtils.*;

@Component
public class ShopifyHttpIntegration {
    public String sendShopifyPost(String shopName, String accessToken, String apiVersion,
                                  String stringQuery, ShopifyGraphResponse.TranslatableResources.Node node) {
        String url = "https://" + shopName + "/admin/api/" + apiVersion + "/graphql.json";
        CloseableHttpClient httpClient = HttpClients.createDefault();
        HttpPost httpPost = new HttpPost(url);

        // 设置头部信息
        httpPost.addHeader("X-Shopify-Access-Token", accessToken);
        httpPost.addHeader("Content-Type", "application/json");

        // 创建查询体
        JSONObject query = new JSONObject();
        query.put("query", stringQuery);
        query.put("variables", node);
        // 将查询体设置到实体中
        StringEntity input = new StringEntity(query.toString(), "UTF-8");
        httpPost.setEntity(input);

        String responseContent = null;
        try {
            CloseableHttpResponse response = callWithTimeoutAndRetry(() -> {
                        try {
                            return httpClient.execute(httpPost);
                        } catch (Exception e) {
                            appInsights.trackTrace("每日须看 sendShopifyPost api报错信息 errors ： " + e.getMessage() + " url : " + url + " 用户：" + shopName);
                            appInsights.trackException(e);
                            return null;
                        }
                    },
                    DEFAULT_TIMEOUT, DEFAULT_UNIT,    // 超时时间
                    DEFAULT_MAX_RETRIES                // 最多重试3次
            );
            if (response == null) {
                return null;
            }
            HttpEntity entity = response.getEntity();
            responseContent = EntityUtils.toString(entity, "UTF-8");
            response.close();
            httpClient.close();
        } catch (Exception e) {
            appInsights.trackTrace("FatalException sendShopifyPost api报错信息 errors ： " + e.getMessage() + " url : " + url + " 用户：" + shopName);
            appInsights.trackException(e);
            return null;
        }
        return responseContent;
    }

    // 设置头部信息
    //查询数据
    public static String sendShopifyPost(ShopifyRequest request, String stringQuery, Map<String, Object> variables) {
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
            CloseableHttpResponse response = callWithTimeoutAndRetry(() -> {
                        try {
                            return httpClient.execute(httpPost);
                        } catch (Exception e) {
                            appInsights.trackTrace("FatalException 每日须看 sendShopifyPost api报错信息 errors ： " + e.getMessage() + " url : " + url + " 用户：" + request.getShopName());
                            appInsights.trackException(e);
                            return null;
                        }
                    },
                    DEFAULT_TIMEOUT, DEFAULT_UNIT,    // 超时时间
                    DEFAULT_MAX_RETRIES                // 最多重试3次
            );
            if (response == null) {
                return null;
            }
            HttpEntity entity = response.getEntity();
            responseContent = EntityUtils.toString(entity, "UTF-8");
            response.close();
            httpClient.close();
        } catch (Exception e) {
            appInsights.trackTrace("FatalException sendShopifyPost api报错信息 errors ： " + e.getMessage() + " url : " + url + " 用户：" + request.getShopName());
            appInsights.trackException(e);
            return null;
        }
        return responseContent;
    }

    public String getInfoByShopify(String shopName, String accessToken, String apiVersion, String query) {
        ShopifyRequest request = new ShopifyRequest();
        request.setShopName(shopName);
        request.setAccessToken(accessToken);
        request.setApiVersion(apiVersion);
        return String.valueOf(getInfoByShopify(request, query));
    }

    public static JSONObject getInfoByShopify(ShopifyRequest shopifyRequest, String query) {
        String response = null;
        int maxRetries = 3;
        int baseDelay = 1000; // 初始延迟 1 秒
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                response = sendShopifyPost(shopifyRequest, query, null);
                if (response != null) {
                    break;
                }
            } catch (Exception e) {
                appInsights.trackException(e);
                appInsights.trackTrace("FatalException getInfoByShopify Shopify request failed on attempt " + attempt + ": " + e.getMessage());
            }

            if (attempt < maxRetries) {
                int delay = baseDelay * (int) Math.pow(2, attempt - 1); // 指数退避：1s, 2s, 4s, 8s, ...
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        if (response == null) {
            appInsights.trackTrace("getInfoByShopify Failed to get response from Shopify after " + maxRetries + " attempts.");
            return null;
        }

        JSONObject jsonObject = JSONObject.parseObject(response);
        return jsonObject.getJSONObject("data");
    }

    //一次存储shopify数据
    public static String registerTransaction(ShopifyRequest request, Map<String, Object> variables) {
        ShopifyRequestBody shopifyRequestBody = new ShopifyRequestBody();
        JSONObject jsonObject;
        int retryCount = 3;  // 最大重试次数
        int retryInterval = 2000;  // 每次重试间隔，单位：毫秒

        for (int i = 0; i < retryCount; i++) {
            try {
                String responseString = sendShopifyPost(request, shopifyRequestBody.registerTransactionQuery(), variables);
                if (responseString == null){
                    continue;
                }
                jsonObject = JSONObject.parseObject(responseString);
                if (jsonObject != null && jsonObject.containsKey("data")) {
                    return jsonObject.getString("data");
                }
            } catch (Exception e) {
                appInsights.trackException(e);
                appInsights.trackTrace("FatalException 每日须看 registerTransaction errors : " + "用户： " + request.getShopName() + " 目标： " + request.getTarget() + "  " + e.getMessage());
            }

            // 如果没有成功，等待一段时间再重试
            try {
                Thread.sleep(retryInterval);  // 延迟后进行下一次重试
            } catch (InterruptedException e) {
                appInsights.trackTrace("每日须看 registerTransaction Thread sleep interrupted: " + e.getMessage());
            }
        }

        // 如果重试后仍然失败，返回 null
        return null;
    }
}


