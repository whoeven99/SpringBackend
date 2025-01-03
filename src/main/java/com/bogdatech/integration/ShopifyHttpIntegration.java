package com.bogdatech.integration;

import com.alibaba.fastjson.JSONObject;
import com.bogdatech.exception.ClientException;
import com.bogdatech.model.controller.request.ShopifyRequest;
import com.bogdatech.requestBody.ShopifyRequestBody;
import com.microsoft.applicationinsights.TelemetryClient;
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

@Component
public class ShopifyHttpIntegration {
    private TelemetryClient appInsights = new TelemetryClient();

    // 设置头部信息
    //查询数据
    public String sendShopifyPost(ShopifyRequest request, String stringQuery, Map<String, Object> variables) {
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

    public JSONObject getInfoByShopify(ShopifyRequest shopifyRequest, String query) {
        String string = sendShopifyPost(shopifyRequest, query, null);
        JSONObject jsonObject = JSONObject.parseObject(string);
        return jsonObject.getJSONObject("data");
    }

    public String registerTransaction(ShopifyRequest request, Map<String, Object> variables) {
        ShopifyRequestBody shopifyRequestBody = new ShopifyRequestBody();
        String string = sendShopifyPost(request, shopifyRequestBody.registerTransactionQuery(), variables);
        JSONObject jsonObject = JSONObject.parseObject(string);
        return jsonObject.getString("data");
    }

}


