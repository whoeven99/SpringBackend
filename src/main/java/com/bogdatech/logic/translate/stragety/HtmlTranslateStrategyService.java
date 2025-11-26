package com.bogdatech.logic.translate.stragety;

import com.bogdatech.context.HtmlContext;
import com.bogdatech.entity.DO.GlossaryDO;
import com.bogdatech.integration.ALiYunTranslateIntegration;
import com.bogdatech.logic.GlossaryService;
import com.bogdatech.utils.JsonUtils;
import com.bogdatech.utils.PlaceholderUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import kotlin.Pair;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.bogdatech.utils.LiquidHtmlTranslatorUtils.*;

@Component
public class HtmlTranslateStrategyService implements ITranslateStrategyService<HtmlContext> {
    @Autowired
    private ALiYunTranslateIntegration aLiYunTranslateIntegration;

    @Override
    public String getType() {
        return "HTML";
    }

    @Override
    public void initAndSetPrompt(HtmlContext ctx) {
        String value = ctx.getContent();
        String target = ctx.getTargetLanguage();

        value = isHtmlEntity(value); //判断是否含有HTML实体,然后解码

        // 1, 解析html，根据html标签，选择不同的解析方式， 将prettyPrint设置为false
        boolean hasHtmlTag = HTML_TAG_PATTERN.matcher(value).find();
        Document doc = parseHtml(value, target, hasHtmlTag);
        ctx.setDoc(doc);
        ctx.setHasHtmlTag(hasHtmlTag);

        // 2. 收集所有 TextNode
        List<TextNode> nodes = new ArrayList<>();
        for (Element element : doc.getAllElements()) {
            nodes.addAll(element.textNodes());
        }

        // 3. 提取要翻译文本并生成映射
        int index = 0;
        for (TextNode node : nodes) {
            String text = node.text().trim();
            if (!text.isEmpty()) {
                ctx.getOriginalTextMap().put(index, text);
                ctx.getNodeMap().put(index, node);
                index++;
            }
        }

        ctx.setPrompt(PlaceholderUtils.getNewestPrompt(
                ctx.getTargetLanguage(), JsonUtils.objectToJson(uncachedMap)));
    }

    @Override
    public void replaceGlossary(HtmlContext ctx, Map<String, GlossaryDO> glossaryMap) {
        Map<Integer, String> idToSourceValueMap = ctx.getOriginalTextMap();
        // 替换glossary
        for (Map.Entry<Integer, String> entry : idToSourceValueMap.entrySet()) {
            Pair<String, Boolean> glossaryPair = GlossaryService.replaceWithGlossary(entry.getValue(), glossaryMap);
            if (glossaryPair.getSecond()) {
                entry.setValue(glossaryPair.getFirst());
                ctx.setHasGlossary(true);
            }
        }
    }

    @Override
    public void executeTranslate(HtmlContext ctx) {
        Pair<String, Integer> pair = aLiYunTranslateIntegration.userTranslate(ctx.getPrompt(), ctx.getTargetLanguage());
        if (pair == null) {
            // fatalException
            return;
        }
        String aiResponse = pair.getFirst();
        if (ctx.isHasGlossary()) {
            aiResponse = getGlossaryReplacedBack(aiResponse);
        }
        Map<Integer, String> translatedValueMap = JsonUtils.jsonToObjectWithNull(aiResponse, new TypeReference<Map<Integer, String>>() {
        });
        if (translatedValueMap == null || translatedValueMap.isEmpty()) {
            // fatalException
            return;
        }
        // 翻译后 - 设置缓存
//        for (Map.Entry<Integer, String> entry : translatedValueMap.entrySet()) {
//            setCache(target, entry.getValue(), idToSourceValueMap.get(entry.getKey()));
//        }

//        translatedValueMap.putAll(cachedMap);

        ctx.setUsedToken(pair.getSecond());
        fillBackTranslatedDataMap(ctx.getNodeMap(), translatedValueMap, ctx.getTargetLanguage(), ctx.getOriginalTextMap());
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
        ctx.setReplaceBackContent(replacedBackString);
    }

    @Override
    public String getTranslateValue(HtmlContext context) {
        return context.getReplaceBackContent();
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

            // 添加到缓存里面
//            redisProcessService.setCacheData(targetLang, translated, originalTextMap.get(key));

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

    private static String getGlossaryReplacedBack(String value) {
        return value.replaceAll("\\{\\[(.*?)]}", "$1");
    }
}
