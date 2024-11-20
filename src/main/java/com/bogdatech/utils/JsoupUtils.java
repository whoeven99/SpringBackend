package com.bogdatech.utils;

import com.bogdatech.integration.TranslateApiIntegration;
import com.bogdatech.model.controller.request.TranslateRequest;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class JsoupUtils {

    @Autowired
    private TranslateApiIntegration translateApiIntegration;

//    private static final Pattern TEMPLATE_PATTERN = Pattern.compile("\\{\\{.*?\\}\\}");

    public String translateHtml(String html, TranslateRequest request, CharacterCountUtils counter) {
        Document doc = Jsoup.parse(html);
        List<String> textsToTranslate = new ArrayList<>();

        // Extract text to translate
        Elements elements = doc.getAllElements();
        for (Element element : elements) {
            if (!element.is("script, style")) { // Ignore script and style tags
                String text = element.ownText();
                if (!text.trim().isEmpty()) {
                    textsToTranslate.add(text);
                }
                if (element.hasAttr("alt")) {
                    textsToTranslate.add(element.attr("alt"));
                }
            }
        }

        // Translate texts
        List<String> translatedTexts = new ArrayList<>();
        for (String text : textsToTranslate) {
            request.setContent(text);
            counter.addChars(text.length());
            //google翻译的接口
//            String translatedValue = translateApiIntegration.googleTranslate(request);
//            String translatedValue = translateApiIntegration.baiDuTranslate(request);
//            translatedTexts.add(translatedValue);
        }

        // Replace original texts with translated ones
        int index = 0;
        for (Element element : elements) {
            if (!element.is("script, style")) {
                if (!element.ownText().trim().isEmpty()) {
                    element.text(translatedTexts.get(index++));
                }
                if (element.hasAttr("alt")) {
                    element.attr("alt", translatedTexts.get(index++));
                }
            }
        }

        return doc.html();
    }

    //判断String类型是否是html数据
    public boolean isHtml(String content) {
        Document doc = Jsoup.parse(content);
        return !doc.body().text().equals(content);
    }
}
