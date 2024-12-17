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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.bogdatech.logic.TranslateService.SINGLE_LINE_TEXT;
import static com.bogdatech.logic.TranslateService.addData;
import static com.bogdatech.utils.CaseSensitiveUtils.*;

@Component
@Slf4j
public class JsoupUtils {

    @Autowired
    private TranslateApiIntegration translateApiIntegration;


    public String translateHtml(String html, TranslateRequest request, CharacterCountUtils counter, String target) {
        Document doc = Jsoup.parse(html);
        List<String> textsToTranslate = new ArrayList<>();
        List<String> altsToTranslate = new ArrayList<>();
        List<Element> elementsWithText = new ArrayList<>();
        List<Element> elementsWithAlt = new ArrayList<>();

        // Extract text and alt attributes to translate
        Elements elements = doc.getAllElements();
        for (Element element : elements) {
            if (!element.is("script, style")) { // Ignore script and style tags
                String text = element.ownText();
                if (!text.trim().isEmpty()) {
                    textsToTranslate.add(text);
                    elementsWithText.add(element);
                }
                if (element.hasAttr("alt")) {
                    String altText = element.attr("alt");
                    altsToTranslate.add(altText);
                    elementsWithAlt.add(element);
                }
            }
        }

        // Translate texts and alt attributes
        List<String> translatedTexts = new ArrayList<>();
        List<String> translatedAlts = new ArrayList<>();
        try {
            // Translate main text
            for (String text : textsToTranslate) {
                String translated = translateSingleLine(text, request.getTarget());
                counter.addChars(text.length());
                if (translated != null) {
                    translatedTexts.add(translated);
                } else {
                    request.setContent(text);
//                    String targetString = translateApiIntegration.googleTranslate(request);
                    String targetString = translateApiIntegration.microsoftTranslate(request);
                    addData(target, text, targetString);
                    translatedTexts.add(targetString);
                }
            }

            // Translate alt text
            for (String altText : altsToTranslate) {
                String translated = translateSingleLine(altText, request.getTarget());
                if (translated != null) {
                    translatedAlts.add(translated);
                } else {
                    request.setContent(altText);
                    String targetString = translateApiIntegration.googleTranslate(request);
//                String targetString = translateApiIntegration.microsoftTranslate(request);
                    addData(target, altText, targetString);
                    translatedAlts.add(targetString);
                }
            }

        } catch (Exception e) {
            log.info(e.getMessage());
        }

        // Replace original texts and alt attributes with translated ones
        try {
            for (int i = 0; i < elementsWithText.size(); i++) {
                Element element = elementsWithText.get(i);
                element.text(translatedTexts.get(i));
            }

            for (int i = 0; i < elementsWithAlt.size(); i++) {
                Element element = elementsWithAlt.get(i);
                element.attr("alt", translatedAlts.get(i));
            }
        } catch (Exception e) {
            throw new ClientException("This text is not a valid html element");
        }

        return doc.html();
    }




    // 对文本进行翻译（词汇表，区分大小写）
    public Map<Element, List<String>> translateTexts(Map<Element, List<String>> elementTextMap, TranslateRequest request, CharacterCountUtils counter, Map<String, String> keyMap, Map<String, String> keyMap0) {
        Map<Element, List<String>> translatedTextMap = new HashMap<>();

        for (Map.Entry<Element, List<String>> entry : elementTextMap.entrySet()) {
            Element element = entry.getKey();
            List<String> texts = entry.getValue();

            List<String> translatedTexts = new ArrayList<>();
            for (String text : texts) {
                String translated = translateSingleLine(text, request.getTarget());
                counter.addChars(text.length());
                if (translated != null) {
                    translatedTexts.add(translated);
                } else {
                    //占位符
                    Map<String, String> placeholderMap = new HashMap<>();
                    //区分大小写
                    String updateText = extractKeywords(text, placeholderMap, keyMap, keyMap0);
                    request.setContent(updateText);
                    // 使用翻译API进行翻译（如Google或Microsoft）
                    String targetString = translateApiIntegration.googleTranslate(request);
//                    String targetString = translateApiIntegration.microsoftTranslate(request);
                    String finalText = restoreKeywords(targetString, placeholderMap);
                    addData(request.getTarget(), text, finalText);
                    translatedTexts.add(finalText);
                }
            }

            translatedTextMap.put(element, translatedTexts); // 保存翻译后的文本和 alt 属性
        }

        return translatedTextMap;
    }

    // 提取需要翻译的文本（包括文本和alt属性）
    public Map<Element, List<String>> extractTextsToTranslate(Document doc) {
        Map<Element, List<String>> elementTextMap = new HashMap<>();
        for (Element element : doc.getAllElements()) {
            if (!element.is("script, style")) { // 忽略script和style标签
                List<String> texts = new ArrayList<>();

                // 提取文本
                String text = element.ownText().trim();
                if (!text.isEmpty()) {
                    texts.add(text);
                }

                // 提取 alt 属性
                if (element.hasAttr("alt")) {
                    String altText = element.attr("alt").trim();
                    if (!altText.isEmpty()) {
                        texts.add(altText);
                    }
                }

                if (!texts.isEmpty()) {
                    elementTextMap.put(element, texts); // 记录元素和对应的文本及 alt
                }
            }
        }
        return elementTextMap;
    }

    // 替换原始文本为翻译后的文本
    public void replaceOriginalTextsWithTranslated(Document doc, Map<Element, List<String>> translatedTextMap) {
        try {
            for (Map.Entry<Element, List<String>> entry : translatedTextMap.entrySet()) {
                Element element = entry.getKey();
                List<String> translatedTexts = entry.getValue();
                // 替换文本内容
                if (!translatedTexts.isEmpty()) {
                    element.text(translatedTexts.get(0)); // 第一个是文本
                }
                // 替换 alt 属性（如果有）
                if (translatedTexts.size() > 1 && element.hasAttr("alt")) {
                    element.attr("alt", translatedTexts.get(1)); // 第二个是 alt 属性
                }
            }
        } catch (Exception e) {
            throw new ClientException("This text is not a valid HTML element");
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
