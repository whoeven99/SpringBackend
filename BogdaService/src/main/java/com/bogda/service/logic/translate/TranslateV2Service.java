package com.bogda.service.logic.translate;

import com.bogda.common.TranslateContext;
import com.bogda.common.contants.TranslateConstants;
import com.bogda.common.controller.response.BaseResponse;
import com.bogda.common.entity.DO.TranslateResourceDTO;
import com.bogda.common.entity.DO.UsersDO;
import com.bogda.common.entity.VO.SingleReturnVO;
import com.bogda.common.entity.VO.SingleTranslateVO;
import com.bogda.common.reporter.ExceptionReporterHolder;
import com.bogda.common.reporter.TraceReporterHolder;
import com.bogda.common.utils.JsonUtils;
import com.bogda.common.utils.PictureUtils;
import com.bogda.common.utils.ShopifyRequestUtils;
import com.bogda.common.utils.StringUtils;
import com.bogda.integration.aimodel.ChatGptIntegration;
import com.bogda.integration.aimodel.GeminiIntegration;
import com.bogda.integration.aimodel.KimiIntegration;
import com.bogda.repository.entity.TranslateTaskV2DO;
import com.bogda.service.Service.IUsersService;
import com.bogda.service.integration.ALiYunTranslateIntegration;
import com.bogda.service.logic.GlossaryService;
import com.bogda.service.logic.ShopifyService;
import com.bogda.service.logic.token.UserTokenService;
import com.bogda.service.logic.translate.stragety.ITranslateStrategyService;
import com.bogda.service.logic.translate.stragety.TranslateStrategyFactory;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import kotlin.Pair;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * v2 批量翻译主路径已下线（Spark worker v4 接管）。
 * 保留：Monitor 调试翻译、单条翻译（legacy API）、资源排序工具。
 */
@Component
public class TranslateV2Service {
    @Autowired
    private IUsersService usersService;
    @Autowired
    private ShopifyService shopifyService;
    @Autowired
    private UserTokenService userTokenService;
    @Autowired
    private GlossaryService glossaryService;
    @Autowired
    private TranslateStrategyFactory translateStrategyFactory;
    @Autowired
    private ALiYunTranslateIntegration aLiYunTranslateIntegration;
    @Autowired
    private GeminiIntegration geminiIntegration;
    @Autowired
    private ChatGptIntegration chatGptIntegration;
    @Autowired
    private KimiIntegration kimiIntegration;
    @Autowired
    private AiModelConfigService aiModelConfigService;

    public BaseResponse<SingleReturnVO> singleTextTranslate(SingleTranslateVO request) {
        if (request.getContext() == null || request.getTarget() == null
                || request.getType() == null || request.getKey() == null
                || StringUtils.isEmpty(request.getShopName())) {
            return BaseResponse.FailedResponse("Missing parameters");
        }

        TraceReporterHolder.report("TranslateV2Service.singleTextTranslate", "单条翻译参数如下 request ： " + request);
        String shopName = request.getShopName();
        String key = request.getKey();
        String resourceId = request.getResourceId();
        String target = request.getTarget();
        String shopifyData = null;
        if (request.getResourceId() != null) {
            UsersDO userByName = usersService.getUserByName(shopName);
            String token = userByName.getAccessToken();
            TranslateTaskV2DO taskDO = new TranslateTaskV2DO();
            taskDO.setNodeKey(key);
            taskDO.setResourceId(resourceId);

            refreshDigestAndSourceValueFromShopify(shopName, token, target, taskDO);
            shopifyData = taskDO.getTargetValue();
        }

        TraceReporterHolder.report("TranslateV2Service.singleTextTranslate", "查询shopify的翻译数据如下： " + shopifyData);
        Integer maxToken = userTokenService.getMaxToken(shopName);
        Integer usedToken = userTokenService.getUsedToken(shopName);
        if (usedToken >= maxToken) {
            return BaseResponse.FailedResponse("Token limit reached");
        }
        String aiModel = aiModelConfigService.getSingleTranslateModel();
        TranslateContext context = new TranslateContext(request.getContext(), target, request.getType(), key,
                glossaryService.getGlossaryDoByShopName(shopName, target), aiModel, request.getResourceType(), shopifyData);
        context.setShopName(shopName);
        ITranslateStrategyService service = translateStrategyFactory.getServiceByContext(context);
        service.translate(context);
        service.finishAndGetJsonRecord(context);
        userTokenService.addUsedToken(shopName, context.getUsedToken());
        TraceReporterHolder.report("TranslateV2Service.singleTextTranslate", "shopName : " + shopName
                + " token 单条翻译消耗 " + context.getUsedToken());
        SingleReturnVO returnVO = new SingleReturnVO();
        returnVO.setTargetText(context.getTranslatedContent());
        returnVO.setTranslateVariables(context.getTranslateVariables());
        return BaseResponse.SuccessResponse(returnVO);
    }

    /** For MonitorController promptTest */
    public Map<String, Object> testTranslate(Map<String, Object> map) {
        String model = String.valueOf(map.getOrDefault("model", ""));
        String prompt = String.valueOf(map.getOrDefault("prompt", ""));
        String target = String.valueOf(map.getOrDefault("target", ""));
        String picUrl = (map.get("picUrl") != null) ? map.get("picUrl").toString() : null;

        try {
            String jsonStr = String.valueOf(map.getOrDefault("json", "{}"));
            Map<Integer, String> languageMap = JsonUtils.jsonToObject(jsonStr, new TypeReference<Map<Integer, String>>() {
            });
            if (CollectionUtils.isEmpty(languageMap)) {
                return defaultNullMap();
            }
            prompt = prompt.replace("{{SOURCE_LANGUAGE_LIST}}", languageMap.toString())
                    .replace("{{TARGET_LANGUAGE}}", target);
        } catch (Exception e) {
            ExceptionReporterHolder.report("TranslateV2Service.testTranslate", e);
            return defaultNullMap();
        }

        try {
            if (model.contains("qwen")) {
                return handleAliYun(prompt, target);
            } else if (model.contains("gemini")) {
                return handleGemini(model, prompt, picUrl);
            } else if (model.contains("gpt")) {
                return handleGpt(model, prompt, target);
            } else if (model.contains("kimi")) {
                return handleKimi(prompt, target);
            }
        } catch (Exception e) {
            ExceptionReporterHolder.report("TranslateV2Service.testTranslate", e);
        }

        return defaultNullMap();
    }

    private Map<String, Object> handleKimi(String prompt, String target) {
        Pair<String, Integer> pair = kimiIntegration.chat(prompt, target);
        if (pair == null) {
            return defaultNullMap();
        }
        return buildResponse(pair.getFirst(), pair.getSecond(), "text");
    }

    private Map<String, Object> handleGpt(String modelName, String prompt, String target) {
        Pair<String, Integer> pair = chatGptIntegration.chatWithGpt(modelName, prompt, target);
        if (pair == null) {
            return defaultNullMap();
        }
        return buildResponse(pair.getFirst(), pair.getSecond(), "text");
    }

    private Map<String, Object> handleAliYun(String prompt, String target) {
        Pair<String, Integer> pair = aLiYunTranslateIntegration.userTranslate(prompt, target, aiModelConfigService.getMagnification("qwen"));
        if (pair == null) {
            return defaultNullMap();
        }
        return buildResponse(pair.getFirst(), pair.getSecond(), "text");
    }

    private Map<String, Object> handleGemini(String model, String prompt, String picUrl) throws Exception {
        if (picUrl == null) {
            Pair<String, Integer> pair = geminiIntegration.generateText(model, prompt, aiModelConfigService.getMagnification("gemini"));
            if (pair == null) {
                return defaultNullMap();
            }
            return buildResponse(pair.getFirst(), pair.getSecond(), "text");
        }

        String picType = PictureUtils.getExtensionFromUrl(picUrl);
        String mimeType = (picType != null) ? PictureUtils.IMAGE_MIME_MAP.get(picType.toLowerCase()) : null;
        if (mimeType == null) {
            return defaultNullMap();
        }

        try (InputStream in = new URL(picUrl).openStream()) {
            byte[] imageBytes = in.readAllBytes();
            Pair<String, Integer> pair = geminiIntegration.generateImage(model, prompt, imageBytes, mimeType);
            if (pair == null) {
                return defaultNullMap();
            }
            String dataUrl = "data:" + mimeType + ";base64," + pair.getFirst();
            return buildResponse(dataUrl, pair.getSecond(), "pic");
        }
    }

    private Map<String, Object> buildResponse(Object content, Integer tokens, String translateModel) {
        Map<String, Object> ans = new HashMap<>();
        ans.put("content", content);
        ans.put("allToken", tokens);
        ans.put("translateModel", translateModel);
        return ans;
    }

    private Map<String, Object> defaultNullMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("content", "error");
        map.put("allToken", 0);
        return map;
    }

    public static List<String> sortTranslateData(List<String> list) {
        List<String> orderList = TranslateResourceDTO.ALL_RESOURCES.stream()
                .map(TranslateResourceDTO::getResourceType)
                .toList();

        Map<String, Integer> orderMap = new HashMap<>();
        for (int i = 0; i < orderList.size(); i++) {
            orderMap.put(orderList.get(i), i);
        }

        List<String> sortedList = new ArrayList<>(list);
        sortedList.sort(Comparator.comparingInt(name -> orderMap.getOrDefault(name, Integer.MAX_VALUE)));
        return sortedList;
    }

    private boolean refreshDigestAndSourceValueFromShopify(String shopName, String token, String target, TranslateTaskV2DO taskDO) {
        if (taskDO == null || taskDO.getResourceId() == null || taskDO.getResourceId().isEmpty()
                || taskDO.getNodeKey() == null || taskDO.getNodeKey().isEmpty()) {
            return false;
        }

        String resourceId = taskDO.getResourceId();
        String nodeKey = taskDO.getNodeKey();
        boolean refreshData = false;

        String graphQuery = ShopifyRequestUtils.singleQueryByResourceId(resourceId, target);
        String shopifyData = shopifyService.getShopifyData(shopName, token, TranslateConstants.API_VERSION_LAST, graphQuery);
        if (shopifyData == null || shopifyData.isEmpty()) {
            return refreshData;
        }

        JsonNode root = JsonUtils.readTree(shopifyData);
        if (root == null) {
            return refreshData;
        }

        JsonNode nodes = root.path("translatableResourcesByIds").path("nodes");
        if (nodes == null || !nodes.isArray() || nodes.isEmpty()) {
            return refreshData;
        }

        for (JsonNode node : nodes) {
            JsonNode translatableContentList = node.path("translatableContent");
            JsonNode translationsList = node.path("translations");
            if (translatableContentList == null || !translatableContentList.isArray() || translationsList == null) {
                continue;
            }
            for (JsonNode translatableContent : translatableContentList) {
                if (nodeKey.equals(translatableContent.path("key").asText(null))) {
                    String latestDigest = translatableContent.path("digest").asText(null);
                    String latestSourceValue = translatableContent.path("value").asText(null);
                    if (latestDigest != null && !latestDigest.isEmpty()) {
                        taskDO.setDigest(latestDigest);
                    }
                    if (latestSourceValue != null) {
                        taskDO.setSourceValue(latestSourceValue);
                    }
                    refreshData = true;
                }
            }

            for (JsonNode translation : translationsList) {
                if (nodeKey.equals(translation.path("key").asText(null))) {
                    String latestTargetValue = translation.path("value").asText(null);
                    if (latestTargetValue != null) {
                        taskDO.setTargetValue(latestTargetValue);
                    }
                }
            }
        }

        TraceReporterHolder.report("TranslateV2Service.refreshDigestAndSourceValueFromShopify",
                "Digest not found for key. shop=" + shopName + " resourceId=" + resourceId + " nodeKey=" + nodeKey);
        return refreshData;
    }

    /** 历史 v2 任务状态码，MonitorController /monitorv2 仍读取 DB 中存量任务。 */
    @Getter
    public enum InitialTaskStatus {
        INIT_READING_SHOPIFY(0, "用户刚创建任务，读取shopify数据中"),
        READ_DONE_TRANSLATING(1, "读取shopify数据，存数据库结束，翻译中"),
        TRANSLATE_DONE_SAVING_SHOPIFY(2, "翻译结束，写入中"),
        SAVE_DONE_SENDING_EMAIL(3, "写入shopify结束，待发送邮件，完成任务"),
        ALL_DONE(4, "全部完成"),
        STOPPED(5, "手动中断 or tokenLimit中断"),
        INIT_STOPPED(6, "初始化阶段已停止"),
        ;

        private final int status;
        private final String desc;

        InitialTaskStatus(int status, String desc) {
            this.status = status;
            this.desc = desc;
        }
    }
}
