package com.bogdatech.utils;

import com.bogdatech.exception.ClientException;
import com.bogdatech.model.controller.request.TranslateRequest;
import com.bogdatech.model.service.KeywordModel;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.bogdatech.constants.TranslateConstants.QWEN_MT;
import static com.bogdatech.constants.UserPrivateConstants.GOOGLE;
import static com.bogdatech.integration.ALiYunTranslateIntegration.callWithMessage;
import static com.bogdatech.integration.ALiYunTranslateIntegration.singleTranslate;
import static com.bogdatech.integration.ArkTranslateIntegration.douBaoTranslate;
import static com.bogdatech.integration.HunYuanIntegration.hunYuanTranslate;
import static com.bogdatech.integration.TranslateApiIntegration.getGoogleTranslationWithRetry;
import static com.bogdatech.logic.TranslateService.SINGLE_LINE_TEXT;
import static com.bogdatech.logic.TranslateService.addData;
import static com.bogdatech.utils.ApiCodeUtils.getLanguageName;
import static com.bogdatech.utils.ApiCodeUtils.qwenMtCode;
import static com.bogdatech.utils.CalculateTokenUtils.googleCalculateToken;
import static com.bogdatech.utils.CaseSensitiveUtils.*;
import static com.bogdatech.utils.LiquidHtmlTranslatorUtils.*;
import static com.bogdatech.utils.PlaceholderUtils.*;
import static java.lang.Thread.sleep;

@Component
public class JsoupUtils {

    /**
     * 翻译词汇表单行文本，保护变量、URL和符号
     */
    private static String translateSingleLineWithProtection(String text, TranslateRequest request, CharacterCountUtils counter,
                                                            Map<String, String> keyMap1, Map<String, String> keyMap0, String resourceType) {
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

            //根据文本条件翻译
            //如果字符数低于100字符，用mt和google翻译
            if (cleanedText.length() <= 100) {
                counter.addChars(googleCalculateToken(cleanedText));
                Map<String, String> placeholderMap = new HashMap<>();
                String updateText = extractKeywords(cleanedText, placeholderMap, keyMap1, keyMap0, request.getSource());
                request.setContent(updateText);
                String targetString = translateAndCount(request, counter, resourceType);
                String finalText = restoreKeywords(targetString, placeholderMap);
                addData(request.getTarget(), cleanedText, finalText);
                return finalText;
            } else {
                //如果字符数大于100字符，用大模型翻译
                String glossaryString = glossaryText(keyMap1, keyMap0, cleanedText);
                //根据关键词生成对应的提示词
                String finalText = glossaryTranslationModel(request, counter, glossaryString);
                addData(request.getTarget(), cleanedText, finalText);
                return finalText;
            }

        });
        addData(request.getTarget(), text, translatedText);
        return translatedText;
    }

    /**
     *
     **/
    public static String glossaryText(Map<String, String> keyMap1, Map<String, String> keyMap0, String cleanedText) {
        //根据keyMap1和keyMap0提取关键词
        List<KeywordModel> keywordModels = mergeKeywordMap(keyMap0, keyMap1);
        String glossaryString = null;
        int i = 0;
        for (KeywordModel entry : keywordModels) {
            if (i == 0 && cleanedText.contains(entry.getKeyword())) {
                i++;
                glossaryString = entry.getKeyword() + "->" + entry.getTranslation();
            } else if (cleanedText.contains(entry.getKeyword())) {
                glossaryString = glossaryString + "," + entry.getKeyword() + "->" + entry.getTranslation();
            }
        }
        return glossaryString;
    }

    /**
     * 处理文本，保护不翻译的变量、URL和符号
     */
    private static String processTextWithProtection(String text, Function<String, String> translator) {
        StringBuilder result = new StringBuilder();
        int lastEnd = 0;

        List<Pattern> patterns = Arrays.asList(
                URL_PATTERN,
//                VARIABLE_PATTERN,
                CUSTOM_VAR_PATTERN
//                ,
//                LIQUID_CONDITION_PATTERN,
//                ARRAY_VAR_PATTERN
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

    //从缓存中获取数据
    public static String translateSingleLine(String sourceText, String target) {
        if (SINGLE_LINE_TEXT.get(target) != null) {
            return SINGLE_LINE_TEXT.get(target).get(sourceText);
        }
        return null;
    }


    /**
     * 调用多模型翻译：1，100字符以内用model翻译。2，ar用hunyuan-turbo-latest翻译 3，hi用doubao-1.5-pro-256k翻译
     * 根据语言代码切换API翻译
     *
     * @param request 翻译所需要的数据
     * @param counter 计数器
     * @param prompt  提示词
     *                return String       翻译后的文本
     */
    public static String translateByModel(TranslateRequest request, CharacterCountUtils counter, String prompt) {
        String target = request.getTarget();
        String source = request.getSource();
        String sourceText = request.getContent();

        //判断是否符合mt翻译 ，是， 调用mt翻译。
        if (QWEN_MT_CODES.contains(target) && QWEN_MT_CODES.contains(source) && sourceText.length() <= 100) {
            return checkTranslationApi(request, counter, prompt);
        }

        //判断是否符合大模型翻译，如果可以，翻译
//        if (sourceText.length() > 100) {
//            return checkTranslationModel(request, counter, prompt);
//        }

        //判断是否符合google翻译， 是， google翻译
//        if (!LANGUAGE_CODES.contains(target) && !LANGUAGE_CODES.contains(source)) {
//            return googleTranslateByJudge(request, counter, prompt);
//        }

        return checkTranslationModel(request, counter, prompt);
    }

    /**
     * 根据判断条件决定是否使用Google翻译
     **/
    public static String googleTranslateByJudge(TranslateRequest request, CharacterCountUtils counter, String resourceType) {
        //判断是否符合google翻译， 是， google翻译
        counter.addChars(googleCalculateToken(request.getContent()));
        if (hasPlaceholders(request.getContent())) {
            return processTextWithPlaceholders(request.getContent(), counter, qwenMtCode(request.getSource()), qwenMtCode(request.getTarget()), GOOGLE, request.getSource(), request.getTarget());
        }
        return getGoogleTranslationWithRetry(request);
    }


    /**
     * 根据每个模型的条件，翻译文本数据
     * 在翻译的同时计数字符数
     *
     * @param request      翻译所需要的数据
     * @param counter      计数器
     * @param resourceType 模块类型
     * @return String 翻译后的文本
     */
    public static String checkTranslationModel(TranslateRequest request, CharacterCountUtils counter, String resourceType) {
        String target = request.getTarget();
        String targetLanguage = getLanguageName(target);
        String content = request.getContent();
        String prompt;
        //判断target里面是否含有变量，如果没有，输入极简提示词；如果有，输入变量提示词
        if (hasPlaceholders(content)) {
            String variableString = getOuterString(content);
            prompt = getVariablePrompt(targetLanguage, variableString);
            return singleTranslate(content, prompt, counter, target);
        } else {
            prompt = getSimplePrompt(targetLanguage);
//            System.out.println("prompt变量和极简: " + prompt);
        }

        //目标语言是中文的，用qwen-max翻译
        if (target.equals("zh-CN") || target.equals("zh-TW") || target.equals("fil")) {
            return singleTranslate(content, prompt, counter, target);
        }

        //hi用doubao-1.5-pro-256k翻译
        if (target.equals("hi") || target.equals("th") || target.equals("de")) {
            return douBaoTranslate(target, prompt, content, counter);
        }
        return hunYuanTranslate(content, prompt, counter, getLanguageName(target), "hunyuan-large");

    }

    /**
     * 根据每个模型的条件，翻译文本数据
     * 在翻译的同时计数字符数
     *
     * @param request        翻译所需要的数据
     * @param counter        计数器
     * @param glossaryString 提示词的数据
     * @return String 翻译后的文本
     */
    public static String glossaryTranslationModel(TranslateRequest request, CharacterCountUtils counter, String glossaryString) {
        String target = request.getTarget();
        String content = request.getContent();
        String targetName = getLanguageName(request.getTarget());
        String prompt;

        if (glossaryString != null) {
            prompt = getGlossaryPrompt(targetName, glossaryString);
        } else {
            prompt = getSimplePrompt(targetName);
        }

        //目标语言是中文的，用qwen-max翻译
        if (target.equals("zh-CN") || target.equals("zh-TW")) {
            return singleTranslate(content, prompt, counter, target);
        }

        //hi用doubao-1.5-pro-256k翻译
        if (target.equals("hi")) {
            target = "Hindi";
            return douBaoTranslate(target, prompt, content, counter);
        }

        if (target.equals("th")) {
            target = "Thai";
            return douBaoTranslate(target, prompt, content, counter);
        }

        return hunYuanTranslate(content, prompt, counter, getLanguageName(target), "hunyuan-large");
    }


    /**
     * 如果source和target都是QwenMT支持的语言，则调用QwenMT的API。
     * 在翻译的同时计数字符数
     *
     * @param request 翻译所需要的数据
     * @param counter 计数器
     * @param prompt  提示词
     *                return String 翻译后的文本
     */
    public static String checkTranslationApi(TranslateRequest request, CharacterCountUtils counter, String prompt) {
        String target = request.getTarget();
        String source = request.getSource();
        //如果source和target都是QwenMT支持的语言，则调用QwenMT的API。 反之亦然
        //目前做个初步的限制，每次用mt翻译前都sleep一下，防止调用频率过高。0.3s. 后面请求解决限制后，删掉这段代码。
        try {
            sleep(300);
        } catch (Exception e) {
            appInsights.trackTrace("sleep错误： " + e.getMessage());
        }

        if (hasPlaceholders(request.getContent())) {
            return processTextWithPlaceholders(request.getContent(), counter, qwenMtCode(request.getSource()), qwenMtCode(request.getTarget()), QWEN_MT, request.getSource(), request.getTarget());
        }

        String resultTranslation;
        try {
            resultTranslation = translateByQwenMt(request.getContent(), source, target, counter);
        } catch (Exception e) {
            //mt翻译失败的话，用其他大模型翻译
            resultTranslation = checkTranslationModel(request, counter, prompt);
        }
        return resultTranslation;
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

    /**
     * 在调用googleTranslateJudgeCode的基础上添加计数功能,并添加到翻译后的文本
     *
     * @param request 翻译所需要的数据
     * @param counter 计数器
     * @param prompt  提示词
     *                return String 翻译后的文本
     */
    public static String translateAndCount(TranslateRequest request,
                                           CharacterCountUtils counter, String prompt) {
        String text = request.getContent();
        String targetString = translateByModel(request, counter, prompt);
        if (targetString == null) {
            return text;
        }
        targetString = isHtmlEntity(targetString);
        addData(request.getTarget(), text, targetString);
        return targetString;
    }

    // 定义google翻译不了的语言代码集合
    private static final Set<String> LANGUAGE_CODES = new HashSet<>(Arrays.asList(
            "ce", "kw", "fo", "ia", "kl", "ks", "ki", "lu", "gv", "nd", "pt",
            "se", "nb", "nn", "os", "rm", "sc", "ii", "bo", "to", "wo", "ar-EG"
    ));

    //定义百炼可以调用的语言代码集合
    public static final Set<String> QWEN_MT_CODES = new HashSet<>(Arrays.asList(
            "zh-CN", "en", "ja", "ko", "th", "fr", "de", "es", "ar",
            "id", "vi", "pt-BR", "it", "nl", "ru", "km", "cs", "pl", "fa", "he", "tr", "hi", "bn", "ur"
    ));

    public static String translateGlossaryHtml(String html, TranslateRequest request, CharacterCountUtils counter, String resourceType, Map<String, String> keyMap0, Map<String, String> keyMap1) {
        // 检查输入是否有效
        if (html == null || html.trim().isEmpty()) {
            return html;
        }

        try {
            // 判断输入是否包含 <html> 标签
            boolean hasHtmlTag = HTML_TAG_PATTERN.matcher(html).find();

            if (hasHtmlTag) {
                // 如果有 <html> 标签，按完整文档处理
                Document doc = Jsoup.parse(html);
                if (doc == null) {
                    return html;
                }

                processNode(doc.body(), request, counter, resourceType, keyMap0, keyMap1);
                return doc.outerHtml();
            } else {
                // 如果没有 <html> 标签，作为片段处理
                Document doc = Jsoup.parseBodyFragment(html);
                Element body = doc.body();

                processNode(body, request, counter, resourceType, keyMap0, keyMap1);

                // 只返回子节点内容，不包含 <body>
                StringBuilder result = new StringBuilder();
                for (Node child : body.childNodes()) {
                    result.append(child.toString());
                }

                return result.toString();
            }

        } catch (Exception e) {
            return html;
        }
    }

    /**
     * 递归处理节点
     *
     * @param node 当前节点
     */
    private static void processNode(Node node, TranslateRequest request, CharacterCountUtils counter, String resourceType, Map<String, String> keyMap0, Map<String, String> keyMap1) {
        try {
            // 如果是元素节点
            if (node instanceof Element) {
                Element element = (Element) node;
                String tagName = element.tagName().toLowerCase();

                // 检查是否为不翻译的标签
                if (noTranslateTags.contains(tagName)) {
                    return;
                }

                // 属性不翻译，保持原样
                element.attributes().forEach(attr -> {
                });

                // 递归处理子节点
                for (Node child : element.childNodes()) {
                    processNode(child, request, counter, resourceType, keyMap0, keyMap1);
                }
            }
            // 如果是文本节点
            else if (node instanceof TextNode) {
                TextNode textNode = (TextNode) node;
                String text = textNode.getWholeText();

                // 如果文本为空或只有空白字符，跳过
                if (text.trim().isEmpty()) {
                    return;
                }

                // 使用缓存处理文本
                String translatedText = translateTextWithCache(text, request, counter, resourceType, keyMap0, keyMap1);
                textNode.text(translatedText);
            }
        } catch (Exception e) {
            appInsights.trackTrace("递归处理节点报错： " + e.getMessage());
        }
    }

    /**
     * 使用缓存处理文本内容，保护变量和URL
     *
     * @param text 输入文本
     * @return 翻译后的文本
     */
    private static String translateTextWithCache(String text, TranslateRequest request, CharacterCountUtils counter, String resourceType, Map<String, String> keyMap0, Map<String, String> keyMap1) {
        // 检查缓存
        String translated = translateSingleLine(text, request.getTarget());
        if (translated != null) {
            return translated;
        }

        // 处理文本中的变量和URL
        String translatedText = translateTextWithProtection(text, request, counter, resourceType, keyMap0, keyMap1);

        // 存入缓存
        addData(request.getTarget(), text, translatedText);
        return translatedText;
    }

    /**
     * 处理文本内容，保护变量和URL
     *
     * @param text 输入文本
     * @return 翻译后的文本
     */
    private static String translateTextWithProtection(String text, TranslateRequest request, CharacterCountUtils counter, String resourceType, Map<String, String> keyMap0, Map<String, String> keyMap1) {
        StringBuilder result = new StringBuilder();
        int lastEnd = 0;

        // 合并所有需要保护的模式
        List<Pattern> patterns = Arrays.asList(
                URL_PATTERN,
//                VARIABLE_PATTERN,
                CUSTOM_VAR_PATTERN,
//                LIQUID_CONDITION_PATTERN,
//                ARRAY_VAR_PATTERN,
                SYMBOL_PATTERN
        );

        List<MatchRange> matches = new ArrayList<>();
        for (Pattern pattern : patterns) {
            Matcher matcher = pattern.matcher(text);
            while (matcher.find()) {
                matches.add(new MatchRange(matcher.start(), matcher.end(), matcher.group()));
            }
        }

        // 按位置排序
        matches.sort(Comparator.comparingInt(m -> m.start));

        // 处理所有匹配项之间的文本
        for (MatchRange match : matches) {
            // 翻译匹配项之前的文本
            if (match.start > lastEnd) {
                String toTranslate = text.substring(lastEnd, match.start);
                String cleanedText = cleanTextFormat(toTranslate); // 清理格式
                //对特殊符号进行处理
                if (cleanedText.matches("\\p{Zs}+")) {
                    result.append(cleanedText);
                    continue;
                }
                if (!cleanedText.trim().isEmpty()) { // 避免翻译空字符串
                    String targetString;
                    try {
                        request.setContent(cleanedText);
                        targetString = translateSingleLineWithProtection(text, request, counter, keyMap1, keyMap0, resourceType);
                        targetString = isHtmlEntity(targetString);
                        result.append(targetString);
                    } catch (ClientException e) {
                        // 如果AI翻译失败，则使用谷歌翻译
                        result.append(cleanedText);
                        continue;
                    }
                } else {
                    result.append(toTranslate); // 保留原始空白
                }
            }
            // 保留匹配到的变量或URL，不翻译
            result.append(match.content);
            lastEnd = match.end;
        }

        // 处理剩余文本
        if (lastEnd < text.length()) {
            String remaining = text.substring(lastEnd);
            String cleanedText = cleanTextFormat(remaining); // 清理格式
            if (cleanedText.matches("\\p{Zs}+")) {
                result.append(cleanedText);
                return result.toString();
            }
            if (!cleanedText.trim().isEmpty() && !cleanedText.matches("\\s*")) {
                String targetString;
                try {
                    request.setContent(cleanedText);
//                        appInsights.trackTrace("处理剩余文本： " + cleanedText);
//                        System.out.println("要翻译的文本： " + cleanedText);
                    targetString = translateSingleLineWithProtection(text, request, counter, keyMap1, keyMap0, resourceType);
                    targetString = isHtmlEntity(targetString);
                    result.append(targetString);
                } catch (ClientException e) {
                    result.append(cleanedText);
                }
            } else {
                result.append(remaining);
            }
        }
        return result.toString();
    }


}
