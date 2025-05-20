package com.bogdatech.integration;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.bogdatech.exception.ClientException;
import com.bogdatech.model.controller.request.TranslateRequest;
import com.bogdatech.utils.CharacterCountUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.bogdatech.logic.TranslateService.addData;
import static com.bogdatech.utils.CaseSensitiveUtils.appInsights;
import static com.bogdatech.utils.JsoupUtils.translateSingleLine;

@Component
public class PrivateIntegration {
    private final RestTemplate restTemplate;
    private final TranslateApiIntegration translateApiIntegration;

    public PrivateIntegration(RestTemplate restTemplate, TranslateApiIntegration translateApiIntegration) {
        this.restTemplate = restTemplate;
        this.translateApiIntegration = translateApiIntegration;
    }

    /**
     * 调用 OpenAI ChatGPT 接口进行对话，并返回 GPT 的回复文本
     *
     * @param prompt       用户输入的对话内容
     * @param model        GPT 模型名称
     * @param apiKey       OpenAI API 密钥
     * @param systemPrompt 系统提示文本
     * @return GPT 回复的文本
     */
    public String translateByGpt(String prompt, String model, String apiKey, String systemPrompt) {
        // 创建 OpenAI 客户端
        String url = "https://api.openai.com/v1/chat/completions";
        String apiValue = System.getenv(apiKey);
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + apiValue);
        headers.set("Content-Type", "application/json");

        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("messages", new Object[]{
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", prompt)
        });

        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(body, headers);
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, requestEntity, String.class);

        return response.getBody();
    }

    /**
     * 调用google接口进行对话，并返回回复文本
     *
     * @param text   用户的文本信息
     * @param source 源语言
     * @param apiKey google 密钥
     * @param target 目标语言
     * @return GPT 回复的文本
     */
    public static String googleTranslate(String text, String source, String apiKey, String target) {
        String encodedQuery = URLEncoder.encode(text, StandardCharsets.UTF_8);
        String url = "https://translation.googleapis.com/language/translate/v2?key=" + apiKey +
                "&q=" + encodedQuery +
                "&source=" + source +
                "&target=" + target +
                "&model=base";
        String result = null;
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
            JSONArray translationsArray = jsonObject.getJSONObject("data").getJSONArray("translations");
            JSONObject translation = translationsArray.getJSONObject(0);
            result = translation.getString("translatedText");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return result;
    }

    //对谷歌翻译API做重试机制
    public static String getGoogleTranslationWithRetry(String text, String source, String apiKey, String target) {
        int maxRetries = 3; // 最大重试次数
        int retryCount = 0; // 当前重试次数
        int baseDelay = 1000; // 初始等待时间（1秒）
        String translatedText;

        do {
            try {
                translatedText = googleTranslate(text, source, apiKey, target);
                if (translatedText != null) {
                    return translatedText; // 成功获取翻译，直接返回
                }
            } catch (Exception e) {
                appInsights.trackTrace("翻译 API 调用失败，重试次数：" + retryCount + "，错误信息：" + e.getMessage());
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
    // 不翻译的Liquid变量模式
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{[^}]+\\}\\}");
    // 自定义变量模式：%{ order.name } 等
    private static final Pattern CUSTOM_VAR_PATTERN = Pattern.compile("%\\{[^}]+\\}");
    // Liquid条件语句模式：{% if order.po_number != blank %} 等
    private static final Pattern LIQUID_CONDITION_PATTERN = Pattern.compile("\\{%[^%]+%\\}");
    // 数组变量模式：[ product[1]] 等
    private static final Pattern ARRAY_VAR_PATTERN = Pattern.compile("\\[\\s*[^\\]]+\\s*\\]");
    // 纯符号模式：匹配单独的 -、×、+、= 等符号（不含字母数字）
    private static final Pattern SYMBOL_PATTERN = Pattern.compile("^[\\-×\\+=×*/|!@#$%^&()_]+$", Pattern.MULTILINE);
    // 判断是否有 <html> 标签的模式
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<\\s*html\\s*", Pattern.CASE_INSENSITIVE);
    // 从配置文件读取不翻译的标签，默认为 "style,img,script"
    private final static Set<String> noTranslateTags = new HashSet<>(Arrays.asList("style", "img", "script"));

    /**
     * 主翻译方法
     *
     * @param html 输入的HTML文本
     * @return 翻译后的HTML文本
     */
    public static String translatePrivateNewHtml(String html, TranslateRequest request, CharacterCountUtils counter, String resourceType, String apiKey) {
        // 检查输入是否有效
        if (html == null || html.trim().isEmpty()) {
            return html;
        }

//        appInsights.trackTrace("现在正在翻译： "  + html);
        try {
            // 判断输入是否包含 <html> 标签
            boolean hasHtmlTag = HTML_TAG_PATTERN.matcher(html).find();

            if (hasHtmlTag) {
                // 如果有 <html> 标签，按完整文档处理
                Document doc = Jsoup.parse(html);
                if (doc == null) {
                    return html;
                }

                processNode(doc.body(), request, counter, resourceType, apiKey);
                String result = doc.outerHtml(); // 返回完整的HTML结构
//                appInsights.trackTrace("有html标签： "  + result);
//                System.out.println("有html标签： " + result);
                return result;
            } else {
                // 如果没有 <html> 标签，作为片段处理
                Document doc = Jsoup.parseBodyFragment(html);
                Element body = doc.body();

                processNode(body, request, counter, resourceType, apiKey);

                // 只返回子节点内容，不包含 <body>
                StringBuilder result = new StringBuilder();
                for (Node child : body.childNodes()) {
                    result.append(child.toString());
                }

                String output = result.toString();
//                appInsights.trackTrace("没有html标签： "  + output);
//                System.out.println("没有html标签： " + output);
                return output;
            }

        } catch (Exception e) {
            return html;
        }
    }

    /**
     * 递归处理节点
     *
     * @param node 当前节点
     */
    private static void processNode(Node node, TranslateRequest request, CharacterCountUtils counter, String resourceType, String apiKey) {
        try {
            // 如果是元素节点
            if (node instanceof Element) {
                Element element = (Element) node;
                String tagName = element.tagName().toLowerCase();

                // 检查是否为不翻译的标签
                if (noTranslateTags.contains(tagName)) {
                    return;
                }

                // 属性不翻译，保持原样
                element.attributes().forEach(attr -> {
                });

                // 递归处理子节点
                for (Node child : element.childNodes()) {
                    processNode(child, request, counter, resourceType, apiKey);
                }
            }
            // 如果是文本节点
            else if (node instanceof TextNode) {
                TextNode textNode = (TextNode) node;
                String text = textNode.getWholeText();

                // 如果文本为空或只有空白字符，跳过
                if (text.trim().isEmpty()) {
                    return;
                }

                // 使用缓存处理文本

                String translatedText = translateTextWithCache(text, request, counter, resourceType, apiKey);
                textNode.text(translatedText);
            }
        } catch (Exception e) {
            appInsights.trackTrace("递归处理节点报错： " + e.getMessage());
        }
    }

    /**
     * 使用缓存处理文本内容，保护变量和URL
     *
     * @param text 输入文本
     * @return 翻译后的文本
     */
    private static String translateTextWithCache(String text, TranslateRequest request, CharacterCountUtils counter, String resourceType, String apiKey) {
        // 检查缓存
        String translated = translateSingleLine(text, request.getTarget());
        if (translated != null) {
            return translated;
        }

        // 处理文本中的变量和URL
        String translatedText = translateTextWithProtection(text, request, counter, resourceType, apiKey);

        // 存入缓存
        addData(request.getTarget(), text, translatedText);
        return translatedText;
    }

    /**
     * 处理文本内容，保护变量和URL
     *
     * @param text 输入文本
     * @return 翻译后的文本
     */
    private static String translateTextWithProtection(String text, TranslateRequest request, CharacterCountUtils counter, String resourceType, String apiKey) {
        StringBuilder result = new StringBuilder();
        int lastEnd = 0;

        // 合并所有需要保护的模式
        List<Pattern> patterns = Arrays.asList(
                URL_PATTERN,
//                VARIABLE_PATTERN,
//                CUSTOM_VAR_PATTERN,
//                LIQUID_CONDITION_PATTERN,
//                ARRAY_VAR_PATTERN,
                SYMBOL_PATTERN
        );

        List<PrivateIntegration.MatchRange> matches = new ArrayList<>();
        for (Pattern pattern : patterns) {
            Matcher matcher = pattern.matcher(text);
            while (matcher.find()) {
                matches.add(new PrivateIntegration.MatchRange(matcher.start(), matcher.end(), matcher.group()));
            }
        }

        // 按位置排序
        matches.sort(Comparator.comparingInt(m -> m.start));

        // 处理所有匹配项之间的文本
        for (PrivateIntegration.MatchRange match : matches) {
            // 翻译匹配项之前的文本
            if (match.start > lastEnd) {
                String toTranslate = text.substring(lastEnd, match.start);
                String cleanedText = cleanTextFormat(toTranslate); // 清理格式
                //对特殊符号进行处理
                if (cleanedText.matches("\\p{Zs}+")) {
                    result.append(cleanedText);
                    continue;
                }
                if (!cleanedText.trim().isEmpty()) { // 避免翻译空字符串
                    String targetString;
                    try {
                        targetString = getGoogleTranslationWithRetry(cleanedText, resourceType, apiKey, request.getTarget());
                        result.append(targetString);
                        addData(request.getTarget(), cleanedText, targetString);
//                        System.out.println("cleanedText: " + cleanedText);
//                        result.append(cleanedText);
                    } catch (ClientException e) {
                        // 如果AI翻译失败，则使用谷歌翻译
                        result.append(cleanedText);
                        continue;
                    }
                } else {
                    result.append(toTranslate); // 保留原始空白
                }
            }
            // 保留匹配到的变量或URL，不翻译
            result.append(match.content);
            lastEnd = match.end;
        }

        // 处理剩余文本
        if (lastEnd < text.length()) {
            String remaining = text.substring(lastEnd);
            String cleanedText = cleanTextFormat(remaining); // 清理格式
            if (cleanedText.matches("\\p{Zs}+")) {
                result.append(cleanedText);
                return result.toString();
            }
            if (!cleanedText.trim().isEmpty() && !cleanedText.matches("\\s*")) {
                String targetString;
                try {
                    //AI翻译
                    targetString = getGoogleTranslationWithRetry(cleanedText, resourceType, apiKey, request.getTarget());
                    result.append(targetString);
                    addData(request.getTarget(), cleanedText, targetString);
//                    System.out.println("cleanedText: " + cleanedText);
//                    result.append(cleanedText);
                } catch (ClientException e) {
                    result.append(cleanedText);
                }
            } else {
                result.append(remaining);
            }

        }

        return result.toString();
    }

    /**
     * 清理文本格式：去除多余的换行符和空格
     *
     * @param text 输入文本
     * @return 清理后的文本
     */
    private static String cleanTextFormat(String text) {
        // 去除首尾的换行符和多余空格，保留内部有效内容
        return text.trim().replaceAll("[\\r\\n]+", "").replaceAll("\\s+", " ");
    }

    // 辅助类用于保存匹配范围
    private static class MatchRange {
        int start;
        int end;
        String content;

        MatchRange(int start, int end, String content) {
            this.start = start;
            this.end = end;
            this.content = content;
        }
    }
}
