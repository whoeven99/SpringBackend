package com.bogda.integration.http;

import org.apache.http.HttpEntity;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.entity.BasicHttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BaseHttpIntegrationTest {

    static class NoSleepBaseHttpIntegration extends BaseHttpIntegration {
        NoSleepBaseHttpIntegration(CloseableHttpClient httpClient) {
            super(httpClient, new PoolingHttpClientConnectionManager());
        }

        @Override
        protected void sleepBeforeRetry(int attempt, long baseDelayMs, long maxDelayMs) {
            // no-op for fast, stable unit tests
        }
    }

    @Test
    void shouldReturnContentWhenNotHtml() throws Exception {
        CloseableHttpClient httpClient = mock(CloseableHttpClient.class);
        NoSleepBaseHttpIntegration integration = new NoSleepBaseHttpIntegration(httpClient);

        CloseableHttpResponse response = mock(CloseableHttpResponse.class);
        when(response.getEntity()).thenReturn(entity("{\"ok\":true}", "application/json"));
        when(httpClient.execute(ArgumentMatchers.any(HttpRequestBase.class))).thenReturn(response);

        String result = integration.httpGet("https://example.com", Map.of("X-Test", "1"));
        assertEquals("{\"ok\":true}", result);
        verify(httpClient, times(1)).execute(any(HttpRequestBase.class));
        verify(response, times(1)).close();
    }

    @Test
    void shouldRetry3TimesAndReturnNullWhenHtmlByContentType() throws Exception {
        CloseableHttpClient httpClient = mock(CloseableHttpClient.class);
        NoSleepBaseHttpIntegration integration = new NoSleepBaseHttpIntegration(httpClient);

        CloseableHttpResponse r1 = mock(CloseableHttpResponse.class);
        CloseableHttpResponse r2 = mock(CloseableHttpResponse.class);
        CloseableHttpResponse r3 = mock(CloseableHttpResponse.class);
        when(r1.getEntity()).thenReturn(entity("<html>1</html>", "text/html; charset=UTF-8"));
        when(r2.getEntity()).thenReturn(entity("<html>2</html>", "text/html; charset=UTF-8"));
        when(r3.getEntity()).thenReturn(entity("<html>3</html>", "text/html; charset=UTF-8"));
        when(httpClient.execute(any(HttpRequestBase.class))).thenReturn(r1, r2, r3);

        String result = integration.httpGet("https://example.com", Map.of());
        assertNull(result);

        verify(httpClient, times(3)).execute(any(HttpRequestBase.class));
        verify(r1, times(1)).close();
        verify(r2, times(1)).close();
        verify(r3, times(1)).close();
    }

    private static HttpEntity entity(String body, String contentType) {
        BasicHttpEntity e = new BasicHttpEntity();
        e.setContent(new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
        if (contentType != null) {
            e.setContentType(contentType);
        }
        return e;
    }
}

