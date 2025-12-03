package com.bogdatech.logic.translate;

import com.bogdatech.entity.DO.TranslateTextDO;
import com.bogdatech.exception.ClientException;
import com.bogdatech.integration.*;
import com.bogdatech.logic.*;
import com.bogdatech.logic.redis.TranslationCounterRedisService;
import com.bogdatech.logic.redis.TranslationParametersRedisService;
import com.bogdatech.logic.token.UserTokenService;
import com.bogdatech.model.controller.request.TranslateRequest;
import com.bogdatech.utils.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import kotlin.Pair;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import static com.bogdatech.constants.TranslateConstants.*;
import static com.bogdatech.integration.ALiYunTranslateIntegration.calculateBaiLianToken;
import static com.bogdatech.integration.DeepLIntegration.DEEPL_LANGUAGE_MAP;
import static com.bogdatech.logic.RabbitMqTranslateService.AUTO;
import static com.bogdatech.logic.RabbitMqTranslateService.BATCH_SIZE;
import static com.bogdatech.utils.ApiCodeUtils.getLanguageName;
import static com.bogdatech.utils.CaseSensitiveUtils.appInsights;
import static com.bogdatech.utils.JsonUtils.isJson;
import static com.bogdatech.utils.JsoupUtils.glossaryText;
import static com.bogdatech.utils.JsoupUtils.isHtml;
import static com.bogdatech.utils.JudgeTranslateUtils.*;
import static com.bogdatech.utils.JudgeTranslateUtils.printTranslateReason;
import static com.bogdatech.utils.LiquidHtmlTranslatorUtils.*;
import static com.bogdatech.utils.LiquidHtmlTranslatorUtils.SYMBOL_PATTERN;
import static com.bogdatech.utils.PlaceholderUtils.*;
import static com.bogdatech.utils.PlaceholderUtils.getShortPrompt;
import static com.bogdatech.utils.RedisKeyUtils.PROGRESS_DONE;
import static com.bogdatech.utils.RedisKeyUtils.generateProcessKey;
import static com.bogdatech.utils.StringUtils.*;
import static java.lang.Thread.sleep;

@Component
public class TranslateDataService {
    @Autowired
    private ShopifyService shopifyService;
    @Autowired
    private RedisProcessService redisProcessService;
    @Autowired
    private TranslationParametersRedisService translationParametersRedisService;
    @Autowired
    private ALiYunTranslateIntegration aLiYunTranslateIntegration;
    @Autowired
    private DeepLIntegration deepLIntegration;
    @Autowired
    private ChatGptIntegration chatGptIntegration;
    @Autowired
    private UserTokenService userTokenService;
    @Autowired
    private TranslationCounterRedisService translationCounterRedisService;

    // glossary 内存翻译  key
    public static String GLOSSARY_CACHE_KEY = "{shopName}:{targetCode}:{sourceText}";
    public static final ConcurrentHashMap<String, String> glossaryCache = new ConcurrentHashMap<>();

    public String translateHtmlData(String sourceText, String shopName, String target, String accessToken,
                                    String languagePack, Integer limitChars, String modeType, CharacterCountUtils counter,
                                    String source, Map<String, Object> translation, String resourceId,
                                    String translationModel, String translateType) {
        appInsights.trackTrace("TranslateDataServiceLog translateHtmlData 用户： " + shopName + "，sourceText: " + sourceText);

        // 进度条 判断translateType是手动还是自动， 手动才修改进度条数据
        if (!AUTO.equals(translateType)) {
            translationParametersRedisService.hsetTranslationStatus(
                    TranslationParametersRedisService.generateProgressTranslationKey(shopName, source, target), String.valueOf(2));
            translationParametersRedisService.hsetTranslatingString(
                    TranslationParametersRedisService.generateProgressTranslationKey(shopName, source, target), sourceText);
        }

        String htmlTranslation;
        try {
            htmlTranslation = newJsonTranslateHtml(
                    sourceText,
                    new TranslateRequest(0, shopName, accessToken, source, target, sourceText),
                    counter,
                    languagePack, limitChars, false, translationModel, translateType);
            appInsights.trackTrace("TranslateDataServiceLog translateHtmlData 完成 用户： " + shopName + "，sourceText: " + sourceText +
                    " translatedText: " + htmlTranslation);
            if (modeType.equals(METAFIELD)) {
                // TODO 这里是不是不会走到了？
                // TODO 这里会走到的
                // 对翻译后的html做格式处理
                appInsights.trackTrace("html所在模块是METAFIELD 用户： " + shopName + "，sourceText: " + sourceText);
                htmlTranslation = normalizeHtml(htmlTranslation);
            }
        } catch (Exception e) {
            appInsights.trackTrace("clickTranslation " + shopName + " html translation errors : " +
                    e.getMessage() + " sourceText: " + sourceText);
            // TODO 这里存这个干什么 又没翻译
            // TODO 很早之前Allen说 翻译失败了，需要回填原文
            shopifyService.saveToShopify(sourceText, translation, resourceId, shopName, accessToken, target, API_VERSION_LAST);
            return null;
        }

        appInsights.trackTrace("存到shopify数据到数据库 用户： " + shopName + "，sourceText: " + sourceText);
        return htmlTranslation;
    }

    public String translateListSingleData(String value, String target, String languagePack, Integer limitChars,
                                          CharacterCountUtils counter, String shopName, String accessToken,
                                          String source, Map<String, Object> translation, String resourceId,
                                          String translateType) {
        appInsights.trackTrace("TranslateDataServiceLog ListSingleData 用户： " + shopName + "，sourceText: " + value);
        // 如果符合要求，则翻译，不符合要求则返回原值
        List<String> resultList = JsonUtils.jsonToObjectWithNull(value, new TypeReference<>() {
        });
        if (resultList == null || resultList.isEmpty()) {
            return value;
        }
        try {
            for (int i = 0; i < resultList.size(); i++) {
                String original = resultList.get(i);
                if (!StringUtils.isValidString(original) && original != null && !original.trim().isEmpty() && !isHtml(value)) {
                    // 走翻译流程
                    String targetCache = redisProcessService.getCacheData(target, value);
                    if (targetCache != null) {
                        resultList.set(i, targetCache);
                        continue;
                    }

                    String translated = translateByModel(
                            new TranslateRequest(0, shopName, accessToken, source, target, value),
                            counter, languagePack, limitChars, false, translateType);

                    // 对null的处理
                    if (translated == null) {
                        appInsights.trackTrace("FatalException 每日须看 translateMetafieldTextData 用户： " + shopName + " 翻译失败，翻译内容为空 value: " + value);
                        translated = checkTranslationModel(
                                new TranslateRequest(0, shopName, accessToken, source, target, value),
                                counter, languagePack, limitChars, false, translateType);
                        resultList.set(i, translated);
                        continue;
                    }
                    redisProcessService.setCacheData(target, translated, value);
                    //将数据填回去
                    resultList.set(i, translated);
                }
            }
            appInsights.trackTrace("TranslateDataServiceLog ListSingleData 成功 用户： " + shopName + "，sourceText: " + value +
                    " translatedText: " + resultList);
            return JsonUtils.objectToJson(resultList);
        } catch (Exception e) {
            //存原数据到shopify本地
            shopifyService.saveToShopify(value, translation, resourceId, shopName, accessToken, target, API_VERSION_LAST);
            appInsights.trackTrace("clickTranslation " + shopName + " LIST errors 错误原因： " + e.getMessage());
        }
        return null;
    }

    // glossary 的缓存翻译 GLOSSARY_CACHE_KEY {shopName}:{targetCode}:{sourceText}
    public static String generateGlossaryKey(String shopName, String targetCode, String sourceText) {
        if (shopName != null && targetCode != null && sourceText != null) {
            return GLOSSARY_CACHE_KEY.replace("{shopName}", shopName)
                    .replace("{targetCode}", targetCode)
                    .replace("{sourceText}", sourceText);
        }
        return "null";
    }

    public String translateGlossaryData(String value, String shopName, String languagePack, String accessToken,
                                        CharacterCountUtils counter, String source, String target,
                                        Map<String, Object> translation, String resourceId, Integer limitChars,
                                        Map<String, String> keyMap0, Map<String, String> keyMap1, String translateType) {
        appInsights.trackTrace("TranslateDataServiceLog translateGlossaryData 用户： " + shopName + "，sourceText: " + value);

        TranslateRequest translateRequest = new TranslateRequest(0, shopName, accessToken, source, target, value);

        String targetText;
        // 判断是否为HTML
        if (isHtml(value)) {
            try {
                targetText = translateGlossaryHtml(value, translateRequest, counter, null, keyMap0
                        , keyMap1, languagePack, limitChars, false, translateType);
                targetText = isHtmlEntity(targetText);
            } catch (Exception e) {
                appInsights.trackTrace("FatalException translateGlossaryData is html failed " + shopName + " glossaryTranslationModel finalText is null " + " sourceText: " + value);
                shopifyService.saveToShopify(value, translation, resourceId, shopName, accessToken, target, API_VERSION_LAST);
                return null;
            }

            appInsights.trackTrace("TranslateDataServiceLog translateGlossaryData isHtml 成功 用户： " + shopName +
                    "，sourceText: " + value + " translatedText: " + targetText);
            return targetText;
        }

        String finalText = null;

        // 其他数据类型，对数据做处理再翻译
        try {
            // glossary缓存翻译
            String glossaryData = glossaryCache.get(generateGlossaryKey(shopName, target, value));

            if (glossaryData != null) {
                return glossaryData;
            }

            // 用大模型翻译
            String glossaryString = glossaryText(keyMap1, keyMap0, value);
            if (!AUTO.equals(translateType)) {
                translationParametersRedisService.hsetTranslationStatus(TranslationParametersRedisService.generateProgressTranslationKey(shopName, source, target), String.valueOf(2));
                translationParametersRedisService.hsetTranslatingString(TranslationParametersRedisService.generateProgressTranslationKey(shopName, source, target), value);
            }

            // 根据关键词生成对应的提示词
            finalText = glossaryTranslationModel(translateRequest, counter, glossaryString, languagePack, limitChars, false, translateType);

            // 对null的处理， 不翻译，看下打印情况
            if (finalText == null) {
                appInsights.trackTrace("FatalException 每日须看 clickTranslation " + shopName + " glossaryTranslationModel finalText is null " + " sourceText: " + value);
                return null;
            }

            // 将翻译结果存入缓存
            glossaryCache.put(generateGlossaryKey(shopName, target, value), finalText);
        } catch (Exception e) {
            appInsights.trackTrace("clickTranslation " + shopName + " glossaryTranslationModel errors " + e + " sourceText: " + value);
            shopifyService.saveToShopify(value, translation, resourceId, shopName, accessToken, target, API_VERSION_LAST);
        }
        appInsights.trackTrace("TranslateDataServiceLog translateGlossaryData notHtml 成功 用户： " + shopName +
                "，sourceText: " + value + " translatedText: " + finalText);
        return finalText;
    }

    public Map<String, String> translatePlainText(List<String> untranslatedTexts, String source, String target,
                                                  String languagePack, String modelType, String translationModel,
                                                  CharacterCountUtils counter, String shopName, Integer limitChars,
                                                  String translationKeyType, String translateType) {
        if (untranslatedTexts.isEmpty()) {
            return new HashMap<>();
        }

        // 1. 建立「原文 → 原索引列表」映射（去重）
        LinkedHashMap<String, List<String>> duplicateIndexMap = new LinkedHashMap<>();
        for (int i = 0; i < untranslatedTexts.size(); i++) {
            String text = untranslatedTexts.get(i);
            duplicateIndexMap.computeIfAbsent(text, k -> new ArrayList<>()).add(String.valueOf(i));
        }

        // 2. 建立唯一文本 map（编号 → 唯一原文）
        LinkedHashMap<String, String> uniqueTextMap = new LinkedHashMap<>();
        int uniqIndex = 1;
        for (String uniqueText : duplicateIndexMap.keySet()) {
            uniqueTextMap.put(String.valueOf(uniqIndex++), uniqueText);
        }

        // 最终翻译结果（编号 → 译文）
        LinkedHashMap<String, String> translatedUniqueMap = new LinkedHashMap<>();

        // 4. 按新提示词翻译
        String prompt = PlaceholderUtils.getNewestPrompt(getLanguageName(target), JsonUtils.objectToJson(uniqueTextMap));
        String translatedJson = translateByCiwiOrGptModel(target, null,
                shopName, source, counter, limitChars, prompt,
                false, translationModel, translateType);

        // 如果主翻译服务 translateBatch 返回 null，则使用阿里云翻译服务作为备用
        if (translatedJson == null) {
            translatedJson = aLiYunTranslateIntegration.userTranslate(null, prompt, counter, target, shopName, limitChars, false, translateType);
        }

        if (translatedJson == null) {
            appInsights.trackTrace("FatalException TranslateDataServiceLog translatePlainTextData 用户： " + shopName +
                    " 翻译失败，map为空 untranslatedTexts: " + untranslatedTexts + " 返回值: " + translatedJson);
            return new HashMap<>();
        }

        // 解析数据
        LinkedHashMap<String, String> resultMap = StringUtils.parseOutputTransaction(translatedJson);

        if (resultMap == null || resultMap.isEmpty()) {
            appInsights.trackTrace("FatalException TranslateDataServiceLog translatePlainTextData 用户： " + shopName +
                    " 翻译失败，map为空 untranslatedTexts: " + untranslatedTexts + " 返回值: " + translatedJson);
            return new HashMap<>();
        }

        // 还原为完整的 key→译文 map（保持原始 key 数量）
        for (Map.Entry<String, List<String>> entry : duplicateIndexMap.entrySet()) {
            String text = entry.getKey();

            // 找到对应翻译
            String translateds = null;
            for (Map.Entry<String, String> transEntry : resultMap.entrySet()) {
                String uniqKey = transEntry.getKey();
                if (uniqueTextMap.get(uniqKey).equals(text)) {
                    translateds = transEntry.getValue();
                    break;
                }
            }

            if (translateds == null) {
                translateds = text;
            }

            translatedUniqueMap.put(text, translateds);
        }

        return translatedUniqueMap;
    }

    /**
     * 翻译词汇表单行文本，保护变量、URL和符号
     */
    private String translateSingleLineWithProtection(String text, TranslateRequest request, CharacterCountUtils counter,
                                                     Map<String, String> keyMap1, Map<String, String> keyMap0
            , String resourceType, String languagePackId, Integer limitChars, boolean isSingleFlag, String translateType) {
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
                counter.addChars(CalculateTokenUtils.googleCalculateToken(cleanedText));
                String targetString = translateAndCount(request, counter, languagePackId, GENERAL
                        , limitChars, isSingleFlag, translateType);
                redisProcessService.setCacheData(request.getTarget(), targetString, cleanedText);
                return targetString;
            } else {
                //如果字符数大于100字符，用大模型翻译
                String glossaryString = glossaryText(keyMap1, keyMap0, cleanedText);
                //根据关键词生成对应的提示词
                String finalText = glossaryTranslationModel(request, counter, glossaryString, languagePackId, limitChars, isSingleFlag, translateType);
                redisProcessService.setCacheData(request.getTarget(), finalText, cleanedText);
                return finalText;
            }

        });

        return translatedText;
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

    /**
     * 调用多模型翻译：1，5字符以内用model翻译和qwen翻译。2，ar用HUN_YUAN_MODEL翻译 3，hi用doubao-1.5-pro-256k翻译
     * 根据语言代码切换API翻译
     *
     * @param request        翻译所需要的数据
     * @param counter        计数器
     * @param languagePackId 语言包id
     *                       return String       翻译后的文本
     */
    public String translateByModel(TranslateRequest request, CharacterCountUtils counter, String languagePackId
            , Integer limitChars, boolean isSingleFlag, String translateType) {
        String sourceText = request.getContent();

        //从缓存中获取数据
        //判断是否符合mt翻译 ，是， 调用mt翻译。
        if (sourceText.length() <= 20) {
            return checkTranslationApi(request, counter, limitChars, null, isSingleFlag, translateType);
        }

        return checkTranslationModel(request, counter, languagePackId, limitChars, isSingleFlag, translateType);
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
    public String checkTranslationModel(TranslateRequest request, CharacterCountUtils counter, String languagePackId, Integer limitChars, boolean isSingleFlag, String translateType) {
        String target = request.getTarget();
        String targetLanguage = getLanguageName(target);
        String content = request.getContent();
        String shopName = request.getShopName();
        String prompt;

        // 判断target里面是否含有变量，如果没有，输入极简提示词；如果有，输入变量提示词
        if (hasPlaceholders(content)) {
            return translateVariableData(request, counter, limitChars, targetLanguage, languagePackId, isSingleFlag, translateType);

        } else {
            prompt = getSimplePrompt(targetLanguage, languagePackId);
            appInsights.trackTrace("clickTranslation " + shopName + " 普通文本：" + content + " Simple提示词: " + prompt);
        }
        try {
            // 对模型进行判断 , 1,ciwi 2,openai 3,deepL
            return translateByCiwiModel(request, counter, limitChars, prompt, isSingleFlag, translateType);
        } catch (Exception e) {
            appInsights.trackException(e);
            appInsights.trackTrace("clickTranslation " + shopName + " checkTranslationModel errors ： " + e.getMessage() + " sourceText: " + content);
            return aLiYunTranslateIntegration.singleTranslate(content, prompt, counter, target, shopName, limitChars, isSingleFlag, translateType);
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
    public String glossaryTranslationModel(TranslateRequest request, CharacterCountUtils counter, String glossaryString
            , String languagePackId, Integer limitChars, boolean isSingleFlag, String translateType) {

        String target = request.getTarget();
        String content = request.getContent();
        String targetName = getLanguageName(request.getTarget());
        String shopName = request.getShopName();
        String prompt;
        if (glossaryString != null) {
            prompt = PlaceholderUtils.getNewestGlossaryPrompt(targetName, glossaryString, content);
            appInsights.trackTrace("clickTranslation " + shopName + " 普通文本： " + content + " Glossary提示词: " + prompt);
        } else {
            prompt = getSimplePrompt(targetName, languagePackId);
            appInsights.trackTrace("clickTranslation " + shopName + " 普通文本：" + content + " Simple提示词: " + prompt);
        }

        try {
            // 判断是否存在词汇表数据 选择不同的模型翻译
            if (glossaryString != null) {
                return translateByCiwiOrGptModel(target, null, shopName, null, counter, limitChars, prompt
                        , isSingleFlag, "1", translateType);
            } else {
                return translateByCiwiModel(request, counter, limitChars, prompt, isSingleFlag, translateType);
            }

        } catch (Exception e) {
            appInsights.trackTrace("FatalException clickTranslation " + shopName + " glossaryTranslationModel errors ： " + e.getMessage() + " sourceText: " + content + " prompt : " + prompt);
            if (glossaryString != null) {
                return translateByCiwiOrGptModel(target, null, shopName, null, counter, limitChars, prompt
                        , isSingleFlag, "1", translateType);
            } else {
                return aLiYunTranslateIntegration.singleTranslate(content, prompt, counter, target, shopName, limitChars, isSingleFlag, translateType);
            }
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
    public String checkTranslationApi(TranslateRequest request, CharacterCountUtils counter, Integer limitChars
            , String translationModel, boolean isSingleFlag, String translateType) {
        String target = request.getTarget();
        String source = request.getSource();
        String content = request.getContent();
        //如果source和target都是QwenMT支持的语言，则调用QwenMT的API。 反之亦然
        //目前做个初步的限制，每次用mt翻译前都sleep一下，防止调用频率过高。0.3s. 后面请求解决限制后，删掉这段代码。
        if (hasPlaceholders(content)) {
            return translateVariableData(request, counter, limitChars, target, null, isSingleFlag, translateType);
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
                resultTranslation = deepLIntegration.translateByDeepL(content, target, counter, request.getShopName()
                        , limitChars, isSingleFlag, translateType);
            } else if (JsoupUtils.QWEN_MT_CODES.contains(target) && JsoupUtils.QWEN_MT_CODES.contains(source)) {
                resultTranslation = translateByQwenMt(request.getContent(), source, target, counter
                        , request.getShopName(), limitChars, isSingleFlag, translateType);
            } else {
                //qwen 短文本翻译
                appInsights.trackTrace("clickTranslation " + request.getShopName() + " 短文本翻译： " + request.getContent() + " 提示词: " + prompt);
                resultTranslation = aLiYunTranslateIntegration.singleTranslate(request.getContent(), prompt, counter
                        , target, request.getShopName(), limitChars, isSingleFlag, translateType);
            }
            return resultTranslation;

        } catch (Exception e) {
            //mt翻译失败的话，用其他大模型翻译
            appInsights.trackException(e);
            appInsights.trackTrace("clickTranslation " + request.getShopName() + " 短文本翻译 errors : " + e.getMessage() + " sourceText: " + content);
            resultTranslation = deepLIntegration.translateByDeepL(content, target, counter, request.getShopName()
                    , limitChars, isSingleFlag, translateType);
            return resultTranslation;
        }
    }

    //包装一下调用百炼mt的方法
    public String translateByQwenMt(String translateText, String source, String target, CharacterCountUtils countUtils
            , String shopName, Integer limitChars, boolean isSingleFlag, String translateType) {
        String changeSource = ApiCodeUtils.qwenMtCode(source);
        String changeTarget = ApiCodeUtils.qwenMtCode(target);
        try {
            return aLiYunTranslateIntegration.callWithMessageMT(QWEN_MT, translateText, changeSource, changeTarget
                    , countUtils, shopName, limitChars, isSingleFlag, translateType);
        } catch (Exception e) {
            try {
                sleep(1000);
            } catch (InterruptedException ex) {
                appInsights.trackTrace("clickTranslation MT sleep errors ： " + ex.getMessage());
            }
            return aLiYunTranslateIntegration.callWithMessageMT(QWEN_MT, translateText, changeSource
                    , changeTarget, countUtils, shopName, limitChars, isSingleFlag, translateType);
        }

    }


    /**
     * 在调用大模型的基础上添加计数功能,并添加到翻译后的文本
     *
     * @param request        翻译所需要的数据
     * @param counter        计数器
     * @param languagePackId 语言包id
     * @param handleData     handle相关数据
     *                       return String 翻译后的文本
     */
    public String translateAndCount(TranslateRequest request,
                                    CharacterCountUtils counter, String languagePackId, String handleData
            , Integer limitChars, boolean isSingleFlag, String translateType) {
        String text = request.getContent();
        // 检测text是不是全大写，如果是的话，最后翻译完也全大写

        String targetString;
        if (handleData.equals(HANDLE)) {
            if (text.length() <= 20) {
                targetString = checkTranslationApi(request, counter, limitChars, null, isSingleFlag, translateType);
            } else {
                targetString = translationHandle(request, counter, languagePackId, limitChars, isSingleFlag, translateType);
            }
        } else {
            targetString = translateByModel(request, counter, languagePackId, limitChars, isSingleFlag, translateType);
        }

        if (targetString == null) {
            // 对null的处理，再次翻译
            return checkTranslationModel(request, counter, languagePackId, limitChars, isSingleFlag, translateType);
        }

        targetString = isHtmlEntity(targetString);

        // 判断translateType是不是handle，再决定是否添加到缓存
        if (!handleData.equals(HANDLE)) {
            redisProcessService.setCacheData(request.getTarget(), targetString, text);
        }

        return targetString;
    }

    public String translateGlossaryHtml(String html, TranslateRequest request, CharacterCountUtils counter
            , String resourceType, Map<String, String> keyMap0, Map<String, String> keyMap1, String languagePackId
            , Integer limitChars, boolean isSingleFlag, String translateType) {
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

                // 获取 <html> 元素并修改 lang 属性
                Element htmlTag = doc.selectFirst("html");
                if (htmlTag != null) {
                    htmlTag.attr("lang", request.getTarget());
                }

                processNode(doc.body(), request, counter, resourceType, keyMap0, keyMap1, languagePackId, limitChars
                        , isSingleFlag, translateType);
                return doc.outerHtml();
            } else {
                // 如果没有 <html> 标签，作为片段处理
                Document doc = Jsoup.parseBodyFragment(html);
                Element body = doc.body();

                processNode(body, request, counter, resourceType, keyMap0, keyMap1, languagePackId, limitChars
                        , isSingleFlag, translateType);

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
    private void processNode(Node node, TranslateRequest request, CharacterCountUtils counter, String resourceType
            , Map<String, String> keyMap0, Map<String, String> keyMap1, String languagePackId, Integer limitChars
            , boolean isSingleFlag, String translateType) {
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
                    processNode(child, request, counter, resourceType, keyMap0, keyMap1, languagePackId, limitChars
                            , isSingleFlag, translateType);
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
                String translatedText = translateTextWithProtection(text, request, counter, resourceType, keyMap0
                        , keyMap1, languagePackId, limitChars, isSingleFlag, translateType);
                textNode.text(translatedText);
            }
        } catch (Exception e) {
            appInsights.trackTrace("FatalException 递归处理节点报错 errors ： " + e.getMessage());
        }
    }


    /**
     * 处理文本内容，保护变量和URL
     *
     * @param text 输入文本
     * @return 翻译后的文本
     */
    private String translateTextWithProtection(String text, TranslateRequest request, CharacterCountUtils counter
            , String resourceType, Map<String, String> keyMap0, Map<String, String> keyMap1, String languagePackId
            , Integer limitChars, boolean isSingleFlag, String translateType) {
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
                        targetString = translateSingleLineWithProtection(text, request, counter, keyMap1, keyMap0
                                , resourceType, languagePackId, limitChars, isSingleFlag, translateType);
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
                    targetString = translateSingleLineWithProtection(text, request, counter, keyMap1, keyMap0
                            , resourceType, languagePackId, limitChars, isSingleFlag, translateType);
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
    public String translationHandle(TranslateRequest request, CharacterCountUtils counter, String languagePackId
            , Integer limitChars, boolean isSingleFlag, String translateType) {

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
            return translateByCiwiModel(request, counter, limitChars, languagePackId, isSingleFlag, translateType);
        } catch (Exception e) {
            appInsights.trackTrace("翻译handle数据报错 errors ： " + e.getMessage() + " sourceText: " + content);
            appInsights.trackException(e);
            return aLiYunTranslateIntegration.singleTranslate(fixContent, prompt, counter, target, shopName
                    , limitChars, isSingleFlag, translateType);
        }
    }

    /**
     * 变量翻译,目前暂定ciwi模型翻译
     */
    public String translateVariableData(TranslateRequest request, CharacterCountUtils counter, Integer limitChars
            , String targetLanguage, String languagePackId, boolean isSingleFlag, String translateType) {
        String target = request.getTarget();
        String content = request.getContent();
        String shopName = request.getShopName();
        String variableString = getOuterString(content);
        String prompt = getVariablePrompt(targetLanguage, variableString, languagePackId);
        appInsights.trackTrace("模块文本： " + content + " variable提示词: " + prompt);
        return aLiYunTranslateIntegration.singleTranslate(content, prompt, counter, target, shopName, limitChars
                , isSingleFlag, translateType);
    }

    /**
     * ciwi 模型翻译   多 System User 翻译
     */
    public String translateByCiwiModel(TranslateRequest request, CharacterCountUtils counter, Integer limitChars
            , String prompt, boolean isSingleFlag, String translateType) {
        String target = request.getTarget();
        String content = request.getContent();
        String shopName = request.getShopName();

        // 目标语言是中文的，用qwen-max翻译
        return aLiYunTranslateIntegration.singleTranslate(content, prompt, counter, target, shopName, limitChars
                , isSingleFlag, translateType);
    }

    /**
     * ciwi 单 User 翻译
     * gpt 翻译
     */
    public String translateByCiwiOrGptModel(String target, String content, String shopName, String source,
                                            CharacterCountUtils counter, Integer limitChars, String prompt,
                                            boolean isSingleFlag, String translationModel, String translateType) {
        Pair<String, Integer> pair;
        if ("2".equals(translationModel)) {
            pair = chatGptIntegration.chatWithGpt(prompt, content, shopName, target);
        } else {
            pair = aLiYunTranslateIntegration.userTranslate(content, prompt, target, shopName);
        }

        if (isSingleFlag) {
            userTokenService.addUsedToken(shopName, pair.getSecond());
        } else {
            userTokenService.addUsedToken(shopName, pair.getSecond());
            translationCounterRedisService.increaseLanguage(shopName, target, pair.getSecond(), translateType);
        }
        counter.addChars(pair.getSecond());
        return pair.getFirst();
    }

    /**
     * 遍历needTranslatedSet, 对Set集合进行通用规则的筛选，返回筛选后的数据
     */
    public Set<TranslateTextDO> filterNeedTranslateSet(String modeType, boolean handleFlag, Set<TranslateTextDO> needTranslateSet, String shopName, String target, String accessToken) {
        Iterator<TranslateTextDO> iterator = needTranslateSet.iterator();
        while (iterator.hasNext()) {
            TranslateTextDO translateTextDO = iterator.next();
            String value = translateTextDO.getSourceText();

            // 当 value 为空时跳过
            if (!isValueBlank(value)) {
                iterator.remove(); //  安全删除

                // 还要删除这条语言
//                ShopifyHttpIntegration.deleteTranslateData(shopName, accessToken, translateTextDO.getResourceId(), target, translateTextDO.getTextKey());
//                appInsights.trackTrace("filterNeedTranslateSet 用户： " + shopName + " token: " + accessToken + " 删除这条语言: " + translateTextDO.getResourceId() + " key: " + translateTextDO.getTextKey() + " target: " + target);
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

                if (key.contains("block") && key.contains("add_button_selector")) {
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
                Map<String, TranslateTextDO> partTranslateTextDOMap = JsoupUtils.extractTranslationsByResourceId(shopDataJson, resourceId, shopName);
                Map<String, TranslateTextDO> partTranslatedTextDOMap = extractTranslatedDataByResourceId(shopDataJson, partTranslateTextDOMap, isCover, target, shopName);
                Set<TranslateTextDO> needTranslatedSet = new HashSet<>(partTranslatedTextDOMap.values());
                return new HashSet<>(needTranslatedSet);
            }
        }
        return new HashSet<>();
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

    /**
     * html的拆分翻译，放弃递归，改为json翻译
     */
    public String newJsonTranslateHtml(String html, TranslateRequest request, CharacterCountUtils counter,
                                       String languagePackId, Integer limitChars, boolean isSingleFlag
            , String translationModel, String translateType) {
        if (!isHtml(html)) {
            return null;
        }

        html = isHtmlEntity(html); //判断是否含有HTML实体,然后解码

        // 1, 解析html，根据html标签，选择不同的解析方式， 将prettyPrint设置为false
        boolean hasHtmlTag = HTML_TAG_PATTERN.matcher(html).find();
        Document doc = parseHtml(html, request.getTarget(), hasHtmlTag);

        // 2. 收集所有 TextNode
        List<TextNode> nodes = new ArrayList<>();
        for (Element element : doc.getAllElements()) {
            nodes.addAll(element.textNodes());
        }

        // 3. 提取要翻译文本并生成映射
        LinkedHashMap<String, String> originalTextMap = new LinkedHashMap<>();
        LinkedHashMap<String, TextNode> nodeMap = new LinkedHashMap<>();
        int index = 1;

        for (TextNode node : nodes) {
            String text = node.text().trim();
            if (!text.isEmpty()) {
                String key = String.valueOf(index++);
                originalTextMap.put(key, text);
                nodeMap.put(key, node);
            }
        }

        // 4. 调用 Map 版本翻译方法（替换原来的 List 版本）
        Map<String, String> translatedMap = translateAllMap(originalTextMap, request, counter,
                languagePackId, limitChars, isSingleFlag, translationModel, translateType);

        // 5. 填回原处
        fillBackTranslatedDataMap(nodeMap, translatedMap, request.getTarget(), request.getShopName(), originalTextMap);

        // 输出翻译后的 HTML
        if (hasHtmlTag) {
            String results = doc.outerHtml(); // 返回完整的HTML结构
            results = isHtmlEntity(results);
            return results;
        } else {
            Element body = doc.body();
            // 只返回子节点内容，不包含 <body>
            StringBuilder results = new StringBuilder();
            for (Node child : body.childNodes()) {
                if (child != null) {
                    String childHtml = child.outerHtml(); // 或 child.toString()
                    results.append(childHtml);
                }
            }
            String output2 = results.toString();
            output2 = isHtmlEntity(output2);
            return output2;
        }
    }

    public Map<String, String> translateAllMap(LinkedHashMap<String, String> originalTextMap,
                                               TranslateRequest request,
                                               CharacterCountUtils counter,
                                               String languagePack,
                                               Integer limitChars,
                                               boolean isSingleFlag,
                                               String translationModel,
                                               String translateType) {

        String target = request.getTarget();
        String shopName = request.getShopName();
        String source = request.getSource();

        // 建立「原文 → keyList」映射，方便后续还原
        LinkedHashMap<String, List<String>> duplicateKeyMap = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : originalTextMap.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            duplicateKeyMap.computeIfAbsent(value, k -> new ArrayList<>()).add(key);
        }

        // 生成唯一原文 map
        LinkedHashMap<String, String> uniqueTextMap = new LinkedHashMap<>();
        int uniqIndex = 1;
        for (String uniqueText : duplicateKeyMap.keySet()) {
            uniqueTextMap.put(String.valueOf(uniqIndex++), uniqueText);
        }

        // 翻译唯一文本
        LinkedHashMap<String, String> translatedUniqueMap = new LinkedHashMap<>();

        // 定义已翻译的数据
        Map<String, String> translatedFullMap = new LinkedHashMap<>();

        // 先缓存翻译一次
        LinkedHashMap<String, String> remainingUniqueMap = cacheAndDbTranslateData(uniqueTextMap, target, translatedUniqueMap, shopName);

        List<Map.Entry<String, String>> entries = new ArrayList<>(remainingUniqueMap.entrySet());
        for (int i = 0; i < entries.size(); i += BATCH_SIZE) {
            if (entries.isEmpty()) {
                break;
            }
            int endIndex = Math.min(i + BATCH_SIZE, entries.size());
            List<Map.Entry<String, String>> batch = entries.subList(i, endIndex);

            // 将 batch 转成 Map<String, String> 以便 processMapBatch 处理
            Map<String, String> batchMap = new LinkedHashMap<>();
            for (Map.Entry<String, String> entry : batch) {
                batchMap.put(entry.getKey(), entry.getValue());
            }

            // 按 token 限制切分
            LinkedHashMap<String, String> currentGroup = new LinkedHashMap<>();
            int currentTokens = 0;

            for (Map.Entry<String, String> entry : batchMap.entrySet()) {
                String text = entry.getValue();
                int tokens = calculateBaiLianToken(text);
                if (currentTokens + tokens > 1000 && !currentGroup.isEmpty()) {
                    processMapBatch(currentGroup, request.getShopName(), shopName, target, source, counter, limitChars,
                            translatedUniqueMap, isSingleFlag, translationModel, translateType);
                    currentGroup = new LinkedHashMap<>();
                    currentTokens = 0;
                }
                currentGroup.put(entry.getKey(), text);
                currentTokens += tokens;
            }

            // 处理最后一组
            if (!currentGroup.isEmpty()) {
                processMapBatch(currentGroup, request.getShopName(), shopName, target, source, counter, limitChars,
                        translatedUniqueMap, isSingleFlag, translationModel, translateType);
            }
        }

        // 还原为完整的 key→译文 map（保持原始 key 数量）
        for (Map.Entry<String, List<String>> entry : duplicateKeyMap.entrySet()) {
            String original = entry.getKey();

            // 在唯一翻译结果中找到译文
            String translated = null;
            for (Map.Entry<String, String> transEntry : translatedUniqueMap.entrySet()) {
                if (transEntry.getValue() != null && transEntry.getKey() != null
                        && uniqueTextMap.get(transEntry.getKey()).equals(original)) {
                    translated = transEntry.getValue();
                    break;
                }
            }

            if (translated == null) {
                translated = original; // 未翻译则原样保留
            }

            // 把同样原文的所有 key 都填充上相同译文
            for (String key : entry.getValue()) {
                translatedFullMap.put(key, translated);
            }
        }

        appInsights.trackTrace("翻译结果还原完成 用户：" + shopName +
                " 原始条数: " + originalTextMap.size() +
                " 翻译后条数: " + translatedFullMap.size());

        return translatedFullMap;
    }


    private void processMapBatch(LinkedHashMap<String, String> batch,
                                 String requestShopName,
                                 String shopName,
                                 String target,
                                 String source,
                                 CharacterCountUtils counter,
                                 Integer limitChars,
                                 LinkedHashMap<String, String> translatedUniqueMap,
                                 boolean isSingleFlag,
                                 String translationModel,
                                 String translateType) {
        String prompt = PlaceholderUtils.getNewestPrompt(ApiCodeUtils.getLanguageName(target), JsonUtils.objectToJson(batch));
        appInsights.trackTrace("translateAllMap 用户： " + shopName + " 翻译类型 : HTML 提示词 : " + prompt + " 待翻译文本 : " + batch.size() + "条");

        LinkedHashMap<String, String> tempResult = processBatch(batch, requestShopName, shopName,
                prompt, target, source, counter, limitChars, translatedUniqueMap,
                isSingleFlag, translationModel, translateType);

        // 将结果放回到原 key

        for (Map.Entry<String, String> entry : batch.entrySet()) {
            String key = entry.getKey();
            String original = entry.getValue();
            String translated = tempResult.getOrDefault(key, original);
            translatedUniqueMap.put(key, translated);
        }
    }

    /**
     * 对拆分完的一批次进行翻译
     */
    private LinkedHashMap<String, String> processBatch(LinkedHashMap<String, String> texts, String requestShopName, String shopName, String prompt, String target
            , String source, CharacterCountUtils counter, Integer limitChars, LinkedHashMap<String, String> translatedUniqueMap
            , boolean isSingleFlag, String translationModel, String translateType) {
        try {
            String translated = translateByCiwiOrGptModel(target, null, shopName, source, counter,
                    limitChars, prompt, isSingleFlag, translationModel, translateType);
            if (translated == null) {
                translated = aLiYunTranslateIntegration.userTranslate(null, prompt, counter, target, shopName
                        , limitChars, isSingleFlag, translateType);
            }

            LinkedHashMap<String, String> resultMap = StringUtils.parseOutputTransaction(translated);
            translatedUniqueMap.putAll(resultMap);

        } catch (Exception e) {
            appInsights.trackException(e);
            appInsights.trackTrace("translateAllList 用户： " + shopName + " 翻译类型 : HTML 提示词 : " + prompt + " 未翻译文本 : " + texts);
        }
        return translatedUniqueMap;
    }

    /**
     * 用缓存和db，翻译List<String>类型的数据
     */
    public LinkedHashMap<String, String> cacheAndDbTranslateData(LinkedHashMap<String, String> uniqueTextMap, String target, Map<String, String> translatedUniqueMap, String shopName) {
        // 1) 备份一份 originalUniqueMap（保留 key->原文，用于后续还原）
        LinkedHashMap<String, String> remainingUniqueMap = new LinkedHashMap<>(uniqueTextMap);

        Iterator<Map.Entry<String, String>> iterator = remainingUniqueMap.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, String> entry = iterator.next();
            String uniqKey = entry.getKey();
            String sourceText = entry.getValue();

            // 先查 Redis 缓存
            String cacheData = redisProcessService.getCacheData(target, sourceText);
            if (cacheData != null) {
                // 命中缓存，加入已翻译map
                translatedUniqueMap.put(uniqKey, cacheData);

                // 翻译成功后删除这条数据，避免重复翻译
                iterator.remove();
            }
        }

        return remainingUniqueMap;
    }

    /**
     * 将翻译后的数据填回原处
     */
    private void fillBackTranslatedDataMap(Map<String, TextNode> nodeMap, Map<String, String> translatedMap,
                                           String targetLang,
                                           String shopName, LinkedHashMap<String, String> originalTextMap) {

        Pattern leadingPattern = Pattern.compile("^(\\p{Zs}+)");
        Pattern trailingPattern = Pattern.compile("(\\p{Zs}+)$");

        for (Map.Entry<String, TextNode> entry : nodeMap.entrySet()) {
            String key = entry.getKey();
            TextNode node = entry.getValue();
            String text = node.getWholeText();

            if (text.isEmpty()) {
                continue;
            }

            // 提取前导空格
            Matcher leadingMatcher = leadingPattern.matcher(text);
            String leading = leadingMatcher.find() ? leadingMatcher.group(1) : "";

            // 提取尾随空格
            Matcher trailingMatcher = trailingPattern.matcher(text);
            String trailing = trailingMatcher.find() ? trailingMatcher.group(1) : "";

            // 去掉空格，得到核心文本
            int begin = leading.length();
            int end = text.length() - trailing.length();
            String core = (begin >= end) ? "" : text.substring(begin, end);

            // 查找翻译
            String translated = translatedMap.get(key);

            // 添加到缓存里面
            redisProcessService.setCacheData(targetLang, translated, originalTextMap.get(key));

            // 拼回空格
            if (translated != null && !translated.equals(core)) {
                translated = leading + translated + trailing;
                node.text(translated);

                appInsights.trackTrace("节点翻译替换成功（保留空格） 用户：" + shopName +
                        " key：" + key +
                        " 原文：" + text +
                        " 翻译：" + translated);
            } else {
                // 没翻译或无变化，保留原样
                node.text(text);
                appInsights.trackTrace("FatalException 节点翻译替换失败 用户：" + shopName + " key：" + key + " 原文：" + text + " 翻译：" + translated);
            }
        }
    }
}
