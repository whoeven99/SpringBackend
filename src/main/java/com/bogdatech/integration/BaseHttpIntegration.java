package com.bogdatech.integration;

import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.springframework.stereotype.Component;
import static com.bogdatech.utils.CaseSensitiveUtils.appInsights;
import static com.bogdatech.utils.TimeOutUtils.*;
import static com.bogdatech.utils.TimeOutUtils.DEFAULT_MAX_RETRIES;

@Component
public class BaseHttpIntegration {

    public String sendHttpGet(String url, String key) throws Exception {
        CloseableHttpClient httpClient = HttpClients.createDefault();
        HttpGet httpGet = new HttpGet(url);
        httpGet.addHeader("Content-Type", "application/json");
        httpGet.addHeader("apikey", key);
        CloseableHttpResponse response = callWithTimeoutAndRetry(() -> {
                    try {
                        return httpClient.execute(httpGet);
                    } catch (Exception e) {
                        appInsights.trackTrace("每日须看 sendHttpGet api报错信息 errors ： " + e.getMessage() + " url : " + url + " key：" + key);
                        appInsights.trackException(e);
                        return null;
                    }
                },
                DEFAULT_TIMEOUT, DEFAULT_UNIT,    // 超时时间
                DEFAULT_MAX_RETRIES                // 最多重试3次
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
