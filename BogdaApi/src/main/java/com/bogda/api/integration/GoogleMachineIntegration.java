package com.bogda.api.integration;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.bogda.api.model.controller.request.TranslateRequest;
import com.bogda.api.utils.*;
import com.google.cloud.translate.Translate;
import com.google.cloud.translate.TranslateOptions;
import com.google.cloud.translate.Translation;
import kotlin.Pair;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import static com.bogda.api.utils.CaseSensitiveUtils.appInsights;

@Component
public class GoogleMachineIntegration {
    private static final int GOOGLE_MACHINE_COEFFICIENT = 2;
    private Translate translate;

    /**
     * 初始化 Google Translate 客户端
     * 支持使用 API Key 或服务账号凭证
     */
    private GoogleMachineIntegration() {
        String defaultApiKey = ConfigUtils.getConfig("GOOGLE_API_KEY");
        translate = TranslateOptions.newBuilder()
                .setApiKey(defaultApiKey)
                .build()
                .getService();
    }

    // 谷歌机器翻译SDK调用

    /**
     * 使用 Google Cloud Translate SDK 进行翻译
     *
     * @param content 要翻译的文本
     * @param target  目标语言代码（如 "zh-CN", "en", "ja" 等）
     * @return Pair<String, Integer> 翻译结果和 token 数量
     */
    public Pair<String, Integer> googleTranslateWithSDK(String content, String target) {
        try {
            // 执行翻译
            Translation translation = TimeOutUtils.callWithTimeoutAndRetry(() ->
                    translate.translate(content, Translate.TranslateOption.targetLanguage(target),
                            Translate.TranslateOption.model("base")), TimeOutUtils.rateLimiter1);
            CaseSensitiveUtils.appInsights.trackTrace("translation: " + translation);
            String translatedText = translation.getTranslatedText();
            int totalToken = content.length() * GOOGLE_MACHINE_COEFFICIENT;

            return new Pair<>(translatedText, totalToken);
        } catch (Exception e) {
            appInsights.trackTrace("FatalException Google Translate SDK 翻译错误：" + e.getMessage());
            appInsights.trackException(e);
            e.printStackTrace();
            return null;
        }
    }

    // 谷歌翻译API
    public Pair<String, Integer> googleTranslate(String content, String target, String privateKey) {
        String encodedQuery = URLEncoder.encode(content, StandardCharsets.UTF_8);
        String apikey = privateKey != null ? privateKey : ConfigUtils.getConfig("GOOGLE_API_KEY");
        String url = "https://translation.googleapis.com/language/translate/v2?key=" + apikey +
                "&q=" + encodedQuery +
                "&target=" + target +
                "&model=base";
        String result = null;
        // 创建HttpGet请求
        HttpPost httpPost = new HttpPost(url);
        // 执行请求
        JSONObject jsonObject;
        try (CloseableHttpClient httpClient = HttpClients.createDefault(); CloseableHttpResponse response = httpClient.execute(httpPost)) {
            // 获取响应实体并转换为字符串
            HttpEntity responseEntity = response.getEntity();
            String responseBody = EntityUtils.toString(responseEntity, StandardCharsets.UTF_8);
            jsonObject = JSONObject.parseObject(responseBody);

            // 获取翻译结果
            JSONArray translationsArray = jsonObject.getJSONObject("data").getJSONArray("translations");
            JSONObject translation = translationsArray.getJSONObject(0);
            result = translation.getString("translatedText");
            int totalToken = encodedQuery.length() * GOOGLE_MACHINE_COEFFICIENT;
            return new Pair<>(result, totalToken);
        } catch (Exception e) {
            appInsights.trackTrace("FatalException 信息：" + e.getMessage());
        }
        return null;
    }
}
