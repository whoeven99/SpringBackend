package com.bogdatech.logic.translate;

import com.bogdatech.entity.DO.TranslateTextDO;
import com.bogdatech.logic.RedisProcessService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

import static com.bogdatech.constants.TranslateConstants.METAFIELD;
import static com.bogdatech.constants.TranslateConstants.URI;
import static com.bogdatech.utils.CaseSensitiveUtils.appInsights;
import static com.bogdatech.utils.JsonUtils.isJson;
import static com.bogdatech.utils.JsoupUtils.isHtml;
import static com.bogdatech.utils.JudgeTranslateUtils.*;
import static com.bogdatech.utils.RedisKeyUtils.PROGRESS_DONE;
import static com.bogdatech.utils.RedisKeyUtils.generateProcessKey;
import static com.bogdatech.utils.StringUtils.isValueBlank;

@Component
public class TranslateDataService {
    @Autowired
    private RedisProcessService redisProcessService;

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
