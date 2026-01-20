package com.bogda.integration.http;

import com.bogda.common.utils.AppInsightsUtils;
import com.bogda.common.utils.TimeOutUtils;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.util.EntityUtils;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
public class BaseHttpIntegration {
    private final CloseableHttpClient httpClient;

    public BaseHttpIntegration() {
        // IMPORTANT:
        // - connectTimeout: 建连超时（TCP/TLS）
        // - connectionRequestTimeout: 从连接池获取连接的等待超时
        // - socketTimeout: 读超时（等待响应数据）
        //
        // 之前使用 HttpClients.createDefault() 会沿用默认超时（部分场景下可能表现为无限等待），
        // 导致线程卡住、任务锁不释放（例如 savingShops）。
        final int connectTimeoutMs = Integer.getInteger("bogda.http.connect-timeout-ms", 10_000);
        final int connectionRequestTimeoutMs = Integer.getInteger("bogda.http.connection-request-timeout-ms", 10_000);
        final int socketTimeoutMs = Integer.getInteger("bogda.http.socket-timeout-ms", 60_000);
        PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager();

        cm.setMaxTotal(200);          // 总连接数
        cm.setDefaultMaxPerRoute(50); // 单 host（如 Shopify）最大连接
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(connectTimeoutMs)
                .setConnectionRequestTimeout(connectionRequestTimeoutMs)
                .setSocketTimeout(socketTimeoutMs)
                .build();

        this.httpClient = HttpClients.custom()
                .setConnectionManager(cm)
                .setDefaultRequestConfig(requestConfig)
                .evictExpiredConnections()
                .evictIdleConnections(30, TimeUnit.SECONDS)
                .build();
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
