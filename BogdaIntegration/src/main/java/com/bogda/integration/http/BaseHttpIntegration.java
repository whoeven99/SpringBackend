package com.bogda.integration.http;

import com.bogda.common.reporter.ExceptionReporterHolder;
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
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
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

    BaseHttpIntegration(CloseableHttpClient httpClient, PoolingHttpClientConnectionManager connectionManager) {
        this.httpClient = httpClient;
        this.connectionManager = connectionManager;
    }

    public String httpPost(String url, String body) {
        return httpPost(url, body, new HashMap<>());
    }

    public String httpPost(String url, String body, Map<String, String> headers) {
        return sendHttp(() -> {
            HttpPost http = new HttpPost(url);
            http.setEntity(new StringEntity(body, StandardCharsets.UTF_8));
            return http;
        }, headers);
    }

    public String httpGet(String url, Map<String, String> headers) {
        return sendHttp(() -> new HttpGet(url), headers);
    }

    private String sendHttp(java.util.function.Supplier<HttpRequestBase> requestSupplier, Map<String, String> headers) {
        final int maxAttempts = 3;
        final long baseDelayMs = 200L;
        final long maxDelayMs = 2_000L;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            HttpRequestBase http = requestSupplier.get();
            http.addHeader("Content-Type", "application/json");
            for (Map.Entry<String, String> header : headers.entrySet()) {
                http.addHeader(header.getKey(), header.getValue());
            }

            try {
                CloseableHttpResponse response = TimeOutUtils.callWithTimeoutAndRetry(() -> {
                    try {
                        return httpClient.execute(http);
                    } catch (Exception e) {
                        ExceptionReporterHolder.report("BaseHttpIntegration.sendHttp.execute", e);
                        return null;
                    }
                });

                if (response == null || response.getEntity() == null) {
                    return null;
                }

                try (response) {
                    String responseContent = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                    if (!isHtmlResponse(response, responseContent)) {
                        return responseContent;
                    }
                }

                if (attempt == maxAttempts) {
                    return null;
                }
                sleepBeforeRetry(attempt, baseDelayMs, maxDelayMs);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                ExceptionReporterHolder.report("BaseHttpIntegration.sendHttp.interrupted", ie);
                return null;
            } catch (Exception e) {
                ExceptionReporterHolder.report("BaseHttpIntegration.sendHttp", e);
                return null;
            }
        }
        return null;
    }

    protected void sleepBeforeRetry(int attempt, long baseDelayMs, long maxDelayMs) throws InterruptedException {
        sleepWithBackoffAndJitter(attempt, baseDelayMs, maxDelayMs);
    }

    private static boolean isHtmlResponse(CloseableHttpResponse response, String responseContent) {
        try {
            if (response.getEntity() != null && response.getEntity().getContentType() != null) {
                String ct = response.getEntity().getContentType().getValue();
                if (ct != null && ct.toLowerCase().contains("text/html")) {
                    return true;
                }
            }
        } catch (Exception ignored) {
            // ignore: fallback to body sniffing
        }
        if (responseContent == null) {
            return false;
        }
        String s = responseContent.stripLeading();
        if (s.isEmpty()) {
            return false;
        }
        String lower = s.length() > 256 ? s.substring(0, 256).toLowerCase() : s.toLowerCase();
        return lower.startsWith("<!doctype html")
                || lower.startsWith("<html")
                || lower.contains("<html")
                || lower.contains("<head")
                || lower.contains("<body");
    }

    private static void sleepWithBackoffAndJitter(int attempt, long baseDelayMs, long maxDelayMs) throws InterruptedException {
        long exp = baseDelayMs * (1L << Math.max(0, attempt - 1));
        long delay = Math.min(maxDelayMs, exp);
        double jitterFactor = 0.8 + ThreadLocalRandom.current().nextDouble() * 0.4; // 0.8 ~ 1.2
        long sleepMs = Math.max(0L, (long) (delay * jitterFactor));
        TimeUnit.MILLISECONDS.sleep(sleepMs);
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
            ExceptionReporterHolder.report("BaseHttpIntegration.destroy", e);
        }
    }
}
