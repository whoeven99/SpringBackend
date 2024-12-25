package com.bogdatech.logic;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.bogdatech.Service.IItemsService;
import com.bogdatech.Service.ITranslateTextService;
import com.bogdatech.Service.ITranslatesService;
import com.bogdatech.Service.IUserSubscriptionsService;
import com.bogdatech.config.LanguageFlagConfig;
import com.bogdatech.entity.ItemsDO;
import com.bogdatech.entity.TranslateResourceDTO;
import com.bogdatech.entity.TranslateTextDO;
import com.bogdatech.enums.ErrorEnum;
import com.bogdatech.exception.ClientException;
import com.bogdatech.integration.ShopifyHttpIntegration;
import com.bogdatech.integration.TestingEnvironmentIntegration;
import com.bogdatech.model.controller.request.*;
import com.bogdatech.model.controller.response.BaseResponse;
import com.bogdatech.requestBody.ShopifyRequestBody;
import com.bogdatech.utils.CharacterCountUtils;
import com.bogdatech.utils.TypeConversionUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static com.bogdatech.entity.TranslateResourceDTO.ALL_RESOURCES;
import static com.bogdatech.entity.TranslateResourceDTO.RESOURCE_MAP;
import static com.bogdatech.enums.ErrorEnum.*;
import static com.bogdatech.utils.CalculateTokenUtils.calculateToken;

@Component
public class ShopifyService {


    private final ShopifyHttpIntegration shopifyApiIntegration;
    private final TestingEnvironmentIntegration testingEnvironmentIntegration;
    private final ITranslateTextService translateTextService;
    private final IUserSubscriptionsService userSubscriptionsService;
    private final IItemsService itemsService;
    private final ITranslatesService translatesService;

    @Autowired
    public ShopifyService(
            ShopifyHttpIntegration shopifyApiIntegration,
            TestingEnvironmentIntegration testingEnvironmentIntegration,
            ITranslateTextService translateTextService,
            IUserSubscriptionsService userSubscriptionsService,
            IItemsService itemsService,
            ITranslatesService translatesService
    ) {
        this.shopifyApiIntegration = shopifyApiIntegration;
        this.testingEnvironmentIntegration = testingEnvironmentIntegration;
        this.translateTextService = translateTextService;
        this.userSubscriptionsService = userSubscriptionsService;
        this.itemsService = itemsService;
        this.translatesService = translatesService;
    }

    //    private final TelemetryClient appInsights = new TelemetryClient();
    ShopifyRequestBody shopifyRequestBody = new ShopifyRequestBody();

    //封装调用云服务器实现获取shopify数据的方法
    public String getShopifyData(CloudServiceRequest cloudServiceRequest) {
        // 使用 ObjectMapper 将对象转换为 JSON 字符串
        ObjectMapper objectMapper = new ObjectMapper();
        String string;
        try {
            String requestBody = objectMapper.writeValueAsString(cloudServiceRequest);
            string = testingEnvironmentIntegration.sendShopifyPost("test123", requestBody);
        } catch (JsonProcessingException e) {
            throw new ClientException(SHOPIFY_CONNECT_ERROR.getErrMsg());
        }
        return string;
    }

    //封装调用云服务器实现更新shopify数据的方法
    public String updateShopifyData(RegisterTransactionRequest registerTransactionRequest) {
        // 使用 ObjectMapper 将对象转换为 JSON 字符串
        ObjectMapper objectMapper = new ObjectMapper();
        String string;
        JSONObject translationsArray;
        JSONArray translationsObject;
        try {
            String requestBody = objectMapper.writeValueAsString(registerTransactionRequest);
            string = testingEnvironmentIntegration.sendShopifyPost("shopify/updateItem", requestBody);
            JSONObject jsonObject = JSON.parseObject(string);
//            System.out.println("jsonObject: " + jsonObject);
            translationsArray = jsonObject.getJSONObject("translationsRegister");
            translationsObject = translationsArray.getJSONArray("translations");
        } catch (JsonProcessingException e) {
            throw new ClientException(SHOPIFY_RETURN_ERROR.getErrMsg());
        }
        return (String) translationsObject.getJSONObject(0).get("value");
    }

    //获得翻译前一共需要消耗的字符数
    public int getTotalWords(ShopifyRequest request) {
        CharacterCountUtils counter = new CharacterCountUtils();
        CharacterCountUtils translateCounter = new CharacterCountUtils();
        CloudServiceRequest cloudServiceRequest = TypeConversionUtils.shopifyToCloudServiceRequest(request);
//        System.out.println("target: " + request.getTarget());
        for (TranslateResourceDTO translateResource : ALL_RESOURCES) {
            translateResource.setTarget(request.getTarget());
            String query = shopifyRequestBody.getFirstQuery(translateResource);
            cloudServiceRequest.setBody(query);
            String infoByShopify = getShopifyData(cloudServiceRequest);
            countBeforeTranslateChars(infoByShopify, request, translateResource, counter, translateCounter);
            System.out.println("目前统计total的总数是： " + counter.getTotalChars());
        }
        return counter.getTotalChars();
    }

    //计数翻译前所需要的总共的字符数
    @Async
    public void countBeforeTranslateChars(String infoByShopify, ShopifyRequest request, TranslateResourceDTO translateResource, CharacterCountUtils counter, CharacterCountUtils translateCounter) {
        JsonNode rootNode = ConvertStringToJsonNode(infoByShopify, translateResource);
        translateSingleLineTextFieldsRecursively(rootNode, request, counter, translateCounter);
        // 递归处理下一页数据
        handlePagination(rootNode, request, counter, translateResource, translateCounter);
        //打印最后使用的值

    }

    //将String数据转化为JsonNode数据
    public JsonNode ConvertStringToJsonNode(String infoByShopify, TranslateResourceDTO translateResource) {
        System.out.println("现在统计到： " + translateResource.getResourceType());

        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode rootNode;
        try {
            rootNode = objectMapper.readTree(infoByShopify);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        return rootNode;
    }

    //递归遍历JSON树：使用 translateSingleLineTextFieldsRecursively 方法递归地遍历整个 JSON 树，并对 translatableContent 字段进行特别处理。
    private void translateSingleLineTextFieldsRecursively(JsonNode node, ShopifyRequest request, CharacterCountUtils counter, CharacterCountUtils translateCounter) {
        translateObjectNode((ObjectNode) node, request, counter, translateCounter);
    }

    //对node节点进行判断，是否调用方法
    @Async
    public void translateObjectNode(ObjectNode objectNode, ShopifyRequest request, CharacterCountUtils counter, CharacterCountUtils translateCounter) {
        AtomicReference<List<String>> strings = new AtomicReference<>(new ArrayList<>());
        JsonNode translatableResourcesNode = objectNode.path("translatableResources");
        if (!translatableResourcesNode.isObject()) {
            return;
        }
        // 处理 nodes 数组
        JsonNode nodesNode = translatableResourcesNode.path("nodes");
        if (!nodesNode.isArray()) {
            return;
        }
        ArrayNode nodesArray = (ArrayNode) nodesNode;
        for (JsonNode nodeElement : nodesArray) {
            if (!nodeElement.isObject()) {
                continue;
            }
            Iterator<Map.Entry<String, JsonNode>> fields = nodeElement.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                String fieldName = field.getKey();
                JsonNode fieldValue = field.getValue();
                //当translates里面有数据时
//                if ("translations".equals(fieldName)) {
//                    strings.set(counterTranslatedContent((ArrayNode) fieldValue));
//                }
                if ("translatableContent".equals(fieldName)) {
                    translateSingleLineTextFields((ArrayNode) fieldValue, request, counter, translateCounter, strings.get());
                }

            }
        }

    }

    //将已翻译value的key放到list集合中
    private List<String> counterTranslatedContent(ArrayNode node) {
        List<String> translatedContent = new ArrayList<>();
        for (JsonNode contentItem : node) {
            ObjectNode contentItemNode = (ObjectNode) contentItem;
            translatedContent.add(contentItemNode.get("key").asText());
        }
//        System.out.println("List内的数据为： " + translatedContent);
        return translatedContent;
    }

    //如果node不为ArrayNode将其转为JsonNode
    private JsonNode translateArrayNode(ArrayNode arrayNode, ShopifyRequest request, CharacterCountUtils counter, CharacterCountUtils translateCounter) {
        for (int i = 0; i < arrayNode.size(); i++) {
            JsonNode element = arrayNode.get(i);
//            arrayNode.set(i, translateSingleLineTextFieldsRecursively(element, request, counter, translateCounter));
        }
        return arrayNode;
    }


    //对符合条件的 value 进行计数
    @Async
    public void translateSingleLineTextFields(ArrayNode contentNode, ShopifyRequest request, CharacterCountUtils counter, CharacterCountUtils translatedCounter, List<String> translatedContent) {
        for (JsonNode contentItem : contentNode) {
            ObjectNode contentItemNode = (ObjectNode) contentItem;
            //打印当前遍历的值 为什么部分不翻译
            // 跳过 key 为 "handle" 的项
                if ("handle".equals(contentItemNode.get("key").asText())
                        || "JSON".equals(contentItemNode.get("type").asText())
                        || "JSON_STRING".equals(contentItemNode.get("type").asText())
                ) {
                    continue;  // 跳过当前项
                }

//             获取 value
            //处理html的数据
            if ("HTML".equals(contentItemNode.get("type").asText())) {
                Document doc = Jsoup.parse(contentItemNode.get("value").asText());
                Map<Element, List<String>> elementListMap = extractTextsToTranslate(doc);
                translateTexts(elementListMap, counter);
                continue;
            }


            String value = contentItemNode.get("value").asText();
//            counter.addChars(value.length());
            counter.addChars(calculateToken(value, 1));

//            if (translatedContent.contains(contentItemNode.get("key").asText())) {
//                translatedCounter.addChars(value.length());
//            }
        }
    }

    // 提取需要翻译的文本（包括文本和alt属性）
    public Map<Element, List<String>> extractTextsToTranslate(Document doc) {
        Map<Element, List<String>> elementTextMap = new HashMap<>();
        for (Element element : doc.getAllElements()) {
            if (!element.is("script, style")) { // 忽略script和style标签
                List<String> texts = new ArrayList<>();

                // 提取文本
                String text = element.ownText().trim();
                if (!text.isEmpty()) {
                    texts.add(text);
                }

                // 提取 alt 属性
                if (element.hasAttr("alt")) {
                    String altText = element.attr("alt").trim();
                    if (!altText.isEmpty()) {
                        texts.add(altText);
                    }
                }

                if (!texts.isEmpty()) {
                    elementTextMap.put(element, texts); // 记录元素和对应的文本及 alt
                }
            }
        }
        return elementTextMap;
    }

    @Async
    public void translateTexts(Map<Element, List<String>> elementTextMap,
                                                     CharacterCountUtils counter) {
        Map<Element, List<String>> translatedTextMap = new HashMap<>();
        for (Map.Entry<Element, List<String>> entry : elementTextMap.entrySet()) {
            List<String> texts = entry.getValue();

            for (String text : texts) {
                counter.addChars(calculateToken(text, 1));
            }
        }
//        return translatedTextMap;
    }

    // 递归处理下一页数据
    private void handlePagination(JsonNode translatedRootNode, ShopifyRequest request, CharacterCountUtils counter, TranslateResourceDTO translateResourceDTO, CharacterCountUtils translateCounter) {
        // 获取translatableResources节点下的pageInfo节点
        JsonNode pageInfoNode = translatedRootNode.path("translatableResources").path("pageInfo");

        // 检查是否存在非空的hasNextPage和endCursor
        boolean hasNextPage = pageInfoNode.hasNonNull("hasNextPage") && pageInfoNode.get("hasNextPage").asBoolean();
        String endCursor = pageInfoNode.hasNonNull("endCursor") ? pageInfoNode.get("endCursor").asText() : null;

        // 如果有下一页，则更新translateResourceDTO并请求下一页
        if (hasNextPage && endCursor != null) {
            translateResourceDTO.setAfter(endCursor);
            translateNextPage(request, counter, translateResourceDTO, translateCounter);
        }
    }

    // 递归处理下一页数据
    private JsonNode translateNextPage(ShopifyRequest request, CharacterCountUtils counter, TranslateResourceDTO translateResource, CharacterCountUtils translateCounter) {
        JsonNode nextPageData = fetchNextPage(translateResource, request);
        translateSingleLineTextFieldsRecursively(nextPageData, request, counter, translateCounter);

        if (hasNextPage(nextPageData)) {
            String newEndCursor = getEndCursor(nextPageData);
            translateResource.setAfter(newEndCursor);
            return translateNextPage(request, counter, translateResource, translateCounter);
        }

        return nextPageData;
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
        return pageInfo.path("endCursor").asText();
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
        String infoByShopify = getShopifyData(cloudServiceRequest);
        if (infoByShopify == null) {
            throw new IllegalArgumentException(String.valueOf(NETWORK_ERROR));
        }
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode rootNode;
        try {
            rootNode = objectMapper.readTree(infoByShopify);
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
        return shopifyApiIntegration.registerTransaction(shopifyRequest, variables);
    }

    //将修改所需要的数据封装成Map格式
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

    //在UserSubscription表里面添加一个购买了免费订阅计划的用户（商家）
    public BaseResponse<Object> addUserFreeSubscription(UserSubscriptionsRequest request) {
        request.setStatus(1);
        request.setPlanId(1);
        LocalDateTime localDate = LocalDateTime.now();
        String localDateFormat = localDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        LocalDateTime startDate = LocalDateTime.parse(localDateFormat, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        request.setStartDate(startDate);
        request.setEndDate(null);
        //先判断是否有这个数据，没有插入，有了更新
        String userSubscriptionPlan = userSubscriptionsService.getUserSubscriptionPlan(request.getShopName());
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

    //修改shopify本地单条数据 和 更新本地数据库相应数据
    public BaseResponse<Object> updateShopifyDataByTranslateTextRequest(RegisterTransactionRequest registerTransactionRequest) {
        String string = updateShopifyData(registerTransactionRequest);
        if (string == null) {
            throw new ClientException(SHOPIFY_CONNECT_ERROR.getErrMsg());
        }
//        System.out.println("string = " + string);
        TranslateTextRequest request = TypeConversionUtils.registerTransactionRequestToTranslateTextRequest(registerTransactionRequest);
        TranslateTextDO translateTextDO = TypeConversionUtils.registerTransactionRequestToTranslateTextDO(registerTransactionRequest);
//        System.out.println("translateTextDO = " + translateTextDO);
        TranslateTextDO translateTextRequests = translateTextService.getTranslateTextInfo(request);
//        System.out.println("translateTextRequests = " + translateTextRequests);
        int i;
        if (translateTextRequests == null) {
            i = translateTextService.insertTranslateText(translateTextDO);
        } else {
            i = translateTextService.updateTranslateText(request);
        }
        if (i > 0 && string.equals(registerTransactionRequest.getValue())) {
            return new BaseResponse<>().CreateSuccessResponse(200);
        } else {
            return new BaseResponse<>().CreateErrorResponse("insert or update error");
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
//        for (Map.Entry<String, List<TranslateResourceDTO>> resourceList : RESOURCE_MAP.entrySet()) {
        CharacterCountUtils allCounter = new CharacterCountUtils();
        CharacterCountUtils translatedCounter = new CharacterCountUtils();
//            String resourceType = resourceList.getKey();
//            System.out.println("resourceType: " + request.getResourceType());
//            for (TranslateResourceDTO resource : resourceList.getValue()) {
        for (TranslateResourceDTO resource : RESOURCE_MAP.get(request.getResourceType())) {
            Map<String, Object> singleResult = new HashMap<>();
            singleResult.put("itemName", resource.getResourceType());
            resource.setTarget(request.getTarget());
            String query = shopifyRequestBody.getFirstQuery(resource);
            cloudServiceRequest.setBody(query);
            String infoByShopify = getShopifyData(cloudServiceRequest);
            countAllItemsAndTranslatedItems(infoByShopify, shopifyRequest, resource, allCounter, translatedCounter);
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
//            System.out.println("total: " + allCounter.getTotalChars());
//            System.out.println("translated: " + translatedCounter.getTotalChars());
        //判断数据库中是否有符合条件的数据，如果有，更新数据库，如果没有插入信息
//            if (!itemsService.readSingleItemInfo(shopifyRequest, resourceType).isEmpty()) {
////                itemsService.updateItemsTotalData(shopifyRequest, allCounter.getTotalChars(), resourceType);
//                itemsService.updateItemsByShopName(shopifyRequest, resourceType, allCounter.getTotalChars(), translatedCounter.getTotalChars());
//
//            } else {
//                itemsService.insertItems(shopifyRequest, resourceType, allCounter.getTotalChars(), translatedCounter.getTotalChars());
//            }
//        }
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
            JsonNode translationsNode = contentItemNode.get("translations");
            JsonNode translatableContentNode = contentItemNode.get("translatableContent");
            if ("ONLINE_STORE_THEME".equals(translateResource.getResourceType())) {
                // 当资源类型为 ONLINE_STORE_THEME 时，调用专门的计数方法
                countThemeData(translationsNode, translatableContentNode, counter, translatedCounter);
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
//                System.out.println("total: " + contentItem);
                counter.addChars(1);

            }
        }

        if (translationsNode != null) {
            // 遍历翻译内容节点，增加已翻译的字符数
            for (JsonNode contentItem : translationsNode) {
//                System.out.println("translated: " + contentItem);
                ObjectNode contentItemNode = (ObjectNode) contentItem;
                if (contentItemNode != null) {
                    translatedCounter.addChars(1);
                }
            }
        }

    }

    //计数非 ONLINE_STORE_THEME 类型的资源数据。
    private void countNonThemeData(JsonNode translationsNode, JsonNode translatableContentNode, CharacterCountUtils counter, CharacterCountUtils translatedCounter) {
//        System.out.println("translatableContentNode: " + translatableContentNode);
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
        boolean hasNextPage = pageInfoNode.hasNonNull("hasNextPage") && pageInfoNode.get("hasNextPage").asBoolean();
        String endCursor = pageInfoNode.hasNonNull("endCursor") ? pageInfoNode.get("endCursor").asText() : null;

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

    //根据项数修改翻译状态
    public int updateTranslationStatus(TranslateRequest request) {
        AtomicInteger i = new AtomicInteger();
        int i1;
        getTranslationItemsInfo(new ResourceTypeRequest(request.getShopName(), request.getAccessToken(), null, request.getTarget()));
        //从数据库中获取数据并判断
        List<ItemsDO> itemsRequests = itemsService.readItemsInfo(new ShopifyRequest(request.getShopName(), null, null, request.getTarget()));
        itemsRequests.forEach(item -> {
            if (Objects.equals(item.getTranslatedNumber(), item.getTotalNumber())) {
                i.getAndIncrement();
            }
        });
        if (i.get() == RESOURCE_MAP.size()) {
            i1 = translatesService.updateTranslateStatus(request.getShopName(), 1, request.getTarget(), request.getSource(), request.getAccessToken());
        } else {
            i1 = translatesService.updateTranslateStatus(request.getShopName(), 3, request.getTarget(), request.getSource(), request.getAccessToken());
        }
        return i1;
    }

}

