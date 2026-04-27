package com.bogda.service.logic.translate;

import com.bogda.common.contants.TranslateConstants;
import com.bogda.common.entity.DO.GlossaryDO;
import com.bogda.common.utils.JsonUtils;
import com.bogda.common.utils.JsoupUtils;
import com.bogda.common.utils.StringUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import kotlin.Pair;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.TextNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class UniversalTranslateService {
    @Autowired
    private ModelTranslateService modelTranslateService;
    @Autowired
    private PromptConfigService promptConfigService;

    private static final Pattern LEADING_WHITESPACE = Pattern.compile("^(\\s+)");
    private static final Pattern TRAILING_WHITESPACE = Pattern.compile("(\\s+)$");

    public boolean shouldUseStructuredTranslation(String sourceValue, boolean isJson, boolean isHtml, String shopifyType) {
        return isJson
                || isHtml
                || (JsonUtils.isListFormat(sourceValue) && TranslateConstants.LIST_SINGLE_LINE_TEXT_FIELD.equals(shopifyType));
    }

    public TranslateResult translateStructuredContent(String content,
                                                      String targetLanguage,
                                                      Map<String, GlossaryDO> glossaryMap,
                                                      String aiModel,
                                                      String module,
                                                      String shopName,
                                                      String sessionId,
                                                      boolean isJson,
                                                      boolean isHtml) {
        if (content == null || content.isEmpty()) {
            return new TranslateResult(content, 0);
        }
        if (isJson) {
            return translateJsonContent(content, targetLanguage, aiModel, module, sessionId);
        }
        if (isHtml) {
            return translateHtmlContent(content, targetLanguage, aiModel, module, sessionId);
        }
        return translateListContent(content, targetLanguage, aiModel, module, sessionId);
    }

    public BatchTranslateResult translateBatchContent(Map<Integer, String> sourceMap,
                                                      String targetLanguage,
                                                      Map<String, GlossaryDO> glossaryMap,
                                                      String aiModel,
                                                      String module,
                                                      String shopName,
                                                      String sessionId) {
        return translateTextMap(sourceMap, targetLanguage, aiModel, module, sessionId);
    }

    private TranslateResult translateJsonContent(String content,
                                                 String targetLanguage,
                                                 String aiModel,
                                                 String module,
                                                 String sessionId) {
        JsonNode root = JsonUtils.readTree(content);
        if (root == null) {
            return new TranslateResult(content, 0);
        }

        Map<Integer, JsonTextPath> pathMap = new LinkedHashMap<>();
        Map<Integer, String> sourceMap = new LinkedHashMap<>();
        collectJsonTextNodes(root, pathMap, sourceMap, new int[]{0});
        if (sourceMap.isEmpty()) {
            return new TranslateResult(content, 0);
        }

        BatchTranslateResult translated = translateTextMap(sourceMap, targetLanguage, aiModel, module, sessionId);
        for (Map.Entry<Integer, JsonTextPath> entry : pathMap.entrySet()) {
            Integer index = entry.getKey();
            JsonTextPath path = entry.getValue();
            String fallback = sourceMap.get(index);
            String value = translated.translatedMap.get(index);
            String finalValue = (value == null || value.isEmpty()) ? fallback : value;
            if (path.node instanceof ObjectNode) {
                ((ObjectNode) path.node).put(path.fieldName, finalValue);
            }
        }
        String translatedJson = JsonUtils.objectToJson(root);
        return new TranslateResult(
                translatedJson == null || translatedJson.isEmpty() ? content : translatedJson,
                translated.usedToken
        );
    }

    private void collectJsonTextNodes(JsonNode node,
                                      Map<Integer, JsonTextPath> pathMap,
                                      Map<Integer, String> sourceMap,
                                      int[] indexHolder) {
        if (node == null) {
            return;
        }
        if (node.isObject()) {
            node.fields().forEachRemaining(entry -> {
                JsonNode child = entry.getValue();
                if (child != null && child.isTextual()) {
                    String text = child.asText();
                    if (text != null && !text.trim().isEmpty()) {
                        int idx = indexHolder[0]++;
                        pathMap.put(idx, new JsonTextPath(node, entry.getKey()));
                        sourceMap.put(idx, text);
                    }
                } else {
                    collectJsonTextNodes(child, pathMap, sourceMap, indexHolder);
                }
            });
            return;
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                collectJsonTextNodes(child, pathMap, sourceMap, indexHolder);
            }
        }
    }

    private TranslateResult translateHtmlContent(String content,
                                                 String targetLanguage,
                                                 String aiModel,
                                                 String module,
                                                 String sessionId) {
        Document document = Jsoup.parseBodyFragment(content);
        List<TextNode> textNodes = new ArrayList<>();
        for (Element element : document.getAllElements()) {
            textNodes.addAll(element.textNodes());
        }

        Map<Integer, String> sourceMap = new LinkedHashMap<>();
        Map<Integer, TextNode> nodeMap = new LinkedHashMap<>();
        int index = 0;
        for (TextNode textNode : textNodes) {
            String coreText = textNode.text();
            if (coreText == null || coreText.trim().isEmpty()) {
                continue;
            }
            sourceMap.put(index, coreText.trim());
            nodeMap.put(index, textNode);
            index++;
        }
        if (sourceMap.isEmpty()) {
            return new TranslateResult(content, 0);
        }

        BatchTranslateResult translated = translateTextMap(sourceMap, targetLanguage, aiModel, module, sessionId);
        for (Map.Entry<Integer, TextNode> entry : nodeMap.entrySet()) {
            Integer key = entry.getKey();
            TextNode textNode = entry.getValue();
            String originalWhole = textNode.getWholeText();
            String fallbackCore = sourceMap.get(key);
            String targetCore = translated.translatedMap.get(key);
            if (targetCore == null || targetCore.isEmpty()) {
                targetCore = fallbackCore;
            }

            String leading = "";
            String trailing = "";
            Matcher leadingMatcher = LEADING_WHITESPACE.matcher(originalWhole);
            if (leadingMatcher.find()) {
                leading = leadingMatcher.group(1);
            }
            Matcher trailingMatcher = TRAILING_WHITESPACE.matcher(originalWhole);
            if (trailingMatcher.find()) {
                trailing = trailingMatcher.group(1);
            }
            textNode.text(leading + targetCore + trailing);
        }

        String html = document.body().html();
        return new TranslateResult(html, translated.usedToken);
    }

    private TranslateResult translateListContent(String content,
                                                 String targetLanguage,
                                                 String aiModel,
                                                 String module,
                                                 String sessionId) {
        List<String> inputList = JsonUtils.jsonToObject(content, new TypeReference<List<String>>() {
        });
        if (inputList == null || inputList.isEmpty()) {
            return new TranslateResult(content, 0);
        }
        Map<Integer, String> sourceMap = new LinkedHashMap<>();
        int totalToken = 0;
        for (int i = 0; i < inputList.size(); i++) {
            String value = inputList.get(i);
            if (value == null || value.trim().isEmpty()) {
                continue;
            }
            if (JsonUtils.isJson(value)) {
                TranslateResult result = translateJsonContent(value, targetLanguage, aiModel, module, sessionId);
                inputList.set(i, result.getTranslatedContent());
                totalToken += result.getUsedToken();
                continue;
            }
            if (JsoupUtils.isHtml(value)) {
                TranslateResult result = translateHtmlContent(value, targetLanguage, aiModel, module, sessionId);
                inputList.set(i, result.getTranslatedContent());
                totalToken += result.getUsedToken();
                continue;
            }
            sourceMap.put(i, value);
        }
        if (sourceMap.isEmpty()) {
            return new TranslateResult(JsonUtils.objectToJson(inputList), totalToken);
        }

        BatchTranslateResult translated = translateTextMap(sourceMap, targetLanguage, aiModel, module, sessionId);
        for (Map.Entry<Integer, String> entry : sourceMap.entrySet()) {
            Integer idx = entry.getKey();
            String fallback = entry.getValue();
            String target = translated.translatedMap.get(idx);
            inputList.set(idx, target == null || target.isEmpty() ? fallback : target);
        }
        return new TranslateResult(JsonUtils.objectToJson(inputList), totalToken + translated.usedToken);
    }

    private BatchTranslateResult translateTextMap(Map<Integer, String> sourceMap,
                                                  String targetLanguage,
                                                  String aiModel,
                                                  String module,
                                                  String sessionId) {
        if (sourceMap == null || sourceMap.isEmpty()) {
            return new BatchTranslateResult(new LinkedHashMap<>(), 0);
        }
        String prompt = promptConfigService.buildPlainJsonPrompt(module, targetLanguage, sourceMap);
        Pair<String, Integer> modelResult = modelTranslateService.modelTranslate(
                aiModel, prompt, targetLanguage, sourceMap, sessionId
        );

        Map<Integer, String> translatedMap = parseMapResult(modelResult == null ? null : modelResult.getFirst());
        if (translatedMap == null) {
            translatedMap = new LinkedHashMap<>();
        }
        Map<Integer, String> finalMap = new LinkedHashMap<>();
        for (Map.Entry<Integer, String> entry : sourceMap.entrySet()) {
            String translated = translatedMap.get(entry.getKey());
            finalMap.put(entry.getKey(), (translated == null || translated.isEmpty()) ? entry.getValue() : translated);
        }
        int usedToken = modelResult == null || modelResult.getSecond() == null ? 0 : Math.max(modelResult.getSecond(), 0);
        return new BatchTranslateResult(finalMap, usedToken);
    }

    private Map<Integer, String> parseMapResult(String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        String jsonPart = StringUtils.extractJsonBlock(raw);
        if (jsonPart == null || jsonPart.isEmpty()) {
            return null;
        }
        jsonPart = JsonUtils.decodeDoubleEscapedUnicode(jsonPart);
        LinkedHashMap<Integer, String> parsed = JsonUtils.jsonToObjectWithNull(
                jsonPart, new TypeReference<LinkedHashMap<Integer, String>>() {
                });
        if (parsed != null) {
            return parsed;
        }
        String repaired = JsonUtils.highlyRobustRepair(jsonPart);
        repaired = JsonUtils.fixMissingQuote(repaired);
        return JsonUtils.jsonToObjectWithNull(repaired, new TypeReference<LinkedHashMap<Integer, String>>() {
        });
    }

    private static class JsonTextPath {
        private final JsonNode node;
        private final String fieldName;

        private JsonTextPath(JsonNode node, String fieldName) {
            this.node = node;
            this.fieldName = fieldName;
        }
    }

    public static class TranslateResult {
        private final String translatedContent;
        private final int usedToken;

        public TranslateResult(String translatedContent, int usedToken) {
            this.translatedContent = translatedContent;
            this.usedToken = usedToken;
        }

        public String getTranslatedContent() {
            return translatedContent;
        }

        public int getUsedToken() {
            return usedToken;
        }
    }

    public static class BatchTranslateResult {
        private final Map<Integer, String> translatedMap;
        private final int usedToken;

        public BatchTranslateResult(Map<Integer, String> translatedMap, int usedToken) {
            this.translatedMap = translatedMap;
            this.usedToken = usedToken;
        }

        public Map<Integer, String> getTranslatedMap() {
            return translatedMap;
        }

        public int getUsedToken() {
            return usedToken;
        }
    }
}
