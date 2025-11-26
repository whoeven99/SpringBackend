package com.bogdatech.logic.translate;

import com.bogdatech.entity.DO.GlossaryDO;
import com.bogdatech.integration.ALiYunTranslateIntegration;
import com.bogdatech.logic.RedisProcessService;
import com.bogdatech.utils.JsonUtils;
import com.bogdatech.utils.JsoupUtils;
import com.bogdatech.utils.PlaceholderUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.microsoft.applicationinsights.TelemetryClient;
import kotlin.Pair;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.bogdatech.constants.TranslateConstants.HTML;
import static com.bogdatech.constants.TranslateConstants.URI;
import static com.bogdatech.utils.LiquidHtmlTranslatorUtils.*;
import static com.bogdatech.utils.PlaceholderUtils.*;

@Component
public class TranslateRulesService {
    @Autowired
    private ALiYunTranslateIntegration aLiYunTranslateIntegration;
    @Autowired
    private RedisProcessService redisProcessService;
    public static TelemetryClient appInsights = new TelemetryClient();

    public Pair<String, Integer> translate(String value, String target,
                                           String textType, String key,
                                           Map<String, GlossaryDO> glossaryMap) {
        // handle的处理
        if (URI.equals(textType) && "handle".equals(key)) {
            String prompt;
            if (value.length() <= 20) {
                if (PlaceholderUtils.hasPlaceholders(value)) {
                    String variableString = getOuterString(value);
                    prompt = getVariablePrompt(target, variableString, null);
                } else {
                    prompt = PlaceholderUtils.getShortPrompt(value);
                }
            } else {
                String fixContent = com.bogdatech.utils.StringUtils.replaceHyphensWithSpaces(value);
                prompt = PlaceholderUtils.getHandlePrompt(target);
                prompt += "The text is: " + fixContent;
            }
            // 不用cache
            return aLiYunTranslateIntegration.userTranslate(prompt, target);
        }

        // html的处理
        if (HTML.equals(textType) || JsoupUtils.isHtml(value)) {
            value = isHtmlEntity(value); //判断是否含有HTML实体,然后解码

            // 1, 解析html，根据html标签，选择不同的解析方式， 将prettyPrint设置为false
            boolean hasHtmlTag = HTML_TAG_PATTERN.matcher(value).find();
            Document doc = parseHtml(value, target, hasHtmlTag);

            // 2. 收集所有 TextNode
            List<TextNode> nodes = new ArrayList<>();
            for (Element element : doc.getAllElements()) {
                nodes.addAll(element.textNodes());
            }

            // 3. 提取要翻译文本并生成映射
            LinkedHashMap<Integer, String> originalTextMap = new LinkedHashMap<>();
            LinkedHashMap<Integer, TextNode> nodeMap = new LinkedHashMap<>();
            int index = 0;

            for (TextNode node : nodes) {
                String text = node.text().trim();
                if (!text.isEmpty()) {
                    originalTextMap.put(index, text);
                    nodeMap.put(index, node);
                    index ++;
                }
            }
            // 开始翻译
            Pair<Map<Integer, String>, Integer> translatedValueMapPair = translateBatch(originalTextMap, target,
                    glossaryMap);
            Map<Integer, String> translatedMap = translatedValueMapPair.getFirst();

            // 5. 填回原处
            fillBackTranslatedDataMap(nodeMap, translatedMap, target, originalTextMap);

            String replacedBackString;
            // 输出翻译后的 HTML
            if (hasHtmlTag) {
                String results = doc.outerHtml(); // 返回完整的HTML结构
                replacedBackString = isHtmlEntity(results);
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
                replacedBackString = isHtmlEntity(output2);
            }

            return new Pair<>(replacedBackString, translatedValueMapPair.getSecond());
        }

        // 普通文本，有变量的处理
        if (hasPlaceholders(value)) {
            String variableString = getOuterString(value);
            String prompt = getVariablePrompt(target, variableString, null);
            prompt += "The text is: " + value;

            // cache
            return aLiYunTranslateIntegration.userTranslate(prompt, target);
        }

        String prompt =  PlaceholderUtils.getSimplePrompt(target, null);
        prompt += "The text is: " + value;
        // cache
        return aLiYunTranslateIntegration.userTranslate(prompt, target);
    }

    private void fillBackTranslatedDataMap(Map<Integer, TextNode> nodeMap,
                                           Map<Integer, String> translatedMap,
                                           String targetLang,
                                           Map<Integer, String> originalTextMap) {

        Pattern leadingPattern = Pattern.compile("^(\\p{Zs}+)");
        Pattern trailingPattern = Pattern.compile("(\\p{Zs}+)$");

        for (Map.Entry<Integer, TextNode> entry : nodeMap.entrySet()) {
            Integer key = entry.getKey();
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

            } else {
                // 没翻译或无变化，保留原样
                node.text(text);
            }
        }
    }

    public Pair<Map<Integer, String>, Integer> translateBatch(Map<Integer, String> idToSourceValueMap, String target) {
        // 3.1 过缓存
        Map<Integer, String> cachedMap = new HashMap<>();
        Map<Integer, String> uncachedMap = new HashMap<>();
        this.getCached(idToSourceValueMap, cachedMap, uncachedMap, target);

        // 3.2 调用翻译接口
        // ************************ //
        // 多条翻译 //
        // ************************ //

        // 对整个uncachedMap调用api开始翻译  批量翻译
        String prompt = PlaceholderUtils.getNewestPrompt(target, JsonUtils.objectToJson(uncachedMap));
        // translatedValue - usedToken
        Pair<String, Integer> pair = aLiYunTranslateIntegration.userTranslate(prompt, target);
        if (pair == null) {
            return null;
        }

        // 翻译后 - 还原glossary
        String aiResponse = pair.getFirst();
        Map<Integer, String> translatedValueMap = JsonUtils.jsonToObjectWithNull(aiResponse, new TypeReference<Map<Integer, String>>() {
        });
        if (translatedValueMap == null) {
            return null;
        }

        // 翻译后 - 设置缓存
//            for (Map.Entry<Integer, String> entry : translatedValueMap.entrySet()) {
//                setCache(target, entry.getValue(), idToSourceValueMap.get(entry.getKey()));
//            }

        translatedValueMap.putAll(cachedMap);
        return new Pair<>(translatedValueMap, pair.getSecond());
    }

    // Pair => translatedMap - usedToken
    // Map => id - translatedValue
    public Pair<Map<Integer, String>, Integer> translateBatch(Map<Integer, String> idToSourceValueMap, String target,
                                                               Map<String, GlossaryDO> glossaryMap) {
        Boolean hasGlossary = false;
        // 替换glossary
        for (Map.Entry<Integer, String> entry : idToSourceValueMap.entrySet()) {
            Pair<String, Boolean> glossaryPair = replaceWithGlossary(entry.getValue(), glossaryMap);
            if (glossaryPair.getSecond()) {
                entry.setValue(glossaryPair.getFirst());
                hasGlossary = true;
            }
        }

        // 3.1 过缓存
        Map<Integer, String> cachedMap = new HashMap<>();
        Map<Integer, String> uncachedMap = new HashMap<>();
        this.getCached(idToSourceValueMap, cachedMap, uncachedMap, target);

        // 3.2 调用翻译接口
        // ************************ //
        // 多条翻译 //
        // ************************ //

        // 对整个uncachedMap调用api开始翻译  批量翻译
        String prompt = PlaceholderUtils.getNewestPrompt(target, JsonUtils.objectToJson(uncachedMap));
//        StringBuilder prompt = new StringBuilder("帮我翻译如下内容，到目标语言：" + target + "。");
//        prompt.append("我会给你一个json格式的数据，你只翻译里面的value值，将翻译后的值填回到value里面，同样的格式返回给我。");
//        if (hasGlossary) {
//            prompt.append("其中{[xxx]}形式的字符串跳过，不要翻译，并且返回原样给我。");
//        }
//        prompt.append("只返回翻译后的内容，不要其他多余的说明。内容如下: ");
//        prompt.append(JsonUtils.objectToJson(uncachedMap));

        // translatedValue - usedToken
        Pair<String, Integer> pair = aLiYunTranslateIntegration.userTranslate(prompt, target);
        if (pair != null && pair.getFirst() != null) {
            // 翻译后 - 还原glossary
            String aiResponse = pair.getFirst();
            if (hasGlossary) {
                aiResponse = getGlossaryReplacedBack(aiResponse);
            }
            Map<Integer, String> translatedValueMap = JsonUtils.jsonToObjectWithNull(aiResponse, new TypeReference<Map<Integer, String>>() {
            });
            if (translatedValueMap != null) {
                // 翻译后 - 设置缓存
                for (Map.Entry<Integer, String> entry : translatedValueMap.entrySet()) {
                    setCache(target, entry.getValue(), idToSourceValueMap.get(entry.getKey()));
                }

                translatedValueMap.putAll(cachedMap);
                return new Pair<>(translatedValueMap, pair.getSecond());
            }
        }
        // FatalException
        return null;
    }

    public Pair<String, Integer> translateSingle(String value, String target,
                                                 Map<String, GlossaryDO> glossaryMap) {
        StringBuilder promptBuilder = new StringBuilder("帮我翻译如下内容，到目标语言：" + target + "。");
        Pair<String, Boolean> glossaryPair = replaceWithGlossary(value, glossaryMap);
        if (glossaryPair.getSecond()) {
            promptBuilder.append("其中{[xxx]}形式的字符串跳过，不要翻译，并且返回原样给我。");
            value = glossaryPair.getFirst();
        }

        // 先替换glossary， 再去做缓存
        String targetCache = redisProcessService.getCacheData(target, value);
        if (targetCache != null) {
            targetCache = isHtmlEntity(targetCache);
            return new Pair<>(targetCache, 0);
        }

        promptBuilder.append("只返回翻译后的内容，不要其他多余的说明。内容如下: ");
        promptBuilder.append(value);

        // translatedValue - usedToken
        Pair<String, Integer> pair = aLiYunTranslateIntegration.userTranslate(promptBuilder.toString(), target);
        if (pair != null && pair.getFirst() != null) {
            String translatedText = pair.getFirst();

            // 把{[xxx]}替换回去 xxx
            if (glossaryPair.getSecond()) {
                translatedText = getGlossaryReplacedBack(translatedText);
            }
            return new Pair<>(translatedText, pair.getSecond());
        }
        return null;
    }


    private static Pair<String, Boolean> replaceWithGlossary(String value, Map<String, GlossaryDO> glossaryMap) {
        if (value == null || glossaryMap == null || glossaryMap.isEmpty()) {
            return new Pair<>(value, false);
        }

        Boolean hasGlossary = false;
        for (Map.Entry<String, GlossaryDO> entry : glossaryMap.entrySet()) {
            String key = entry.getKey();
            GlossaryDO glossaryDO = entry.getValue();
            Integer isCaseSensitive = glossaryDO.getCaseSensitive();

            // 当 isCaseSensitive 为 1 时，要求大小写完全一致才替换；否则不区分大小写替换
            String replacement = "{[" + glossaryDO.getTargetText() + "]}";
            if (isCaseSensitive != null && isCaseSensitive == 1) {
                if (value.contains(key)) {
                    value = value.replace(key, replacement);
                    hasGlossary = true;
                }
            } else {
                // 不区分大小写替换：使用正则的 CASE_INSENSITIVE
                Pattern pattern = Pattern.compile(Pattern.quote(key), Pattern.CASE_INSENSITIVE);
                Matcher matcher = pattern.matcher(value);
                if (matcher.find()) {
                    value = matcher.replaceAll(Matcher.quoteReplacement(replacement));
                    hasGlossary = true;
                }
            }
        }

        return new Pair<>(value, hasGlossary);
    }

    private static String getGlossaryReplacedBack(String value) {
        return value.replaceAll("\\{\\[(.*?)]}", "$1");
    }

    private void getCached(Map<Integer, String> idToSourceValueMap,
                           Map<Integer, String> cachedMap,
                           Map<Integer, String> unCachedMap,
                           String target) {
        idToSourceValueMap.forEach((id, sourceValue) -> {
            String targetCache = redisProcessService.getCacheData(target, sourceValue);
            if (false) {
                targetCache = isHtmlEntity(targetCache);
                cachedMap.put(id, targetCache);
            } else {
                unCachedMap.put(id, sourceValue);
            }
        });
        appInsights.trackTrace("Translation getCached: total " + idToSourceValueMap.size() +
                " cached " + cachedMap.size() +
                " uncached " + unCachedMap.size());
    }

    private void setCache(String target, String targetValue, String sourceValue) {
        redisProcessService.setCacheData(target, targetValue, sourceValue);
    }
}
