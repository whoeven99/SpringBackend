package com.bogdatech.utils;

import com.bogdatech.exception.ClientException;
import com.bogdatech.integration.TranslateApiIntegration;
import com.bogdatech.model.controller.request.TranslateRequest;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

import static com.bogdatech.logic.TranslateService.SINGLE_LINE_TEXT;
import static com.bogdatech.logic.TranslateService.addData;

@Component
@Slf4j
public class JsoupUtils {

    @Autowired
    private TranslateApiIntegration translateApiIntegration;


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
        try {
            for (String text : textsToTranslate) {
                String translated = translateSingleLine(text, request.getTarget());
                counter.addChars(text.length());
                if (translated != null) {
                    translatedTexts.add(translated);
                } else {
                    request.setContent(text);
                    //google翻译的接口
                    String targetString = translateApiIntegration.googleTranslate(request);
//                    String targetString = translateApiIntegration.microsoftTranslate(request);
                    addData(target, text, targetString);
                    translatedTexts.add(targetString);
                }
            }
        } catch (Exception e) {
            log.info(e.getMessage());
//            System.out.println(e.getMessage());
        }

//         Replace original texts with translated ones
        int index = 0;
        try {
            for (Element element : elements) {
                if (!element.is("script, style")) {
                    if (!element.ownText().trim().isEmpty()) {
//                        System.out.println("html_error: " + translatedTexts.get(index));
                        element.text(translatedTexts.get(index++));
                    }
                    if (element.hasAttr("alt")) {
                        element.attr("alt", translatedTexts.get(index++));
                    }
                }
            }
        } catch (Exception e) {
            throw new ClientException("This text is not a html element");
        }

        return doc.html();
    }

    // 提取需要翻译的文本（包括文本和alt属性）
    public Map<Element, String> extractTextsToTranslate(Document doc) {
        Map<Element, String> elementTextMap = new HashMap<>();

        for (Element element : doc.getAllElements()) {
            if (!element.is("script, style")) { // 忽略script和style标签
                String text = element.ownText().trim();
                if (!text.isEmpty()) {
                    elementTextMap.put(element, text); // 记录元素和对应的文本
                }
                if (element.hasAttr("alt")) {
                    String altText = element.attr("alt").trim();
                    if (!altText.isEmpty()) {
                        elementTextMap.put(element, altText); // 记录alt属性文本
                    }
                }
            }
        }
        return elementTextMap;
    }

    // 对文本进行翻译（词汇表）
    public List<String> translateTexts(List<String> textsToTranslate, TranslateRequest request, CharacterCountUtils counter) {
        List<String> translatedTexts = new ArrayList<>();

        try {
            for (String text : textsToTranslate) {
                String translated = translateSingleLine(text, request.getTarget());
                counter.addChars(text.length());
                if (translated != null) {
                    translatedTexts.add(translated);
                } else {
                    request.setContent(text);
                    // 使用翻译API进行翻译（如Google或Microsoft）
//                    String targetString = translateApiIntegration.googleTranslate(request);
                    String targetString = translateApiIntegration.microsoftTranslate(request);
                    System.out.println("targetString: " + targetString);
                    addData(request.getTarget(), text, targetString);
                    translatedTexts.add(targetString);
                }
            }
        } catch (Exception e) {
            log.info("Translation error: " + e.getMessage());
        }

        return translatedTexts;
    }

    // 替换原始文本为翻译后的文本
    public void replaceOriginalTextsWithTranslated(Document doc, Map<Element, String> elementTextMap, Iterator<String> translatedIterator) {
        try {
            for (Element element : doc.getAllElements()) {
                if (!element.is("script, style")) {
                    // 更新文本节点
                    String originalText = elementTextMap.get(element);
                    if (originalText != null && !originalText.isEmpty()) {
                        element.text(translatedIterator.next());
                    }
                    // 更新alt属性
                    if (element.hasAttr("alt")) {
                        element.attr("alt", translatedIterator.next());
                    }
                }
            }
        } catch (Exception e) {
            throw e;
        }
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
