package com.bogdatech.utils;

import org.apache.commons.text.StringEscapeUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.TextNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class HtmlUtils {
    public static final Pattern HTML_TAG_PATTERN = Pattern.compile("<\\s*html\\s*", Pattern.CASE_INSENSITIVE);

    public static List<String> parseHtml(String value, String target) {
        // &lt;div&gt;Hello &amp; Welcome&lt;/div&gt;
        // <div>Hello & Welcome</div>
        int i = 0;
        while (!value.equals(StringEscapeUtils.unescapeHtml4(value))) {
            // 如果有 HTML 实体，则解码
            value = StringEscapeUtils.unescapeHtml4(value);
            i++;
            if (i > 3) {
                break;
            }
        }

        Document doc;
        if (HTML_TAG_PATTERN.matcher(value).find()) {
            doc = Jsoup.parse(value);
            // 获取 <html> 元素并修改 lang 属性
            Element htmlTag = doc.selectFirst("html");
            if (htmlTag != null) {
                htmlTag.attr("lang", target);
            }
        } else {
            doc = Jsoup.parseBodyFragment(value);
        }
        doc.outputSettings().prettyPrint(false);

        List<String> originalTexts = new ArrayList<>();
        for (Element element : doc.getAllElements()) {
            for (TextNode textNode : element.textNodes()) {
                String text = textNode.text().trim();
                if (!text.isEmpty() && !originalTexts.contains(text)) {
                    originalTexts.add(text);
                }
            }
        }
        return originalTexts;
    }

    public static String replaceBack(String value, List<String> originalTexts,
                                     Map<Integer, String> translatedValueMap) {
        int i = 0;
        while (!value.equals(StringEscapeUtils.unescapeHtml4(value))) {
            // 如果有 HTML 实体，则解码
            value = StringEscapeUtils.unescapeHtml4(value);
            i++;
            if (i > 3) {
                break;
            }
        }

        for (int index = 0; index < originalTexts.size(); index++) {
            String originalText = originalTexts.get(index);
            String translatedText = translatedValueMap.get(index);
            if (translatedText != null) {
                value = value.replace(originalText, translatedText);
            }
        }
        return value;
    }
}
