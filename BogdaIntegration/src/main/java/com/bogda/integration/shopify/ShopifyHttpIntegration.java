package com.bogda.integration.shopify;

import com.alibaba.fastjson.JSONObject;
import com.bogda.integration.http.BaseHttpIntegration;
import com.bogda.integration.model.ShopifyGraphResponse;
import com.bogda.integration.model.ShopifyResponse;
import com.bogda.common.contants.TranslateConstants;
import com.bogda.common.utils.JsonUtils;
import com.bogda.common.utils.ShopifyRequestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ShopifyHttpIntegration {
    @Autowired
    private BaseHttpIntegration baseHttpIntegration;

    public ShopifyResponse getInfo(String shopName, String query, String accessToken) {
        String url = "https://" + shopName + "/admin/api/" + TranslateConstants.API_VERSION_LAST + "/graphql.json";

        JSONObject queryMap = new JSONObject();
        queryMap.put("query", query);

        String httpRes = baseHttpIntegration.httpPost(url, queryMap.toString(), Map.of("X-Shopify-Access-Token", accessToken));
        if (httpRes == null) {
            return null;
        }
        return JsonUtils.jsonToObjectWithNull(httpRes, ShopifyResponse.class);
    }

    public String saveShopifyData(String shopName, String accessToken,
                                  ShopifyGraphResponse.TranslatableResources.Node node) {
        String url = "https://" + shopName + "/admin/api/" + TranslateConstants.API_VERSION_LAST + "/graphql.json";

        JSONObject queryMap = new JSONObject();
        queryMap.put("query", ShopifyRequestUtils.registerTransactionQuery());
        queryMap.put("variables", node);

        return baseHttpIntegration.httpPost(url, queryMap.toString(), Map.of("X-Shopify-Access-Token", accessToken));
    }

    public ShopifyRemoveResponse deleteShopifyData(String shopName, String accessToken, ShopifyTranslationsRemove shopifyTranslationsRemove){
        String url = "https://" + shopName + "/admin/api/" + TranslateConstants.API_VERSION_LAST + "/graphql.json";

        JSONObject queryMap = new JSONObject();
        queryMap.put("query", ShopifyRequestUtils.deleteQuery());
        queryMap.put("variables", shopifyTranslationsRemove);

        String httpRes = baseHttpIntegration.httpPost(url, queryMap.toString(), Map.of("X-Shopify-Access-Token", accessToken));
        if (httpRes == null) {
            return null;
        }
        return JsonUtils.jsonToObjectWithNull(httpRes, ShopifyRemoveResponse.class);
    }


    public String sendShopifyPost(String shopName, String accessToken, String stringQuery, Map<String, Object> variables) {
        String url = "https://" + shopName + "/admin/api/" + TranslateConstants.API_VERSION_LAST + "/graphql.json";

        JSONObject query = new JSONObject();
        query.put("query", stringQuery);
        if (variables != null && !variables.isEmpty()) {
            query.put("variables", new JSONObject(variables));
        }

        return baseHttpIntegration.httpPost(url, query.toString(), Map.of("X-Shopify-Access-Token", accessToken));
    }

    public String getInfoByShopify(String shopName, String accessToken, String apiVersion, String query) {
        return String.valueOf(getInfoByShopify(shopName, accessToken, query));
    }

    public JSONObject getInfoByShopify(String shopName, String accessToken, String query) {
        String response = sendShopifyPost(shopName, accessToken, query, null);
        JSONObject jsonObject = JSONObject.parseObject(response);
        return jsonObject.getJSONObject("data");
    }

    //一次存储shopify数据
    public String registerTransaction(String shopName, String accessToken, Map<String, Object> variables) {
        String responseString = sendShopifyPost(shopName, accessToken, ShopifyRequestUtils.registerTransactionQuery(), variables);
        if (responseString == null){
            return null;
        }
        JSONObject jsonObject = JSONObject.parseObject(responseString);
        if (jsonObject != null && jsonObject.containsKey("data")) {
            return jsonObject.getString("data");
        }
        return null;
    }
}
