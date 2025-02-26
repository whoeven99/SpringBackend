package com.bogdatech.utils;

import com.bogdatech.entity.AILanguagePacksDO;
import com.bogdatech.exception.ClientException;
import com.bogdatech.integration.TranslateApiIntegration;
import com.bogdatech.model.controller.request.TranslateRequest;
import com.microsoft.applicationinsights.TelemetryClient;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

import static com.bogdatech.constants.TranslateConstants.TRANSLATION_EXCEPTION;
import static com.bogdatech.integration.ALiYunTranslateIntegration.singleTranslate;
import static com.bogdatech.logic.TranslateService.SINGLE_LINE_TEXT;
import static com.bogdatech.logic.TranslateService.addData;
import static com.bogdatech.utils.CalculateTokenUtils.calculateToken;
import static com.bogdatech.utils.CaseSensitiveUtils.extractKeywords;
import static com.bogdatech.utils.CaseSensitiveUtils.restoreKeywords;

@Component
@Slf4j
public class JsoupUtils {

    private final TranslateApiIntegration translateApiIntegration;
    TelemetryClient appInsights = new TelemetryClient();

    @Autowired
    public JsoupUtils(TranslateApiIntegration translateApiIntegration) {
        this.translateApiIntegration = translateApiIntegration;
    }

    public String translateHtml(String html, TranslateRequest request, CharacterCountUtils counter, AILanguagePacksDO aiLanguagePacksDO, String resourceType) {
        Document doc = Jsoup.parse(html);
//        System.out.println("html: " + html);
        String target = request.getTarget();
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
                String translated = translateSingleLine(text, target);

                String targetString;
                if (translated != null) {
//                    counter.addChars(calculateToken(text, 1));
                    translatedTexts.add(translated);
                } else {
                    request.setContent(text);

                    try {
                        if (text.length() > 32) {
                            //AI翻译
                            targetString = singleTranslate(text,resourceType, counter, target);
                        } else {
                            targetString = translateAndCount(request, counter, resourceType);
                        }
                    } catch (ClientException e) {
                        // 如果AI翻译失败，则使用谷歌翻译
                        translatedTexts.add(text);
                        if (e.getErrorMessage().equals(TRANSLATION_EXCEPTION)) {
                            //终止翻译，并返回状态4
                            throw new ClientException(TRANSLATION_EXCEPTION);
                        }
                        continue;
                    }
                    addData(target, text, targetString);
                    translatedTexts.add(targetString);
                }
            }

            // Translate alt text
            for (String altText : altsToTranslate) {
                String translated = translateSingleLine(altText, request.getTarget());
                String targetString;
                if (translated != null) {
                    translatedAlts.add(translated);
                } else {
                    request.setContent(altText);
                    //AI翻译
                    try {
                        if (altText.length() > 32) {
                            //AI翻译
                            targetString = singleTranslate(altText,resourceType, counter, target);
                        } else {
                            targetString = translateAndCount(request, counter, resourceType);
                        }
                    } catch (ClientException e) {
                        // 如果AI翻译失败，则使用谷歌翻译
                        translatedAlts.add(altText);
                        if (e.getErrorMessage().equals(TRANSLATION_EXCEPTION)) {
                            //终止翻译，并返回状态4
                            throw new ClientException(TRANSLATION_EXCEPTION);
                        }
                        continue;
                    }
                    addData(target, altText, targetString);
                    translatedAlts.add(targetString);
                }
            }

        } catch (ClientException e) {
            if (e.getErrorMessage().equals(TRANSLATION_EXCEPTION)) {
                //终止翻译，并返回状态4
                throw new ClientException(TRANSLATION_EXCEPTION);
            }
        } catch (Exception e) {
            appInsights.trackTrace("HTML" + e.getMessage());
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

//        System.out.println("OPENAI 翻译结果：" + doc.toString());
        return doc.html();
    }


    // 对文本进行翻译（词汇表）
    public Map<Element, List<String>> translateGlossaryTexts(Map<Element, List<String>> elementTextMap, TranslateRequest request,
                                                             CharacterCountUtils counter, Map<String, String> keyMap, Map<String, String> keyMap0, AILanguagePacksDO aiLanguagePacksDO) {
        Map<Element, List<String>> translatedTextMap = new HashMap<>();
        for (Map.Entry<Element, List<String>> entry : elementTextMap.entrySet()) {
            Element element = entry.getKey();
            List<String> texts = entry.getValue();
            List<String> translatedTexts = new ArrayList<>();
            for (String text : texts) {
                String translated = translateSingleLine(text, request.getTarget());
                if (translated != null) {
//                    counter.addChars(calculateToken(text, 1));
                    translatedTexts.add(translated);
                } else {
                    //目前没有翻译html的提示词，用的是谷歌翻译
                    counter.addChars(calculateToken(text, 1));
                    Map<String, String> placeholderMap = new HashMap<>();
                    String updateText = extractKeywords(text, placeholderMap, keyMap, keyMap0);
                    request.setContent(updateText);
                    String targetString = translateApiIntegration.getGoogleTranslationWithRetry(request);
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


    //对文本进行翻译（通用）
    public Map<Element, List<String>> translateTexts(Map<Element, List<String>> elementTextMap, TranslateRequest request,
                                                     CharacterCountUtils counter, AILanguagePacksDO aiLanguagePacksDO, String resourceType) {
        Map<Element, List<String>> translatedTextMap = new HashMap<>();
        for (Map.Entry<Element, List<String>> entry : elementTextMap.entrySet()) {
            Element element = entry.getKey();
            List<String> texts = entry.getValue();
            List<String> translatedTexts = new ArrayList<>();
            for (String text : texts) {
                String translated = translateSingleLine(text, request.getTarget());
                if (translated != null) {
                    translatedTexts.add(translated);
                } else {
                    request.setContent(text);
                    //TODO： 目前没有翻译html的提示词，用的是谷歌翻译
                    String targetString = translateAndCount(request, counter, resourceType);
                    translatedTexts.add(targetString);
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

    //调用google翻译前需要先判断 是否是google支持的语言 如果不支持改用AI翻译
    public List<String> googleTranslateJudgeCode(TranslateRequest request, CharacterCountUtils counter, String resourceType) {
        String target = request.getTarget();
        String source = request.getSource();
        List<String> result = new ArrayList<>();
        if (LANGUAGE_CODES.contains(target) || LANGUAGE_CODES.contains(source)) {
            String s = singleTranslate(request.getContent(),resourceType, counter, target);
            result.add(s);
            result.add("0");
            return result;
        }

        String s = translateApiIntegration.getGoogleTranslationWithRetry(request);

        result.add(s);
        result.add("1");
        return result;
    }

    //在调用googleTranslateJudgeCode的基础上添加计数功能,并添加到翻译后的文本
    public String translateAndCount(TranslateRequest request,
                                    CharacterCountUtils counter, String resourceType) {
        String text = request.getContent();
        List<String> strings = googleTranslateJudgeCode(request, counter, resourceType);
        String targetString = strings.get(0);
        String flag = strings.get(1);
        if ("0".equals(flag)) {
            return targetString;
        }
        counter.addChars(calculateToken(text, 1));
        addData(request.getTarget(), text, targetString);
        return targetString;
    }

    // 定义语言代码集合
    private static final Set<String> LANGUAGE_CODES = new HashSet<>(Arrays.asList(
            "ce", "kw", "fo", "ia", "kl", "ks", "ki", "lu", "gv", "nd",
            "se", "nb", "nn", "os", "rm", "sc", "ii", "bo", "to", "wo", "ar-EG"
    ));


}
