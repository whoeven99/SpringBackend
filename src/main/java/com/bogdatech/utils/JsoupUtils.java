package com.bogdatech.utils;

import com.bogdatech.exception.ClientException;
import com.bogdatech.model.controller.request.TranslateRequest;
import com.microsoft.applicationinsights.TelemetryClient;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.bogdatech.constants.TranslateConstants.QWEN_MT;
import static com.bogdatech.integration.ALiYunTranslateIntegration.callWithMessage;
import static com.bogdatech.integration.ALiYunTranslateIntegration.singleTranslate;
import static com.bogdatech.integration.TranslateApiIntegration.getGoogleTranslationWithRetry;
import static com.bogdatech.logic.TranslateService.SINGLE_LINE_TEXT;
import static com.bogdatech.logic.TranslateService.addData;
import static com.bogdatech.utils.ApiCodeUtils.qwenMtCode;
import static com.bogdatech.utils.CalculateTokenUtils.googleCalculateToken;
import static com.bogdatech.utils.CaseSensitiveUtils.extractKeywords;
import static com.bogdatech.utils.CaseSensitiveUtils.restoreKeywords;
import static com.bogdatech.utils.LiquidHtmlTranslatorUtils.*;
import static com.bogdatech.utils.PlaceholderUtils.hasPlaceholders;
import static com.bogdatech.utils.PlaceholderUtils.processTextWithPlaceholders;
import static java.lang.Thread.sleep;

@Component
public class JsoupUtils {


    static TelemetryClient appInsights = new TelemetryClient();

    // 对文本进行翻译（词汇表）
    public Map<Element, List<String>> translateGlossaryTexts(Map<Element, List<String>> elementTextMap, TranslateRequest request,
                                                             CharacterCountUtils counter, Map<String, String> keyMap, Map<String, String> keyMap0, String resourceType) {
        Map<Element, List<String>> translatedTextMap = new HashMap<>();
        for (Map.Entry<Element, List<String>> entry : elementTextMap.entrySet()) {
            Element element = entry.getKey();
            List<String> texts = entry.getValue();
            List<String> translatedTexts = new ArrayList<>();
            for (String text : texts) {
                String translated = translateSingleLineWithProtection(text, request, counter, keyMap, keyMap0, resourceType);
                translatedTexts.add(translated);
            }
            translatedTextMap.put(element, translatedTexts); // 保存翻译后的文本和 alt 属性
        }
        return translatedTextMap;
    }

    /**
     * 翻译单行文本，保护变量、URL和符号
     */
    private String translateSingleLineWithProtection(String text, TranslateRequest request, CharacterCountUtils counter,
                                                     Map<String, String> keyMap, Map<String, String> keyMap0, String resourceType) {
        // 检查缓存
        String translatedCache = translateSingleLine(text, request.getTarget());
        if (translatedCache != null) {
            return translatedCache;
        }

        // 处理文本，保护不翻译的部分
        String translatedText = processTextWithProtection(text, (cleanedText) -> {
            String translated = translateSingleLine(cleanedText, request.getTarget());
            if (translated != null) {
                return translated;
            }

            // 使用谷歌翻译
            counter.addChars(googleCalculateToken(cleanedText));
            Map<String, String> placeholderMap = new HashMap<>();
            String updateText = extractKeywords(cleanedText, placeholderMap, keyMap, keyMap0);
            request.setContent(updateText);
            String targetString = translateAndCount(request,counter, resourceType);
            String finalText = restoreKeywords(targetString, placeholderMap);
            addData(request.getTarget(), cleanedText, finalText);
            return finalText;
        });

        addData(request.getTarget(), text, translatedText);
        return translatedText;
    }

    /**
     * 处理文本，保护不翻译的变量、URL和符号
     */
    private String processTextWithProtection(String text, Function<String, String> translator) {
        StringBuilder result = new StringBuilder();
        int lastEnd = 0;

        List<Pattern> patterns = Arrays.asList(
                URL_PATTERN,
                VARIABLE_PATTERN,
                CUSTOM_VAR_PATTERN,
                LIQUID_CONDITION_PATTERN,
                ARRAY_VAR_PATTERN
        );

        List<MatchRange> matches = new ArrayList<>();
        for (Pattern pattern : patterns) {
            Matcher matcher = pattern.matcher(text);
            while (matcher.find()) {
                matches.add(new MatchRange(matcher.start(), matcher.end(), matcher.group()));
            }
        }

        matches.sort(Comparator.comparingInt(m -> m.start));

        for (MatchRange match : matches) {
            if (match.start > lastEnd) {
                String toTranslate = text.substring(lastEnd, match.start);
                String cleanedText = cleanTextFormat(toTranslate);
                if (!cleanedText.isEmpty()) {
                    if (SYMBOL_PATTERN.matcher(cleanedText).matches()) {
                        result.append(cleanedText); // 纯符号不翻译
                    } else {
                        result.append(translator.apply(cleanedText)); // 普通文本翻译
                    }
                }
            }
            result.append(match.content); // 保留变量或URL
            lastEnd = match.end;
        }

        if (lastEnd < text.length()) {
            String remaining = text.substring(lastEnd);
            String cleanedText = cleanTextFormat(remaining);
            if (!cleanedText.isEmpty()) {
                if (SYMBOL_PATTERN.matcher(cleanedText).matches()) {
                    result.append(cleanedText);
                } else {
                    result.append(translator.apply(cleanedText));
                }
            }
        }

        return result.toString();
    }

    // 提取需要翻译的文本（包括文本和alt属性）
    public Map<Element, List<String>> extractTextsToTranslate(Document doc) {
        Map<Element, List<String>> elementTextMap = new HashMap<>();
        for (Element element : doc.getAllElements()) {
            if (!noTranslateTags.contains(element.tagName().toLowerCase())) { // 忽略script和style标签
                List<String> texts = new ArrayList<>();

                // 提取文本
                String text = element.ownText().trim();
                if (!text.isEmpty()) {
                    texts.add(text);
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
            }

        } catch (Exception e) {
//            System.out.println("This text is not a valid HTML element: " + translatedTextMap.values());
            throw new ClientException("This text is not a valid HTML element");
        }
    }

    //判断String类型是否是html数据
    public static boolean isHtml(String content) {
        Document doc = Jsoup.parse(content);
        return !doc.body().text().equals(content);
    }

    public static String translateSingleLine(String sourceText, String target) {
        if (SINGLE_LINE_TEXT.get(target) != null) {
            return SINGLE_LINE_TEXT.get(target).get(sourceText);
        }
        return null;
    }

    /**
     * 调用google翻译前需要先判断 是否是google支持的语言 如果不支持改用AI翻译
     * 根据语言代码切换API翻译
     *
     * @param request      翻译所需要的数据
     * @param counter      计数器
     * @param resourceType 模块类型
     *                     return String 翻译后的文本
     */
    public static String googleTranslateJudgeCode(TranslateRequest request, CharacterCountUtils counter, String resourceType) {
        String target = request.getTarget();
        String source = request.getSource();

        if (LANGUAGE_CODES.contains(target) || LANGUAGE_CODES.contains(source)) {
            return singleTranslate(request.getContent(), resourceType, counter, target);
        }

        //如果source和target都是QwenMT支持的语言，则调用QwenMT的API。 反之亦然
        return checkTranslationApi(request, counter, resourceType);
    }

    /**
     * 如果source和target都是QwenMT支持的语言，则调用QwenMT的API。 反之亦然
     * 在翻译的同时计数字符数
     *
     * @param request 翻译所需要的数据
     * @param counter 计数器
     *                return String 翻译后的文本
     */
    public static String checkTranslationApi(TranslateRequest request, CharacterCountUtils counter, String resourceType) {
        String target = request.getTarget();
        String source = request.getSource();
        //如果source和target都是QwenMT支持的语言，则调用QwenMT的API。 反之亦然
        if (QWEN_MT_CODES.contains(target) && QWEN_MT_CODES.contains(source)) {
            //TODO：目前做个初步的限制，每次用mt翻译前都sleep一下，防止调用频率过高。0.3s. 后面请求解决限制后，删掉这段代码。
            try {
                sleep(300);
            } catch (Exception e) {
                appInsights.trackTrace("sleep错误： " + e.getMessage());
            }

            if (hasPlaceholders(request.getContent())) {
                return processTextWithPlaceholders(request.getContent(), counter, qwenMtCode(request.getSource()), qwenMtCode(request.getTarget()));
            }

            String resultTranslation = null;
            try {
                resultTranslation = translateByQwenMt(request.getContent(), source, target, counter);
            } catch (Exception e) {
                //TODO：mt翻译失败的话，用百炼 API翻译
                resultTranslation = singleTranslate(request.getContent(), resourceType, counter, target);
            }
            return resultTranslation;
        } else {
            //TODO： 添加token字数和计数规则
            counter.addChars(googleCalculateToken(request.getContent()));
            return getGoogleTranslationWithRetry(request);
        }
    }

    //包装一下调用百炼mt的方法
    public static String translateByQwenMt(String translateText, String source, String target, CharacterCountUtils countUtils) {
        String changeSource = qwenMtCode(source);
        String changeTarget = qwenMtCode(target);
        try {
            return callWithMessage(QWEN_MT, translateText, changeSource, changeTarget, countUtils);
        } catch (Exception e) {
            try {
                sleep(1000);
            } catch (InterruptedException ex) {
                appInsights.trackTrace("sleep错误： " + ex.getMessage());
            }
            return callWithMessage(QWEN_MT, translateText, changeSource, changeTarget, countUtils);
        }
    }

    //在调用googleTranslateJudgeCode的基础上添加计数功能,并添加到翻译后的文本
    public static String translateAndCount(TranslateRequest request,
                                           CharacterCountUtils counter, String resourceType) {
        String text = request.getContent();
        String targetString = googleTranslateJudgeCode(request, counter, resourceType);
        addData(request.getTarget(), text, targetString);
        return targetString;
    }

    // 定义语言代码集合
    private static final Set<String> LANGUAGE_CODES = new HashSet<>(Arrays.asList(
            "ce", "kw", "fo", "ia", "kl", "ks", "ki", "lu", "gv", "nd",
            "se", "nb", "nn", "os", "rm", "sc", "ii", "bo", "to", "wo", "ar-EG"
    ));

    //定义百炼可以调用的语言代码集合
    public static final Set<String> QWEN_MT_CODES = new HashSet<>(Arrays.asList(
            "zh-CN", "en", "ja", "ko", "th", "fr", "de", "es", "ar",
            "id", "vi", "pt-BR", "it", "nl", "ru", "km", "cs", "pl", "fa", "he", "tr", "hi", "bn", "ur"
    ));

}
