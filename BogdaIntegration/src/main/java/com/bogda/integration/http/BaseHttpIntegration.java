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
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.util.EntityUtils;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
public class BaseHttpIntegration {
    private final CloseableHttpClient httpClient;
    private final PoolingHttpClientConnectionManager connectionManager;

    public BaseHttpIntegration() {
        // 创建连接池管理器
        this.connectionManager = new PoolingHttpClientConnectionManager();
        // 设置最大连接数
        connectionManager.setMaxTotal(200);
        // 设置每个路由的最大连接数（Shopify 每个域名）
        connectionManager.setDefaultMaxPerRoute(50);

        // 配置请求超时参数
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(120000)  // 连接超时 30 秒
                .setSocketTimeout(120000)  // 读取超时 120 秒（GraphQL 查询可能需要更长时间）
                .setConnectionRequestTimeout(10000)  // 从连接池获取连接的超时时间 10 秒
                .build();

        // 构建 HttpClient
        this.httpClient = HttpClientBuilder.create()
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(requestConfig)
                .evictIdleConnections(30, TimeUnit.SECONDS)  // 清理空闲连接
                .evictExpiredConnections()  // 清理过期连接
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

    @PreDestroy
    public void destroy() {
        try {
            if (httpClient != null) {
                httpClient.close();
            }
            if (connectionManager != null) {
                connectionManager.close();
            }
        } catch (Exception e) {
            AppInsightsUtils.trackException(e);
        }
    }
}
