package com.bogda.integration.http;

import com.bogda.common.utils.AppInsightsUtils;
import com.bogda.common.utils.TimeOutUtils;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
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
        http.setEntity(new StringEntity(body, ContentType.APPLICATION_JSON.withCharset("UTF-8")));
        return sendHttp(http, headers);
    }

    public String httpGet(String url, Map<String, String> headers) {
        HttpGet http = new HttpGet(url);
        return sendHttp(http, headers);
    }

    private String sendHttp(ClassicHttpRequest http, Map<String, String> headers) {
        http.addHeader("Content-Type", "application/json");
        for (Map.Entry<String, String> header : headers.entrySet()) {
            http.addHeader(header.getKey(), header.getValue());
        }

        try {
            return TimeOutUtils.callWithTimeoutAndRetry(() -> {
                try {
                    return httpClient.execute(http, (ClassicHttpResponse response) -> {
                        var entity = response.getEntity();
                        if (entity == null) {
                            return null;
                        }
                        String responseContent = EntityUtils.toString(entity);
                        EntityUtils.consume(entity);
                        return responseContent;
                    });
                } catch (Exception e) {
                    AppInsightsUtils.trackException(e);
                    return null;
                }
            });
        } catch (Exception e) {
            AppInsightsUtils.trackException(e);
            return null;
        }
    }
}
