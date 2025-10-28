package com.bogdatech.utils;

import com.bogdatech.entity.DO.TranslateTextDO;
import com.bogdatech.entity.VO.KeywordVO;
import com.bogdatech.exception.ClientException;
import com.bogdatech.integration.*;
import com.bogdatech.logic.RedisProcessService;
import com.bogdatech.logic.redis.TranslationParametersRedisService;
import com.bogdatech.model.controller.request.TranslateRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import static com.bogdatech.constants.TranslateConstants.*;
import static com.bogdatech.integration.DeepLIntegration.DEEPL_LANGUAGE_MAP;
import static com.bogdatech.logic.redis.TranslationParametersRedisService.generateProgressTranslationKey;
import static com.bogdatech.utils.ApiCodeUtils.getLanguageName;
import static com.bogdatech.utils.ApiCodeUtils.qwenMtCode;
import static com.bogdatech.utils.CalculateTokenUtils.googleCalculateToken;
import static com.bogdatech.utils.CaseSensitiveUtils.*;
import static com.bogdatech.utils.JsonUtils.isJson;
import static com.bogdatech.utils.JudgeTranslateUtils.*;
import static com.bogdatech.utils.LiquidHtmlTranslatorUtils.*;
import static com.bogdatech.utils.PlaceholderUtils.*;
import static com.bogdatech.utils.RedisKeyUtils.PROGRESS_DONE;
import static com.bogdatech.utils.RedisKeyUtils.generateProcessKey;
import static com.bogdatech.utils.StringUtils.isValueBlank;
import static com.bogdatech.utils.StringUtils.replaceHyphensWithSpaces;
import static java.lang.Thread.sleep;

@Component
public class JsoupUtils {

    @Autowired
    private ALiYunTranslateIntegration aLiYunTranslateIntegration;
    @Autowired
    private TranslationParametersRedisService translationParametersRedisService;
    @Autowired
    private HunYuanIntegration hunYuanIntegration;
    @Autowired
    private DeepLIntegration deepLIntegration;
    @Autowired
    private RedisProcessService redisProcessService;
    @Autowired
    private ChatGptIntegration chatGptIntegration;


    /**
     * 翻译词汇表单行文本，保护变量、URL和符号
     */
    private String translateSingleLineWithProtection(String text, TranslateRequest request, CharacterCountUtils counter,
                                                     Map<String, String> keyMap1, Map<String, String> keyMap0, String resourceType, String languagePackId, Integer limitChars, boolean isSingleFlag) {
        // 检查缓存
        String translatedCache = redisProcessService.getCacheData(request.getTarget(), text);
        if (translatedCache != null) {
            return translatedCache;
        }

        // 处理文本，保护不翻译的部分
        String translatedText = processTextWithProtection(text, (cleanedText) -> {
            String translated = redisProcessService.getCacheData(request.getTarget(), cleanedText);
            if (translated != null) {
                return translated;
            }

            //根据文本条件翻译
            //如果字符数低于5字符，用mt和qwen翻译
            if (cleanedText.length() <= 5) {
                counter.addChars(googleCalculateToken(cleanedText));
                String targetString = translateAndCount(request, counter, languagePackId, GENERAL, limitChars, isSingleFlag);
                redisProcessService.setCacheData(request.getTarget(), targetString, cleanedText);
                return targetString;
            } else {
                //如果字符数大于100字符，用大模型翻译
                String glossaryString = glossaryText(keyMap1, keyMap0, cleanedText);
                //根据关键词生成对应的提示词
                String finalText = glossaryTranslationModel(request, counter, glossaryString, languagePackId, limitChars, isSingleFlag);
                redisProcessService.setCacheData(request.getTarget(), finalText, cleanedText);
                return finalText;
            }

        });

        translationParametersRedisService.hsetTranslationStatus(generateProgressTranslationKey(request.getShopName(), request.getSource(), request.getTarget()), String.valueOf(2));
        translationParametersRedisService.hsetTranslatingString(generateProgressTranslationKey(request.getShopName(), request.getSource(), request.getTarget()), text);
        return translatedText;
    }

    /**
     *
     **/
    public static String glossaryText(Map<String, String> keyMap1, Map<String, String> keyMap0, String cleanedText) {
        //根据keyMap1和keyMap0提取关键词
        List<KeywordVO> KeywordVOs = mergeKeywordMap(keyMap0, keyMap1);
        String glossaryString = null;
        int i = 0;
        for (KeywordVO entry : KeywordVOs) {
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
                CUSTOM_VAR_PATTERN,
                SYMBOL_PATTERN
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

    //判断String类型是否是html数据
    public static boolean isHtml(String content) {
        //如果content里面有html标签，再判断，否则返回false
        if (!content.contains("<") && !content.contains("</")) {
            return false;
        }
        Document doc = Jsoup.parse(content);
        return !doc.body().text().equals(content);
    }

    /**
     * 调用多模型翻译：1，5字符以内用model翻译和qwen翻译。2，ar用HUN_YUAN_MODEL翻译 3，hi用doubao-1.5-pro-256k翻译
     * 根据语言代码切换API翻译
     *
     * @param request        翻译所需要的数据
     * @param counter        计数器
     * @param languagePackId 语言包id
     *                       return String       翻译后的文本
     */
    public String translateByModel(TranslateRequest request, CharacterCountUtils counter, String languagePackId, Integer limitChars, boolean isSingleFlag) {
        String sourceText = request.getContent();

        //从缓存中获取数据
        //判断是否符合mt翻译 ，是， 调用mt翻译。
        if (sourceText.length() <= 20) {
            return checkTranslationApi(request, counter, limitChars, null, isSingleFlag);
        }

        return checkTranslationModel(request, counter, languagePackId, limitChars, isSingleFlag);
    }

    /**
     * 根据每个模型的条件，翻译文本数据
     * 在翻译的同时计数字符数
     *
     * @param request        翻译所需要的数据
     * @param counter        计数器
     * @param languagePackId 语言包id
     * @return String 翻译后的文本
     */
    public String checkTranslationModel(TranslateRequest request, CharacterCountUtils counter, String languagePackId, Integer limitChars, boolean isSingleFlag) {
        String target = request.getTarget();
        String targetLanguage = getLanguageName(target);
        String content = request.getContent();
        String shopName = request.getShopName();
        String prompt;

        // 判断target里面是否含有变量，如果没有，输入极简提示词；如果有，输入变量提示词
        if (hasPlaceholders(content)) {
            return translateVariableData(request, counter, limitChars, targetLanguage, languagePackId, isSingleFlag);

        } else {
            prompt = getSimplePrompt(targetLanguage, languagePackId);
            appInsights.trackTrace("clickTranslation " + shopName + " 普通文本：" + content + " Simple提示词: " + prompt);
        }
        try {
            // 对模型进行判断 , 1,ciwi 2,openai 3,deepL
            return translateByCiwiModel(request, counter, limitChars, prompt, isSingleFlag);
        } catch (Exception e) {
            appInsights.trackException(e);
            appInsights.trackTrace("clickTranslation " + shopName + " checkTranslationModel errors ： " + e.getMessage() + " sourceText: " + content);
            return aLiYunTranslateIntegration.singleTranslate(content, prompt, counter, target, shopName, limitChars, isSingleFlag);
        }

    }

    /**
     * 根据每个模型的条件，翻译文本数据
     * 在翻译的同时计数字符数
     *
     * @param request        翻译所需要的数据
     * @param counter        计数器
     * @param glossaryString 提示词的数据
     * @param languagePackId 语言包id
     * @return String 翻译后的文本
     */
    public String glossaryTranslationModel(TranslateRequest request, CharacterCountUtils counter, String glossaryString, String languagePackId, Integer limitChars, boolean isSingleFlag) {

        String target = request.getTarget();
        String content = request.getContent();
        String targetName = getLanguageName(request.getTarget());
        String shopName = request.getShopName();
        String prompt;
        if (glossaryString != null) {
            prompt = getGlossaryPrompt(targetName, glossaryString, languagePackId);
            appInsights.trackTrace("clickTranslation " + shopName + " 普通文本： " + content + " Glossary提示词: " + prompt);
        } else {
            prompt = getSimplePrompt(targetName, languagePackId);
            appInsights.trackTrace("clickTranslation " + shopName + " 普通文本：" + content + " Simple提示词: " + prompt);
        }

        try {
            return translateByCiwiModel(request, counter, limitChars, prompt, isSingleFlag);
        } catch (Exception e) {
            appInsights.trackTrace("clickTranslation " + shopName + " glossaryTranslationModel errors ： " + e.getMessage() + " sourceText: " + content);
            return aLiYunTranslateIntegration.singleTranslate(content, prompt, counter, target, shopName, limitChars, isSingleFlag);
        }
    }


    /**
     * 如果source和target都是QwenMT支持的语言，则调用QwenMT的API。
     * 在翻译的同时计数字符数
     *
     * @param request    翻译所需要的数据
     * @param counter    计数器
     * @param limitChars 用户最大限制
     *                   return String 翻译后的文本
     */
    public String checkTranslationApi(TranslateRequest request, CharacterCountUtils counter, Integer limitChars, String translationModel, boolean isSingleFlag) {
        String target = request.getTarget();
        String source = request.getSource();
        String content = request.getContent();
        //如果source和target都是QwenMT支持的语言，则调用QwenMT的API。 反之亦然
        //目前做个初步的限制，每次用mt翻译前都sleep一下，防止调用频率过高。0.3s. 后面请求解决限制后，删掉这段代码。
        if (hasPlaceholders(content)) {
            return translateVariableData(request, counter, limitChars, target, null, isSingleFlag);
        }

        try {
            sleep(300);
        } catch (Exception e) {
            appInsights.trackTrace("clickTranslation sleep errors ： " + e.getMessage());
        }
        String targetLanguage = getLanguageName(target);
        String prompt = getShortPrompt(targetLanguage);
        String resultTranslation;
        try {
            if (DEEPL_MODEL.equals(translationModel) && !deepLIntegration.isDeepLEnough() && DEEPL_LANGUAGE_MAP.containsKey(target)) {
                resultTranslation = deepLIntegration.translateByDeepL(content, target, counter, request.getShopName(), limitChars, isSingleFlag);
            } else if (QWEN_MT_CODES.contains(target) && QWEN_MT_CODES.contains(source)) {
                resultTranslation = translateByQwenMt(request.getContent(), source, target, counter, request.getShopName(), limitChars, isSingleFlag);
            } else {
                //qwen 短文本翻译
                appInsights.trackTrace("clickTranslation " + request.getShopName() + " 短文本翻译： " + request.getContent() + " 提示词: " + prompt);
                resultTranslation = aLiYunTranslateIntegration.singleTranslate(request.getContent(), prompt, counter, target, request.getShopName(), limitChars, isSingleFlag);
            }
            return resultTranslation;

        } catch (Exception e) {
            //mt翻译失败的话，用其他大模型翻译
            appInsights.trackException(e);
            appInsights.trackTrace("clickTranslation " + request.getShopName() + " 短文本翻译 errors : " + e.getMessage() + " sourceText: " + content);
            resultTranslation = deepLIntegration.translateByDeepL(content, target, counter, request.getShopName(), limitChars, isSingleFlag);
            return resultTranslation;
        }
    }

    //包装一下调用百炼mt的方法
    public String translateByQwenMt(String translateText, String source, String target, CharacterCountUtils countUtils, String shopName, Integer limitChars, boolean isSingleFlag) {
        String changeSource = qwenMtCode(source);
        String changeTarget = qwenMtCode(target);
        try {
            return aLiYunTranslateIntegration.callWithMessageMT(QWEN_MT, translateText, changeSource, changeTarget, countUtils, shopName, limitChars, isSingleFlag);
        } catch (Exception e) {
            try {
                sleep(1000);
            } catch (InterruptedException ex) {
                appInsights.trackTrace("clickTranslation MT sleep errors ： " + ex.getMessage());
            }
            return aLiYunTranslateIntegration.callWithMessageMT(QWEN_MT, translateText, changeSource, changeTarget, countUtils, shopName, limitChars, isSingleFlag);
        }

    }


    /**
     * 在调用大模型的基础上添加计数功能,并添加到翻译后的文本
     *
     * @param request        翻译所需要的数据
     * @param counter        计数器
     * @param languagePackId 语言包id
     * @param translateType  翻译类型
     *                       return String 翻译后的文本
     */
    public String translateAndCount(TranslateRequest request,
                                    CharacterCountUtils counter, String languagePackId, String translateType, Integer limitChars, boolean isSingleFlag) {
        String text = request.getContent();
        // 检测text是不是全大写，如果是的话，最后翻译完也全大写

        String targetString;
        if (translateType.equals(HANDLE)) {
            if (text.length() <= 20) {
                targetString = checkTranslationApi(request, counter, limitChars, null, isSingleFlag);
            } else {
                targetString = translationHandle(request, counter, languagePackId, limitChars, isSingleFlag);
            }
        } else {
            targetString = translateByModel(request, counter, languagePackId, limitChars, isSingleFlag);
        }

        if (targetString == null) {
            // 对null的处理，再次翻译
            return checkTranslationModel(request, counter, languagePackId, limitChars, isSingleFlag);
        }

        targetString = isHtmlEntity(targetString);

        // 判断translateType是不是handle，再决定是否添加到缓存
        if (!translateType.equals(HANDLE)) {
            redisProcessService.setCacheData(request.getTarget(), targetString, text);
        }

        translationParametersRedisService.hsetTranslationStatus(generateProgressTranslationKey(request.getShopName(), request.getSource(), request.getTarget()), String.valueOf(2));
        translationParametersRedisService.hsetTranslatingString(generateProgressTranslationKey(request.getShopName(), request.getSource(), request.getTarget()), "Searching for content to translate…");
        return targetString;
    }



    // 定义google翻译不了的语言代码集合
    public static final Set<String> LANGUAGE_CODES = new HashSet<>(Arrays.asList(
            "ce", "kw", "fo", "ia", "kl", "ks", "ki", "lu", "gv", "nd", "pt",
            "se", "nb", "nn", "os", "rm", "sc", "ii", "bo", "to", "wo", "ar-EG"
    ));

    //定义百炼可以调用的语言代码集合
    public static final Set<String> QWEN_MT_CODES = new HashSet<>(Arrays.asList(
            "zh-CN", "en", "ja", "ko", "th", "fr", "de", "es", "ar",
            "id", "vi", "pt-BR", "it", "nl", "ru", "km", "cs", "pl", "fa", "he", "tr", "hi", "bn", "ur"
    ));

    public String translateGlossaryHtml(String html, TranslateRequest request, CharacterCountUtils counter, String resourceType, Map<String, String> keyMap0, Map<String, String> keyMap1, String languagePackId, Integer limitChars, boolean isSingleFlag) {
        // 检查输入是否有效
        if (html == null || html.trim().isEmpty()) {
            return html;
        }

        translationParametersRedisService.hsetTranslationStatus(generateProgressTranslationKey(request.getShopName(), request.getSource(), request.getTarget()), String.valueOf(2));
        translationParametersRedisService.hsetTranslatingString(generateProgressTranslationKey(request.getShopName(), request.getSource(), request.getTarget()), html);

        try {
            // 判断输入是否包含 <html> 标签
            boolean hasHtmlTag = HTML_TAG_PATTERN.matcher(html).find();

            if (hasHtmlTag) {
                // 如果有 <html> 标签，按完整文档处理
                Document doc = Jsoup.parse(html);
                if (doc == null) {
                    return html;
                }

                // 获取 <html> 元素并修改 lang 属性
                Element htmlTag = doc.selectFirst("html");
                if (htmlTag != null) {
                    htmlTag.attr("lang", request.getTarget());
                }

                processNode(doc.body(), request, counter, resourceType, keyMap0, keyMap1, languagePackId, limitChars, isSingleFlag);
                return doc.outerHtml();
            } else {
                // 如果没有 <html> 标签，作为片段处理
                Document doc = Jsoup.parseBodyFragment(html);
                Element body = doc.body();

                processNode(body, request, counter, resourceType, keyMap0, keyMap1, languagePackId, limitChars, isSingleFlag);

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
    private void processNode(Node node, TranslateRequest request, CharacterCountUtils counter, String resourceType, Map<String, String> keyMap0, Map<String, String> keyMap1, String languagePackId, Integer limitChars, boolean isSingleFlag) {
        try {
            // 如果是元素节点
            if (node instanceof Element) {
                Element element = (Element) node;
                String tagName = element.tagName().toLowerCase();

                // 检查是否为不翻译的标签
                if (NO_TRANSLATE_TAGS.contains(tagName)) {
                    return;
                }

                // 属性不翻译，保持原样
                element.attributes().forEach(attr -> {
                });

                // 递归处理子节点
                for (Node child : element.childNodes()) {
                    processNode(child, request, counter, resourceType, keyMap0, keyMap1, languagePackId, limitChars, isSingleFlag);
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
                String translatedText = translateTextWithCache(text, request, counter, resourceType, keyMap0, keyMap1, languagePackId, limitChars, isSingleFlag);
                textNode.text(translatedText);
            }
        } catch (Exception e) {
            appInsights.trackTrace("递归处理节点报错 errors ： " + e.getMessage());
        }
    }

    /**
     * 使用缓存处理文本内容，保护变量和URL
     *
     * @param text 输入文本
     * @return 翻译后的文本
     */
    private String translateTextWithCache(String text, TranslateRequest request, CharacterCountUtils counter, String resourceType, Map<String, String> keyMap0, Map<String, String> keyMap1, String languagePackId, Integer limitChars, boolean isSingleFlag) {
        return translateTextWithProtection(text, request, counter, resourceType, keyMap0, keyMap1, languagePackId, limitChars, isSingleFlag);
    }

    /**
     * 处理文本内容，保护变量和URL
     *
     * @param text 输入文本
     * @return 翻译后的文本
     */
    private String translateTextWithProtection(String text, TranslateRequest request, CharacterCountUtils counter, String resourceType, Map<String, String> keyMap0, Map<String, String> keyMap1, String languagePackId, Integer limitChars, boolean isSingleFlag) {
        StringBuilder result = new StringBuilder();
        int lastEnd = 0;

        // 合并所有需要保护的模式
        List<Pattern> patterns = Arrays.asList(
                URL_PATTERN,
                CUSTOM_VAR_PATTERN,
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
//                        appInsights.trackTrace("处理剩余文本： " + cleanedText);
//                        appInsights.trackTrace("要翻译的文本： " + cleanedText);
                        targetString = translateSingleLineWithProtection(text, request, counter, keyMap1, keyMap0, resourceType, languagePackId, limitChars, isSingleFlag);
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
//                    appInsights.trackTrace("要翻译的文本： " + cleanedText);
                    targetString = translateSingleLineWithProtection(text, request, counter, keyMap1, keyMap0, resourceType, languagePackId, limitChars, isSingleFlag);
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

    /**
     * 翻译handle数据
     * 根据每个模型的条件，翻译文本数据
     * 在翻译的同时计数字符数
     *
     * @param request        翻译所需要的数据
     * @param counter        计数器
     * @param languagePackId 语言包id
     * @return String 翻译后的文本
     */
    public String translationHandle(TranslateRequest request, CharacterCountUtils counter, String languagePackId, Integer limitChars, boolean isSingleFlag) {

        String target = request.getTarget();
        String targetLanguage = getLanguageName(target);
        String content = request.getContent();
        String shopName = request.getShopName();
        String fixContent = replaceHyphensWithSpaces(content);
        //handle特供翻译， handle特用提示词
        String prompt = getHandlePrompt(targetLanguage);
        appInsights.trackTrace("普通文本： " + content + " Handle提示词: " + prompt);
        try {
            //目标语言是中文的，用qwen-max翻译
            return translateByCiwiModel(request, counter, limitChars, languagePackId, isSingleFlag);
        } catch (Exception e) {
            appInsights.trackTrace("翻译handle数据报错 errors ： " + e.getMessage() + " sourceText: " + content);
            appInsights.trackException(e);
            return aLiYunTranslateIntegration.singleTranslate(fixContent, prompt, counter, target, shopName, limitChars, isSingleFlag);
        }
    }

    /**
     * 变量翻译,目前暂定ciwi模型翻译
     */
    public String translateVariableData(TranslateRequest request, CharacterCountUtils counter, Integer limitChars, String targetLanguage, String languagePackId, boolean isSingleFlag) {
        String target = request.getTarget();
        String content = request.getContent();
        String shopName = request.getShopName();
        String variableString = getOuterString(content);
        String prompt = getVariablePrompt(targetLanguage, variableString, languagePackId);
        appInsights.trackTrace("模块文本： " + content + " variable提示词: " + prompt);
        return aLiYunTranslateIntegration.singleTranslate(content, prompt, counter, target, shopName, limitChars, isSingleFlag);
    }

    /**
     * ciwi 模型翻译   多 System User 翻译
     */
    public String translateByCiwiModel(TranslateRequest request, CharacterCountUtils counter, Integer limitChars, String prompt, boolean isSingleFlag) {
        String target = request.getTarget();
        String content = request.getContent();
        String shopName = request.getShopName();

        // 目标语言是中文的，用qwen-max翻译
        if ("fr".equals(target) || "ko".equals(target) || "es".equals(target) || "de".equals(target) || "it".equals(target) || "nl".equals(target) || "ro".equals(request.getSource()) || "en".equals(target) || "zh-CN".equals(target) || "zh-TW".equals(target) || "fil".equals(target) || "ar".equals(target) || "el".equals(target)) {
            return aLiYunTranslateIntegration.singleTranslate(content, prompt, counter, target, shopName, limitChars, isSingleFlag);
        }

        return hunYuanIntegration.hunYuanTranslate(content, prompt, counter, HUN_YUAN_MODEL, shopName, limitChars, target, isSingleFlag);
    }

    /**
     * ciwi 单 User 翻译
     * gpt 翻译
     */
    public String translateByCiwiOrGptModel(String target, String content, String shopName, String source, CharacterCountUtils counter, Integer limitChars, String prompt, boolean isSingleFlag, String translationModel) {
        appInsights.trackTrace("千问或gpt翻译 用户： " + shopName + " 模型类型： " + translationModel);
        if ("2".equals(translationModel)) {
            // gpt 翻译
            return chatGptIntegration.chatWithGpt(prompt, content, shopName, target, counter, limitChars, isSingleFlag);
        }
        return aLiYunTranslateIntegration.userTranslate(content, prompt, counter, target, shopName, limitChars, isSingleFlag);
    }

    /**
     * 遍历needTranslatedSet, 对Set集合进行通用规则的筛选，返回筛选后的数据
     */
    public Set<TranslateTextDO> filterNeedTranslateSet(String modeType, boolean handleFlag, Set<TranslateTextDO> needTranslateSet, String shopName, String target) {
        Iterator<TranslateTextDO> iterator = needTranslateSet.iterator();
        while (iterator.hasNext()) {
            TranslateTextDO translateTextDO = iterator.next();
            String value = translateTextDO.getSourceText();

            // 当 value 为空时跳过
            if (!isValueBlank(value)) {
                iterator.remove(); //  安全删除
                redisProcessService.addProcessData(generateProcessKey(shopName, target), PROGRESS_DONE, 1L);
                continue;
            }

            String type = translateTextDO.getTextType();

            // 如果是特定类型，也从集合中移除
            if ("FILE_REFERENCE".equals(type) || "LINK".equals(type)
                    || "LIST_FILE_REFERENCE".equals(type) || "LIST_LINK".equals(type)
                    || "LIST_URL".equals(type)
                    || "JSON".equals(type)
                    || "JSON_STRING".equals(type)
            ) {
                iterator.remove(); // 根据业务条件删除
                redisProcessService.addProcessData(generateProcessKey(shopName, target), PROGRESS_DONE, 1L);
                continue;
            }

            //判断数据是不是json数据。是的话删除
            if (isJson(value)) {
                iterator.remove(); // 根据业务条件删除
                redisProcessService.addProcessData(generateProcessKey(shopName, target), PROGRESS_DONE, 1L);
                continue;
            }

            String key = translateTextDO.getTextKey();
            //如果handleFlag为false，则跳过
            if (type.equals(URI) && "handle".equals(key)) {
                if (!handleFlag) {
                    iterator.remove();
                    redisProcessService.addProcessData(generateProcessKey(shopName, target), PROGRESS_DONE, 1L);
                    continue;
                }
            }

            //通用的不翻译数据
            if (!generalTranslate(key, value)) {
                iterator.remove(); // 根据业务条件删除
                redisProcessService.addProcessData(generateProcessKey(shopName, target), PROGRESS_DONE, 1L);
                continue;
            }

            //如果是theme模块的数据
            if (TRANSLATABLE_RESOURCE_TYPES.contains(modeType)) {
                //如果是html放html文本里面
                if (isHtml(value)) {
                    continue;
                }

                //对key中包含slide  slideshow  general.lange 的数据不翻译
                if (key.contains("general.lange")) {
                    printTranslateReason(value + "是包含general.lange的key是： " + key);
                    iterator.remove();
                    redisProcessService.addProcessData(generateProcessKey(shopName, target), PROGRESS_DONE, 1L);
                    continue;
                }

                if (key.contains("block") && key.contains("add_button_selector")){
                    printTranslateReason(value + "是包含block,add_button_selector的key是： " + key);
                    iterator.remove();
                    redisProcessService.addProcessData(generateProcessKey(shopName, target), PROGRESS_DONE, 1L);
                    continue;
                }
                //对key中含section和general的做key值判断
                if (GENERAL_OR_SECTION_PATTERN.matcher(key).find()) {
                    //进行白名单的确认
                    if (whiteListTranslate(key)) {
                        continue;
                    }

                    //如果包含对应key和value，则跳过
                    if (!shouldTranslate(key, value)) {
                        iterator.remove();
                        redisProcessService.addProcessData(generateProcessKey(shopName, target), PROGRESS_DONE, 1L);
                        continue;
                    }
                }
            }

            //对METAFIELD字段翻译
            if (modeType.equals(METAFIELD)) {
                //如UXxSP8cSm，UgvyqJcxm。有大写字母和小写字母的组合。有大写字母，小写字母和数字的组合。 10位 字母和数字不翻译
                if (SUSPICIOUS_PATTERN.matcher(value).matches() || SUSPICIOUS2_PATTERN.matcher(value).matches()) {
                    iterator.remove();
                    redisProcessService.addProcessData(generateProcessKey(shopName, target), PROGRESS_DONE, 1L);
                    continue;
                }
                if (!metaTranslate(value)) {
                    iterator.remove();
                    redisProcessService.addProcessData(generateProcessKey(shopName, target), PROGRESS_DONE, 1L);
                    continue;
                }
                //如果是base64编码的数据，不翻译
                if (BASE64_PATTERN.matcher(value).matches()) {
                    printTranslateReason(value + "是base64编码的数据, key是： " + key);
                    iterator.remove();
                    redisProcessService.addProcessData(generateProcessKey(shopName, target), PROGRESS_DONE, 1L);
                    continue;
                }
                if (isJson(value)) {
                    iterator.remove();
                    redisProcessService.addProcessData(generateProcessKey(shopName, target), PROGRESS_DONE, 1L);
                    continue;
                }
            }

        }
        return needTranslateSet;
    }

    /**
     * 解析shopifyData数据，分析数据是否可用，然后判断出可翻译数据
     */
    public Set<TranslateTextDO> translatedDataParse(JsonNode shopDataJson, String shopName, Boolean isCover, String target) {
        Set<TranslateTextDO> doubleTranslateTextDOSet = new HashSet<>();
        try {
            // 获取 translatableResources 节点
            JsonNode translatableResourcesNode = shopDataJson.path("translatableResources");
            if (!translatableResourcesNode.isObject()) {
                return null;
            }
            // 处理 nodes 数组
            JsonNode nodesNode = translatableResourcesNode.path("nodes");
            if (!nodesNode.isArray()) {
                return null;
            }
            // nodesArray.size()相当于resourceId的数量，相当于items数
            ArrayNode nodesArray = (ArrayNode) nodesNode;
            for (JsonNode nodeElement : nodesArray) {
                if (nodeElement.isObject()) {
                    Set<TranslateTextDO> stringTranslateTextDOSet = needTranslatedSet(nodeElement, shopName, isCover, target);
                    doubleTranslateTextDOSet.addAll(stringTranslateTextDOSet);
                }
            }
        } catch (Exception e) {
            appInsights.trackException(e);
            appInsights.trackTrace("clickTranslation 用户 " + shopName + " 分析数据失败 errors : " + e);
        }
        return doubleTranslateTextDOSet;
    }

    /**
     * 分析用户需要翻译的数据
     */
    public Set<TranslateTextDO> needTranslatedSet(JsonNode shopDataJson, String shopName, Boolean isCover, String target) {
        String resourceId;
        Iterator<Map.Entry<String, JsonNode>> fields = shopDataJson.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            String fieldName = field.getKey();
            JsonNode fieldValue = field.getValue();

            // 根据字段名称进行处理
            if ("resourceId".equals(fieldName)) {
                if (fieldValue == null) {
                    continue;
                }
                resourceId = fieldValue.asText(null);
                // 提取翻译内容映射
                Map<String, TranslateTextDO> partTranslateTextDOMap = extractTranslationsByResourceId(shopDataJson, resourceId, shopName);
                Map<String, TranslateTextDO> partTranslatedTextDOMap = extractTranslatedDataByResourceId(shopDataJson, partTranslateTextDOMap, isCover, target, shopName);
                Set<TranslateTextDO> needTranslatedSet = new HashSet<>(partTranslatedTextDOMap.values());
                return new HashSet<>(needTranslatedSet);
            }
        }
        return new HashSet<>();
    }

    private static Map<String, TranslateTextDO> extractTranslationsByResourceId(JsonNode shopDataJson, String resourceId, String shopName) {
        Map<String, TranslateTextDO> translations = new HashMap<>();
        JsonNode translationsNode = shopDataJson.path("translatableContent");
        if (translationsNode.isArray() && !translationsNode.isEmpty()) {
            translationsNode.forEach(translation -> {
                if (translation == null) {
                    return;
                }
                if (translation.path("value").asText(null) == null || translation.path("key").asText(null) == null) {
                    return;
                }
                //当用户修改数据后，outdated的状态为true，将该数据放入要翻译的集合中
                TranslateTextDO translateTextDO = new TranslateTextDO();
                String key = translation.path("key").asText(null);
                translateTextDO.setTextKey(key);
                translateTextDO.setSourceText(translation.path("value").asText(null));
                translateTextDO.setSourceCode(translation.path("locale").asText(null));
                translateTextDO.setDigest(translation.path("digest").asText(null));
                translateTextDO.setTextType(translation.path("type").asText(null));
                translateTextDO.setResourceId(resourceId);
                translateTextDO.setShopName(shopName);
                translations.put(key, translateTextDO);
            });
        }
        return translations;
    }

    /**
     * 获取所有的resourceId下的已翻译数据
     */
    public Map<String, TranslateTextDO> extractTranslatedDataByResourceId(JsonNode shopDataJson, Map<String, TranslateTextDO> partTranslateTextDOSet, Boolean isCover, String target, String shopName) {
        JsonNode contentNode = shopDataJson.path("translations");
        if (contentNode.isArray() && !contentNode.isEmpty() && !isCover) {
            contentNode.forEach(content -> {
                if (partTranslateTextDOSet == null) {
                    return;
                }
                String key = content.path("key").asText(null);
                String outdated = content.path("outdated").asText("null");
                if ("false".equals(outdated)) {
                    //相当于已翻译一条
                    if (partTranslateTextDOSet.containsKey(key)) {
                        redisProcessService.addProcessData(generateProcessKey(shopName, target), PROGRESS_DONE, 1L);
                    }
                    partTranslateTextDOSet.remove(key);
                }
            });
        }
        return partTranslateTextDOSet;
    }
}
