package com.bogda.api.integration;

import com.bogda.api.utils.TimeOutUtils;
import com.bogda.common.utils.AppInsightsUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class BaseHttpIntegration {
    private final CloseableHttpClient httpClient;

    public BaseHttpIntegration() {
        this.httpClient = HttpClients.createDefault();
    }

    public String httpPost(String url, String body) {
        return httpPost(url, body, new HashMap<>());
    }

    public String httpPost(String url, String body, Map<String, String> headers) {
        HttpPost http = new HttpPost(url);
        http.setEntity(new StringEntity(body, "UTF-8"));
        return sendHttp(http, headers);
    }

    public String httpGet(String url, Map<String, String> headers) {
        HttpGet http = new HttpGet(url);
        return sendHttp(http, headers);
    }

    private String sendHttp(HttpRequestBase http, Map<String, String> headers) {
        http.addHeader("Content-Type", "application/json");
        for (Map.Entry<String, String> header : headers.entrySet()) {
            http.addHeader(header.getKey(), header.getValue());
        }

        try {
            CloseableHttpResponse response = TimeOutUtils.callWithTimeoutAndRetry(() -> {
                try {
                    return httpClient.execute(http);
                } catch (Exception e) {
                    AppInsightsUtils.trackException(e);
                    return null;
                }
            });
            if (response == null || response.getEntity() == null) {
                return null;
            }
            String responseContent = EntityUtils.toString(response.getEntity(), "UTF-8");
            response.close();
            return responseContent;
        } catch (Exception e) {
            AppInsightsUtils.trackException(e);
            return null;
        }
    }
}
