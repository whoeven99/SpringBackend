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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class JsoupUtils {

    @Autowired
    private TranslateApiIntegration translateApiIntegration;

    private static final Pattern TEMPLATE_PATTERN = Pattern.compile("\\{\\{.*?\\}\\}");
    public String translateHtml(String html, TranslateRequest request) {
//        Document doc = Jsoup.parse(html);
//        List<String> textsToTranslate = new ArrayList<>();
//
//        // Extract text to translate
//        Elements elements = doc.getAllElements();
//        for (Element element : elements) {
//            if (!element.is("script, style")) { // Ignore script and style tags
//                String text = element.ownText();
//                System.out.println("text1: " + text);
//                if (!text.trim().isEmpty()) {
//                    textsToTranslate.add(text);
//                }
//                if (element.hasAttr("alt")) {
//                    textsToTranslate.add(element.attr("alt"));
//                }
//            }
//        }
//
//        // Translate texts
//        List<String> translatedTexts = new ArrayList<>();
//        for (String text : textsToTranslate) {
//            System.out.println("text2: " + text);
//            request.setContent(text);
//            String translatedValue = translateApiIntegration.baiDuTranslate(request);
//            translatedTexts.add(translatedValue);
//        }
//
//        // Replace original texts with translated ones
//        int index = 0;
//        for (Element element : elements) {
//            if (!element.is("script, style")) {
//                if (!element.ownText().trim().isEmpty()) {
//                    element.text(translatedTexts.get(index++));
//                }
//                if (element.hasAttr("alt")) {
//                    element.attr("alt", translatedTexts.get(index++));
//                }
//            }
//        }
//
//        return doc.html();
//        private final Translate translate = TranslateOptions.getDefaultInstance().getService();



            Document doc = Jsoup.parse(html);
            List<String> textsToTranslate = new ArrayList<>();
            List<String> originalTexts = new ArrayList<>();

            // 提取需要翻译的文本
            extractTexts(doc.getAllElements(), textsToTranslate, originalTexts);

            // 翻译文本
            List<String> translatedTexts = translateTexts(textsToTranslate, request);

            // 替换原始文本为翻译后的文本
            replaceTexts(doc.getAllElements(), translatedTexts, originalTexts);

            return doc.html();
        }

        private void extractTexts(Elements elements, List<String> textsToTranslate, List<String> originalTexts) {
            for (Element element : elements) {
                if (!element.is("script, style")) { // 忽略<script>和<style>标签
                    processText(element.ownText(), textsToTranslate, originalTexts);
                    if (element.hasAttr("alt")) {
                        processText(element.attr("alt"), textsToTranslate, originalTexts);
                    }
                }
            }
        }

        private void processText(String text, List<String> textsToTranslate, List<String> originalTexts) {
            if (text.trim().isEmpty()) {
                return;
            }

            Matcher matcher = TEMPLATE_PATTERN.matcher(text);
            if (matcher.find()) {
                // 分割文本，提取非模板变量部分
                String[] parts = matcher.replaceAll("|").split("\\|");
                for (String part : parts) {
                    if (!part.trim().isEmpty()) {
                        if (TEMPLATE_PATTERN.matcher(part).find()) {
                            originalTexts.add(part);
                        } else {
                            textsToTranslate.add(part);
                            originalTexts.add(part);
                        }
                    }
                }
            } else {
                textsToTranslate.add(text);
                originalTexts.add(text);
            }
        }

        private List<String> translateTexts(List<String> textsToTranslate, TranslateRequest request) {
            List<String> translatedTexts = new ArrayList<>();
            for (String text : textsToTranslate) {
                request.setContent(text);
                String translatedValue = translateApiIntegration.baiDuTranslate(request);
                translatedTexts.add(translatedValue);
            }
            return translatedTexts;
        }

        private void replaceTexts(Elements elements, List<String> translatedTexts, List<String> originalTexts) {
            int index = 0;
            for (Element element : elements) {
                if (!element.is("script, style")) {
                    if (!element.ownText().trim().isEmpty()) {
                        element.text(reconstructText(element.ownText(), translatedTexts, originalTexts, index));
                        index += countParts(element.ownText());
                    }
                    if (element.hasAttr("alt")) {
                        element.attr("alt", reconstructText(element.attr("alt"), translatedTexts, originalTexts, index));
                        index += countParts(element.attr("alt"));
                    }
                }
            }
        }

        private String reconstructText(String originalText, List<String> translatedTexts, List<String> originalTexts, int startIndex) {
            Matcher matcher = TEMPLATE_PATTERN.matcher(originalText);
            if (matcher.find()) {
                StringBuilder translatedBuilder = new StringBuilder();
                String[] parts = matcher.replaceAll("|").split("\\|");
                for (String part : parts) {
                    if (!part.trim().isEmpty()) {
                        if (TEMPLATE_PATTERN.matcher(part).find()) {
                            translatedBuilder.append(part);
                        } else {
                            int partIndex = originalTexts.indexOf(part);
                            translatedBuilder.append(translatedTexts.get(partIndex - startIndex));
                        }
                    }
                }
                return translatedBuilder.toString();
            } else {
                return translatedTexts.get(startIndex);
            }
        }

        private int countParts(String text) {
            Matcher matcher = TEMPLATE_PATTERN.matcher(text);
            if (matcher.find()) {
                return matcher.replaceAll("|").split("\\|").length;
            } else {
                return 1;
            }
        }


//    private String
}
