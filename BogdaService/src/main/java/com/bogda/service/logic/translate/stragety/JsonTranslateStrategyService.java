package com.bogda.service.logic.translate.stragety;

import com.bogda.service.context.TranslateContext;
import com.bogda.common.utils.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

@Component
public class JsonTranslateStrategyService implements ITranslateStrategyService {

    @Autowired
    private BatchTranslateStrategyService batchTranslateStrategyService;

    @Override
    public String getType() {
        return "JSON";
    }

    @Override
    public void translate(TranslateContext ctx) {
        String value = ctx.getContent();
        String target = ctx.getTargetLanguage();

        // 解析 JSON
        JsonNode rootNode = JsonUtils.readTree(value);
        if (rootNode == null) {
            ctx.setTranslatedContent(value);
            return;
        }

        // 存储需要翻译的文本节点路径和索引
        List<JsonNodePath> textNodePaths = new ArrayList<>();
        int index = 0;

        // 递归查找所有 type="text" 的节点，并提取 value 到 map
        findTextNodesAndExtractValues(rootNode, textNodePaths, ctx.getOriginalTextMap(), index);

        // 如果没有需要翻译的内容，直接返回原文
        if (ctx.getOriginalTextMap().isEmpty()) {
            ctx.setTranslatedContent(value);
            ctx.setStrategy("JSON翻译（无内容）");
            return;
        }

        batchTranslateStrategyService.translate(ctx);

        // 回填翻译结果到 JSON
        for (JsonNodePath nodePath : textNodePaths) {
            JsonNode textNode = nodePath.getNode();
            if (textNode.has("value") && textNode.get("value").isTextual()) {
                Integer key = nodePath.getIndex();
                String originalValue = ctx.getOriginalTextMap().get(key);
                String translatedValue = ctx.getTranslatedTextMap().get(key);

                // 如果有翻译结果，使用翻译结果；否则使用原文
                String finalValue = (translatedValue != null && !translatedValue.isEmpty())
                        ? translatedValue
                        : originalValue;

                // 回填到 JSON 节点
                if (textNode instanceof ObjectNode) {
                    ((ObjectNode) textNode).put("value", finalValue);
                }
            }
        }

        // 将翻译后的 JSON 转换回字符串
        String translatedJson = JsonUtils.objectToJson(rootNode);
        ctx.setStrategy("JSON翻译");
        ctx.setTranslatedContent(translatedJson != null ? translatedJson : value);
    }

    /**
     * 使用 while 循环查找所有 type="text" 的节点，并提取 value 到 map
     * @param rootNode 根节点
     * @param textNodePaths 存储找到的文本节点路径
     * @param originalTextMap 存储原始文本的 map
     * @param startIndex 起始索引
     * @return 下一个可用的索引
     */
    private int findTextNodesAndExtractValues(JsonNode rootNode, List<JsonNodePath> textNodePaths,
                                              Map<Integer, String> originalTextMap, int startIndex) {
        int currentIndex = startIndex;

        // 使用栈来存储待处理的节点
        Stack<JsonNode> nodeStack = new Stack<>();
        nodeStack.push(rootNode);

        // 使用 while 循环处理栈中的节点
        while (!nodeStack.isEmpty()) {
            JsonNode node = nodeStack.pop();

            if (node.isObject()) {
                // 检查是否是 type="text" 的节点
                if (node.has("type") && "text".equals(node.get("type").asText())) {
                    // 只有当节点有 value 字段且不为空时，才添加到 map
                    if (node.has("value") && node.get("value").isTextual()) {
                        String textValue = node.get("value").asText();
                        if (textValue != null && !textValue.trim().isEmpty()) {
                            textNodePaths.add(new JsonNodePath(node, currentIndex));
                            originalTextMap.put(currentIndex, textValue);
                            currentIndex++;
                        }
                    }
                }

                // 将 children 数组中的节点压入栈（逆序压入，保证正序处理）
                if (node.has("children") && node.get("children").isArray()) {
                    JsonNode children = node.get("children");
                    // 逆序压入栈，保证正序处理
                    for (int i = children.size() - 1; i >= 0; i--) {
                        nodeStack.push(children.get(i));
                    }
                }
            } else if (node.isArray()) {
                // 如果是数组，将每个元素压入栈（逆序压入，保证正序处理）
                for (int i = node.size() - 1; i >= 0; i--) {
                    nodeStack.push(node.get(i));
                }
            }
        }

        return currentIndex;
    }

    /**
     * 假翻译方法 - 用于演示，实际应该调用真实的翻译服务
     * @param originalTextMap 原始文本映射
     * @param targetLanguage 目标语言
     * @return 翻译后的文本映射
     */
    private Map<Integer, String> fakeTranslate(Map<Integer, String> originalTextMap, String targetLanguage) {
        Map<Integer, String> translatedMap = new HashMap<>();

        for (Map.Entry<Integer, String> entry : originalTextMap.entrySet()) {
            Integer key = entry.getKey();
            String originalText = entry.getValue();

            // 假翻译：在原文前加上 "[翻译到" + targetLanguage + "]"
            // 实际使用时应该替换为真实的翻译逻辑
            String translatedText = "[翻译到" + targetLanguage + "]" + originalText;
            translatedMap.put(key, translatedText);
        }

        return translatedMap;
    }

    /**
     * 用于存储 JSON 节点路径和索引的内部类
     */
    private static class JsonNodePath {
        private final JsonNode node;
        private final int index;

        public JsonNodePath(JsonNode node, int index) {
            this.node = node;
            this.index = index;
        }

        public JsonNode getNode() {
            return node;
        }

        public int getIndex() {
            return index;
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

