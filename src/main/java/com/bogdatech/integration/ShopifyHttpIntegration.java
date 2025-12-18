package com.bogdatech.integration;

import com.alibaba.fastjson.JSONObject;
import com.bogdatech.integration.model.ShopifyGraphResponse;
import com.bogdatech.model.controller.request.ShopifyRequest;
import com.bogdatech.requestBody.ShopifyRequestBody;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

import static com.bogdatech.constants.TranslateConstants.APIVERSION;

@Component
public class ShopifyHttpIntegration {
    @Autowired
    private BaseHttpIntegration httpIntegration;

    public String sendShopifyPost(String shopName, String accessToken, String apiVersion,
                                  String stringQuery, ShopifyGraphResponse.TranslatableResources.Node node) {
        String url = "https://" + shopName + "/admin/api/" + apiVersion + "/graphql.json";

        JSONObject query = new JSONObject();
        query.put("query", stringQuery);
        if (node != null) {
            query.put("variables", node);
        }
        return httpIntegration.httpPost(url, query.toString(), Map.of("X-Shopify-Access-Token", accessToken));
    }

    public String getInfoByShopify(String shopName, String accessToken, String apiVersion, String query) {
        String response = sendShopifyPost(shopName, accessToken, APIVERSION, query, null);
        if (response == null) {
            return null;
        }
        JSONObject jsonObject = JSONObject.parseObject(response);
        return String.valueOf(jsonObject.getJSONObject("data"));
    }

    //一次存储shopify数据
    public String registerTransaction(ShopifyRequest request, Map<String, Object> variables) {
        ShopifyRequestBody shopifyRequestBody = new ShopifyRequestBody();

        String url = "https://" + request.getShopName() + "/admin/api/" + APIVERSION + "/graphql.json";
        JSONObject query = new JSONObject();
        query.put("query", shopifyRequestBody.registerTransactionQuery());
        if (variables != null && !variables.isEmpty()) {
            query.put("variables", new JSONObject(variables));
        }
        String responseString = httpIntegration.httpPost(url, query.toString(), Map.of("X-Shopify-Access-Token", request.getAccessToken()));

        JSONObject jsonObject = JSONObject.parseObject(responseString);
        if (jsonObject != null && jsonObject.containsKey("data")) {
            return jsonObject.getString("data");
        }
        return null;
    }
}
