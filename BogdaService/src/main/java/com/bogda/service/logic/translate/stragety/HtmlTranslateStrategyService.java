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

    private void translateLiquidHtmlForEmailTemplate(TranslateContext ctx) {
        String originalCode = ctx.getContent();
        originalCode = replaceHtmlLangValue(originalCode, ctx.getTargetLanguage());
        List<String> originalTexts = getLiquidTranslatableTexts(originalCode);

        int index = 0;
        for (String text : originalTexts) {
            ctx.getOriginalTextMap().put(index, text);
            index++;
        }

        batchTranslateStrategyService.translate(ctx);

        Map<Integer, String> translatedTextMap = ctx.getTranslatedTextMap();
        String replacedCode = originalCode;

        for (int i = 0; i < originalTexts.size(); i++) {
            String originalText = originalTexts.get(i);
            String translatedText = translatedTextMap.get(i);
            if (translatedText != null && !translatedText.equals(originalText)) {
                replacedCode = replacedCode.replace(originalText, translatedText);
            }
        }

        // AI 结果/JSON 反序列化链路可能会把真实换行转成字面量的 "\n"（两个字符：\ + n），
        // 这会破坏 Liquid 邮件模板原有排版；这里在最终输出前统一还原。
        replacedCode = restoreEscapedNewlines(replacedCode);
        TraceReporterHolder.report("debug", "replaceCode : " + replacedCode);
        ctx.setStrategy("EMAIL_TEMPLATE的Liquid HTML翻译");
        ctx.setTranslatedContent(isHtmlEntity(replacedCode));
        ctx.setDoc(null);
        ctx.getNodeMap().clear();
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

    private List<String> getLiquidTranslatableTexts(String code) {
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
}
