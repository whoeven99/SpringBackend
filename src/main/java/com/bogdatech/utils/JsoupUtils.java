package com.bogdatech.utils;

import com.bogdatech.exception.ClientException;
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

import static com.bogdatech.logic.TranslateService.SINGLE_LINE_TEXT;
import static com.bogdatech.logic.TranslateService.addData;

@Component
public class JsoupUtils {

    @Autowired
    private TranslateApiIntegration translateApiIntegration;

//    private static final Pattern TEMPLATE_PATTERN = Pattern.compile("\\{\\{.*?\\}\\}");

    public String translateHtml(String html, TranslateRequest request, CharacterCountUtils counter, String target) {
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
            String translated = translateSingleLine(text, request.getTarget());
            counter.addChars(text.length());
            if (translated != null) {
//                System.out.println("用了缓存的数据html： " + translated);

                translatedTexts.add(translated);
            } else {
                request.setContent(text);
                //google翻译的接口
            String targetString = translateApiIntegration.googleTranslate(request);
//                String targetString = translateApiIntegration.microsoftTranslate(request);
                addData(target, text, targetString);
                translatedTexts.add(targetString);
            }
        }

//         Replace original texts with translated ones
        int index = 0;
        try {
            for (Element element : elements) {
                if (!element.is("script, style")) {
                    if (!element.ownText().trim().isEmpty()) {
                        System.out.println("html_error: " + translatedTexts.get(index));
                        element.text(translatedTexts.get(index++));
                    }
                    if (element.hasAttr("alt")) {
                        element.attr("alt", translatedTexts.get(index++));
                    }
                }
            }
        } catch (Exception e) {
            throw new ClientException("This text is not a string");
        }

        return doc.html();
    }

    //判断String类型是否是html数据
    public boolean isHtml(String content) {
        Document doc = Jsoup.parse(content);
        return !doc.body().text().equals(content);
    }

    public String translateSingleLine(String sourceText, String target) {
        if (SINGLE_LINE_TEXT.get(target) != null) {
            return SINGLE_LINE_TEXT.get(target).get(sourceText);
        }
        return null;
    }
}
