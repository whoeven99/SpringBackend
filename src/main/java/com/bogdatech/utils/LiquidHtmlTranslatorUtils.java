package com.bogdatech.utils;


import com.bogdatech.Service.IVocabularyService;
import com.bogdatech.entity.DTO.FullAttributeSnapshotDTO;
import com.bogdatech.exception.ClientException;
import com.bogdatech.integration.ALiYunTranslateIntegration;
import com.bogdatech.integration.ChatGptIntegration;
import com.bogdatech.logic.RedisProcessService;
import com.bogdatech.model.controller.request.TranslateRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import org.apache.commons.text.StringEscapeUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import static com.bogdatech.constants.TranslateConstants.*;
import static com.bogdatech.logic.RabbitMqTranslateService.BATCH_SIZE;
import static com.bogdatech.logic.TranslateService.OBJECT_MAPPER;
import static com.bogdatech.logic.TranslateService.userTranslate;
import static com.bogdatech.utils.ApiCodeUtils.getLanguageName;
import static com.bogdatech.utils.CaseSensitiveUtils.appInsights;
import static com.bogdatech.utils.JsoupUtils.isHtml;
import static com.bogdatech.utils.MapUtils.getTranslationStatusMap;
import static com.bogdatech.utils.PlaceholderUtils.*;

@Component
public class LiquidHtmlTranslatorUtils {

    // 不翻译的URL模式
    public static final Pattern URL_PATTERN = Pattern.compile("https?://[^\\s<>\"]+|www\\.[^\\s<>\"]+");
    //    // 不翻译的Liquid变量模式
//    public static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{[^}]+\\}\\}");
    // 自定义变量模式：%{ order.name } 等
    public static final Pattern CUSTOM_VAR_PATTERN = Pattern.compile("\\{\\{[^}]+\\}\\}|\\{\\w+\\}|%\\{[^}]+\\}|\\{%(.*?)%\\}|\\[[^\\]]+\\]");
    //    // Liquid条件语句模式：{% if order.po_number != blank %} 等
//    public static final Pattern LIQUID_CONDITION_PATTERN = Pattern.compile("\\{%[^%]+%\\}");
//    // 数组变量模式：[ product[1]] 等
//    public static final Pattern ARRAY_VAR_PATTERN = Pattern.compile("\\[\\s*[^\\]]+\\s*\\]");
    // 纯符号模式：匹配单独的 -、×、+、= 等符号（不含字母数字）
    public static final Pattern SYMBOL_PATTERN = Pattern.compile("^[\\+=×*/|!@#$%^&()_]+$", Pattern.MULTILINE);
    // 判断是否有 <html> 标签的模式
    public static final Pattern HTML_TAG_PATTERN = Pattern.compile("<\\s*html\\s*", Pattern.CASE_INSENSITIVE);
    // 从配置文件读取不翻译的标签，默认为 "style,img,script"
    public final static Set<String> NO_TRANSLATE_TAGS = Set.of("script", "style", "meta", "svg", "canvas", "link");
    private static final Pattern EMOJI_PATTERN = Pattern.compile("[\\p{So}\\p{Cn}]|(?:[\uD83C-\uDBFF\uDC00\uDFFF])+");
    private static final String STYLE_TEXT = "data-id";

    @Autowired
    private ALiYunTranslateIntegration aLiYunTranslateIntegration;
    @Autowired
    private JsoupUtils jsoupUtils;
    @Autowired
    private ChatGptIntegration chatGptIntegration;
    @Autowired
    private RedisProcessService redisProcessService;
    @Autowired
    private IVocabularyService vocabularyService;

    /**
     * 主翻译方法
     *
     * @param html 输入的HTML文本
     * @return 翻译后的HTML文本
     */
    public String translateNewHtml(String html, TranslateRequest request, CharacterCountUtils counter, String languagePackId, Integer limitChars, String model, String customKey, String translationModel) {
        // 检查输入是否有效
        if (html == null || html.trim().isEmpty()) {
            return html;
        }

        try {
            // 判断输入是否包含 <html> 标签
            boolean hasHtmlTag = HTML_TAG_PATTERN.matcher(html).find();

            if (hasHtmlTag) {
                // 如果有 <html> 标签，按完整文档处理
                Document doc = Jsoup.parse(html);

                // 获取 <html> 元素并修改 lang 属性
                Element htmlTag = doc.selectFirst("html");
                if (htmlTag != null) {
                    htmlTag.attr("lang", request.getTarget());
                }

                processNode(doc.body(), request, counter, languagePackId, limitChars, model, customKey, translationModel);
                String result = doc.outerHtml(); // 返回完整的HTML结构
                result = isHtmlEntity(result);
                return result;
            } else {
                // 如果没有 <html> 标签，作为片段处理
                Document doc = Jsoup.parseBodyFragment(html);
                Element body = doc.body();
                processNode(body, request, counter, languagePackId, limitChars, model, customKey, translationModel);
                // 只返回子节点内容，不包含 <body>
                StringBuilder result = new StringBuilder();
                for (Node child : body.childNodes()) {
                    result.append(child.toString());
                }

                String output = result.toString();
                output = isHtmlEntity(output);
                return output;
            }

        } catch (Exception e) {
            appInsights.trackException(e);
            appInsights.trackTrace("clickTranslation " + request.getShopName() + " new html errors : " + e + " sourceText: " + html);
            return html;
        }
    }

    /**
     * 递归处理节点
     *
     * @param node 当前节点
     */
    private void processNode(Node node, TranslateRequest request, CharacterCountUtils counter, String languagePackId, Integer limitChars, String model, String customKey, String translationModel) {
        try {
            // 如果是元素节点
            if (node instanceof Element) {
                Element element = (Element) node;
                String tagName = element.tagName().toLowerCase();

                // 检查是否为不翻译的标签
                if (NO_TRANSLATE_TAGS.contains(tagName)) {
                    return;
                }

                // 属性不翻译，保持原样
                element.attributes().forEach(attr -> {
                });

                // 递归处理子节点
                for (Node child : element.childNodes()) {
                    processNode(child, request, counter, languagePackId, limitChars, model, customKey, translationModel);
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

                String translatedText = translateTextWithCache(text, request, counter, languagePackId, limitChars, model, customKey, translationModel);
                textNode.text(translatedText);
            }
        } catch (Exception e) {
            appInsights.trackTrace("clickTranslation " + request.getShopName() + " 递归处理节点报错： " + e.getMessage());
        }
    }

    /**
     * 使用缓存处理文本内容，保护变量和URL
     *
     * @param text 输入文本
     * @return 翻译后的文本
     */
    private String translateTextWithCache(String text, TranslateRequest request, CharacterCountUtils counter, String languagePackId, Integer limitChars, String model, String customKey, String translationModel) {
        // 检查缓存
        String translated = redisProcessService.getCacheData(request.getTarget(), text);
        if (translated != null) {
            return translated;
        }

        // 处理文本中的变量和URL
        return translateTextWithProtection(text, request, counter, languagePackId, limitChars, model, customKey, translationModel);
    }

    /**
     * 处理文本内容，保护变量和URL
     *
     * @param text 输入文本
     * @return 翻译后的文本
     */
    private String translateTextWithProtection(String text, TranslateRequest request, CharacterCountUtils counter, String languagePackId, Integer limitChars, String model, String customKey, String translationModel) {
        StringBuilder result = new StringBuilder();
        int lastEnd = 0;

        // 合并所有需要保护的模式
        List<Pattern> patterns = Arrays.asList(
                URL_PATTERN,
                SYMBOL_PATTERN,
                EMOJI_PATTERN
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
//                appInsights.trackTrace("cleanedText1: " + cleanedText);
                //对特殊符号进行处理
                if (cleanedText.matches("\\p{Zs}+")) {
//                    appInsights.trackTrace("要翻译的空白1： " + cleanedText);
                    result.append(cleanedText);
                    continue;
                }
                if (!cleanedText.trim().isEmpty()) { // 避免翻译空字符串
                    String targetString;
                    try {
                        request.setContent(cleanedText);
                        targetString = addSpaceAfterTranslated(cleanedText, request, counter, languagePackId, limitChars, model, customKey, translationModel);
                        result.append(targetString);
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
//            appInsights.trackTrace("cleanedText2: " + cleanedText);
            if (cleanedText.matches("\\p{Zs}+")) {
                result.append(cleanedText);
                return result.toString();
            }
            if (!cleanedText.trim().isEmpty() && !cleanedText.matches("\\s*")) {
                String targetString;
                try {
                    request.setContent(cleanedText);
                    targetString = addSpaceAfterTranslated(cleanedText, request, counter, languagePackId, limitChars, model, customKey, translationModel);
                    result.append(targetString);
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
//        return text.trim().replaceAll("[\\r\\n]+", "").replaceAll("\\s+", " ");
        return text.replaceAll("[\\r\\n]+", "");
    }

    // 辅助类用于保存匹配范围
    public static class MatchRange {
        public int start;
        public int end;
        public String content;

        public MatchRange(int start, int end, String content) {
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
            if (i > 3) {
                return text;
            }
        }

        // 最终结果（无 HTML 实体后直接输出）
        return text;
    }

    /**
     * 翻译html文本（整段翻译，需要处理一下）
     * 对数据进行处理，将属性替换
     * 判断调用qwen还是openai
     */
    public String fullTranslateHtmlByQwen(String text, String languagePack, CharacterCountUtils counter, String target, String shopName, Integer limitChars, String translateModel, String source) {
        //选择翻译html的提示词
        String targetLanguage = getLanguageName(target);
        String fullHtmlPrompt = getFullHtmlPrompt(targetLanguage, languagePack);
        appInsights.trackTrace("clickTranslation " + shopName + " 翻译 html 的提示词：" + fullHtmlPrompt);

        boolean hasHtmlTag = HTML_TAG_PATTERN.matcher(text).find();
        Document originalDoc;
        if (hasHtmlTag) {
            originalDoc = Jsoup.parse(text);
        } else {
            originalDoc = Jsoup.parseBodyFragment(text);
        }

        // 2. 提取样式并标记 ID
        Map<String, FullAttributeSnapshotDTO> attrMap = tagElementsAndSaveFullAttributes(originalDoc);

        // 3. 获取清洗后的 HTML（无样式）
        removeAllAttributesExceptMarker(originalDoc);

        String cleanedHtml = originalDoc.body().html();
        //返回翻译结果
        try {
            if (translateModel != null && translateModel.equals(OPENAI_MODEL)) {
                String chatGptString = chatGptIntegration.chatWithGpt(fullHtmlPrompt, cleanedHtml, new TranslateRequest(0, shopName, null, source, target, cleanedHtml), counter, limitChars);
                return processTranslationResult(chatGptString, attrMap, hasHtmlTag);
            }
            String aLiString = aLiYunTranslateIntegration.singleTranslate(cleanedHtml, fullHtmlPrompt, counter, target, shopName, limitChars);
            return processTranslationResult(aLiString, attrMap, hasHtmlTag);

        } catch (Exception e) {
            appInsights.trackException(e);
            appInsights.trackTrace("clickTranslation " + shopName + " html 翻译失败 errors : " + e + "source: " + cleanedHtml);
            //睡眠10s
            try {
                Thread.sleep(10000);
            } catch (Exception exception) {
                appInsights.trackTrace("clickTranslation " + shopName + " sleep 失败 ");
            }
            String aLiString = aLiYunTranslateIntegration.singleTranslate(cleanedHtml, fullHtmlPrompt, counter, target, shopName, limitChars);
            if (aLiString != null) {
                return aLiString;
            }
            return text;
        }
    }

    /**
     * 对大模型翻译后的数据进行处理并返回
     */
    public static String processTranslationResult(String translatedText, Map<String, FullAttributeSnapshotDTO> attrMap, boolean hasHtmlTag) {
        Document translatedDoc;
        if (hasHtmlTag) {
            translatedDoc = Jsoup.parse(translatedText);
        } else {
            translatedDoc = Jsoup.parseBodyFragment(translatedText);
        }
        // 第五步：将样式还原到翻译后的 HTML 中
        restoreFullAttributes(translatedDoc, attrMap);
        return translatedDoc.body().html();
    }

    /**
     * 翻译政策html文本
     * 目前专门用qwen翻译
     */
    public String fullTranslatePolicyHtmlByQwen(String text, CharacterCountUtils counter, String target, String shopName, Integer limitChars) {
        //选择翻译html的提示词
        String targetLanguage = getLanguageName(target);
        String fullPolicyHtmlPrompt = getPolicyPrompt(targetLanguage);
        appInsights.trackTrace("clickTranslation " + shopName + " 翻译 政策 html 的提示词：" + fullPolicyHtmlPrompt);
        //调用qwen翻译
        //返回翻译结果
        try {
            return aLiYunTranslateIntegration.singleTranslate(text, fullPolicyHtmlPrompt, counter, target, shopName, limitChars);
        } catch (Exception e) {
            appInsights.trackTrace("clickTranslation " + shopName + " 翻译 政策 html 翻译失败 errors : " + e + " sourceText: " + text);
            //睡眠10s
            try {
                Thread.sleep(10000);
            } catch (Exception exception) {
                appInsights.trackTrace("clickTranslation " + shopName + " sleep 失败 ");
            }
            String aLiString = aLiYunTranslateIntegration.singleTranslate(text, fullPolicyHtmlPrompt, counter, target, shopName, limitChars);
            if (aLiString != null) {
                return aLiString;
            }
            return text;
        }
    }

    /**
     * 手动添加空格
     */
    public String addSpaceAfterTranslated(String sourceText, TranslateRequest request, CharacterCountUtils counter, String languagePackId, Integer limitChars, String model, String customKey, String translationModel) {
        // Step 1: 记录开头和结尾的空格数量
        int leadingSpaces = countLeadingSpaces(sourceText);
        int trailingSpaces = countTrailingSpaces(sourceText);
        // Step 2: 去除首尾空格，准备翻译
        String textToTranslate = sourceText.trim();

        // Step 3: 翻译操作
        request.setContent(textToTranslate);
        String targetString;
        if (model != null && customKey != null && model.equals(PRODUCT)) {
            targetString = jsoupUtils.translateKeyModelAndCount(request, counter, languagePackId, limitChars, "product description", customKey, translationModel);
        } else if (model != null && customKey != null && model.equals(ARTICLE)) {
            targetString = jsoupUtils.translateKeyModelAndCount(request, counter, languagePackId, limitChars, "article content", customKey, translationModel);
        } else if (model != null && customKey != null) {
            targetString = jsoupUtils.translateKeyModelAndCount(request, counter, languagePackId, limitChars, null, customKey, translationModel);
        } else {
            targetString = jsoupUtils.translateAndCount(request, counter, languagePackId, GENERAL, limitChars);
        }
//        String targetString = textToTranslate + 1;
        // Step 4: 恢复开头和结尾空格
        StringBuilder finalResult = new StringBuilder();
        if (leadingSpaces != 0) {
            finalResult.append(" ".repeat(Math.max(0, leadingSpaces)));
        }

        finalResult.append(targetString);

        if (trailingSpaces != 0) {
            finalResult.append(" ".repeat(Math.max(0, trailingSpaces)));
        }

        appInsights.trackTrace("clickTranslation " + request.getShopName() + " finalResult: " + "'" + finalResult + "'" + " sourceText: " + sourceText);

        return finalResult.toString();
    }

    public static int countLeadingSpaces(String s) {
        if (s == null || s.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (char c : s.toCharArray()) {
            if (c == ' ') {
                count++;
            } else {
                break;
            }
        }
        return count;
    }

    public static int countTrailingSpaces(String s) {
        if (s == null || s.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (int i = s.length() - 1; i >= 0; i--) {
            if (s.charAt(i) == ' ') {
                count++;
            } else {
                break;
            }
        }
        return count;
    }


    /**
     * 给所有含保留属性的元素打上唯一 data-id，并保存这些属性
     *
     * @param doc HTML 文档
     * @return 属性快照映射表
     */
    public static Map<String, FullAttributeSnapshotDTO> tagElementsAndSaveFullAttributes(Document doc) {
        Map<String, FullAttributeSnapshotDTO> attrMap = new LinkedHashMap<>();
        AtomicInteger idGen = new AtomicInteger(0);

        for (Element el : doc.getAllElements()) {
            // 排除我们自己用于标记的属性
            List<Attribute> attrList = el.attributes().asList().stream()
                    .filter(attr -> !attr.getKey().equals(STYLE_TEXT))
                    .toList();

            // 拼接为完整属性字符串
            if (!attrList.isEmpty()) {
                String id = "attr-" + idGen.getAndIncrement();
                el.attr(STYLE_TEXT, id); // 添加唯一标识

                String fullAttrs = attrList.stream()
                        .map(attr -> attr.getKey() + "=\"" + attr.getValue() + "\"")
                        .collect(Collectors.joining(" "));

                FullAttributeSnapshotDTO dto = new FullAttributeSnapshotDTO();
                dto.fullAttributes = fullAttrs;
                attrMap.put(id, dto);
            }
        }

        return attrMap;
    }


    /**
     * 移除所有保存的属性（包括 style, class, data-* 等），只保留 data-id
     */
    public static void removeAllAttributesExceptMarker(Document doc) {
        for (Element el : doc.getAllElements()) {
            List<String> attrKeys = el.attributes().asList().stream()
                    .map(Attribute::getKey)
                    .filter(key -> !key.equals(STYLE_TEXT))
                    .toList();

            attrKeys.forEach(el::removeAttr);
        }
    }

    /**
     * 还原之前保存的属性信息
     */
    public static void restoreFullAttributes(Document doc, Map<String, FullAttributeSnapshotDTO> attrMap) {
        for (Element el : doc.select("[" + STYLE_TEXT + "]")) {
            String id = el.attr(STYLE_TEXT);
            FullAttributeSnapshotDTO snapshot = attrMap.get(id);

            if (snapshot != null) {
                // 安全处理 null
                String fullAttrs = snapshot.fullAttributes != null ? snapshot.fullAttributes.trim() : "";
                String innerHtml = el.html() != null ? el.html() : "";

                // 构造 fakeOuterHtml
                String fakeOuterHtml = "<" + el.tagName() + " " + fullAttrs + ">" + innerHtml + "</" + el.tagName() + ">";

                // 解析并安全获取第一个子节点
                Element body = Jsoup.parseBodyFragment(fakeOuterHtml).body();
                if (body.childrenSize() > 0) {
                    Element restored = body.child(0);

                    // 替换所有属性
                    el.clearAttributes();
                    restored.attributes().forEach(attr -> el.attr(attr.getKey(), attr.getValue()));
                } else {
                    // 记录异常情况，方便排查
                    appInsights.trackTrace(
                            "[restoreFullAttributes] 无法解析出有效子节点, id=" + id +
                                    ", tag=" + el.tagName() +
                                    ", fakeOuterHtml=" + fakeOuterHtml
                    );
                }
            }

            // 可选：删除还原标记
            el.removeAttr(STYLE_TEXT);
        }
    }

    /**
     * html的拆分翻译，放弃递归，改为json翻译
     */
    public String newJsonTranslateHtml(String html, TranslateRequest request, CharacterCountUtils counter, String languagePackId, Integer limitChars) {
        if (!isHtml(html)) {
            return null;
        }
        Map<String, Object> translationStatusMap = getTranslationStatusMap(html, 2);
        userTranslate.put(request.getShopName(), translationStatusMap);
        html = isHtmlEntity(html); //判断是否含有HTML实体,然后解码
        //1, 解析html，根据html标签，选择不同的解析方式， 将prettyPrint设置为false
        boolean hasHtmlTag = HTML_TAG_PATTERN.matcher(html).find();
        Document doc = parseHtml(html, request.getTarget(), hasHtmlTag);

        // 2. 收集所有 TextNode
        List<TextNode> nodes = new ArrayList<>();
        for (Element element : doc.getAllElements()) {
            nodes.addAll(element.textNodes());
        }

        // 3. 提取要翻译文本
        List<String> originalTexts = new ArrayList<>();
        for (TextNode node : nodes) {
            String text = node.text().trim();
            if (!text.isEmpty() && !originalTexts.contains(text)) {
                originalTexts.add(text);
            }
        }

        // 4. 每50条一次翻译
        Map<String, String> translatedTexts = translateAllList(originalTexts, request, counter, languagePackId, limitChars);

        // 5. 填回原处
        fillBackTranslatedData(nodes, translatedTexts);

        // 输出翻译后的 HTML
        if (hasHtmlTag) {
            String results = doc.outerHtml(); // 返回完整的HTML结构
            results = isHtmlEntity(results);
            return results;
        } else {
            Element body = doc.body();
            // 只返回子节点内容，不包含 <body>
            StringBuilder results = new StringBuilder();
            for (Node child : body.childNodes()) {
                if (child != null) {
                    String childHtml = child.outerHtml(); // 或 child.toString()
                    results.append(childHtml);
                }
            }
            String output2 = results.toString();
            output2 = isHtmlEntity(output2);
            return output2;
        }
    }

    /**
     * 解析html，根据html标签，选择不同的解析方式， 将prettyPrint设置为false
     */
    public static Document parseHtml(String html, String target, boolean hasHtmlTag) {
        Document doc;
        if (hasHtmlTag) {
            doc = Jsoup.parse(html);
            // 获取 <html> 元素并修改 lang 属性
            Element htmlTag = doc.selectFirst("html");
            if (htmlTag != null) {
                htmlTag.attr("lang", target);
            }
        } else {
            doc = Jsoup.parseBodyFragment(html);
        }
        doc.outputSettings().prettyPrint(false);
        return doc;
    }

    /**
     * 每50条文本翻译一次
     */
    public Map<String, String> translateAllList(List<String> originalTexts, TranslateRequest request, CharacterCountUtils counter, String languagePack, Integer limitChars) {
        String target = request.getTarget();
        String shopName = request.getShopName();
        String source = request.getSource();
        String prompt = getListPrompt(getLanguageName(target), languagePack, null, null);
        Map<String, String> allTranslatedMap = new HashMap<>();
        //先缓存翻译一次
        cacheAndDbTranslateData(originalTexts, target, source, allTranslatedMap);
        //翻译剩余未翻译的数据
        for (int i = 0; i < originalTexts.size(); i += BATCH_SIZE) {
            // 取每次的50条（或剩余全部）
            int endIndex = Math.min(i + BATCH_SIZE, originalTexts.size());
            List<String> batch = originalTexts.subList(i, endIndex);
            if (batch.isEmpty()) {
                continue;
            }
            //筛选出来要翻译的数据
            String sourceJson;
            try {
                sourceJson = OBJECT_MAPPER.writeValueAsString(batch);
                //翻译
                String translated = jsoupUtils.translateByCiwiUserModel(target, sourceJson, shopName, source, counter, limitChars, prompt);
                Map<String, String> resultMap = OBJECT_MAPPER.readValue(translated, new TypeReference<>() {});
                allTranslatedMap.putAll(resultMap);
            } catch (JsonProcessingException e) {
                appInsights.trackException(e);
                appInsights.trackTrace("translateAllList 用户： " + shopName + " 翻译类型 : HTML 提示词 : " + prompt + " 未翻译文本 : " + batch);
            }
        }
        return allTranslatedMap;
    }

    // 模拟批量翻译
    private static Map<String, String> fakeTranslateAll(List<String> texts) {
        Map<String, String> resultMap = new HashMap<>();
        for (String t : texts) {
            resultMap.put(t, "Z " + t + " Z");
        }
        return resultMap;
    }

    /**
     * 用缓存和db，翻译List<String>类型的数据
     */
    private void cacheAndDbTranslateData(List<String> originalTexts, String target, String source, Map<String, String> allTranslatedMap) {
        Iterator<String> it = originalTexts.iterator();
        while (it.hasNext()) {
            String sourceText = it.next();
            String cacheData = redisProcessService.getCacheData(target, sourceText);
            if (cacheData != null) {
                allTranslatedMap.put(sourceText, cacheData);
                it.remove(); // 安全删除当前元素
                continue;
            }

            String dbData = vocabularyService.getTranslateTextDataInVocabulary(target, sourceText, source);
            if (dbData != null) {
                allTranslatedMap.put(sourceText, dbData);
                redisProcessService.setCacheData(target, dbData, sourceText);
                it.remove(); // 同样可以安全删除
            }
        }
    }

    /**
     * 将翻译后的数据填回原处
     * */
    public static void fillBackTranslatedData(List<TextNode> nodes, Map<String, String> translatedTexts) {
        for (TextNode node : nodes) {
            String text = node.getWholeText();
            if (!text.isEmpty()) {
                //记录空格，还需要填回
                // Step 1: 记录开头和结尾的空格数量
                // 匹配前导空格
                Matcher leadingMatcher = Pattern.compile("^(\\p{Zs}+)").matcher(text);
                String leading = leadingMatcher.find() ? leadingMatcher.group(1) : "";
                // 匹配尾随空格
                Matcher trailingMatcher = Pattern.compile("(\\p{Zs}+)$").matcher(text);
                String trailing = trailingMatcher.find() ? trailingMatcher.group(1) : "";
                // 去掉前后空格，得到核心文本
                int begin = leading.length();
                int end = text.length() - trailing.length();
                String core = (begin >= end) ? "" : text.substring(begin, end);
                // 拼回原来的空格
                String targetText = translatedTexts.get(core);
                if (core.isEmpty()) {
                    // 没有核心文本，只保留原始空格（避免被清空，也避免重复加）
                    targetText = text;
                    appInsights.trackTrace("fillBackTranslatedData targetText 没有被翻译，原文是: " + targetText);
                } else {
                    if (targetText != null && !targetText.trim().isEmpty()) {
                        targetText = leading + targetText + trailing;
                    } else {
                        targetText = leading + core + trailing;
                    }
                }

                node.text(targetText);
            }
        }
    }
}
