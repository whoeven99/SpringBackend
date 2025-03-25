package com.bogdatech.utils;


import com.bogdatech.exception.ClientException;
import com.bogdatech.model.controller.request.TranslateRequest;
import com.microsoft.applicationinsights.TelemetryClient;
import org.apache.commons.text.StringEscapeUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.bogdatech.integration.ALiYunTranslateIntegration.singleTranslate;
import static com.bogdatech.logic.TranslateService.addData;
import static com.bogdatech.utils.JsoupUtils.translateAndCount;
import static com.bogdatech.utils.JsoupUtils.translateSingleLine;

@Component
public class LiquidHtmlTranslatorUtils {

    static TelemetryClient appInsights = new TelemetryClient();
    // 不翻译的URL模式
    public static final Pattern URL_PATTERN = Pattern.compile("https?://[^\\s<>\"]+|www\\.[^\\s<>\"]+");
    // 不翻译的Liquid变量模式
    public static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{[^}]+\\}\\}");
    // 自定义变量模式：%{ order.name } 等
    public static final Pattern CUSTOM_VAR_PATTERN = Pattern.compile("%\\{[^}]+\\}");
    // Liquid条件语句模式：{% if order.po_number != blank %} 等
    public static final Pattern LIQUID_CONDITION_PATTERN = Pattern.compile("\\{%[^%]+%\\}");
    // 数组变量模式：[ product[1]] 等
    public static final Pattern ARRAY_VAR_PATTERN = Pattern.compile("\\[\\s*[^\\]]+\\s*\\]");
    // 纯符号模式：匹配单独的 -、×、+、= 等符号（不含字母数字）
    public static final Pattern SYMBOL_PATTERN = Pattern.compile("^[\\-×\\+=×*/|!@#$%^&()_]+$", Pattern.MULTILINE);
    // 判断是否有 <html> 标签的模式
    public static final Pattern HTML_TAG_PATTERN = Pattern.compile("<\\s*html\\s*", Pattern.CASE_INSENSITIVE);
    // 从配置文件读取不翻译的标签，默认为 "style,img,script"
    public final static Set<String> noTranslateTags = new HashSet<>(Arrays.asList("style", "img", "script"));

    /**
     * 主翻译方法
     *
     * @param html 输入的HTML文本
     * @return 翻译后的HTML文本
     */
    public static String translateNewHtml(String html, TranslateRequest request, CharacterCountUtils counter, String resourceType) {
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

                processNode(doc.body(), request, counter, resourceType);
                String result = doc.outerHtml(); // 返回完整的HTML结构
//                appInsights.trackTrace("有html标签： "  + result);
//                System.out.println("有html标签： "  + result);
                return result;
            } else {
                // 如果没有 <html> 标签，作为片段处理
                Document doc = Jsoup.parseBodyFragment(html);
                Element body = doc.body();

                processNode(body, request, counter, resourceType);

                // 只返回子节点内容，不包含 <body>
                StringBuilder result = new StringBuilder();
                for (Node child : body.childNodes()) {
                    result.append(child.toString());
                }

                String output = result.toString();
//                appInsights.trackTrace("没有html标签： "  + output);
//                System.out.println("没有html标签： "  + output);
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
    private static void processNode(Node node, TranslateRequest request, CharacterCountUtils counter, String resourceType) {
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
                    processNode(child, request, counter, resourceType);
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

                String translatedText = translateTextWithCache(text, request, counter, resourceType);
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
    private static String translateTextWithCache(String text, TranslateRequest request, CharacterCountUtils counter, String resourceType) {
        // 检查缓存
        String translated = translateSingleLine(text, request.getTarget());
        if (translated != null) {
            return translated;
        }

        // 处理文本中的变量和URL
        String translatedText = translateTextWithProtection(text, request, counter, resourceType);

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
    private static String translateTextWithProtection(String text, TranslateRequest request, CharacterCountUtils counter, String resourceType) {
        StringBuilder result = new StringBuilder();
        int lastEnd = 0;

        // 合并所有需要保护的模式
        List<Pattern> patterns = Arrays.asList(
                URL_PATTERN,
                VARIABLE_PATTERN,
                CUSTOM_VAR_PATTERN,
                LIQUID_CONDITION_PATTERN,
                ARRAY_VAR_PATTERN,
                SYMBOL_PATTERN
        );

        List<MatchRange> matches = new ArrayList<>();
        for (Pattern pattern : patterns) {
            Matcher matcher = pattern.matcher(text);
            while (matcher.find()) {
                matches.add(new MatchRange(matcher.start(), matcher.end(), matcher.group()));
            }
        }

        // 按位置排序
        matches.sort(Comparator.comparingInt(m -> m.start));

        // 处理所有匹配项之间的文本
        for (MatchRange match : matches) {
            // 翻译匹配项之前的文本
            if (match.start > lastEnd) {
                String toTranslate = text.substring(lastEnd, match.start);
                String cleanedText = cleanTextFormat(toTranslate); // 清理格式
                //对特殊符号进行处理
                if (cleanedText.matches("\\p{Zs}")) {
//                    System.out.println("要翻译的空白： " + cleanedText);
                    result.append(cleanedText);
                    continue;
                }
                if (!cleanedText.trim().isEmpty()) { // 避免翻译空字符串
                    String targetString;
                    try {
                        if (cleanedText.length() > 32) {
                            //AI翻译
//                            System.out.println("要翻译的文本AI： " + cleanedText);
//                            appInsights.trackTrace("要翻译的文本AI： " + cleanedText);
                            targetString = singleTranslate(cleanedText, resourceType, counter, request.getTarget());
                            targetString = isHtmlEntity(targetString);
                            result.append(targetString);
                        } else {
                            request.setContent(cleanedText);
//                            appInsights.trackTrace("要翻译的文本： " + cleanedText);
//                            System.out.println("要翻译的文本： " + cleanedText);
                            targetString = translateAndCount(request, counter, resourceType);
                            targetString = isHtmlEntity(targetString);;
                            result.append(targetString);
                        }
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
            if (cleanedText.matches("\\p{Zs}")) {
//                System.out.println("要翻译的剩余空白： " + cleanedText);
                result.append(cleanedText);
                return result.toString();
            }
            if (!cleanedText.trim().isEmpty() && !cleanedText.matches("\\s*")) {
                String targetString = null;
                try {
                    if (cleanedText.length() > 32) {
                        //AI翻译
//                        appInsights.trackTrace("处理剩余文本AI： " + cleanedText);
//                        System.out.println("要翻译的文本AI： " + cleanedText);
                        targetString = singleTranslate(cleanedText, resourceType, counter, request.getTarget());
                        targetString = isHtmlEntity(targetString);
                        result.append(targetString);
                    } else {
                        request.setContent(cleanedText);
//                        appInsights.trackTrace("处理剩余文本： " + cleanedText);
//                        System.out.println("要翻译的文本： " + cleanedText);
                        targetString = translateAndCount(request, counter, resourceType);
                        targetString = isHtmlEntity(targetString);
                        result.append(targetString);
                    }
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
    public static String cleanTextFormat(String text) {
        // 去除首尾的换行符和多余空格，保留内部有效内容
        return text.trim().replaceAll("[\\r\\n]+", "").replaceAll("\\s+", " ");
    }

    // 辅助类用于保存匹配范围
    public static class MatchRange {
        int start;
        int end;
        String content;

        MatchRange(int start, int end, String content) {
            this.start = start;
            this.end = end;
            this.content = content;
        }
    }

    //判断是否含有HTML实体
    public static String isHtmlEntity(String text) {
        int i = 0;
        while (!text.equals(StringEscapeUtils.unescapeHtml4(text))) {
            // 如果有 HTML 实体，则解码
            text = StringEscapeUtils.unescapeHtml4(text);
            i++;
            if (i > 3){
                return text;
            }
        }

        // 最终结果（无 HTML 实体后直接输出）
        return text;
    }
}
