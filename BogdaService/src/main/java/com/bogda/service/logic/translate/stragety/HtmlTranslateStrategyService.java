package com.bogda.service.logic.translate.stragety;

import com.bogda.common.TranslateContext;
import com.bogda.common.contants.TranslateConstants;
import com.bogda.common.reporter.TraceReporterHolder;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.bogda.common.utils.LiquidHtmlTranslatorUtils.*;

@Component
public class HtmlTranslateStrategyService implements ITranslateStrategyService {
    // 判断是否有 <html> 标签的模式
    public static final Pattern HTML_TAG_PATTERN = Pattern.compile("<\\s*html\\s*", Pattern.CASE_INSENSITIVE);
    // 匹配 <html ... lang="ko"> / <html lang=ko> 等写法
    private static final Pattern HTML_LANG_ATTRIBUTE_PATTERN =
            Pattern.compile("(?i)(<\\s*html\\b[^>]*?\\blang\\s*=\\s*)(\"[^\"]*\"|'[^']*'|[^\\s>]+)");
    private static final Pattern LIQUID_CAPTURE_PATTERN =
            // 保留任意 capture 块内文本（可见文案往往在 endcapture 之间）
            // 例如：{% capture some_var %}Hello{% endcapture %}
            Pattern.compile("(?s)\\{%\\s*capture\\s+\\w+\\s*%\\}(.*?)\\{%\\s*endcapture\\s*%\\}");
    private static final Pattern LIQUID_TAG_PATTERN = Pattern.compile("(?s)\\{%.*?%\\}");
    private static final Pattern LIQUID_VARIABLE_PATTERN = Pattern.compile("(?s)\\{\\{.*?\\}\\}");
    private static final Pattern STYLE_TAG_PATTERN = Pattern.compile("(?si)<style[^>]*>.*?</style>");
    private static final Pattern SCRIPT_TAG_PATTERN = Pattern.compile("(?si)<script[^>]*>.*?</script>");
    private static final Pattern HTML_ELEMENT_TAG_PATTERN = Pattern.compile("<[^>]+>");
    private static final Pattern LETTER_PATTERN = Pattern.compile("\\p{L}");
    private static final Pattern TEXT_SEGMENT_PATTERN = Pattern.compile("\\S+(?:[ \\t]\\S+)*");

    @Autowired
    private BatchTranslateStrategyService batchTranslateStrategyService;

    @Override
    public String getType() {
        return "HTML";
    }

    @Override
    public void translate(TranslateContext ctx) {
        String value = ctx.getContent();
        String target = ctx.getTargetLanguage();

        value = isHtmlEntity(value); //判断是否含有HTML实体,然后解码
        ctx.setContent(value);

        if (TranslateConstants.EMAIL_TEMPLATE.equals(ctx.getModule())) {
            translateLiquidHtmlForEmailTemplate(ctx);
            return;
        }

        boolean hasHtmlTag = HTML_TAG_PATTERN.matcher(value).find();
        Document doc = parseHtml(value, target, hasHtmlTag);
        ctx.setDoc(doc);
        ctx.setHasHtmlTag(hasHtmlTag);

        List<TextNode> nodes = new ArrayList<>();
        for (Element element : doc.getAllElements()) {
            nodes.addAll(element.textNodes());
        }

        // 生成json
        int index = 0;
        for (TextNode node : nodes) {
            String text = node.text().trim();
            if (!text.isEmpty()) {
                ctx.getOriginalTextMap().put(index, text);
                ctx.getNodeMap().put(index, node);
                index++;
            }
        }

        // 翻译 originalTextMap
        batchTranslateStrategyService.translate(ctx);

        Map<Integer, String> translatedTextMap = ctx.getTranslatedTextMap();

        fillBackTranslatedDataMap(ctx.getNodeMap(), translatedTextMap, ctx.getTargetLanguage(), ctx.getOriginalTextMap());
        String replacedBackString;
        // 输出翻译后的 HTML
        if (ctx.isHasHtmlTag()) {
            String results = ctx.getDoc().outerHtml(); // 返回完整的HTML结构
            replacedBackString = isHtmlEntity(results);
        } else {
            Element body = ctx.getDoc().body();
            // 只返回子节点内容，不包含 <body>
            StringBuilder results = new StringBuilder();
            for (Node child : body.childNodes()) {
                if (child != null) {
                    String childHtml = child.outerHtml(); // 或 child.toString()
                    results.append(childHtml);
                }
            }
            String output2 = results.toString();
            replacedBackString = isHtmlEntity(output2);
        }
        ctx.setStrategy("HTML的json翻译");
        ctx.setTranslatedContent(replacedBackString);
        ctx.setDoc(null);
        ctx.getNodeMap().clear();
    }

    public void translateLiquidHtmlForEmailTemplate(TranslateContext ctx) {
        String originalCode = ctx.getContent();
        originalCode = replaceHtmlLangValue(originalCode, ctx.getTargetLanguage());

        // 1. 计算受保护区域并抽取带位置信息的 segment
        List<int[]> protectedRegions = computeProtectedRegions(originalCode);
        List<Segment> segments = extractSegments(originalCode, protectedRegions);

        if (segments.isEmpty()) {
            ctx.setStrategy("EMAIL_TEMPLATE的Liquid HTML翻译");
            ctx.setTranslatedContent(isHtmlEntity(originalCode));
            ctx.setDoc(null);
            ctx.getNodeMap().clear();
            return;
        }

        // 2. 去重构建翻译请求（相同文本只翻译一次，减少 API 调用）
        LinkedHashMap<String, Integer> uniqueTextToIndex = new LinkedHashMap<>();
        int index = 0;
        for (Segment seg : segments) {
            if (!uniqueTextToIndex.containsKey(seg.sourceForModel)) {
                uniqueTextToIndex.put(seg.sourceForModel, index);
                ctx.getOriginalTextMap().put(index, seg.sourceForModel);
                index++;
            }
        }

        // 3. 批量翻译
        batchTranslateStrategyService.translate(ctx);
        Map<Integer, String> translatedTextMap = ctx.getTranslatedTextMap();

        // 4. 构建 sourceForModel -> translated 映射
        Map<String, String> translationMap = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : uniqueTextToIndex.entrySet()) {
            String translated = translatedTextMap.get(entry.getValue());
            translationMap.put(entry.getKey(), translated != null ? translated : entry.getKey());
        }

        // 5. 按位置倒序替换，确保前面的坐标不受后面替换长度变化的影响
        segments.sort((a, b) -> Integer.compare(b.start, a.start));
        StringBuilder sb = new StringBuilder(originalCode);
        for (Segment seg : segments) {
            String translated = translationMap.get(seg.sourceForModel);
            if (translated != null && !translated.equals(seg.sourceForModel)) {
                sb.replace(seg.start, seg.end, translated);
            }
        }

        String replacedCode = sb.toString();
        replacedCode = restoreEscapedNewlines(replacedCode);
        TraceReporterHolder.report("debug", "replaceCode : " + replacedCode);
        ctx.setStrategy("EMAIL_TEMPLATE的Liquid HTML翻译");
        ctx.setTranslatedContent(isHtmlEntity(replacedCode));
        ctx.setDoc(null);
        ctx.getNodeMap().clear();
    }

    /**
     * 计算原始 HTML 中所有"受保护区域"的位置范围。
     * 受保护区域包括：style 块、script 块、Liquid 标签 / 变量、HTML 元素标签（含属性）。
     * 这些区域中的文本即使与待翻译原文匹配，也不应被替换。
     */
    private List<int[]> computeProtectedRegions(String code) {
        List<int[]> regions = new ArrayList<>();
        addMatchRegions(regions, STYLE_TAG_PATTERN, code);
        addMatchRegions(regions, SCRIPT_TAG_PATTERN, code);
        addMatchRegions(regions, LIQUID_TAG_PATTERN, code);
        addMatchRegions(regions, LIQUID_VARIABLE_PATTERN, code);
        addMatchRegions(regions, HTML_ELEMENT_TAG_PATTERN, code);
        regions.sort((a, b) -> Integer.compare(a[0], b[0]));
        return regions;
    }

    private void addMatchRegions(List<int[]> regions, Pattern pattern, String code) {
        Matcher matcher = pattern.matcher(code);
        while (matcher.find()) {
            regions.add(new int[]{matcher.start(), matcher.end()});
        }
    }

    /**
     * 在非受保护区域中扫描可翻译文本，为每段文本记录其在 originalCode 中的精确位置。
     * 回填时直接按 [start, end) 替换，彻底避免 indexOf 二次搜索导致的错位/误替换。
     */
    private List<Segment> extractSegments(String code, List<int[]> protectedRegions) {
        List<int[]> merged = mergeOverlappingRegions(protectedRegions);
        List<int[]> gaps = computeGaps(code.length(), merged);
        List<Segment> segments = new ArrayList<>();
        for (int[] gap : gaps) {
            String gapText = code.substring(gap[0], gap[1]);
            Matcher m = TEXT_SEGMENT_PATTERN.matcher(gapText);
            while (m.find()) {
                String raw = m.group();
                String decoded = decodeHtmlEntities(raw);
                if (LETTER_PATTERN.matcher(decoded).find()) {
                    Segment seg = new Segment();
                    seg.start = gap[0] + m.start();
                    seg.end = gap[0] + m.end();
                    seg.sourceForModel = decoded;
                    segments.add(seg);
                }
            }
        }
        return segments;
    }

    /**
     * 合并已按 start 排序的区间列表中的重叠/相邻区间。
     */
    private List<int[]> mergeOverlappingRegions(List<int[]> sortedRegions) {
        if (sortedRegions.isEmpty()) {
            return new ArrayList<>();
        }
        List<int[]> merged = new ArrayList<>();
        int[] current = {sortedRegions.get(0)[0], sortedRegions.get(0)[1]};
        for (int i = 1; i < sortedRegions.size(); i++) {
            int[] next = sortedRegions.get(i);
            if (next[0] <= current[1]) {
                current[1] = Math.max(current[1], next[1]);
            } else {
                merged.add(current);
                current = new int[]{next[0], next[1]};
            }
        }
        merged.add(current);
        return merged;
    }

    /**
     * 根据合并后的受保护区间，计算出所有"间隙"（非受保护区域）的位置范围。
     */
    private List<int[]> computeGaps(int length, List<int[]> mergedRegions) {
        List<int[]> gaps = new ArrayList<>();
        int cursor = 0;
        for (int[] region : mergedRegions) {
            if (cursor < region[0]) {
                gaps.add(new int[]{cursor, region[0]});
            }
            cursor = Math.max(cursor, region[1]);
        }
        if (cursor < length) {
            gaps.add(new int[]{cursor, length});
        }
        return gaps;
    }

    private String decodeHtmlEntities(String text) {
        return text
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'");
    }

    /**
     * 将字面量 "\n" / "\r\n" / "\r" 还原为真实换行符。
     * <p>
     * 注意：这里只处理反斜杠转义的换行序列，避免影响原本就存在的真实换行。
     */
    private String restoreEscapedNewlines(String code) {
        if (code == null) {
            return null;
        }

        // 先处理最完整的组合，避免被后续 replace 吃掉。
        String restored = code;
        restored = restored.replace("\\r\\n", "\n");
        restored = restored.replace("\\n", "\n");
        restored = restored.replace("\\r", "\n");
        return restored;
    }

    private String replaceHtmlLangValue(String originalCode, String targetLanguage) {
        if (originalCode == null || targetLanguage == null || targetLanguage.isEmpty()) {
            return originalCode;
        }

        Matcher matcher = HTML_LANG_ATTRIBUTE_PATTERN.matcher(originalCode);
        if (!matcher.find()) {
            return originalCode;
        }

        StringBuilder stringBuilder = new StringBuilder();
        matcher.reset();
        while (matcher.find()) {
            String prefix = matcher.group(1);
            String langValue = matcher.group(2);
            String replacementLangValue;
            if ((langValue.startsWith("\"") && langValue.endsWith("\""))
                    || (langValue.startsWith("'") && langValue.endsWith("'"))) {
                String quote = langValue.substring(0, 1);
                replacementLangValue = quote + targetLanguage + quote;
            } else {
                replacementLangValue = targetLanguage;
            }

            String replacement = prefix + replacementLangValue;
            matcher.appendReplacement(stringBuilder, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(stringBuilder);
        return stringBuilder.toString();
    }

    public List<String> getLiquidTranslatableTexts(String code) {
        String tempCode = code;
        // 只去掉 capture/endcapture 包装，保留块内文本进入后续抽取逻辑
        tempCode = LIQUID_CAPTURE_PATTERN.matcher(tempCode).replaceAll("$1");
        tempCode = LIQUID_TAG_PATTERN.matcher(tempCode).replaceAll(" ");
        tempCode = LIQUID_VARIABLE_PATTERN.matcher(tempCode).replaceAll(" ");
        tempCode = STYLE_TAG_PATTERN.matcher(tempCode).replaceAll(" ");
        tempCode = SCRIPT_TAG_PATTERN.matcher(tempCode).replaceAll(" ");
        tempCode = HTML_ELEMENT_TAG_PATTERN.matcher(tempCode).replaceAll(" ");

        tempCode = tempCode.replace("&nbsp;", " ");
        tempCode = tempCode.replace("&amp;", "&");
        tempCode = tempCode.replace("&lt;", "<");
        tempCode = tempCode.replace("&gt;", ">");
        tempCode = tempCode.replace("&quot;", "\"");
        tempCode = tempCode.replace("&#39;", "'");

        String[] rawTexts = tempCode.split("\\s{2,}|\\n");
        List<String> validTexts = new ArrayList<>();
        for (String text : rawTexts) {
            String trimmedText = text.trim();
            if (!trimmedText.isEmpty() && LETTER_PATTERN.matcher(trimmedText).find()) {
                validTexts.add(trimmedText);
            }
        }
        return validTexts;
    }

    public void finishAndGetJsonRecord(TranslateContext ctx) {
        ctx.finish();
        Map<String, String> variable = new HashMap<>();
        variable.put("strategy", ctx.getStrategy());
        variable.put("usedToken", String.valueOf(ctx.getUsedToken()));
        variable.put("translatedTime", String.valueOf(ctx.getTranslatedTime()));
        variable.put("cachedCount", String.valueOf(ctx.getCachedCount()));
        variable.put("glossaryCount", String.valueOf(ctx.getGlossaryCount()));
        variable.put("translatedChars", String.valueOf(ctx.getTranslatedChars()));
        ctx.setTranslateVariables(variable);
    }

    private void fillBackTranslatedDataMap(Map<Integer, TextNode> nodeMap,
                                           Map<Integer, String> translatedMap,
                                           String targetLang,
                                           Map<Integer, String> originalTextMap) {

        Pattern leadingPattern = Pattern.compile("^(\\p{Zs}+)");
        Pattern trailingPattern = Pattern.compile("(\\p{Zs}+)$");

        for (Map.Entry<Integer, TextNode> entry : nodeMap.entrySet()) {
            Integer key = entry.getKey();
            TextNode node = entry.getValue();
            String text = node.getWholeText();

            if (text.isEmpty()) {
                continue;
            }

            // 提取前导空格
            Matcher leadingMatcher = leadingPattern.matcher(text);
            String leading = leadingMatcher.find() ? leadingMatcher.group(1) : "";

            // 提取尾随空格
            Matcher trailingMatcher = trailingPattern.matcher(text);
            String trailing = trailingMatcher.find() ? trailingMatcher.group(1) : "";

            // 去掉空格，得到核心文本
            int begin = leading.length();
            int end = text.length() - trailing.length();
            String core = (begin >= end) ? "" : text.substring(begin, end);

            // 查找翻译
            String translated = translatedMap.get(key);

            // 拼回空格
            if (translated != null && !translated.equals(core)) {
                translated = leading + translated + trailing;
                node.text(translated);

            } else {
                // 没翻译或无变化，保留原样
                node.text(text);
            }
        }
    }

    /**
     * 表示 originalCode 中一段可翻译文本的精确位置。
     * 回填时通过 [start, end) 直接定位，不再依赖 indexOf。
     */
    private static class Segment {
        int start;
        int end;
        String sourceForModel;
    }
}
