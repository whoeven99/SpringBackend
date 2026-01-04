package com.bogda.common.utils;

import org.apache.commons.text.StringEscapeUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

public class LiquidHtmlTranslatorUtils {
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
}
