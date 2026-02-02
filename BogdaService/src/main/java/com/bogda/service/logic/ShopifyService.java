package com.bogda.service.logic;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.bogda.common.controller.request.*;
import com.bogda.common.entity.DO.*;
import com.bogda.common.entity.VO.SubscriptionVO;
import com.bogda.integration.model.*;
import com.bogda.repository.entity.SubscriptionQuotaRecordDO;
import com.bogda.repository.repo.SubscriptionQuotaRecordRepo;
import com.bogda.service.Service.*;
import com.bogda.service.utils.LanguageFlagConfig;
import com.bogda.common.entity.DTO.TranslateTextDTO;
import com.bogda.common.utils.StringUtils;
import com.bogda.service.utils.TypeConversionUtils;
import com.bogda.common.contants.TranslateConstants;
import com.bogda.common.enums.ErrorEnum;
import com.bogda.service.integration.ALiYunTranslateIntegration;
import com.bogda.integration.http.BaseHttpIntegration;
import com.bogda.integration.shopify.ShopifyHttpIntegration;
import com.bogda.common.controller.response.BaseResponse;
import com.bogda.common.utils.JsoupUtils;
import com.bogda.common.utils.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.util.concurrent.RateLimiter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static com.bogda.common.entity.DO.TranslateResourceDTO.RESOURCE_MAP;
import static com.bogda.common.entity.DO.TranslateResourceDTO.TOKEN_MAP;
import static com.bogda.service.integration.ALiYunTranslateIntegration.calculateBaiLianToken;
import static com.bogda.common.utils.JsonUtils.isJson;
import static com.bogda.common.utils.JudgeTranslateUtils.*;
import static com.bogda.common.utils.StringUtils.isValueBlank;

@Service
public class ShopifyService {

    @Autowired
    private IUserTypeTokenService userTypeTokenService;
    @Autowired
    private IUserSubscriptionsService userSubscriptionsService;
    @Autowired
    private IItemsService itemsService;
    @Autowired
    private ITranslatesService translatesService;
    @Autowired
    private ShopifyHttpIntegration shopifyHttpIntegration;
    @Autowired
    private ShopifyRateLimitService shopifyRateLimitService;
    @Autowired
    private BaseHttpIntegration baseHttpIntegration;
    @Autowired
    private IUsersService iUsersService;
    @Autowired
    private ISubscriptionPlansService iSubscriptionPlansService;
    @Autowired
    private IUserTrialsService iUserTrialsService;
    @Autowired
    private ICharsOrdersService iCharsOrdersService;
    @Autowired
    private SubscriptionQuotaRecordRepo subscriptionQuotaRecordRepo;

    // TODO 所有需要轮询shopify数据的， 挪到这里
    public void rotateAllShopifyGraph(String shopName, String resourceType, String accessToken,
                                      Integer first, String target, String afterEndCursor,
                                      Consumer<ShopifyGraphResponse.TranslatableResources.Node> consumer,
                                      Consumer<String> setAfterEndCursorConsumer) {
        String graphQuery = ShopifyRequestUtils.getQuery(resourceType, first.toString(), target, afterEndCursor);

        // 在第一次请求前，获取速率限制器并等待许可
        ShopifyGraphResponse data = getShopifyDataWithRateLimit(shopName, accessToken, graphQuery);
        while (data != null) {
            // 一个shopify data有250个nodes
            if (data.getTranslatableResources() != null && !CollectionUtils.isEmpty(data.getTranslatableResources().getNodes())) {
                for (ShopifyGraphResponse.TranslatableResources.Node node : data.getTranslatableResources().getNodes()) {
                    // 外面自己处理node数据
                    consumer.accept(node);
                }
            }

            // 还有下一页，就轮询
            if (data.getTranslatableResources() != null
                    && data.getTranslatableResources().getPageInfo() != null
                    && data.getTranslatableResources().getPageInfo().isHasNextPage()) {
                String endCursor = data.getTranslatableResources().getPageInfo().getEndCursor();
                setAfterEndCursorConsumer.accept(endCursor);

                graphQuery = ShopifyRequestUtils.getQuery(resourceType, first.toString(), target, endCursor);

                // 在请求下一页前，重新获取速率限制器（可能已被更新）并等待许可
                data = getShopifyDataWithRateLimit(shopName, accessToken, graphQuery);
            } else {
                data = null;
            }
        }
    }

    // 删除 shopify 数据（带速率限制，返回完整响应包括 extensions）
    public ShopifyGraphRemoveResponse deleteShopifyDataWithRateLimit(String shopName, String accessToken,
                                                                     ShopifyTranslationsRemove remove) {
        RateLimiter rateLimiter = shopifyRateLimitService.getOrCreateRateLimiter(shopName);
        rateLimiter.acquire();

        ShopifyRemoveResponse shopifyResponse = shopifyHttpIntegration.deleteShopifyData(shopName, accessToken, remove);
        if (shopifyResponse == null) {
            return null;
        }

        if (shopifyResponse.getExtensions() != null) {
            shopifyRateLimitService.updateRateLimit(shopName, shopifyResponse.getExtensions());
        }
        return shopifyResponse.getData();
    }

    // 获取 Shopify 数据（带速率限制，返回完整响应包括 extensions）
    private ShopifyGraphResponse getShopifyDataWithRateLimit(String shopName, String accessToken, String query) {
        RateLimiter rateLimiter = shopifyRateLimitService.getOrCreateRateLimiter(shopName);
        rateLimiter.acquire();

        // 获取完整响应（包括 extensions）
        ShopifyResponse shopifyResponse = shopifyHttpIntegration.getInfo(shopName, query, accessToken);
        if (shopifyResponse == null) {
            return null;
        }
        if (shopifyResponse.getExtensions() != null) {
            shopifyRateLimitService.updateRateLimit(shopName, shopifyResponse.getExtensions());
        }
        return shopifyResponse.getData();
    }

    // 保存 Shopify 数据（带速率限制，返回完整响应包括 extensions）
    public String saveDataWithRateLimit(String shopName, String token, ShopifyGraphResponse.TranslatableResources.Node node) {
        RateLimiter rateLimiter = shopifyRateLimitService.getOrCreateRateLimiter(shopName);
        rateLimiter.acquire();

        String response = shopifyHttpIntegration.saveShopifyData(shopName, token, node);
        if (response == null) {
            return null;
        }
        JSONObject jsonObject = JSONObject.parseObject(response);
        if (jsonObject == null) {
            return null;
        }

        if (jsonObject.getJSONObject("extensions") != null) {
            ShopifyExtensions extensions = JsonUtils.jsonToObjectWithNull(jsonObject.getJSONObject("extensions").toString(), ShopifyExtensions.class);
            if (extensions != null) {
                shopifyRateLimitService.updateRateLimit(shopName, extensions);
            }
        }

        if (jsonObject.getJSONObject("data") == null) {
            return null;
        }
        return response;
    }

    public String getShopifyData(String shopName, String accessToken, String apiVersion, String query) {
        if (!ConfigUtils.isLocalEnv()) {
            return shopifyHttpIntegration.getInfoByShopify(shopName, accessToken, apiVersion, query);
        } else {
            // 本地调用test cloud做个转发
            CloudServiceRequest cloudServiceRequest = new CloudServiceRequest();
            cloudServiceRequest.setShopName(shopName);
            cloudServiceRequest.setAccessToken(accessToken);
            cloudServiceRequest.setTarget(apiVersion);
            cloudServiceRequest.setBody(query);

            String body = JsonUtils.objectToJson(cloudServiceRequest);
            return baseHttpIntegration.httpPost(
                    "https://springbackendservice-e3hgbjgqafb9cpdh.canadacentral-01.azurewebsites.net/" + "test123",
                    body);
        }
    }

    // 包装一层，用于获取用户实际未翻译和部分翻译的语言数
    public int getUnTranslatedToken(ShopifyRequest request, String method, TranslateResourceDTO translateResource, String source) {
        // 获取该用户所有的未翻译和部分翻译的所有token数据
        List<String> all = getUserShopifyLanguage(request.getShopName(), source, request.getAccessToken());

        if (all == null || all.isEmpty()) {
            return 0;
        }

        int allLanguage = 0;
        for (String status : all) {
            if ("0".equals(status) || "3".equals(status) || "7".equals(status) || "6".equals(status)) {
                allLanguage++;
            }
        }

        if (allLanguage == 0) {
            return 0;
        }

        int totalWords = getTotalWords(request, method, translateResource);
        return totalWords * allLanguage;
    }

    /**
     * 获取用户未翻译和部分翻译的语言数
     */
    public List<String> getUserShopifyLanguage(String shopName, String sourceCode, String accessToken) {
        try {
            // Step 1: 获取 Shopify 数据
            String query = ShopifyRequestUtils.getLanguagesQuery();
            String shopifyData = getShopifyData(shopName, accessToken, TranslateConstants.API_VERSION_LAST, query);
            JsonNode root = JsonUtils.readTree(shopifyData);

            if (root == null || !root.has("shopLocales")) {
                AppInsightsUtils.trackTrace("Shopify response is empty or missing 'shopLocales' field for shop: " + shopName);
                return Collections.emptyList();
            }

            // Step 2: 提取语言列表
            List<String> shopLanguageList = new ArrayList<>();
            for (JsonNode node : root.path("shopLocales")) {
                String locale = node.path("locale").asText(null);
                if (locale != null && !locale.isEmpty()) {
                    shopLanguageList.add(locale);
                }
            }

            if (shopLanguageList.isEmpty()) {
                AppInsightsUtils.trackTrace("No locales found for shop: " + shopName);
                return Collections.emptyList();
            }

            // Step 3: 查询数据库中对应语言的状态
            List<TranslatesDO> usedLanguages = translatesService.list(
                    new LambdaQueryWrapper<TranslatesDO>()
                            .eq(TranslatesDO::getShopName, shopName)
                            .eq(TranslatesDO::getSource, sourceCode)
                            .in(TranslatesDO::getTarget, shopLanguageList)
            );

            // Step 4: 提取状态值
            return usedLanguages.stream()
                    .map(translate -> String.valueOf(translate.getStatus()))
                    .toList();

        } catch (Exception e) {
            AppInsightsUtils.trackException(e);
            AppInsightsUtils.trackTrace("FatalException Failed to sync Shopify languages for shop: " + shopName + " - " + e.getMessage());
            return Collections.emptyList();
        }
    }

    // 获得翻译前一共需要消耗的字符数
    public int getTotalWords(ShopifyRequest request, String method, TranslateResourceDTO translateResource) {
        CharacterCountUtils counter = new CharacterCountUtils();
        translateResource.setTarget(request.getTarget());
        String query = ShopifyRequestUtils.getQuery(translateResource.getResourceType(),
                translateResource.getFirst(), request.getTarget());

        String infoByShopify = getShopifyData(request.getShopName(), request.getAccessToken(), request.getApiVersion(), query);
        countBeforeTranslateChars(infoByShopify, request, translateResource, counter);
        return counter.getTotalChars();
    }

    //计数翻译前所需要的总共的字符数
    public void countBeforeTranslateChars(String infoByShopify, ShopifyRequest request, TranslateResourceDTO translateResource, CharacterCountUtils counter) {
        JsonNode rootNode = ConvertStringToJsonNode(infoByShopify, translateResource);
        if (rootNode == null || rootNode.isEmpty()) {
            return;
        }
        translateObjectNode(rootNode, request, counter, translateResource);

        //1, 获取所有要翻译的数据, 将单个语言的所有数据都统计
        Set<TranslateTextDTO> needTranslatedData = translatedAllDataParse(rootNode, request.getShopName());
        if (needTranslatedData != null) {
            filterNeedTranslateSetAndCount(translateResource.getResourceType(), true, needTranslatedData, counter);
        }

        // 获取translatableResources节点
        JsonNode translatableResourcesNode = rootNode.path("translatableResources");

        // 获取pageInfo节点
        JsonNode pageInfoNode = translatableResourcesNode.path("pageInfo");
        if (translatableResourcesNode.hasNonNull("pageInfo")) {
            if (pageInfoNode.hasNonNull("hasNextPage") && pageInfoNode.get("hasNextPage").asBoolean()) {
                JsonNode endCursor = pageInfoNode.path("endCursor");
                translateResource.setAfter(endCursor.asText(null));

                JsonNode nextPageData;
                try {
                    nextPageData = fetchNextPage(translateResource, request);
                    if (nextPageData == null) {
                        return;
                    }
                } catch (Exception e) {
                    return;
                }
                // 重新开始翻译流程
                countBeforeTranslateChars(nextPageData.toString(), request, translateResource, counter);
            }
        }
    }

    // 将String数据转化为JsonNode数据
    public JsonNode ConvertStringToJsonNode(String infoByShopify, TranslateResourceDTO translateResource) {
        JsonNode rootNode = null;
        try {
            rootNode = JsonUtils.OBJECT_MAPPER.readTree(infoByShopify);
        } catch (JsonProcessingException e) {
            AppInsightsUtils.trackException(e);
            AppInsightsUtils.trackTrace("解析JSON数据失败 errors： " + translateResource);
        }
        return rootNode;
    }

    //对node节点进行判断，是否调用方法
    public void translateObjectNode(JsonNode objectNode, ShopifyRequest request, CharacterCountUtils counter, TranslateResourceDTO translateResource) {
        //1, 获取所有要翻译的数据, 将单个语言的所有数据都统计
        Set<TranslateTextDTO> needTranslatedData = translatedAllDataParse(objectNode, request.getShopName());
        if (needTranslatedData == null) {
            return;
        }
        //2，在此基础上筛选掉规则以外的数据,并计数
        filterNeedTranslateSetAndCount(translateResource.getResourceType(), true, needTranslatedData, counter);
    }

    /**
     * 遍历needTranslatedSet, 对Set集合进行通用规则的筛选，返回筛选后的数据
     */
    public void filterNeedTranslateSetAndCount(String modeType, boolean handleFlag, Set<TranslateTextDTO> needTranslateSet, CharacterCountUtils counter) {
        for (TranslateTextDTO translateTextDTO : needTranslateSet) {
            String value = translateTextDTO.getSourceText();

            // 当 value 为空时跳过
            if (!isValueBlank(value)) {
                continue;
            }

            String type = translateTextDTO.getTextType();

            // 如果是特定类型，也从集合中移除
            if ("FILE_REFERENCE".equals(type) || "LINK".equals(type)
                    || "LIST_FILE_REFERENCE".equals(type) || "LIST_LINK".equals(type)
                    || "LIST_URL".equals(type) || "JSON".equals(type)
                    || "JSON_STRING".equals(type)) {
                continue;
            }

            String key = translateTextDTO.getTextKey();
            // 如果handleFlag为false，则跳过
            if (type.equals(TranslateConstants.URI) && "handle".equals(key)) {
                if (!handleFlag) {
                    continue;
                }
            }

            // 通用的不翻译数据
            if (!JudgeTranslateUtils.translationRuleJudgment(key, value)) {
                continue;
            }

            //如果是theme模块的数据
            if (JudgeTranslateUtils.TRANSLATABLE_RESOURCE_TYPES.contains(modeType)) {
                // 如果是html放html文本里面
                if (JsoupUtils.isHtml(value)) {
                    continue;
                }

                if (JsonUtils.isJson(value)) {
                    continue;
                }

                // 对key中包含slide  slideshow  general.lange 的数据不翻译
                if (key.contains("slide") || key.contains("slideshow") || key.contains("general.lange")) {
                    continue;
                }

                if (key.contains("block") && key.contains("add_button_selector")) {
                    continue;
                }
                // 对key中含section和general的做key值判断
                if (JudgeTranslateUtils.GENERAL_OR_SECTION_PATTERN.matcher(key).find()) {
                    // 进行白名单的确认
                    if (JudgeTranslateUtils.whiteListTranslate(key)) {
                        counter.addChars(ALiYunTranslateIntegration.calculateBaiLianToken(translateTextDTO.getSourceText()));
                        continue;
                    }

                    // 如果包含对应key和value，则跳过
                    if (!JudgeTranslateUtils.shouldTranslate(key, value)) {
                        continue;
                    }
                }
                counter.addChars(ALiYunTranslateIntegration.calculateBaiLianToken(translateTextDTO.getSourceText()));
                continue;
            }

            // 对METAOBJECT字段翻译
            if (modeType.equals(TranslateConstants.METAOBJECT)) {
                if (JsonUtils.isJson(value)) {
                    continue;
                }
                counter.addChars(ALiYunTranslateIntegration.calculateBaiLianToken(translateTextDTO.getSourceText()));
                continue;
            }

            // 对METAFIELD字段翻译
            if (modeType.equals(TranslateConstants.METAFIELD)) {
                // 如UXxSP8cSm，UgvyqJcxm。有大写字母和小写字母的组合。有大写字母，小写字母和数字的组合。 10位 字母和数字不翻译
                if (SUSPICIOUS_PATTERN.matcher(value).matches() || SUSPICIOUS2_PATTERN.matcher(value).matches()) {
                    continue;
                }
                if (!JudgeTranslateUtils.metaTranslate(value)) {
                    continue;
                }
                // 如果是base64编码的数据，不翻译
                if (BASE64_PATTERN.matcher(value).matches()) {
                    continue;
                }
                if (isJson(value)) {
                    continue;
                }
                counter.addChars(calculateBaiLianToken(translateTextDTO.getSourceText()));
                continue;
            }
            counter.addChars(ALiYunTranslateIntegration.calculateBaiLianToken(translateTextDTO.getSourceText()));
        }
    }

    /**
     * 解析shopifyData数据，将所有数据都存Set里面
     */
    public Set<TranslateTextDTO> translatedAllDataParse(JsonNode shopDataJson, String shopName) {
        Set<TranslateTextDTO> doubleTranslateTextDTOSet = new HashSet<>();
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
                    doubleTranslateTextDTOSet.addAll(needTranslatedSet(nodeElement));
                }
            }
        } catch (Exception e) {
            AppInsightsUtils.trackException(e);
            AppInsightsUtils.trackTrace("FatalException clickTranslation 用户 " + shopName + " 分析数据失败 errors : " + e);
        }
        return doubleTranslateTextDTOSet;
    }

    /**
     * 解析一下所有Set
     */
    public Set<TranslateTextDTO> needTranslatedSet(JsonNode shopDataJson) {
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
                // 提取翻译内容映射
                Map<String, TranslateTextDTO> partTranslateTextDOMap = extractTranslationsByResourceId(shopDataJson);
                Set<TranslateTextDTO> needTranslatedSet = new HashSet<>(partTranslateTextDOMap.values());
                return new HashSet<>(needTranslatedSet);
            }
        }
        return new HashSet<>();
    }

    /**
     * 同一个resourceId下的获取所有数据
     */
    public static Map<String, TranslateTextDTO> extractTranslationsByResourceId(JsonNode shopDataJson) {
        Map<String, TranslateTextDTO> translations = new HashMap<>();
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
                TranslateTextDTO translateTextDTO = new TranslateTextDTO();
                String key = translation.path("key").asText(null);
                translateTextDTO.setTextKey(key);
                translateTextDTO.setSourceText(translation.path("value").asText(null));
                translateTextDTO.setTextType(translation.path("type").asText(null));
                translations.put(key, translateTextDTO);
            });
        }
        return translations;
    }

    // 检查是否有下一页
    private boolean hasNextPage(JsonNode translatedNextPage) {
        JsonNode translatableResourcesNode = translatedNextPage.path("translatableResources");
        if (translatableResourcesNode.hasNonNull("pageInfo")) {
            JsonNode pageInfo = translatableResourcesNode.path("pageInfo");
            return pageInfo.hasNonNull("hasNextPage") && pageInfo.path("hasNextPage").asBoolean();
        }
        return false;
    }

    // 获取结束游标
    private String getEndCursor(JsonNode translatedNextPage) {
        JsonNode translatableResourcesNode = translatedNextPage.path("translatableResources");
        JsonNode pageInfo = translatableResourcesNode.path("pageInfo");
        return pageInfo.path("endCursor").asText(null);
    }


    // 修改getTestQuery里面的testQuery，用获取后的的查询语句进行查询
    public JsonNode fetchNextPage(TranslateResourceDTO translateResource, ShopifyRequest request) {
        String query = ShopifyRequestUtils.getQuery(translateResource.getResourceType(), translateResource.getFirst(),
                translateResource.getTarget(), translateResource.getAfter());
        String infoByShopify = null;
        try {
            infoByShopify = getShopifyData(request.getShopName(), request.getAccessToken(), TranslateConstants.API_VERSION_LAST, query);
        } catch (Exception e) {
            // 如果出现异常，则跳过, 翻译其他的内容
            AppInsightsUtils.trackTrace("FatalException fetchNextPage errors : " + e.getMessage());
        }
        if (infoByShopify == null || infoByShopify.isEmpty()) {
            return null;
        }
        JsonNode rootNode;
        try {
            rootNode = JsonUtils.OBJECT_MAPPER.readTree(infoByShopify);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        return rootNode;
    }

    //修改单个文本数据
    public String updateShopifySingleData(RegisterTransactionRequest registerTransactionRequest) {
        Map<String, Object> variables = getVariables(registerTransactionRequest);
        return shopifyHttpIntegration.registerTransaction(registerTransactionRequest.getShopName(), registerTransactionRequest.getAccessToken(), variables);
    }

    //修改多个文本数据
    public String updateShopifyManyData(ShopifyRequest shopifyRequest, List<RegisterTransactionRequest> registerTransactionRequests) {
        // 将List<RegisterTransactionRequest>处理成variables数据
        Map<String, Object> variables = toVariables(registerTransactionRequests);
        return shopifyHttpIntegration.registerTransaction(shopifyRequest.getShopName(), shopifyRequest.getAccessToken(), variables);
    }

    //一次修改多条shopify本地数据
    public String updateShopifyDataByTranslateTextRequests(List<RegisterTransactionRequest> registerTransactionRequests) {
        ShopifyRequest request = new ShopifyRequest();
        request.setShopName(registerTransactionRequests.get(0).getShopName());
        request.setAccessToken(registerTransactionRequests.get(0).getAccessToken());
        request.setTarget(registerTransactionRequests.get(0).getTarget());
        return updateShopifyManyData(request, registerTransactionRequests);
    }

    //将一条数据所需要的数据封装成Map格式
    static Map<String, Object> getVariables(RegisterTransactionRequest registerTransactionRequest) {
        Map<String, Object> variables = new HashMap<>();
        Map<String, Object> translation = new HashMap<>();
        translation.put("locale", registerTransactionRequest.getTarget());
        translation.put("key", registerTransactionRequest.getKey());
        translation.put("translatableContentDigest", registerTransactionRequest.getTranslatableContentDigest());
        translation.put("value", registerTransactionRequest.getValue());
        Object[] translations = new Object[]{
                translation
        };
        variables.put("resourceId", registerTransactionRequest.getResourceId());
        variables.put("translations", translations);
        return variables;
    }

    //将多条数据所需要的数据封装成Map格式
    private static Map<String, Object> toVariables(List<RegisterTransactionRequest> registerTransactionRequest) {
        Map<String, Object> variables = new HashMap<>();
        List<Map<String, Object>> translations = new ArrayList<>();
        for (RegisterTransactionRequest request : registerTransactionRequest) {
            Map<String, Object> translation = new HashMap<>();
            translation.put("locale", request.getTarget());
            translation.put("key", request.getKey());
            translation.put("translatableContentDigest", request.getTranslatableContentDigest());
            translation.put("value", request.getValue());
            translations.add(translation);
        }

        variables.put("resourceId", registerTransactionRequest.get(0).getResourceId());
        variables.put("translations", translations);
        return variables;
    }


    /**
     * 在UserSubscription表里面添加一个购买了免费订阅计划的用户（商家）
     */
    public BaseResponse<Object> addUserFreeSubscription(UserSubscriptionsRequest request) {
        request.setStatus(1);
        request.setPlanId(8); //将初始化的计划改为8 新的Free计划 初始额度是0
        LocalDateTime localDate = LocalDateTime.now();
        String localDateFormat = localDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        LocalDateTime startDate = LocalDateTime.parse(localDateFormat, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        request.setStartDate(startDate);
        request.setEndDate(null);
        //先判断是否有这个数据，没有插入，有了更新
        Integer userSubscriptionPlan = userSubscriptionsService.getUserSubscriptionPlan(request.getShopName());
        if (userSubscriptionPlan == null) {
            Integer i = userSubscriptionsService.addUserSubscription(request);
            if (i > 0) {
                return new BaseResponse<>().CreateSuccessResponse("success");
            } else {
                return new BaseResponse<>().CreateErrorResponse(ErrorEnum.SQL_INSERT_ERROR);
            }
        } else {
            return new BaseResponse<>().CreateSuccessResponse("success");
        }
    }

    /**
     * 修改shopify本地单条数据
     */
    public BaseResponse<Object> updateShopifyDataByTranslateTextRequest(RegisterTransactionRequest registerTransactionRequest) {
        AppInsightsUtils.trackTrace("updateShopifyDataByTranslateTextRequest " + registerTransactionRequest.getShopName() + " 传入的值： " + registerTransactionRequest);
        String string = updateShopifySingleData(registerTransactionRequest);
        AppInsightsUtils.trackTrace("updateShopifyDataByTranslateTextRequest " + registerTransactionRequest.getShopName() + " 返回的值： " + string);
        if (string.contains("\"value\":")) {
            return new BaseResponse<>().CreateSuccessResponse(200);
        } else {
            return new BaseResponse<>().CreateErrorResponse(getMessage(string));
        }
    }

    //解析JSON数据，获取message消息
    private static String getMessage(String json) {
        String message = null;
        try {
            JsonNode root = JsonUtils.OBJECT_MAPPER.readTree(json);

            JsonNode messageNode = root
                    .path("translationsRegister")
                    .path("userErrors")
                    .path(0)
                    .path("message");

            if (!messageNode.isMissingNode()) {
                AppInsightsUtils.trackTrace("updateShopifyDataByTranslateTextRequest Message: " + messageNode.asText());
                message = messageNode.asText();
            } else {
                message = json;
                AppInsightsUtils.trackTrace("updateShopifyDataByTranslateTextRequest   Message not found");
            }
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        return message;
    }

    //根据前端传来的值，返回对应的图片信息
    public Map<String, Object> getImageInfo(String[] strings) {
        Map<String, Object> imageInfo = new HashMap<>();
        for (String string : strings) {
            try {
                Field field = LanguageFlagConfig.class.getField(string.toUpperCase());
                imageInfo.put(string, field.get(null));
            } catch (NoSuchFieldException | IllegalAccessException e) {
                AppInsightsUtils.trackException(e);
                throw new RuntimeException(e);
            }
        }
        return imageInfo;
    }

    private CloudServiceRequest shopifyToCloudServiceRequest(ShopifyRequest request) {
        CloudServiceRequest cloudServiceRequest = new CloudServiceRequest();
        cloudServiceRequest.setShopName(request.getShopName());
        cloudServiceRequest.setAccessToken(request.getAccessToken());
        cloudServiceRequest.setTarget(request.getTarget());
        return cloudServiceRequest;
    }

    //计算被翻译项的总数和已翻译的个数
    public Map<String, Map<String, Object>> getTranslationItemsInfo(ResourceTypeRequest request) {
        ShopifyRequest shopifyRequest = TypeConversionUtils.resourceTypeRequestToShopifyRequest(request);
        CloudServiceRequest cloudServiceRequest = shopifyToCloudServiceRequest(shopifyRequest);
        Map<String, Map<String, Object>> result = new HashMap<>();
        // 遍历List中的每个TranslateResourceDTO对象
        CharacterCountUtils allCounter = new CharacterCountUtils();
        CharacterCountUtils translatedCounter = new CharacterCountUtils();
        try {
            if (request.getResourceType() == null || request.getResourceType().isEmpty()) {
                return result;
            }
            for (TranslateResourceDTO resource : RESOURCE_MAP.get(request.getResourceType())) {
                Map<String, Object> singleResult = new HashMap<>();
                singleResult.put("itemName", resource.getResourceType());
                resource.setTarget(request.getTarget());
                String query = ShopifyRequestUtils.getQuery(resource.getResourceType(),
                        resource.getFirst(), resource.getTarget());

                cloudServiceRequest.setBody(query);
                String infoByShopify;
                try {
                    infoByShopify = getShopifyData(request.getShopName(), request.getAccessToken(), TranslateConstants.API_VERSION_LAST, query);
                } catch (Exception e) {
                    //如果出现异常，则跳过, 翻译其他的内容
                    AppInsightsUtils.trackTrace("FatalException getTranslationItemsInfo errors : " + e.getMessage());
                    continue;
                }
                if (infoByShopify == null || infoByShopify.isEmpty()) {
                    continue;
                }
                countAllItemsAndTranslatedItems(infoByShopify, shopifyRequest, resource, allCounter, translatedCounter);
                //判断数据库对应语言是否翻译，为1，就满的
                Integer statusByShopNameAndTargetAndSource = translatesService.getStatusByShopNameAndTargetAndSource(request.getShopName(), request.getTarget(), request.getSource());
                if (statusByShopNameAndTargetAndSource != null && statusByShopNameAndTargetAndSource == 1) {
                    translatedCounter.addChars(allCounter.getTotalChars());
                }

                if (allCounter.getTotalChars() <= translatedCounter.getTotalChars()) {
                    translatedCounter.reset();
                    translatedCounter.addChars(allCounter.getTotalChars());
                }
                singleResult.put("totalNumber", allCounter.getTotalChars());
                singleResult.put("translatedNumber", translatedCounter.getTotalChars());
                singleResult.put("target", request.getTarget());
                singleResult.put("status", 1);
                result.put(request.getResourceType(), singleResult);
            }
        } catch (Exception e) {
            AppInsightsUtils.trackTrace("FatalException getTranslationItemsInfoAll errors : 用户:" + request.getShopName() + " 目标： " + request.getTarget() + "  " + "模块： " + request.getResourceType() + e.getMessage());
            AppInsightsUtils.trackException(e);
        }
        return result;
    }


    //计数当前项所总共的项数和已翻译的项数
    @Async
    public void countAllItemsAndTranslatedItems(String infoByShopify, ShopifyRequest request, TranslateResourceDTO translateResource, CharacterCountUtils allCounter, CharacterCountUtils translatedCounter) {
        JsonNode rootNode = ConvertStringToJsonNode(infoByShopify, translateResource);
        convertArrayNodeToJsonNode(rootNode, request, allCounter, translatedCounter, translateResource);
        // 递归处理下一页数据
        countHandlePagination(rootNode, request, allCounter, translateResource, translatedCounter);
    }

    //对node判断如果是对象类型就进入下一个方法，如果是数组类型就转为对象类型
    private JsonNode convertArrayNodeToJsonNode(JsonNode node, ShopifyRequest request, CharacterCountUtils counter, CharacterCountUtils translatedCounter, TranslateResourceDTO translateResource) {
        if (node.isObject()) {
            return countObjectNode((ObjectNode) node, request, counter, translatedCounter, translateResource);
        } else if (node.isArray()) {
            return countArrayNode((ArrayNode) node, request, counter, translatedCounter, translateResource);
        } else {
            return node;
        }
    }

    //如果node不为ArrayNode将其转为JsonNode
    private JsonNode countArrayNode(ArrayNode arrayNode, ShopifyRequest request, CharacterCountUtils counter, CharacterCountUtils translatedCounter, TranslateResourceDTO translateResource) {
        for (int i = 0; i < arrayNode.size(); i++) {
            JsonNode element = arrayNode.get(i);
            arrayNode.set(i, convertArrayNodeToJsonNode(element, request, counter, translatedCounter, translateResource));
        }
        return arrayNode;
    }

    //遍历objectNode节点，当符合条件时进入下一个方法
    private JsonNode countObjectNode(ObjectNode objectNode, ShopifyRequest request, CharacterCountUtils counter, CharacterCountUtils translatedCounter, TranslateResourceDTO translateResource) {
        objectNode.fieldNames().forEachRemaining(fieldName -> {
            JsonNode fieldValue = objectNode.get(fieldName);
            if ("nodes".equals(fieldName)) {
                countTranslationsResourceId((ArrayNode) fieldValue, counter, translatedCounter, translateResource);
                //当遇到translations时
            } else {
                objectNode.set(fieldName, convertArrayNodeToJsonNode(fieldValue, request, counter, translatedCounter, translateResource));
            }
        });
        return objectNode;
    }

    //对符合条件的resourceId进行计数

    //对符合条件的 resourceId 进行计数。
    @Async
    public void countTranslationsResourceId(ArrayNode contentNode, CharacterCountUtils counter, CharacterCountUtils translatedCounter, TranslateResourceDTO translateResource) {
        for (JsonNode contentItem : contentNode) {
            ObjectNode contentItemNode = (ObjectNode) contentItem;
            JsonNode translationsNode = contentItemNode.path("translations");
            JsonNode translatableContentNode = contentItemNode.path("translatableContent");
            if (TranslateConstants.ONLINE_STORE_THEME.equals(translateResource.getResourceType()) || TranslateConstants.SHOP.equals(translateResource.getResourceType())) {
                // 当资源类型为 ONLINE_STORE_THEME 时，调用专门的计数方法
                countThemeData(translationsNode, translatableContentNode, counter, translatedCounter);
            } else if (TranslateConstants.METAFIELD.equals(translateResource.getResourceType())) {
                countMetafieldData(translationsNode, translatableContentNode, counter, translatedCounter);
            } else {
                // 处理其他类型的数据
                countNonThemeData(translationsNode, translatableContentNode, counter, translatedCounter);
            }
        }
    }

    //计数 ONLINE_STORE_THEME 类型的资源数据。
    private void countThemeData(JsonNode translationsNode, JsonNode translatableContentNode, CharacterCountUtils counter, CharacterCountUtils translatedCounter) {
        if (translatableContentNode != null) {

            // 遍历可翻译内容节点，增加未翻译的字符数
            for (JsonNode contentItem : translatableContentNode) {
                counter.addChars(1);
            }
        }

        if (translationsNode != null) {
            // 遍历翻译内容节点，增加已翻译的字符数
            for (JsonNode contentItem : translationsNode) {
//                AppInsightsUtils.trackTrace("translated: " + contentItem);
                ObjectNode contentItemNode = (ObjectNode) contentItem;
                if (contentItemNode != null) {
                    translatedCounter.addChars(1);
                }
            }
        }

    }

    //计数 METAFIELD 类型的资源数据
    private void countMetafieldData(JsonNode translationsNode, JsonNode translatableContentNode, CharacterCountUtils counter, CharacterCountUtils translatedCounter) {
        if (translatableContentNode != null) {

            // 遍历可翻译内容节点，增加未翻译的字符数
            for (JsonNode contentItem : translatableContentNode) {
                ObjectNode contentItemNode = (ObjectNode) contentItem;
                // 跳过 value 为空的项

                if (contentItemNode == null) {
                    continue;
                }

                String value;
                String key;
                String type;
                try {
                    JsonNode valueNode = contentItemNode.path("value");
                    if (valueNode == null) {
                        continue;
                    }
                    value = contentItemNode.path("value").asText(null);
                    key = contentItemNode.path("key").asText(null);
                    type = contentItemNode.path("type").asText(null);
                    if (value == null) {
                        continue;  // 跳过当前项
                    }
                    if (value.matches("\\p{Zs}+")) {
                        continue;
                    }
                    if (value.trim().isEmpty()) {
                        continue;
                    }
                } catch (Exception e) {
                    AppInsightsUtils.trackTrace("FatalException 失败的原因： " + e.getMessage());
                    translatedCounter.addChars(1);
                    continue;
                }
                if (key.contains("metafield:") || key.contains("color")
                        || key.contains("formId:") || key.contains("phone_text") || key.contains("email_text")
                        || key.contains("carousel_easing") || key.contains("_link") || key.contains("general") || key.contains("css:")
                        || key.contains("icon:") || "FILE_REFERENCE".equals(type) || "LINK".equals(type)
                        || "LIST_FILE_REFERENCE".equals(type) || "LIST_LINK".equals(type)
                        || type.equals(("LIST_URL"))
                ) {
                    translatedCounter.addChars(1);
                }

                //如果是METAFIELD模块的数据
                if (TranslateConstants.SINGLE_LINE_TEXT_FIELD.equals(type) && !JsoupUtils.isHtml(value)) {
                    //纯数字字母符号 且有两个  标点符号 以#开头，长度为10 不翻译
                    if (StringUtils.isValidString(value)) {
                        translatedCounter.addChars(1);
                    }
                } else if (!TranslateConstants.LIST_SINGLE_LINE_TEXT_FIELD.equals(type)) {
                    translatedCounter.addChars(1);
                }
                counter.addChars(1);
            }
        }

        if (translationsNode != null) {
            // 遍历翻译内容节点，增加已翻译的字符数
            for (JsonNode contentItem : translationsNode) {
                ObjectNode contentItemNode = (ObjectNode) contentItem;
                if (contentItemNode != null) {
                    translatedCounter.addChars(1);
                }
            }
        }
    }

    //计数非 ONLINE_STORE_THEME 类型的资源数据。
    private void countNonThemeData(JsonNode translationsNode, JsonNode translatableContentNode, CharacterCountUtils counter, CharacterCountUtils translatedCounter) {
//        AppInsightsUtils.trackTrace("translatableContentNode: " + translatableContentNode);
        if (!translatableContentNode.isEmpty()) {
            counter.addChars(1);
        } else {
            return;
        }
        if (translationsNode != null && !translationsNode.isEmpty()) {
            // 如果翻译内容节点不为空，则增加已翻译的字符数
            translatedCounter.addChars(1);
        }
    }

    // 递归处理下一页数据
    private void countHandlePagination(JsonNode translatedRootNode, ShopifyRequest request, CharacterCountUtils counter, TranslateResourceDTO translateResourceDTO, CharacterCountUtils translatedCounter) {
        // 获取translatableResources节点下的pageInfo节点
        JsonNode pageInfoNode = translatedRootNode.path("translatableResources").path("pageInfo");

        // 检查是否存在非空的hasNextPage和endCursor
        boolean hasNextPage = pageInfoNode.hasNonNull("hasNextPage") && pageInfoNode.path("hasNextPage").asBoolean();
        String endCursor = pageInfoNode.hasNonNull("endCursor") ? pageInfoNode.path("endCursor").asText(null) : null;

        // 如果有下一页，则更新translateResourceDTO并请求下一页
        if (hasNextPage && endCursor != null) {
            translateResourceDTO.setAfter(endCursor);
            countNextPage(request, counter, translateResourceDTO, translatedCounter);
        }
    }

    // 递归处理下一页数据
    private JsonNode countNextPage(ShopifyRequest request, CharacterCountUtils counter, TranslateResourceDTO translateResource, CharacterCountUtils translatedCounter) {
        JsonNode nextPageData = fetchNextPage(translateResource, request);
        JsonNode countNextPage = convertArrayNodeToJsonNode(nextPageData, request, counter, translatedCounter, translateResource);

        if (hasNextPage(countNextPage)) {
            String newEndCursor = getEndCursor(countNextPage);
            translateResource.setAfter(newEndCursor);
            return countNextPage(request, counter, translateResource, translatedCounter);
        }

        return countNextPage;
    }

    //从数据库中获取items数据
    public BaseResponse<Object> getItemsByShopName(ResourceTypeRequest request) {
        ShopifyRequest shopifyRequest = TypeConversionUtils.resourceTypeRequestToShopifyRequest(request);
        List<ItemsDO> itemsRequests = itemsService.readItemsInfo(shopifyRequest);
        Map<String, ItemsDO> itemMap = itemsRequests.stream()
                .collect(Collectors.toMap(ItemsDO::getItemName, item -> new ItemsDO(item.getItemName(), item.getTotalNumber(), item.getTranslatedNumber(), item.getTarget(), item.getStatus())));
        if (itemsRequests.isEmpty()) {
            getTranslationItemsInfo(request);
            shopifyRequest = TypeConversionUtils.resourceTypeRequestToShopifyRequest(request);
            itemsRequests = itemsService.readItemsInfo(shopifyRequest);
            itemMap = itemsRequests.stream()
                    .collect(Collectors.toMap(ItemsDO::getItemName, item -> new ItemsDO(item.getItemName(), item.getTotalNumber(), item.getTranslatedNumber(), item.getTarget(), item.getStatus())));
        }
        return new BaseResponse<>().CreateSuccessResponse(itemMap);
    }

    /**
     * 根据shopifyRequest，key和method获取对应模块的token。
     *
     * @param shopifyRequest 请求对象，包含shopName、target、source，accessToken等信息
     * @param key            模块类型
     * @param method         调用方式
     */
    @Async
    public void insertInitialByTranslation(ShopifyRequest shopifyRequest, String key, String method) {
        int tokens = 0;

        for (TranslateResourceDTO translateResourceDTO : TOKEN_MAP.get(key)) {
            int token = getTotalWords(shopifyRequest, method, translateResourceDTO);
            tokens += token;
        }

        if ("collection".equals(key) || "notifications".equals(key) || "theme".equals(key)
                || "article".equals(key) || "blog_titles".equals(key) || "filters".equals(key) || "metaobjects".equals(key)
                || "pages".equals(key) || "products".equals(key) || "navigation".equals(key)
                || "shop".equals(key) || "shipping".equals(key) || "delivery".equals(key) || "metadata".equals(key) || "policies".equals(key)) {
            UpdateWrapper<UserTypeTokenDO> updateWrapper = new UpdateWrapper<>();
            updateWrapper.eq(TranslateConstants.SHOP_NAME, shopifyRequest.getShopName());

            // 根据传入的列名动态设置更新的字段
            updateWrapper.set(key, tokens);
            userTypeTokenService.update(null, updateWrapper);
        } else {
            throw new IllegalArgumentException("Invalid column name");
        }
    }

    public BaseResponse<Object> getUserSubscriptionPlan(String shopName) {
        UserSubscriptionsDO userSubscriptionsDO = userSubscriptionsService.getDataByShopName(shopName);
        if (userSubscriptionsDO == null) {
            AppInsightsUtils.trackTrace("getUserSubscriptionPlan 用户获取的数据失败： " + shopName);
            return new BaseResponse<>().CreateErrorResponse("userSubscriptionsDO is null");
        }

        SubscriptionVO subscriptionVO = buildBaseSubscriptionVO(userSubscriptionsDO);
        if (subscriptionVO == null) {
            return new BaseResponse<>().CreateErrorResponse("parsePlanType is null");
        }

        Integer feeType = userSubscriptionsDO.getFeeType() != null ? userSubscriptionsDO.getFeeType() : 0;
        subscriptionVO.setFeeType(feeType);

        BaseResponse<Object> whiteListResponse = checkWhiteList(shopName, subscriptionVO, feeType);
        if (whiteListResponse != null) {
            return whiteListResponse;
        }

        Integer planId = userSubscriptionsDO.getPlanId();
        // 免费计划：1/2/8 不展示周期与额度发放时间
        if (planId == 1 || planId == 2 || planId == 8) {
            subscriptionVO.setCurrentPeriodEnd(null);
            subscriptionVO.setFeeType(0);
            return new BaseResponse<>().CreateSuccessResponse(subscriptionVO);
        }

        // 免费试用计划 7
        if (planId == 7) {
            UserTrialsDO userTrialsDO = iUserTrialsService.getDataByShopName(shopName);
            subscriptionVO.setUserSubscriptionPlan(7);
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            subscriptionVO.setCurrentPeriodEnd((userTrialsDO.getTrialEnd().toLocalDateTime().format(formatter)));
            subscriptionVO.setFeeType(feeType);
            return new BaseResponse<>().CreateSuccessResponse(subscriptionVO);
        }

        // 付费计划：需 CharsOrders（Shopify 订阅 id）查周期与额度发放时间
        CharsOrdersDO charsOrdersDO = iCharsOrdersService.listDataByShopNameAndStatus(shopName, "ACTIVE")
                .stream()
                .filter(order -> order.getId() != null && order.getId().contains("AppSubscription"))
                .findFirst()
                .orElse(null);

        if (charsOrdersDO == null) {
            return new BaseResponse<>().CreateErrorResponse("charsOrdersDO is null");
        }

        // 付费计划：从 SubscriptionQuotaRecord 按 subscription_id（对应 plan）取最新额度发放时间
        setQuotaIssuedAtBySubscriptionId(subscriptionVO, charsOrdersDO.getId());

        ShopifyPeriodResult periodResult = fetchCurrentPeriodEndFromShopify(shopName, charsOrdersDO);
        if (periodResult.isNodeCancelled()) {
            subscriptionVO.setFeeType(0);
            subscriptionVO.setUserSubscriptionPlan(2);
        } else {
            subscriptionVO.setFeeType(feeType);
            subscriptionVO.setUserSubscriptionPlan(planId);
        }
        return new BaseResponse<>().CreateSuccessResponse(subscriptionVO);
    }

    /** 根据用户订阅构建基础 VO（planType、userSubscriptionPlan） */
    private SubscriptionVO buildBaseSubscriptionVO(UserSubscriptionsDO userSubscriptionsDO) {
        String planName = iSubscriptionPlansService.getDataByPlanId(userSubscriptionsDO.getPlanId()).getPlanName();
        String parsePlanType = parsePlanName(planName);
        if (parsePlanType == null) {
            return null;
        }
        SubscriptionVO vo = new SubscriptionVO();
        vo.setPlanType(parsePlanType);
        vo.setUserSubscriptionPlan(userSubscriptionsDO.getPlanId());
        return vo;
    }

    /** 根据 Shopify 订阅 id 从 SubscriptionQuotaRecord 取最新额度发放时间并写入 VO */
    private void setQuotaIssuedAtBySubscriptionId(SubscriptionVO subscriptionVO, String subscriptionId) {
        SubscriptionQuotaRecordDO latest = subscriptionQuotaRecordRepo.getLatestBySubscriptionId(subscriptionId);

        if (latest != null && latest.getCreatedAt() != null) {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            LocalDateTime periodEnd = latest.getCreatedAt().toLocalDateTime().plusDays(31);
            subscriptionVO.setCurrentPeriodEnd(periodEnd.format(formatter));
        }
    }

    /** 从 Shopify 拉取当前周期结束时间及 node 是否为空（计划取消） */
    private ShopifyPeriodResult fetchCurrentPeriodEndFromShopify(String shopName, CharsOrdersDO charsOrdersDO) {
        UsersDO usersDO = iUsersService.getUserByName(shopName);
        String query = ShopifyRequestUtils.getSubscriptionQuery(charsOrdersDO.getId());
        String infoByShopify = null;
        try {
            infoByShopify = CompletableFuture
                    .supplyAsync(() -> getShopifyData(shopName, usersDO.getAccessToken(), TranslateConstants.API_VERSION_LAST, query))
                    .get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            AppInsightsUtils.trackTrace("FatalException task getUserSubscriptionPlan Shopify 请求异常： " + shopName + " " + e.getMessage());
        }
        if (infoByShopify == null || infoByShopify.isEmpty()) {
            return new ShopifyPeriodResult(null, false);
        }
        JSONObject root = JSON.parseObject(infoByShopify);
        JSONObject node = root.getJSONObject("node");
        if (node == null || node.isEmpty()) {
            return new ShopifyPeriodResult(null, true);
        }
        return new ShopifyPeriodResult(node.getString("currentPeriodEnd"), false);
    }

    /** 仅用于 getUserSubscriptionPlan 的 Shopify 周期结果 */
    private static class ShopifyPeriodResult {
        private final String currentPeriodEnd;
        private final boolean nodeCancelled;

        ShopifyPeriodResult(String currentPeriodEnd, boolean nodeCancelled) {
            this.currentPeriodEnd = currentPeriodEnd;
            this.nodeCancelled = nodeCancelled;
        }

        String getCurrentPeriodEnd() {
            return currentPeriodEnd;
        }

        boolean isNodeCancelled() {
            return nodeCancelled;
        }
    }

    private static BaseResponse<Object> checkWhiteList(String shopName, SubscriptionVO subscriptionVO, Integer feeType) {
        if ("5bf8b3.myshopify.com".equals(shopName) || "c5ba7c-7c.myshopify.com".equals(shopName) || "digitevil.myshopify.com".equals(shopName)) {
            subscriptionVO.setUserSubscriptionPlan(6);
            subscriptionVO.setCurrentPeriodEnd(null);
            subscriptionVO.setFeeType(feeType);
            subscriptionVO.setPlanType("Premium");
            return new BaseResponse<>().CreateSuccessResponse(subscriptionVO);
        }
        return null;
    }

    /**
     * 判断计划名称 最终输出：Free || Basic || Pro || Premium
     */
    public static String parsePlanName(String planName) {
        if (planName == null) {
            return null;
        }
        if (planName.contains("Free")) {
            return "Free";
        }
        if (planName.contains("Basic")) {
            return "Basic";
        }
        if (planName.contains("Pro")) {
            return "Pro";
        }
        if (planName.contains("Premium")) {
            return "Premium";
        }
        return null;
    }
}

