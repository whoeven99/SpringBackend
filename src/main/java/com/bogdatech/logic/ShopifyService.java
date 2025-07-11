package com.bogdatech.logic;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.bogdatech.Service.IItemsService;
import com.bogdatech.Service.ITranslatesService;
import com.bogdatech.Service.IUserSubscriptionsService;
import com.bogdatech.Service.IUserTypeTokenService;
import com.bogdatech.config.LanguageFlagConfig;
import com.bogdatech.entity.DO.ItemsDO;
import com.bogdatech.entity.DO.TranslateResourceDTO;
import com.bogdatech.entity.DO.UserTypeTokenDO;
import com.bogdatech.enums.ErrorEnum;
import com.bogdatech.exception.ClientException;
import com.bogdatech.model.controller.request.*;
import com.bogdatech.model.controller.response.BaseResponse;
import com.bogdatech.model.service.StoringDataPublisherService;
import com.bogdatech.requestBody.ShopifyRequestBody;
import com.bogdatech.utils.CharacterCountUtils;
import com.bogdatech.utils.TypeConversionUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
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

import static com.bogdatech.constants.TranslateConstants.*;
import static com.bogdatech.entity.DO.TranslateResourceDTO.RESOURCE_MAP;
import static com.bogdatech.entity.DO.TranslateResourceDTO.TOKEN_MAP;
import static com.bogdatech.enums.ErrorEnum.*;
import static com.bogdatech.integration.ALiYunTranslateIntegration.calculateBaiLianToken;
import static com.bogdatech.integration.ShopifyHttpIntegration.getInfoByShopify;
import static com.bogdatech.integration.ShopifyHttpIntegration.registerTransaction;
import static com.bogdatech.integration.TestingEnvironmentIntegration.sendShopifyPost;
import static com.bogdatech.logic.TranslateService.OBJECT_MAPPER;
import static com.bogdatech.logic.TranslateService.translationLogic;
import static com.bogdatech.utils.CaseSensitiveUtils.appInsights;
import static com.bogdatech.utils.JsonUtils.*;
import static com.bogdatech.utils.JsoupUtils.isHtml;
import static com.bogdatech.utils.JudgeTranslateUtils.*;
import static com.bogdatech.utils.JudgeTranslateUtils.shouldTranslate;
import static com.bogdatech.utils.PlaceholderUtils.getSimplePrompt;
import static com.bogdatech.utils.RegularJudgmentUtils.isValidString;
import static com.bogdatech.utils.StringUtils.countWords;

@Component
public class ShopifyService {


    private final IUserTypeTokenService userTypeTokenService;
    private final IUserSubscriptionsService userSubscriptionsService;
    private final IItemsService itemsService;
    private final ITranslatesService translatesService;
    private final StoringDataPublisherService storingDataPublisherService;

    @Autowired
    public ShopifyService(
            IUserTypeTokenService userTypeTokenService,
            IUserSubscriptionsService userSubscriptionsService,
            IItemsService itemsService,
            ITranslatesService translatesService,
            StoringDataPublisherService storingDataPublisherService) {
        this.userTypeTokenService = userTypeTokenService;
        this.userSubscriptionsService = userSubscriptionsService;
        this.itemsService = itemsService;
        this.translatesService = translatesService;
        this.storingDataPublisherService = storingDataPublisherService;
    }

    ShopifyRequestBody shopifyRequestBody = new ShopifyRequestBody();
    private final int length = 32;

    //封装调用云服务器实现获取shopify数据的方法
    public static String getShopifyDataByCloud(CloudServiceRequest cloudServiceRequest) {
        // 使用 ObjectMapper 将对象转换为 JSON 字符串
        String string;
        try {
            String requestBody = OBJECT_MAPPER.writeValueAsString(cloudServiceRequest);
            string = sendShopifyPost("test123", requestBody);
        } catch (Exception e) {
            return null;
        }
        return string;
    }

    //封装调用云服务器实现更新shopify数据的方法
    public String updateShopifyData(RegisterTransactionRequest registerTransactionRequest) {
        // 使用 ObjectMapper 将对象转换为 JSON 字符串
        String string;
        JSONObject translationsArray;
        JSONArray translationsObject;
        try {
            String requestBody = OBJECT_MAPPER.writeValueAsString(registerTransactionRequest);
            string = sendShopifyPost("shopify/updateItem", requestBody);
            JSONObject jsonObject = JSON.parseObject(string);
            translationsArray = jsonObject.getJSONObject("translationsRegister");
            translationsObject = translationsArray.getJSONArray("translations");
        } catch (JsonProcessingException | ClientException e) {
            throw new ClientException(SHOPIFY_RETURN_ERROR.getErrMsg());
        }
        return (String) translationsObject.getJSONObject(0).get("value");
    }

    //获得翻译前一共需要消耗的字符数
    public int getTotalWords(ShopifyRequest request, String method, TranslateResourceDTO translateResource) {
        CharacterCountUtils counter = new CharacterCountUtils();
        CharacterCountUtils translateCounter = new CharacterCountUtils();
        CloudServiceRequest cloudServiceRequest = TypeConversionUtils.shopifyToCloudServiceRequest(request);
        String env = System.getenv("ApplicationEnv");
        translateResource.setTarget(request.getTarget());
        String query = shopifyRequestBody.getFirstQuery(translateResource);
        cloudServiceRequest.setBody(query);
        String infoByShopify;
        if ("prod".equals(env) || "dev".equals(env)) {
            infoByShopify = String.valueOf(getInfoByShopify(request, query));
        } else {
            infoByShopify = getShopifyDataByCloud(cloudServiceRequest);
        }
        try {
            if (infoByShopify == null || infoByShopify.isEmpty()) {
                return 0;
            }
            countBeforeTranslateChars(infoByShopify, request, translateResource, counter, translateCounter, method);
        } catch (Exception e) {
            appInsights.trackTrace(request.getShopName() + "用户 " + request.getTarget() + "目标 方法： " + method + " 统计字符数失败 errors ： " + e.getMessage());
        }
        return counter.getTotalChars();
    }

    //计数翻译前所需要的总共的字符数
    public void countBeforeTranslateChars(String infoByShopify, ShopifyRequest request, TranslateResourceDTO translateResource, CharacterCountUtils counter, CharacterCountUtils translateCounter, String method) {
        JsonNode rootNode = ConvertStringToJsonNode(infoByShopify, translateResource);
        if (rootNode == null || rootNode.isEmpty()) {
            return;
        }
        translateSingleLineTextFieldsRecursively(rootNode, request, counter, translateCounter, translateResource, method);
        // 递归处理下一页数据
        handlePagination(rootNode, request, counter, translateResource, translateCounter, method);
        //打印最后使用的值
    }

    //将String数据转化为JsonNode数据
    public JsonNode ConvertStringToJsonNode(String infoByShopify, TranslateResourceDTO translateResource) {
//        System.out.println("现在统计到： " + translateResource.getResourceType());

        JsonNode rootNode = null;
        try {
            rootNode = OBJECT_MAPPER.readTree(infoByShopify);
        } catch (JsonProcessingException e) {
//            System.out.println("解析JSON数据失败： " + translateResource);
            appInsights.trackTrace("解析JSON数据失败 errors： " + translateResource);
        }
        return rootNode;
    }

    //递归遍历JSON树：使用 translateSingleLineTextFieldsRecursively 方法递归地遍历整个 JSON 树，并对 translatableContent 字段进行特别处理。
    private void translateSingleLineTextFieldsRecursively(JsonNode node, ShopifyRequest request, CharacterCountUtils counter, CharacterCountUtils translateCounter, TranslateResourceDTO translateResource, String method) {
        translateObjectNode((ObjectNode) node, request, counter, translateCounter, translateResource, method);
    }

    //对node节点进行判断，是否调用方法
    public void translateObjectNode(ObjectNode objectNode, ShopifyRequest request, CharacterCountUtils counter,
                                    CharacterCountUtils translateCounter, TranslateResourceDTO translateResource, String method) {
        AtomicReference<List<String>> strings = new AtomicReference<>(new ArrayList<>());
        if (objectNode == null || objectNode.isEmpty()) {
            return;
        }
        JsonNode translatableResourcesNode = objectNode.path("translatableResources");
        if (!translatableResourcesNode.isObject()) {
            return;
        }
        // 处理 nodes 数组
        JsonNode nodesNode = translatableResourcesNode.path("nodes");
        if (nodesNode == null || !nodesNode.isArray()) {
            return;
        }
        ArrayNode nodesArray = (ArrayNode) nodesNode;
        for (JsonNode nodeElement : nodesArray) {
            if (nodeElement == null || !nodeElement.isObject()) {
                continue;
            }
            Iterator<Map.Entry<String, JsonNode>> fields = nodeElement.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                String fieldName = field.getKey();
                JsonNode fieldValue = field.getValue();
                //当translates里面有数据时
                if ("translations".equals(fieldName) && !"initial".equals(method)) {
                    strings.set(counterTranslatedContent((ArrayNode) fieldValue));
                }
                if ("translatableContent".equals(fieldName)) {
                    translateSingleLineTextFields((ArrayNode) fieldValue, request, counter, translateCounter, strings.get(), translateResource, method);
                }

            }
        }

    }

    //将已翻译value的key放到list集合中
    private List<String> counterTranslatedContent(ArrayNode node) {
        List<String> translatedContent = new ArrayList<>();
        for (JsonNode contentItem : node) {
            ObjectNode contentItemNode = (ObjectNode) contentItem;
            translatedContent.add(contentItemNode.path("key").asText(null));
        }
        return translatedContent;
    }


    //对符合条件的 value 进行计数
    @Async
    public void translateSingleLineTextFields(ArrayNode contentNode, ShopifyRequest request, CharacterCountUtils counter, CharacterCountUtils translatedCounter, List<String> translatedContent, TranslateResourceDTO translateResourceDTO, String method) {
        switch (method) {
            case "initial":
                calculateInitialToken(contentNode, counter, translateResourceDTO, request);
                break;
            case "tokens":
                try {
                    calculateExactToken(contentNode, counter, translatedContent, translateResourceDTO, request);
                } catch (Exception e) {
                    appInsights.trackTrace("errors 计数失败的原因： " + e.getMessage());
                    return;
                }
                break;
            case "words":
                estimatedTranslationWords(contentNode, counter, translatedCounter, translatedContent, translateResourceDTO);
                break;
        }
    }

    //计算剩余精确值
    public void calculateInitialToken(ArrayNode contentNode, CharacterCountUtils counter, TranslateResourceDTO translateResourceDTO, ShopifyRequest request) {
        for (JsonNode contentItem : contentNode) {
            ObjectNode contentItemNode = (ObjectNode) contentItem;
            //当在contentItemNode的key在translatedContent里面，则跳过
            if (contentItemNode == null) {
                continue;
            }

            String value;
            try {
                JsonNode valueNode = contentItemNode.path("value");
                if (valueNode == null) {
                    continue;
                }
                value = contentItemNode.path("value").asText(null);
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
                continue;
            }

            String key = contentItemNode.path("key").asText(null);
            //通用的不翻译数据
            String resourceType = translateResourceDTO.getResourceType();
            String type = contentItemNode.path("type").asText(null);
            if (translationLogic(key, value, type, resourceType)) {
                continue;
            }

            //如果是theme模块的数据
            if (TRANSLATABLE_RESOURCE_TYPES.contains(resourceType)) {
                //如果是html放html文本里面
                if (isHtml(value)) {
                    counter.addChars(calculateModelToken(value));
                    continue;
                }
                //对key中含section和general的做key值判断
                if (GENERAL_OR_SECTION_PATTERN.matcher(key).find()) {
                    //进行白名单的确认
                    if (whiteListTranslate(key)){
                        counter.addChars(calculateModelToken(value));
                        continue;
                    }
                    //如果包含对应key和value，则跳过
                    if (!shouldTranslate(key, value)) {
                        continue;
                    }
                }
                counter.addChars(calculateModelToken(value));
                continue;
            }
            //对METAOBJECT字段翻译
            if (resourceType.equals(METAOBJECT)) {
                if (isJson(value)) {
                    continue;
                }
                counter.addChars(calculateModelToken(value));
                continue;
            }

            //对METAFIELD字段翻译
            if (resourceType.equals(METAFIELD)) {
                //如UXxSP8cSm，UgvyqJcxm。有大写字母和小写字母的组合。有大写字母，小写字母和数字的组合。 10位 字母和数字不翻译
                if (SUSPICIOUS_PATTERN.matcher(value).matches() || SUSPICIOUS2_PATTERN.matcher(value).matches()) {
                    continue;
                }
                if (!metaTranslate(value)) {
                    continue;
                }
                //如果是base64编码的数据，不翻译
                if (BASE64_PATTERN.matcher(value).matches()) {
                    printTranslateReason(value + "是base64编码的数据, key是： " + key);
                    continue;
                }
                counter.addChars(calculateModelToken(value));
                continue;
            }

            //处理html的数据
            if ("HTML".equals(contentItemNode.path("type").asText(null)) || isHtml(value)) {
                Document doc = Jsoup.parse(contentItemNode.path("value").asText(null));
                extractTextsToTranslate(doc, counter, resourceType, request);
                continue;
            }

            counter.addChars(calculateModelToken(value));
        }
    }

    private void estimatedTranslationWords(ArrayNode contentNode, CharacterCountUtils counter, CharacterCountUtils translatedCounter, List<String> translatedContent, TranslateResourceDTO translateResourceDTO) {
        for (JsonNode contentItem : contentNode) {
            ObjectNode contentItemNode = (ObjectNode) contentItem;
            //打印当前遍历的值 为什么部分不翻译
            // 跳过 key 为 "handle" 的项
            if ("JSON".equals(contentItemNode.path("type").asText(null))
                    || "JSON_STRING".equals(contentItemNode.path("type").asText(null))
            ) {
                continue;  // 跳过当前项
            }

            //如果value为空跳过
            String value;
            try {
                JsonNode valueNode = contentItemNode.path("value");
                if (valueNode == null) {
                    continue;
                }
                value = contentItemNode.path("value").asText(null);
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
                continue;
            }

            //处理用AI翻译包翻译的类型
            String resourceType = translateResourceDTO.getResourceType();
            if (PRODUCT.equals(resourceType)
                    || PRODUCT_OPTION.equals(resourceType)
                    || PRODUCT_OPTION_VALUE.equals(resourceType)
                    || BLOG.equals(resourceType)
                    || ARTICLE.equals(resourceType)) {

                //处理html数据
                if ("HTML".equals(contentItemNode.path("type").asText(null))) {
                    Document doc = Jsoup.parse(contentItemNode.path("value").asText(null));
                    extractTextsToCount(doc, counter);
                    continue;
                }
                if (value.length() > length) {
//                    String s = value + " Accurately translate the {{product}} data of the e-commerce website into {{Chinese}}. No additional text is required.Please keep the text format unchanged.Punctuation should be consistent with the original text.Translate: ";
//                    counter.addChars(countWords(s));
                    counter.addChars(countWords(value));
                } else {
                    counter.addChars(countWords(value));
                }
                continue;
            }
//             获取 value
            //处理html的数据
            if ("HTML".equals(contentItemNode.path("type").asText(null))) {
                Document doc = Jsoup.parse(contentItemNode.path("value").asText(null));
                extractTextsToCount(doc, counter);
                continue;
            }
            counter.addChars(countWords(value));
        }
    }

    //计算剩余精确值
    public void calculateExactToken(ArrayNode contentNode, CharacterCountUtils counter,
                                    List<String> translatedContent, TranslateResourceDTO translateResourceDTO, ShopifyRequest request) {
        if (translatedContent == null) {
            return;
        }
        for (JsonNode contentItem : contentNode) {
            ObjectNode contentItemNode = (ObjectNode) contentItem;
            if (contentItemNode == null) {
                continue;
            }
            //当在contentItemNode的key在translatedContent里面，则跳过
            String key = contentItemNode.path("key").asText(null);
            if (key == null || translatedContent.contains(key)) {
                continue;
            }

            String value;
            try {
                JsonNode valueNode = contentItemNode.path("value");
                if (valueNode == null) {
                    continue;
                }
                value = contentItemNode.path("value").asText(null);
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
                continue;
            }

            //通用的不翻译数据
            String resourceType = translateResourceDTO.getResourceType();
            String type = contentItemNode.path("type").asText(null);
            if (translationLogic(key, value, type, resourceType)) {
                continue;
            }

            //如果是theme模块的数据
            if (TRANSLATABLE_RESOURCE_TYPES.contains(resourceType)) {

                //如果是html放html文本里面
                if (isHtml(value)) {
                    counter.addChars(calculateModelToken(value));
                    continue;
                }

                if (GENERAL_OR_SECTION_PATTERN.matcher(key).find()){
                    //进行白名单的确认
                    if (whiteListTranslate(key)){
                        counter.addChars(calculateModelToken(value));
                        continue;
                    }
                    //如果包含对应key和value，则跳过
                    if (!shouldTranslate(key, value)) {
                        continue;
                    }
                }
                counter.addChars(calculateModelToken(value));
                continue;
            }
            //对METAOBJECT字段翻译
            if (resourceType.equals(METAOBJECT)) {
                if (isJson(value)) {
                    continue;
                }
                counter.addChars(calculateModelToken(value));
                continue;
            }

            //对METAFIELD字段翻译
            if (resourceType.equals(METAFIELD)) {
                //如UXxSP8cSm，UgvyqJcxm。有大写字母和小写字母的组合。有大写字母，小写字母和数字的组合。 10位 字母和数字不翻译
                if (SUSPICIOUS_PATTERN.matcher(value).matches() || SUSPICIOUS2_PATTERN.matcher(value).matches()) {
                    continue;
                }
                if (!metaTranslate(value)) {
                    continue;
                }
                //如果是base64编码的数据，不翻译
                if (BASE64_PATTERN.matcher(value).matches()) {
                    printTranslateReason(value + "是base64编码的数据, key是： " + key);
                    continue;
                }
                counter.addChars(calculateModelToken(value));
                continue;
            }
            //处理用AI翻译包翻译的类型
            //处理html的数据
            if ("HTML".equals(contentItemNode.path("type").asText(null)) || isHtml(value)) {
                Document doc = Jsoup.parse(contentItemNode.path("value").asText(null));
                extractTextsToTranslate(doc, counter, resourceType, request);
                continue;
            }

            counter.addChars(calculateModelToken(value));
        }
    }


    //普通文本计算token
    public static int calculateModelToken(String text) {
        String prompt = getSimplePrompt(text, "enen");
        return calculateBaiLianToken(prompt);
    }

    // 提取需要翻译的文本（包括文本和alt属性）
    public void extractTextsToTranslate(Document doc, CharacterCountUtils counter, String type, ShopifyRequest request) {
        for (Element element : doc.getAllElements()) {
            if (!element.is("script, style")) { // 忽略script和style标签

                // 提取文本
                String text = element.ownText().trim();
                if (!text.isEmpty()) {
                    counter.addChars(calculateModelToken(text));
                }

                // 提取 alt 属性
                if (element.hasAttr("alt")) {
                    String altText = element.attr("alt").trim();
                    if (!altText.isEmpty()) {
                        counter.addChars(calculateModelToken(altText));
                    }
                }
            }
        }
    }

    public void extractTextsToCount(Document doc, CharacterCountUtils counter) {
        // 使用 StringBuilder 减少字符串拼接的开销
        StringBuilder translationTextBuilder = new StringBuilder();

        for (Element element : doc.getAllElements()) {
            if (!element.is("script, style")) { // 忽略script和style标签
                processTextElement(element, counter, translationTextBuilder);

                // 提取 alt 属性
                if (element.hasAttr("alt")) {
                    String altText = element.attr("alt").trim();
                    if (!altText.isEmpty()) {
                        processTextForTranslation(altText, counter, translationTextBuilder);
                    }
                }
            }
        }
    }

    private void processTextElement(Element element, CharacterCountUtils counter, StringBuilder translationTextBuilder) {
        String text = element.ownText().trim();
        if (!text.isEmpty()) {
            processTextForTranslation(text, counter, translationTextBuilder);
        }
    }

    private void processTextForTranslation(String text, CharacterCountUtils counter, StringBuilder translationTextBuilder) {
        if (text.length() > length) {
            // 清空 StringBuilder，避免每次拼接时创建新的字符串对象
            translationTextBuilder.setLength(0);
            counter.addChars(countWords(text));
        } else {
            counter.addChars(countWords(text));
        }
    }


    // 递归处理下一页数据
    private void handlePagination(JsonNode translatedRootNode, ShopifyRequest request, CharacterCountUtils counter, TranslateResourceDTO translateResourceDTO, CharacterCountUtils translateCounter, String method) {
        // 获取translatableResources节点下的pageInfo节点
        JsonNode pageInfoNode = translatedRootNode.path("translatableResources").path("pageInfo");

        // 检查是否存在非空的hasNextPage和endCursor
        boolean hasNextPage = pageInfoNode.hasNonNull("hasNextPage") && pageInfoNode.path("hasNextPage").asBoolean();
        String endCursor = pageInfoNode.hasNonNull("endCursor") ? pageInfoNode.path("endCursor").asText(null) : null;

        // 如果有下一页，则更新translateResourceDTO并请求下一页
        if (hasNextPage && endCursor != null) {
            translateResourceDTO.setAfter(endCursor);
            translateNextPage(request, counter, translateResourceDTO, translateCounter, method);
        }
    }

    // 递归处理下一页数据
    private JsonNode translateNextPage(ShopifyRequest request, CharacterCountUtils counter, TranslateResourceDTO translateResource, CharacterCountUtils translateCounter, String method) {
        JsonNode nextPageData = fetchNextPage(translateResource, request);
        if (nextPageData == null) {
            return null;
        }
        translateSingleLineTextFieldsRecursively(nextPageData, request, counter, translateCounter, translateResource, method);

        if (hasNextPage(nextPageData)) {
            String newEndCursor = getEndCursor(nextPageData);
            translateResource.setAfter(newEndCursor);
            return translateNextPage(request, counter, translateResource, translateCounter, method);
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


    //在UserSubscription表里面添加一个购买了免费订阅计划的用户（商家）
    public BaseResponse<Object> addUserFreeSubscription(UserSubscriptionsRequest request) {
        request.setStatus(1);
        request.setPlanId(2);
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

    //修改shopify本地单条数据
    public BaseResponse<Object> updateShopifyDataByTranslateTextRequest(RegisterTransactionRequest registerTransactionRequest) {
        appInsights.trackTrace("传入的值： " + registerTransactionRequest.toString());
        String string = updateShopifySingleData(registerTransactionRequest);
        appInsights.trackTrace("返回的值： " + string);
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
                throw new RuntimeException(e);
            }
        }
        return imageInfo;
    }

    //计算被翻译项的总数和已翻译的个数
    public Map<String, Map<String, Object>> getTranslationItemsInfo(ResourceTypeRequest request) {
//        System.out.println("request: " + request);
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
//                if (counter.getTotalChars() != translatedCounter.getTotalChars()){
//                    translatedCounter.addChars(counter.getTotalChars());
//                }
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
//                System.out.println("translated: " + contentItem);
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
                    if (isValidString(value)) {
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

    //根据项数修改翻译状态
    public int updateTranslationStatus(TranslateRequest request) {
        AtomicInteger i = new AtomicInteger();
        int i1;
        getTranslationItemsInfo(new ResourceTypeRequest(request.getShopName(), request.getAccessToken(), null, request.getTarget(), null));
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

    /**
     * 异步调用。 对各个type统计需要消耗的token数，统计过程中状态为2，统计完成后状态为1.如果统计过程中出现异常，则状态为3。
     * 当状态为2时，不调用统计方法。 统计完成后存储到数据库。
     *
     * @param request 用户数据对象，包含用户信息（如shopName、accessToken、target）
     */
    //异步调用getTranslationItemsInfo
    @Async
    public void getTranslationItemsInfoTest(ResourceTypeRequest request) {
//        System.out.println("request1: " + request.getResourceType());
        Map<String, Map<String, Object>> translationItemsInfo = getTranslationItemsInfo(request);
//        System.out.println("translationItemsInfo" + translationItemsInfo);

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
                appInsights.trackTrace("用户： " + cloudServiceRequest.getShopName() + " saveToShopify : " + s);
            } else {
                sendShopifyPost("translate/insertTranslatedText", requestBody);
            }

        } catch (JsonProcessingException | ClientException e) {
            appInsights.trackTrace("Failed to save to Shopify: " + e.getMessage());
        }
    }

    //将翻译后的数据存shopify本地中
    public void saveToShopify(String translatedValue, Map<String, Object> translation, String resourceId, ShopifyRequest request) {
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
            CloudInsertRequest cloudServiceRequest = new CloudInsertRequest(request.getShopName(), request.getAccessToken(), request.getApiVersion(), request.getTarget(), variables);
            String json = objectToJson(cloudServiceRequest);
            // 调用 saveToShopify 方法
            storingDataPublisherService.storingData(json);
//
        } catch (Exception e) {
            appInsights.trackTrace(request.getShopName() + " save to Shopify errors : " + e.getMessage());
        }
    }
}

