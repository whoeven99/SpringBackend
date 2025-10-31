package com.bogdatech.logic;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.bogdatech.Service.*;
import com.bogdatech.config.LanguageFlagConfig;
import com.bogdatech.entity.DO.*;
import com.bogdatech.enums.ErrorEnum;
import com.bogdatech.exception.ClientException;
import com.bogdatech.integration.ShopifyHttpIntegration;
import com.bogdatech.integration.TestingEnvironmentIntegration;
import com.bogdatech.logic.redis.TranslationParametersRedisService;
import com.bogdatech.model.controller.request.*;
import com.bogdatech.model.controller.response.BaseResponse;
import com.bogdatech.requestBody.ShopifyRequestBody;
import com.bogdatech.utils.CharacterCountUtils;
import com.bogdatech.utils.JsonUtils;
import com.bogdatech.utils.StringUtils;
import com.bogdatech.utils.TypeConversionUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import static com.bogdatech.constants.TranslateConstants.*;
import static com.bogdatech.entity.DO.TranslateResourceDTO.RESOURCE_MAP;
import static com.bogdatech.entity.DO.TranslateResourceDTO.TOKEN_MAP;
import static com.bogdatech.integration.ALiYunTranslateIntegration.calculateBaiLianToken;
import static com.bogdatech.integration.ShopifyHttpIntegration.getInfoByShopify;
import static com.bogdatech.integration.ShopifyHttpIntegration.registerTransaction;
import static com.bogdatech.integration.TestingEnvironmentIntegration.sendShopifyPost;
import static com.bogdatech.logic.TranslateService.OBJECT_MAPPER;
import static com.bogdatech.logic.redis.TranslationParametersRedisService.WRITE_TOTAL;
import static com.bogdatech.logic.redis.TranslationParametersRedisService.generateWriteStatusKey;
import static com.bogdatech.utils.CaseSensitiveUtils.appInsights;
import static com.bogdatech.utils.JsonUtils.*;
import static com.bogdatech.utils.JsoupUtils.isHtml;
import static com.bogdatech.utils.JudgeTranslateUtils.*;
import static com.bogdatech.utils.StringUtils.isValueBlank;

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
    private UserTranslationDataService userTranslationDataService;
    @Autowired
    private RedisTranslateUserStatusService redisTranslateUserStatusService;
    @Autowired
    private TranslationParametersRedisService translationParametersRedisService;
    @Autowired
    private ShopifyHttpIntegration shopifyHttpIntegration;
    @Autowired
    private TestingEnvironmentIntegration testingEnvironmentIntegration;

    ShopifyRequestBody shopifyRequestBody = new ShopifyRequestBody();

    //封装调用云服务器实现获取shopify数据的方法
    public static String getShopifyDataByCloud(CloudServiceRequest cloudServiceRequest) {
        String requestBody = JsonUtils.objectToJson(cloudServiceRequest);
        return sendShopifyPost("test123", requestBody);
    }

    public String getShopifyData(String shopName, String accessToken, String apiVersion, String query) {
        String env = System.getenv("ApplicationEnv");
        if ("prod".equals(env) || "dev".equals(env)) {
            return shopifyHttpIntegration.getInfoByShopify(shopName, accessToken, apiVersion, query);
        } else {
            // 本地调用shopify
            return testingEnvironmentIntegration.sendShopifyPost("test123", shopName, accessToken, apiVersion, query);
        }
    }

    // 包装一层，用于获取用户实际未翻译和部分翻译的语言数
    public int getUnTranslatedToken(ShopifyRequest request, String method, TranslateResourceDTO translateResource, String source) {
        // 获取该用户所有的未翻译和部分翻译的所有token数据
        List<String> all = redisTranslateUserStatusService.getAll(request.getShopName(), source);
        int allLanguage = 1;
        for (String status : all) {
            if ("0".equals(status) || "3".equals(status) || "7".equals(status)) {
                allLanguage++;
            }
        }
        if (allLanguage > 1) {
            allLanguage -= 1;
        }
        int totalWords = getTotalWords(request, method, translateResource);
        return totalWords * allLanguage;
    }

    // 获得翻译前一共需要消耗的字符数
    public int getTotalWords(ShopifyRequest request, String method, TranslateResourceDTO translateResource) {
        CharacterCountUtils counter = new CharacterCountUtils();
        translateResource.setTarget(request.getTarget());
        String query = shopifyRequestBody.getFirstQuery(translateResource);

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
        Set<TranslateTextDO> needTranslatedData = translatedAllDataParse(rootNode, request.getShopName());
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

    //将String数据转化为JsonNode数据
    public JsonNode ConvertStringToJsonNode(String infoByShopify, TranslateResourceDTO translateResource) {
        JsonNode rootNode = null;
        try {
            rootNode = OBJECT_MAPPER.readTree(infoByShopify);
        } catch (JsonProcessingException e) {
            appInsights.trackException(e);
            appInsights.trackTrace("解析JSON数据失败 errors： " + translateResource);
        }
        return rootNode;
    }

    //对node节点进行判断，是否调用方法
    public void translateObjectNode(JsonNode objectNode, ShopifyRequest request, CharacterCountUtils counter, TranslateResourceDTO translateResource) {
        //1, 获取所有要翻译的数据, 将单个语言的所有数据都统计
        Set<TranslateTextDO> needTranslatedData = translatedAllDataParse(objectNode, request.getShopName());
        if (needTranslatedData == null) {
            return;
        }
        //2，在此基础上筛选掉规则以外的数据,并计数
        filterNeedTranslateSetAndCount(translateResource.getResourceType(), true, needTranslatedData, counter);
    }

    /**
     * 遍历needTranslatedSet, 对Set集合进行通用规则的筛选，返回筛选后的数据
     */
    public void filterNeedTranslateSetAndCount(String modeType, boolean handleFlag, Set<TranslateTextDO> needTranslateSet, CharacterCountUtils counter) {
        for (TranslateTextDO translateTextDO : needTranslateSet) {
            String value = translateTextDO.getSourceText();

            // 当 value 为空时跳过
            if (!isValueBlank(value)) {
                continue;
            }

            String type = translateTextDO.getTextType();

            // 如果是特定类型，也从集合中移除
            if ("FILE_REFERENCE".equals(type) || "LINK".equals(type)
                    || "LIST_FILE_REFERENCE".equals(type) || "LIST_LINK".equals(type)
                    || "LIST_URL".equals(type)
                    || "JSON".equals(type)
                    || "JSON_STRING".equals(type) || isJson(value)
            ) {
                continue;
            }

            String key = translateTextDO.getTextKey();
            //如果handleFlag为false，则跳过
            if (type.equals(URI) && "handle".equals(key)) {
                if (!handleFlag) {
                    continue;
                }
            }

            //通用的不翻译数据
            if (!generalTranslate(key, value)) {
                continue;
            }

            //如果是theme模块的数据
            if (TRANSLATABLE_RESOURCE_TYPES.contains(modeType)) {
                //如果是html放html文本里面
                if (isHtml(value)) {
                    continue;
                }

                //对key中包含slide  slideshow  general.lange 的数据不翻译
                if (key.contains("slide") || key.contains("slideshow") || key.contains("general.lange")) {
                    continue;
                }

                if (key.contains("block") && key.contains("add_button_selector")) {
                    continue;
                }
                //对key中含section和general的做key值判断
                if (GENERAL_OR_SECTION_PATTERN.matcher(key).find()) {
                    //进行白名单的确认
                    if (whiteListTranslate(key)) {
                        counter.addChars(calculateBaiLianToken(translateTextDO.getSourceText()));
                        continue;
                    }

                    //如果包含对应key和value，则跳过
                    if (!shouldTranslate(key, value)) {
                        continue;
                    }
                }
                counter.addChars(calculateBaiLianToken(translateTextDO.getSourceText()));
                continue;
            }
            //对METAOBJECT字段翻译
            if (modeType.equals(METAOBJECT)) {
                if (isJson(value)) {
                    continue;
                }
                counter.addChars(calculateBaiLianToken(translateTextDO.getSourceText()));
                continue;
            }

            //对METAFIELD字段翻译
            if (modeType.equals(METAFIELD)) {
                //如UXxSP8cSm，UgvyqJcxm。有大写字母和小写字母的组合。有大写字母，小写字母和数字的组合。 10位 字母和数字不翻译
                if (SUSPICIOUS_PATTERN.matcher(value).matches() || SUSPICIOUS2_PATTERN.matcher(value).matches()) {
                    continue;
                }
                if (!metaTranslate(value)) {
                    continue;
                }
                //如果是base64编码的数据，不翻译
                if (BASE64_PATTERN.matcher(value).matches()) {
                    continue;
                }
                if (isJson(value)) {
                    continue;
                }
                counter.addChars(calculateBaiLianToken(translateTextDO.getSourceText()));
                continue;
            }
            counter.addChars(calculateBaiLianToken(translateTextDO.getSourceText()));
        }
    }

    /**
     * 解析shopifyData数据，将所有数据都存Set里面
     */
    public Set<TranslateTextDO> translatedAllDataParse(JsonNode shopDataJson, String shopName) {
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
                    doubleTranslateTextDOSet.addAll(needTranslatedSet(nodeElement));
                }
            }
        } catch (Exception e) {
            appInsights.trackException(e);
            appInsights.trackTrace("clickTranslation 用户 " + shopName + " 分析数据失败 errors : " + e);
        }
        return doubleTranslateTextDOSet;
    }

    /**
     * 解析一下所有Set
     */
    public Set<TranslateTextDO> needTranslatedSet(JsonNode shopDataJson) {
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
                Map<String, TranslateTextDO> partTranslateTextDOMap = extractTranslationsByResourceId(shopDataJson);
                Set<TranslateTextDO> needTranslatedSet = new HashSet<>(partTranslateTextDOMap.values());
                return new HashSet<>(needTranslatedSet);
            }
        }
        return new HashSet<>();
    }

    /**
     * 同一个resourceId下的获取所有数据
     */
    public static Map<String, TranslateTextDO> extractTranslationsByResourceId(JsonNode shopDataJson) {
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
                translateTextDO.setTextType(translation.path("type").asText(null));
                translations.put(key, translateTextDO);
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


    //修改getTestQuery里面的testQuery，用获取后的的查询语句进行查询
    public JsonNode fetchNextPage(TranslateResourceDTO translateResource, ShopifyRequest request) {
        CloudServiceRequest cloudServiceRequest = new CloudServiceRequest();
        cloudServiceRequest.setShopName(request.getShopName());
        cloudServiceRequest.setAccessToken(request.getAccessToken());
        cloudServiceRequest.setTarget(request.getTarget());

        ShopifyRequestBody shopifyRequestBody = new ShopifyRequestBody();
        String query = shopifyRequestBody.getAfterQuery(translateResource);
        cloudServiceRequest.setBody(query);
        String infoByShopify = null;
        try {
            String env = System.getenv("ApplicationEnv");
            if ("prod".equals(env) || "dev".equals(env)) {
                infoByShopify = String.valueOf(getInfoByShopify(request, query));
            } else {
                infoByShopify = getShopifyDataByCloud(cloudServiceRequest);
            }
        } catch (Exception e) {
            //如果出现异常，则跳过, 翻译其他的内容
            appInsights.trackTrace("fetchNextPage errors : " + e.getMessage());
        }
        if (infoByShopify == null || infoByShopify.isEmpty()) {
            return null;
        }
        JsonNode rootNode;
        try {
            rootNode = OBJECT_MAPPER.readTree(infoByShopify);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        return rootNode;
    }

    //修改单个文本数据
    public String updateShopifySingleData(RegisterTransactionRequest registerTransactionRequest) {
        ShopifyRequest shopifyRequest = new ShopifyRequest();
        shopifyRequest.setShopName(registerTransactionRequest.getShopName());
        shopifyRequest.setAccessToken(registerTransactionRequest.getAccessToken());
        shopifyRequest.setTarget(registerTransactionRequest.getTarget());
        Map<String, Object> variables = getVariables(registerTransactionRequest);
        return registerTransaction(shopifyRequest, variables);
    }

    //修改多个文本数据
    public String updateShopifyManyData(ShopifyRequest shopifyRequest, List<RegisterTransactionRequest> registerTransactionRequests) {
        // 将List<RegisterTransactionRequest>处理成variables数据
        Map<String, Object> variables = toVariables(registerTransactionRequests);
        return registerTransaction(shopifyRequest, variables);
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
    static Map<String, Object> toVariables(List<RegisterTransactionRequest> registerTransactionRequest) {
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
        appInsights.trackTrace("updateShopifyDataByTranslateTextRequest " + registerTransactionRequest.getShopName() + " 传入的值： " + registerTransactionRequest);
        String string = updateShopifySingleData(registerTransactionRequest);
        appInsights.trackTrace("updateShopifyDataByTranslateTextRequest " + registerTransactionRequest.getShopName() + " 返回的值： " + string);
        if (string.contains("\"value\":")) {
            return new BaseResponse<>().CreateSuccessResponse(200);
        } else {
            return new BaseResponse<>().CreateErrorResponse(getMessage(string));
        }
    }

    //根据前端传来的值，返回对应的图片信息
    public Map<String, Object> getImageInfo(String[] strings) {
        Map<String, Object> imageInfo = new HashMap<>();
        for (String string : strings) {
            try {
                Field field = LanguageFlagConfig.class.getField(string.toUpperCase());
                imageInfo.put(string, field.get(null));
            } catch (NoSuchFieldException | IllegalAccessException e) {
                appInsights.trackException(e);
                throw new RuntimeException(e);
            }
        }
        return imageInfo;
    }

    //计算被翻译项的总数和已翻译的个数
    public Map<String, Map<String, Object>> getTranslationItemsInfo(ResourceTypeRequest request) {
        ShopifyRequest shopifyRequest = TypeConversionUtils.resourceTypeRequestToShopifyRequest(request);
        CloudServiceRequest cloudServiceRequest = TypeConversionUtils.shopifyToCloudServiceRequest(shopifyRequest);
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
                String query = shopifyRequestBody.getFirstQuery(resource);
                cloudServiceRequest.setBody(query);
                String infoByShopify;
                try {
                    String env = System.getenv("ApplicationEnv");
                    if ("prod".equals(env) || "dev".equals(env)) {
                        infoByShopify = String.valueOf(getInfoByShopify(shopifyRequest, query));
                    } else {
                        infoByShopify = getShopifyDataByCloud(cloudServiceRequest);
                    }
                } catch (Exception e) {
                    //如果出现异常，则跳过, 翻译其他的内容
                    appInsights.trackTrace("getTranslationItemsInfo errors : " + e.getMessage());
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
            appInsights.trackTrace("getTranslationItemsInfoAll errors : 用户:" + request.getShopName() + " 目标： " + request.getTarget() + "  " + "模块： " + request.getResourceType() + e.getMessage());
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
            if (ONLINE_STORE_THEME.equals(translateResource.getResourceType()) || SHOP.equals(translateResource.getResourceType())) {
                // 当资源类型为 ONLINE_STORE_THEME 时，调用专门的计数方法
                countThemeData(translationsNode, translatableContentNode, counter, translatedCounter);
            } else if (METAFIELD.equals(translateResource.getResourceType())) {
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
//                appInsights.trackTrace("translated: " + contentItem);
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
                    appInsights.trackTrace("失败的原因： " + e.getMessage());
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
                if (SINGLE_LINE_TEXT_FIELD.equals(type) && !isHtml(value)) {
                    //纯数字字母符号 且有两个  标点符号 以#开头，长度为10 不翻译
                    if (StringUtils.isValidString(value)) {
                        translatedCounter.addChars(1);
                    }
                } else if (!LIST_SINGLE_LINE_TEXT_FIELD.equals(type)) {
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
//        appInsights.trackTrace("translatableContentNode: " + translatableContentNode);
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
            updateWrapper.eq(SHOP_NAME, shopifyRequest.getShopName());

            // 根据传入的列名动态设置更新的字段
            updateWrapper.set(key, tokens);
            userTypeTokenService.update(null, updateWrapper);
        } else {
            throw new IllegalArgumentException("Invalid column name");
        }
    }

    //封装调用云服务器实现将数据存入shopify本地的方法
    public static void saveToShopify(CloudInsertRequest cloudServiceRequest) {
        ShopifyRequest request = new ShopifyRequest();
        request.setShopName(cloudServiceRequest.getShopName());
        request.setAccessToken(cloudServiceRequest.getAccessToken());
        request.setTarget(cloudServiceRequest.getTarget());
        Map<String, Object> body = cloudServiceRequest.getBody();

        try {
            String requestBody = OBJECT_MAPPER.writeValueAsString(cloudServiceRequest);
            String env = System.getenv("ApplicationEnv");
            if ("prod".equals(env) || "dev".equals(env)) {
                String s = registerTransaction(request, body);
                appInsights.trackTrace("saveToShopify 用户： " + cloudServiceRequest.getShopName() + " target: " + cloudServiceRequest.getTarget() + " saveToShopify : " + s);
            } else {
                sendShopifyPost("translate/insertTranslatedText", requestBody);
            }

        } catch (JsonProcessingException | ClientException e) {
            appInsights.trackTrace("saveToShopify " + request.getShopName() + " Failed to save to Shopify errors : " + e.getMessage());
        }
    }

    public void saveToShopify(String translatedValue, Map<String, Object> translation, String resourceId,
                              String shopName, String accessToken, String target, String apiVersion) {
        saveToShopify(translatedValue, translation, resourceId, new ShopifyRequest(shopName, accessToken, target, apiVersion));
    }

    //将翻译后的数据存shopify本地中
    public void saveToShopify(String translatedValue, Map<String, Object> translation, String resourceId, ShopifyRequest request) {
        String shopName = request.getShopName();
        String accessToken = request.getAccessToken();
        String target = request.getTarget();
        String apiVersion = request.getApiVersion();
        try {
            // 创建一个新的映射，避免修改原始的 translation
            Map<String, Object> newTranslation = new HashMap<>(translation);
            newTranslation.put("value", translatedValue);

            // 构建 variables 映射
            Map<String, Object> variables = new HashMap<>();
            variables.put("resourceId", resourceId);

            // 创建 translations 数组
            Object[] translations = new Object[]{newTranslation};
            variables.put("translations", translations);
//        //将翻译后的内容发送mq，通过ShopifyAPI记录到shopify本地
            CloudInsertRequest cloudServiceRequest = new CloudInsertRequest(shopName, accessToken, apiVersion, target, variables);
            String json = objectToJson(cloudServiceRequest);

            // 存到数据库中
            int maxRetries = 3;           // 最大重试次数
            int retryCount = 0;           // 当前重试次数
            long waitTime = 1000;         // 初始等待时间（毫秒） = 1s

            boolean insertFlag;
            while (retryCount < maxRetries) {
                try {
                    insertFlag = userTranslationDataService.insertTranslationData(json, shopName);
                    if (insertFlag) {
                        translationParametersRedisService.addWritingData(generateWriteStatusKey(shopName, target), WRITE_TOTAL, 1L);
                        appInsights.trackTrace("saveToShopify 用户： " + shopName + " target: " + target + " 插入成功 数据是： " + json);
                        break; // 成功就跳出循环
                    } else {
                        throw new RuntimeException("插入返回false");
                    }
                } catch (Exception e1) {
                    retryCount++;
                    if (retryCount >= maxRetries) {
                        appInsights.trackTrace("saveToShopify 已达到最大重试次数，插入失败: " + shopName + " 要插入的数据" + json + e1.getMessage());
                        break;
                    }

                    appInsights.trackTrace("saveToShopify 第 " + retryCount + " 次插入失败，" +
                            "等待 " + waitTime + " 毫秒后重试..." + " 用户： " + shopName + " 要插入的数据" + json);

                    try {
                        Thread.sleep(waitTime);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }

                    waitTime *= 2; // 等待时间翻倍（指数退避）
                }
            }
        } catch (Exception e2) {
            appInsights.trackTrace("saveToShopify 每日须看 " + shopName + " save to Shopify errors : " + e2.getMessage());
        }
    }
}

