package com.bogda.service.logic.translate.stragety;

import com.bogda.common.TranslateContext;
import com.bogda.common.reporter.ExceptionReporterHolder;
import com.bogda.common.utils.StringUtils;
import com.bogda.common.utils.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.bogda.service.logic.redis.ConfigRedisRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.Set;

@Component
public class JsonTranslateStrategyService implements ITranslateStrategyService {

    private static final String METAFIELD_JSON_TRANSLATE_RULE = "METAFIELD_JSON_TRANSLATE_RULE";
    private static final String RULE_MODE_TYPE_MATCH = "typeFieldMatch";
    private static final String RULE_MODE_PATH = "path";

    @Autowired
    private BatchTranslateStrategyService batchTranslateStrategyService;

    @Autowired(required = false)
    private ConfigRedisRepo configRedisRepo;

    @Override
    public String getType() {
        return "JSON";
    }

    /**
     * JSON 翻译主流程：
     * 1. 将输入字符串解析为 JSON 树；
     * 2. 按配置规则抽取待翻译文本（写入 originalTextMap，并记录节点定位信息）；
     * 3. 复用批量翻译策略执行翻译；
     * 4. 将翻译结果按原索引回填到对应 JSON 字段；
     * 5. 序列化为字符串并写回上下文。
     *
     * 设计要点：
     * - 抽取与回填通过同一 index 绑定，避免路径匹配重复计算；
     * - 对空 JSON、无可翻译文本、单条翻译失败均做兼容降级，保证流程可继续；
     * - 回填时若译文为空，自动回退原文，避免写入空字符串破坏业务数据。
     */
    @Override
    public void translate(TranslateContext ctx) {
        String sourceJson = ctx.getContent();

        // 解析失败时直接回传原值，避免非 JSON 文本触发异常流程。
        JsonNode rootNode = JsonUtils.readTree(sourceJson);
        if (rootNode == null) {
            ctx.setTranslatedContent(sourceJson);
            return;
        }

        // nodePaths 负责“定位回填位置”，originalTextMap 负责“索引 -> 原文”映射。
        List<JsonNodePath> textNodePaths = new ArrayList<>();
        List<JsonExtractRule> rules = loadJsonExtractRules();
        findTextNodesAndExtractValues(rootNode, textNodePaths, ctx.getOriginalTextMap(), 0, rules);

        // 没有可翻译文本时，短路返回，减少不必要调用。
        if (ctx.getOriginalTextMap().isEmpty()) {
            ctx.setTranslatedContent(sourceJson);
            ctx.setStrategy("JSON翻译（无内容）");
            return;
        }

        // 统一复用批量翻译能力：输入 originalTextMap，输出 translatedTextMap。
        batchTranslateStrategyService.translate(ctx);

        // 回填翻译结果到 JSON：优先译文，失败则使用原文兜底。
        for (JsonNodePath nodePath : textNodePaths) {
            Integer key = nodePath.getIndex();
            String originalValue = ctx.getOriginalTextMap().get(key);
            String translatedValue = ctx.getTranslatedTextMap().get(key);
            String finalValue = (translatedValue != null && !translatedValue.isEmpty())
                    ? translatedValue
                    : originalValue;

            JsonNode targetNode = nodePath.getNode();
            if (targetNode instanceof ObjectNode) {
                ((ObjectNode) targetNode).put(nodePath.getFieldName(), finalValue);
            }
        }

        // 序列化失败时回退原始输入，确保 translatedContent 一定可用。
        String translatedJson = JsonUtils.objectToJson(rootNode);
        ctx.setStrategy("JSON翻译");
        ctx.setTranslatedContent(translatedJson != null ? translatedJson : sourceJson);
    }

    /**
     * 使用 while/递归按配置查找需要翻译的 JSON 字段
     * @param rootNode 根节点
     * @param textNodePaths 存储找到的文本节点路径
     * @param originalTextMap 存储原始文本的 map
     * @param startIndex 起始索引
     * @return 下一个可用的索引
     */
    private int findTextNodesAndExtractValues(JsonNode rootNode, List<JsonNodePath> textNodePaths,
                                              Map<Integer, String> originalTextMap, int startIndex,
                                              List<JsonExtractRule> rules) {
        int currentIndex = startIndex;
        Set<String> dedupKeySet = new HashSet<>();
        for (JsonExtractRule rule : rules) {
            if (RULE_MODE_TYPE_MATCH.equals(rule.getMode())) {
                currentIndex = collectByTypeMatchRule(rootNode, textNodePaths, originalTextMap, currentIndex, rule, dedupKeySet);
            } else if (RULE_MODE_PATH.equals(rule.getMode())) {
                currentIndex = collectByPathRule(rootNode, textNodePaths, originalTextMap, currentIndex, rule, dedupKeySet);
            }
        }
        return currentIndex;
    }

    private int collectByTypeMatchRule(JsonNode rootNode, List<JsonNodePath> textNodePaths,
                                       Map<Integer, String> originalTextMap, int startIndex,
                                       JsonExtractRule rule, Set<String> dedupKeySet) {
        int currentIndex = startIndex;
        if (StringUtils.isEmpty(rule.getTypeField()) || StringUtils.isEmpty(rule.getTypeValue())
                || StringUtils.isEmpty(rule.getTranslateField())) {
            return currentIndex;
        }

        Stack<JsonNode> nodeStack = new Stack<>();
        nodeStack.push(rootNode);

        while (!nodeStack.isEmpty()) {
            JsonNode node = nodeStack.pop();
            if (node == null) {
                continue;
            }

            if (node.isObject()) {
                JsonNode typeNode = node.get(rule.getTypeField());
                String currentTypeValue = typeNode == null ? null : typeNode.asText();
                if (rule.getTypeValue().equals(currentTypeValue)) {
                    JsonNode textFieldNode = node.get(rule.getTranslateField());
                    if (textFieldNode != null && textFieldNode.isTextual()) {
                        String textValue = textFieldNode.asText();
                        if (!StringUtils.isEmpty(textValue) && !StringUtils.isEmpty(textValue.trim())) {
                            String dedupKey = buildDedupKey(node, rule.getTranslateField());
                            if (!dedupKeySet.contains(dedupKey)) {
                                dedupKeySet.add(dedupKey);
                                textNodePaths.add(new JsonNodePath(node, rule.getTranslateField(), currentIndex));
                                originalTextMap.put(currentIndex, textValue);
                                currentIndex++;
                            }
                        }
                    }
                }

                Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
                if (fields != null) {
                    while (fields.hasNext()) {
                        Map.Entry<String, JsonNode> entry = fields.next();
                        JsonNode child = entry.getValue();
                        if (child != null && (child.isArray() || child.isObject())) {
                            nodeStack.push(child);
                        }
                    }
                }
            } else if (node.isArray()) {
                for (int i = node.size() - 1; i >= 0; i--) {
                    nodeStack.push(node.get(i));
                }
            }
        }

        return currentIndex;
    }

    private int collectByPathRule(JsonNode rootNode, List<JsonNodePath> textNodePaths,
                                  Map<Integer, String> originalTextMap, int startIndex,
                                  JsonExtractRule rule, Set<String> dedupKeySet) {
        if (StringUtils.isEmpty(rule.getPath())) {
            return startIndex;
        }
        String[] segments = rule.getPath().split("\\.");
        return collectByPathSegments(rootNode, segments, 0, textNodePaths, originalTextMap, startIndex, dedupKeySet);
    }

    private int collectByPathSegments(JsonNode currentNode, String[] segments, int segmentIndex,
                                      List<JsonNodePath> textNodePaths, Map<Integer, String> originalTextMap,
                                      int currentIndex, Set<String> dedupKeySet) {
        if (currentNode == null || segmentIndex >= segments.length) {
            return currentIndex;
        }

        String segment = segments[segmentIndex];
        boolean isLast = segmentIndex == segments.length - 1;

        if (currentNode.isArray()) {
            for (JsonNode arrayItem : currentNode) {
                currentIndex = collectByPathSegments(arrayItem, segments, segmentIndex, textNodePaths, originalTextMap, currentIndex, dedupKeySet);
            }
            return currentIndex;
        }

        if (!currentNode.isObject()) {
            return currentIndex;
        }

        if (segment.endsWith("[*]")) {
            String arrayFieldName = segment.substring(0, segment.length() - 3);
            JsonNode arrayNode = currentNode.get(arrayFieldName);
            if (arrayNode != null && arrayNode.isArray()) {
                for (JsonNode arrayItem : arrayNode) {
                    currentIndex = collectByPathSegments(arrayItem, segments, segmentIndex + 1, textNodePaths, originalTextMap, currentIndex, dedupKeySet);
                }
            }
            return currentIndex;
        }

        JsonNode nextNode = currentNode.get(segment);
        if (nextNode == null) {
            return currentIndex;
        }

        if (isLast && nextNode.isTextual()) {
            String textValue = nextNode.asText();
            if (!StringUtils.isEmpty(textValue) && !StringUtils.isEmpty(textValue.trim())) {
                String dedupKey = buildDedupKey(currentNode, segment);
                if (!dedupKeySet.contains(dedupKey)) {
                    dedupKeySet.add(dedupKey);
                    textNodePaths.add(new JsonNodePath(currentNode, segment, currentIndex));
                    originalTextMap.put(currentIndex, textValue);
                    return currentIndex + 1;
                }
            }
            return currentIndex;
        }

        return collectByPathSegments(nextNode, segments, segmentIndex + 1, textNodePaths, originalTextMap, currentIndex, dedupKeySet);
    }

    private String buildDedupKey(JsonNode node, String fieldName) {
        return System.identityHashCode(node) + ":" + fieldName;
    }

    private List<JsonExtractRule> loadJsonExtractRules() {
        List<JsonExtractRule> defaultRules = buildDefaultRules();
        if (configRedisRepo == null) {
            return defaultRules;
        }

        try {
            String configJson = configRedisRepo.getConfig(METAFIELD_JSON_TRANSLATE_RULE);
            JsonNode configNode = JsonUtils.readTree(configJson);
            if (configNode == null || !configNode.isObject()) {
                return defaultRules;
            }

            JsonNode rulesNode = configNode.get("jsonExtractRules");
            if (rulesNode == null || !rulesNode.isArray() || rulesNode.size() == 0) {
                return defaultRules;
            }

            List<JsonExtractRule> rules = new ArrayList<>();
            for (JsonNode ruleNode : rulesNode) {
                if (ruleNode == null || !ruleNode.isObject()) {
                    continue;
                }
                JsonExtractRule rule = new JsonExtractRule();
                JsonNode modeNode = ruleNode.get("mode");
                JsonNode typeFieldNode = ruleNode.get("typeField");
                JsonNode typeValueNode = ruleNode.get("typeValue");
                JsonNode translateFieldNode = ruleNode.get("translateField");
                JsonNode pathNode = ruleNode.get("path");
                rule.setMode(modeNode == null ? null : modeNode.asText());
                rule.setTypeField(typeFieldNode == null ? null : typeFieldNode.asText());
                rule.setTypeValue(typeValueNode == null ? null : typeValueNode.asText());
                rule.setTranslateField(translateFieldNode == null ? null : translateFieldNode.asText());
                rule.setPath(pathNode == null ? null : pathNode.asText());
                rules.add(rule);
            }
            return rules.isEmpty() ? defaultRules : rules;
        } catch (Exception e) {
            ExceptionReporterHolder.report("JsonTranslateStrategyService.loadJsonExtractRules", e);
            return defaultRules;
        }
    }

    private List<JsonExtractRule> buildDefaultRules() {
        List<JsonExtractRule> rules = new ArrayList<>();
        JsonExtractRule defaultRule = new JsonExtractRule();
        defaultRule.setMode(RULE_MODE_TYPE_MATCH);
        defaultRule.setTypeField("type");
        defaultRule.setTypeValue("text");
        defaultRule.setTranslateField("value");
        rules.add(defaultRule);

        JsonExtractRule titlePathRule = new JsonExtractRule();
        titlePathRule.setMode(RULE_MODE_PATH);
        titlePathRule.setPath("virtual_options[*].title");
        rules.add(titlePathRule);

        JsonExtractRule keyPathRule = new JsonExtractRule();
        keyPathRule.setMode(RULE_MODE_PATH);
        keyPathRule.setPath("virtual_options[*].values[*].key");
        rules.add(keyPathRule);
        return rules;
    }

    /**
     * 用于存储 JSON 节点路径和索引的内部类
     */
    private static class JsonNodePath {
        private final JsonNode node;
        private final String fieldName;
        private final int index;

        public JsonNodePath(JsonNode node, String fieldName, int index) {
            this.node = node;
            this.fieldName = fieldName;
            this.index = index;
        }

        public JsonNode getNode() {
            return node;
        }

        public String getFieldName() {
            return fieldName;
        }

        public int getIndex() {
            return index;
        }
    }

    private static class JsonExtractRule {
        private String mode;
        private String typeField;
        private String typeValue;
        private String translateField;
        private String path;

        public String getMode() {
            return mode;
        }

        public void setMode(String mode) {
            this.mode = mode;
        }

        public String getTypeField() {
            return typeField;
        }

        public void setTypeField(String typeField) {
            this.typeField = typeField;
        }

        public String getTypeValue() {
            return typeValue;
        }

        public void setTypeValue(String typeValue) {
            this.typeValue = typeValue;
        }

        public String getTranslateField() {
            return translateField;
        }

        public void setTranslateField(String translateField) {
            this.translateField = translateField;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }
    }

    @Override
    public void finishAndGetJsonRecord(TranslateContext ctx) {
        ctx.finish();
        Map<String, String> variable = new HashMap<>();
        variable.put("strategy", ctx.getStrategy());
        variable.put("usedToken", String.valueOf(ctx.getUsedToken()));
        variable.put("translatedTime", String.valueOf(ctx.getTranslatedTime()));
        variable.put("cachedCount", String.valueOf(ctx.getCachedCount()));
        variable.put("glossaryCount", String.valueOf(ctx.getGlossaryCount()));
        variable.put("translatedChars", String.valueOf(ctx.getTranslatedChars()));
        ctx.setTranslateVariables(variable);
    }
}

