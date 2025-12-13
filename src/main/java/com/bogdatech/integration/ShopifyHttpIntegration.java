package com.bogdatech.integration;

import com.alibaba.fastjson.JSONObject;
import com.bogdatech.integration.model.ShopifyGraphResponse;
import com.bogdatech.model.controller.request.ShopifyRequest;
import com.bogdatech.requestBody.ShopifyRequestBody;
import com.bogdatech.utils.ShopifyRequestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

import static com.bogdatech.constants.TranslateConstants.APIVERSION;
import static com.bogdatech.utils.CaseSensitiveUtils.appInsights;

@Component
public class ShopifyHttpIntegration {
    @Autowired
    private BaseHttpIntegration baseHttpIntegration;

    public String updateNodes(String shopName, String accessToken,
                              ShopifyGraphResponse.TranslatableResources.Node node) {
        JSONObject query = new JSONObject();
        query.put("query", ShopifyRequestUtils.registerTransactionQuery());
        query.put("variables", node);

        return baseHttpIntegration.httpPost("https://" + shopName + "/admin/api/" + APIVERSION + "/graphql.json",
                query.toString(),
                Map.of("X-Shopify-Access-Token", accessToken)
        );
    }

    public String sendShopifyPost(String shopName, String accessToken, String stringQuery,
                                  Map<String, Object> variables) {
        JSONObject query = new JSONObject();
        query.put("query", stringQuery);
        if (variables != null && !variables.isEmpty()) {
            query.put("variables", new JSONObject(variables));
        }

        return baseHttpIntegration.httpPost("https://" + shopName + "/admin/api/" + APIVERSION + "/graphql.json",
                query.toString(),
                Map.of("X-Shopify-Access-Token", accessToken)
        );
    }

    public String getInfoByShopify(String shopName, String accessToken, String apiVersion, String query) {
        ShopifyRequest request = new ShopifyRequest();
        request.setShopName(shopName);
        request.setAccessToken(accessToken);
        request.setApiVersion(apiVersion);
        return String.valueOf(getInfoByShopify(request, query));
    }

    public JSONObject getInfoByShopify(ShopifyRequest shopifyRequest, String query) {
        String response = null;
        int maxRetries = 3;
        int baseDelay = 1000; // 初始延迟 1 秒
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                response = sendShopifyPost(shopifyRequest.getShopName(), shopifyRequest.getAccessToken(), query, null);
                if (response != null) {
                    break;
                }
            } catch (Exception e) {
                appInsights.trackException(e);
                appInsights.trackTrace("getInfoByShopify Shopify request failed on attempt " + attempt + ": " + e.getMessage());
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
    public String registerTransaction(ShopifyRequest request, Map<String, Object> variables) {
        ShopifyRequestBody shopifyRequestBody = new ShopifyRequestBody();
        JSONObject jsonObject;
        int retryCount = 3;  // 最大重试次数
        int retryInterval = 2000;  // 每次重试间隔，单位：毫秒

        for (int i = 0; i < retryCount; i++) {
            try {
                String responseString = sendShopifyPost(request.getShopName(), request.getAccessToken(), shopifyRequestBody.registerTransactionQuery(), variables);
                if (responseString == null){
                    continue;
                }
                jsonObject = JSONObject.parseObject(responseString);
                if (jsonObject != null && jsonObject.containsKey("data")) {
                    return jsonObject.getString("data");
                }
            } catch (Exception e) {
                appInsights.trackException(e);
                appInsights.trackTrace("每日须看 registerTransaction errors : " + "用户： " + request.getShopName() + " 目标： " + request.getTarget() + "  " + e.getMessage());
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


