package com.bogdatech.integration;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.bogdatech.Service.IUserPrivateTranslateService;
import com.bogdatech.entity.DTO.FullAttributeSnapshotDTO;
import com.bogdatech.utils.AppInsightsUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;
import static com.bogdatech.logic.PrivateKeyService.GOOGLE_MODEL;
import static com.bogdatech.logic.PrivateKeyService.OPENAI_MODEL;
import static com.bogdatech.utils.ApiCodeUtils.getLanguageName;
import static com.bogdatech.utils.CaseSensitiveUtils.appInsights;
import static com.bogdatech.utils.LiquidHtmlTranslatorUtils.*;
import static com.bogdatech.utils.PlaceholderUtils.getFullHtmlPrompt;
import static com.bogdatech.utils.TimeOutUtils.*;
import static com.bogdatech.utils.TimeOutUtils.DEFAULT_MAX_RETRIES;

@Component
public class PrivateIntegration {
    @Autowired
    private RestTemplate restTemplate;
    @Autowired
    private IUserPrivateTranslateService iUserPrivateTranslateService;

    /**
     * 调用 OpenAI ChatGPT 接口进行对话，并返回 GPT 的回复文本
     *
     * @param prompt       用户输入的对话内容
     * @param model        GPT 模型名称
     * @param apiKey       OpenAI API 密钥
     * @param systemPrompt 系统提示文本
     * @return GPT 回复的文本
     */
    public String translateByGpt(String prompt, String model, String apiKey, String systemPrompt, String shopName, Long limit) {
        // 创建 OpenAI 客户端
        String url = "https://api.openai.com/v1/chat/completions";
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + apiKey);
        headers.set("Content-Type", "application/json");
//        appInsights.trackTrace(shopName + " 用户的 apiKey: " + apiKey);
        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("messages", new Object[]{
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", prompt)
        });

        String content;
        try {
            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = callWithTimeoutAndRetry(() -> {
                        try {
                            return restTemplate.exchange(url, HttpMethod.POST, requestEntity, String.class);
                        } catch (Exception e) {
                            appInsights.trackTrace("每日须看 translateByGpt gpt报错信息 errors ： " + e.getMessage() + " prompt : " + prompt + " 用户：" + shopName);
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

            // 将翻译数据存储到数据库中
            // 解析 JSON 字符串
            String responseBody = response.getBody();
            if (responseBody != null && responseBody.contains("content") && responseBody.contains("total_tokens")) {
                JSONObject obj = JSON.parseObject(response.getBody());
                // 获取 content
                JSONArray choices = obj.getJSONArray("choices");
                JSONObject firstChoice = choices.getJSONObject(0);
                content = firstChoice.getJSONObject("message").getString("content");

                // 获取 total_tokens
                int totalTokens = obj.getJSONObject("usage").getIntValue("total_tokens");
                AppInsightsUtils.printPrivateTranslateCost(totalTokens);
                appInsights.trackTrace("translateByGpt " + shopName + " 用户 openai 私有key翻译 all: " + totalTokens + " sourceText: " + prompt + " targetText : " + content);
                iUserPrivateTranslateService.updateUserUsedCount(OPENAI_MODEL, totalTokens, shopName, limit);
            }else {
                appInsights.trackTrace("translateByGpt translateByGpt errors openai 翻译失败 ： " +  responseBody);
                return null;
            }
        } catch (Exception e) {
            return null;
        }

        return content;
    }

    /**
     * 调用google接口进行对话，并返回回复文本
     *
     * @param text   用户的文本信息
     * @param apiKey google 密钥
     * @param target 目标语言
     * @return GPT 回复的文本
     */
    public static String googleTranslate(String text, String apiKey, String target) {
        String encodedQuery = URLEncoder.encode(text, StandardCharsets.UTF_8);
        String url = "https://translation.googleapis.com/language/translate/v2?key=" + apiKey +
                "&q=" + encodedQuery +
//                "&source=" + source +
                "&target=" + target +
                "&model=base";
        String result;
        // 创建HttpGet请求
        HttpPost httpPost = new HttpPost(url);
        // 执行请求
        JSONObject jsonObject;
        try (CloseableHttpClient httpClient = HttpClients.createDefault();
             CloseableHttpResponse response = httpClient.execute(httpPost)) {
            // 获取响应实体并转换为字符串
            org.apache.http.HttpEntity responseEntity = response.getEntity();
            String responseBody = EntityUtils.toString(responseEntity, StandardCharsets.UTF_8);
            jsonObject = JSONObject.parseObject(responseBody);
            // 获取翻译结果
            AppInsightsUtils.printPrivateTranslateCost(encodedQuery.length());
            JSONArray translationsArray = jsonObject.getJSONObject("data").getJSONArray("translations");
            JSONObject translation = translationsArray.getJSONObject(0);
            result = translation.getString("translatedText");

        } catch (Exception e) {
            return null;
        }
        return result;
    }

    //对谷歌翻译API做重试机制
    public String getGoogleTranslationWithRetry(String text, String apiKey, String target, String shopName, Long limit) {
        int maxRetries = 3; // 最大重试次数
        int retryCount = 0; // 当前重试次数
        int baseDelay = 1000; // 初始等待时间（1秒）
        String translatedText;

        do {
            try {
                translatedText = googleTranslate(text, apiKey, target);
                if (translatedText != null) {
                    // 将字符数存到数据库中
                    iUserPrivateTranslateService.updateUserUsedCount(GOOGLE_MODEL, text.length(), shopName, limit);
                    appInsights.trackTrace("translate " + shopName + " 用户 私有key google翻译为 ：" + translatedText + " google  all: " + text.length());
                    return translatedText; // 成功获取翻译，直接返回
                }
            } catch (Exception e) {
                appInsights.trackTrace("translate 翻译 API 调用失败，重试次数：" + retryCount + "，错误信息：" + e.getMessage() + " sourceText: " + text + " target: " + target);
            }

            try {
                Thread.sleep(baseDelay * (long) Math.pow(2, retryCount)); // 指数退避
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // 恢复中断状态
            }

            retryCount++;
        } while (retryCount < maxRetries);

        return null; // 重试后仍然失败，返回 null
    }

    // 不翻译的URL模式
    private static final Pattern URL_PATTERN = Pattern.compile("https?://[^\\s<>\"]+|www\\.[^\\s<>\"]+");
    // 判断是否有 <html> 标签的模式
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<\\s*html\\s*", Pattern.CASE_INSENSITIVE);

    /**
     * 主翻译方法
     *
     * @param html 输入的HTML文本
     * @return 翻译后的HTML文本
     */
    public  String translatePrivateNewHtml(String html, String target, String apiKey, String model, String shopName, Long limit) {
        //选择翻译html的提示词
        String targetLanguage = getLanguageName(target);
        String fullHtmlPrompt = getFullHtmlPrompt(targetLanguage, null);
        appInsights.trackTrace("translate " + shopName + " 翻译 html 的提示词：" + fullHtmlPrompt);

        boolean hasHtmlTag = HTML_TAG_PATTERN.matcher(html).find();
        Document originalDoc;
        if (hasHtmlTag) {
            originalDoc = Jsoup.parse(html);
        }else {
            originalDoc = Jsoup.parseBodyFragment(html);
        }

        // 2. 提取样式并标记 ID
        Map<String, FullAttributeSnapshotDTO> attrMap = tagElementsAndSaveFullAttributes(originalDoc);

        // 3. 获取清洗后的 HTML（无样式）
        removeAllAttributesExceptMarker(originalDoc);

        String cleanedHtml = originalDoc.body().html();

        //暂时先用openai翻译
        try {
            String chatGptString = translateByGpt(cleanedHtml, model, apiKey, fullHtmlPrompt, shopName, limit);
            if (chatGptString == null){
                return null;
            }
            return processTranslationResult(chatGptString, attrMap, hasHtmlTag);
        } catch (Exception e) {
            appInsights.trackException(e);
            return null;
        }
    }

}
