package com.bogdatech.logic.translate;

import com.bogdatech.model.controller.request.TranslateRequest;
import com.bogdatech.utils.CharacterCountUtils;
import com.bogdatech.utils.JsoupUtils;
import com.bogdatech.utils.LiquidHtmlTranslatorUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.bogdatech.integration.ALiYunTranslateIntegration.calculateBaiLianToken;
import static com.bogdatech.logic.RabbitMqTranslateService.BATCH_SIZE;
import static com.bogdatech.logic.TranslateService.OBJECT_MAPPER;
import static com.bogdatech.utils.ApiCodeUtils.getLanguageName;
import static com.bogdatech.utils.CaseSensitiveUtils.appInsights;
import static com.bogdatech.utils.JsonUtils.objectToJson;
import static com.bogdatech.utils.PlaceholderUtils.getListPrompt;
import static com.bogdatech.utils.StringUtils.parseJson;

public class HtmlTranslateLogic {

    public String newJsonTranslateHtml(String html, TranslateRequest request, CharacterCountUtils counter,
                                       String languagePackId, Integer limitChars, boolean isSingleFlag, String translationModel) {
        if (!JsoupUtils.isHtml(html)) {
            return null;
        }

        html = LiquidHtmlTranslatorUtils.isHtmlEntity(html); //判断是否含有HTML实体,然后解码
        boolean hasHtmlTag = LiquidHtmlTranslatorUtils.HTML_TAG_PATTERN.matcher(html).find();

        Document doc = LiquidHtmlTranslatorUtils.parseHtml(html, request.getTarget(), hasHtmlTag);
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
        Map<String, String> translatedTexts = translateAllList(originalTexts, request, counter, languagePackId, limitChars, isSingleFlag, translationModel);

        // 5. 填回原处
        fillBackTranslatedData(nodes, translatedTexts, request.getTarget(), request.getShopName());

        // 输出翻译后的 HTML
        if (hasHtmlTag) {
            String results = doc.outerHtml(); // 返回完整的HTML结构
            results = LiquidHtmlTranslatorUtils.isHtmlEntity(results);
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
            output2 = LiquidHtmlTranslatorUtils.isHtmlEntity(output2);
            return output2;
        }
    }
}
