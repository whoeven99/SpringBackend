package com.bogda.common.integration;

import com.bogda.common.utils.CaseSensitiveUtils;
import com.bogda.common.utils.TimeOutUtils;
import org.apache.http.HttpEntity;
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

    public String httpPost(String url, String requestBodyString) {
        HttpPost http = new HttpPost(url);
        http.setEntity(new StringEntity(requestBodyString, "UTF-8"));
        return sendHttp(http, new HashMap<>());
    }

    public String httpPost(String url, String requestBodyString, Map<String, String> headers) {
        HttpPost http = new HttpPost(url);
        http.setEntity(new StringEntity(requestBodyString, "UTF-8"));
        return sendHttp(http, headers);
    }

    private String httpGet(String url, Map<String, String> headers) {
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
                            CaseSensitiveUtils.appInsights.trackException(e);
                            return null;
                        }
                    }
            );
            if (response == null || response.getEntity() == null) {
                return null;
            }
            String responseContent = EntityUtils.toString(response.getEntity(), "UTF-8");
            response.close();
            return responseContent;
        } catch (Exception e) {
            //
            return null;
        }
    }

    // todo remove
    public String sendHttpGet(String url, String key) throws Exception {
        CloseableHttpClient httpClient = HttpClients.createDefault();
        HttpGet httpGet = new HttpGet(url);
        httpGet.addHeader("Content-Type", "application/json");
        httpGet.addHeader("apikey", key);
        CloseableHttpResponse response = TimeOutUtils.callWithTimeoutAndRetry(() -> {
                    try {
                        return httpClient.execute(httpGet);
                    } catch (Exception e) {
                        CaseSensitiveUtils.appInsights.trackTrace("每日须看 sendHttpGet api报错信息 errors ： " + e.getMessage() + " url : " + url + " key：" + key);
                        CaseSensitiveUtils.appInsights.trackException(e);
                        return null;
                    }
                },
                TimeOutUtils.DEFAULT_TIMEOUT, TimeOutUtils.DEFAULT_UNIT,    // 超时时间
                TimeOutUtils.DEFAULT_MAX_RETRIES                // 最多重试3次
        );
        if (response == null) {
            return null;
        }
        HttpEntity entity = response.getEntity();
        String responseContent = EntityUtils.toString(entity, "UTF-8");

        response.close();
        httpClient.close();
        return responseContent;
    }
}
