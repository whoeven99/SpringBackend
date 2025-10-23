package com.bogdatech.utils;


import com.bogdatech.entity.DTO.FullAttributeSnapshotDTO;
import com.bogdatech.integration.ALiYunTranslateIntegration;
import com.bogdatech.logic.RedisProcessService;
import com.bogdatech.model.controller.request.TranslateRequest;
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
import static com.bogdatech.integration.ALiYunTranslateIntegration.calculateBaiLianToken;
import static com.bogdatech.logic.RabbitMqTranslateService.BATCH_SIZE;
import static com.bogdatech.logic.TranslateService.OBJECT_MAPPER;
import static com.bogdatech.utils.ApiCodeUtils.getLanguageName;
import static com.bogdatech.utils.CaseSensitiveUtils.appInsights;
import static com.bogdatech.utils.JsonUtils.objectToJson;
import static com.bogdatech.utils.JsoupUtils.isHtml;
import static com.bogdatech.utils.PlaceholderUtils.*;
import static com.bogdatech.utils.StringUtils.parseJson;

@Component
public class LiquidHtmlTranslatorUtils {

    // 不翻译的URL模式
    public static final Pattern URL_PATTERN = Pattern.compile("https?://[^\\s<>\"]+|www\\.[^\\s<>\"]+");
    // 自定义变量模式：%{ order.name } 等
    public static final Pattern CUSTOM_VAR_PATTERN = Pattern.compile("\\{\\{[^}]+\\}\\}|\\{\\w+\\}|%\\{[^}]+\\}|\\{%(.*?)%\\}|\\[[^\\]]+\\]");
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
    private RedisProcessService redisProcessService;

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
    public String newJsonTranslateHtml(String html, TranslateRequest request, CharacterCountUtils counter, String languagePackId, Integer limitChars, boolean isSingleFlag) {
        if (!isHtml(html)) {
            return null;
        }

        html = isHtmlEntity(html); //判断是否含有HTML实体,然后解码
        //1, 解析html，根据html标签，选择不同的解析方式， 将prettyPrint设置为false
        appInsights.trackTrace("定义translateRequest 用户： " + request.getShopName());
        boolean hasHtmlTag = HTML_TAG_PATTERN.matcher(html).find();
        appInsights.trackTrace("解析html 用户： " + request.getShopName());
        Document doc = parseHtml(html, request.getTarget(), hasHtmlTag);

        // 2. 收集所有 TextNode
        List<TextNode> nodes = new ArrayList<>();
        for (Element element : doc.getAllElements()) {
            nodes.addAll(element.textNodes());
        }
        appInsights.trackTrace("收集完所有的TextNode 用户： " + request.getShopName());

        // 3. 提取要翻译文本
        List<String> originalTexts = new ArrayList<>();
        for (TextNode node : nodes) {
            String text = node.text().trim();
            if (!text.isEmpty() && !originalTexts.contains(text)) {
                originalTexts.add(text);
            }
        }
        appInsights.trackTrace("提取完所有的翻译文本 用户： " + request.getShopName());

        // 4. 每50条一次翻译
        Map<String, String> translatedTexts = translateAllList(originalTexts, request, counter, languagePackId, limitChars, isSingleFlag);
        appInsights.trackTrace("翻译完所有文本 用户： " + request.getShopName());

        // 5. 填回原处
        appInsights.trackTrace("填回原处前 用户： " + request.getShopName());
        fillBackTranslatedData(nodes, translatedTexts, request.getTarget(), request.getShopName());
        appInsights.trackTrace("填回原处后 用户： " + request.getShopName());

        // 输出翻译后的 HTML
        if (hasHtmlTag) {
            appInsights.trackTrace("输出翻译后的 HTML 1 用户： " + request.getShopName());
            String results = doc.outerHtml(); // 返回完整的HTML结构
            results = isHtmlEntity(results);
            return results;
        } else {
            appInsights.trackTrace("输出翻译后的 HTML 2 用户： " + request.getShopName());
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
    public Map<String, String> translateAllList(List<String> originalTexts, TranslateRequest request, CharacterCountUtils counter, String languagePack, Integer limitChars, boolean isSingleFlag) {
        String target = request.getTarget();
        String shopName = request.getShopName();
        String source = request.getSource();
        String prompt = getListPrompt(getLanguageName(target), languagePack, null, null);
        appInsights.trackTrace("translateAllList 用户： " + shopName + " 翻译类型 : HTML 提示词 : " + prompt + " 待翻译文本 : " + originalTexts.size() + "条");
        Map<String, String> allTranslatedMap = new HashMap<>();
        //先缓存翻译一次
        cacheAndDbTranslateData(originalTexts, target, source, allTranslatedMap, shopName);
        appInsights.trackTrace("缓存翻译完成 用户： " + request.getShopName());
        for (int i = 0; i < originalTexts.size(); i += BATCH_SIZE) {
            int endIndex = Math.min(i + BATCH_SIZE, originalTexts.size());
            List<String> batch = originalTexts.subList(i, endIndex);
            appInsights.trackTrace("取对应数据 用户： " + request.getShopName() + endIndex);
            if (batch.isEmpty()) {
                continue;
            }

            // 二次切分：每批次最多 1000 token
            List<String> currentGroup = new ArrayList<>();
            int currentTokens = 0;
            appInsights.trackTrace("二次切分前 用户： " + request.getShopName() + " currentGroup : " + currentGroup + " currentTokens : " + currentTokens);
            for (String text : batch) {
                int tokens = calculateBaiLianToken(text);
                // 如果加上这条会超过 1000 token，就先处理当前组
                if (currentTokens + tokens > 1000 && !currentGroup.isEmpty()) {
                    processBatch(currentGroup, request.getShopName(), shopName, prompt, target, source, counter, limitChars, allTranslatedMap, isSingleFlag);
                    currentGroup = new ArrayList<>();
                    currentTokens = 0;
                }

                currentGroup.add(text);
                currentTokens += tokens;
                appInsights.trackTrace("1000token以内的数据： " + currentTokens + " currentGroup : " + currentGroup);
            }

            // 处理最后剩下的一组
            if (!currentGroup.isEmpty()) {
                processBatch(currentGroup, request.getShopName(), shopName, prompt, target, source, counter, limitChars, allTranslatedMap, isSingleFlag);
            }
        }

        return allTranslatedMap;
    }

    /**
     * 对拆分完的一批次进行翻译
     */
    private void processBatch(List<String> texts, String requestShopName, String shopName, String prompt, String target, String source, CharacterCountUtils counter, Integer limitChars, Map<String, String> allTranslatedMap, boolean isSingleFlag) {
        try {
            String sourceJson = objectToJson(texts);
            appInsights.trackTrace("开始模型翻译 用户： " + requestShopName);
            String translated = jsoupUtils.translateByCiwiUserModel(target, sourceJson, shopName, source, counter, limitChars, prompt, isSingleFlag);
            if (translated == null) {
                translated = aLiYunTranslateIntegration.userTranslate(sourceJson, prompt, counter, target, shopName, limitChars, isSingleFlag);
            }
            appInsights.trackTrace("翻译结束 解析数据 用户 ：" + requestShopName);
            String parseJson = parseJson(translated, shopName);
            Map<String, String> resultMap = OBJECT_MAPPER.readValue(parseJson, new TypeReference<>() {
            });
            appInsights.trackTrace("解析后的数据： "  + requestShopName + " resultMap" + resultMap.toString());
            allTranslatedMap.putAll(resultMap);
            appInsights.trackTrace("存完数据 用户： " + shopName + " sourceJson: " + sourceJson);
        } catch (Exception e) {
            appInsights.trackException(e);
            appInsights.trackTrace("translateAllList 用户： " + shopName + " 翻译类型 : HTML 提示词 : " + prompt + " 未翻译文本 : " + texts);
        }
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
    public void cacheAndDbTranslateData(List<String> originalTexts, String target, String source, Map<String, String> allTranslatedMap, String shopName) {
        Iterator<String> it = originalTexts.iterator();
        while (it.hasNext()) {
            String sourceText = it.next();
            String cacheData = redisProcessService.getCacheData(target, sourceText);
            appInsights.trackTrace("redis翻译 用户： " + shopName + " cacheData " + cacheData + " sourceText " + sourceText);
            if (cacheData != null) {
                allTranslatedMap.put(sourceText, cacheData);
                it.remove();
                continue;
            }
        }
    }

    /**
     * 将翻译后的数据填回原处
     * */
    public void fillBackTranslatedData(List<TextNode> nodes, Map<String, String> translatedTexts, String target, String shopName) {
        for (TextNode node : nodes) {
            String text = node.getWholeText();
            if (!text.isEmpty()) {
                //记录空格，还需要填回
                // Step 1: 记录开头和结尾的空格数量
                // 匹配前导空格
                appInsights.trackTrace("匹配前导空格 用户： " + shopName);
                Matcher leadingMatcher = Pattern.compile("^(\\p{Zs}+)").matcher(text);
                String leading = leadingMatcher.find() ? leadingMatcher.group(1) : "";
                // 匹配尾随空格
                appInsights.trackTrace("匹配尾随空格 用户： " + shopName);
                Matcher trailingMatcher = Pattern.compile("(\\p{Zs}+)$").matcher(text);
                String trailing = trailingMatcher.find() ? trailingMatcher.group(1) : "";
                // 去掉前后空格，得到核心文本
                appInsights.trackTrace("去掉前后空格 用户： " + shopName);
                int begin = leading.length();
                int end = text.length() - trailing.length();
                String core = (begin >= end) ? "" : text.substring(begin, end);
                // 拼回原来的空格
                appInsights.trackTrace("拼回原来的空格 用户： " + shopName);
                String targetText = translatedTexts.get(core);
                if (core.isEmpty()) {
                    // 没有核心文本，只保留原始空格（避免被清空，也避免重复加）
                    targetText = text;
                    appInsights.trackTrace("fillBackTranslatedData targetText 没有被翻译，原文是: " + targetText);
                } else {
                    //添加到缓存里面
                    appInsights.trackTrace("添加到缓存前 用户： " + shopName);
                    redisProcessService.setCacheData(target, targetText, core);
                    if (targetText != null && !targetText.trim().isEmpty()) {
                        targetText = leading + targetText + trailing;
                    } else {
                        targetText = leading + core + trailing;
                    }
                }
                appInsights.trackTrace("添加到缓存后 用户： " + shopName);
                node.text(targetText);
            }
        }
    }
}
