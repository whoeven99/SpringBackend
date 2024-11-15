package com.bogdatech.logic;


import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.bogdatech.entity.TranslateResourceDTO;
import com.bogdatech.entity.TranslatesDO;
import com.bogdatech.exception.ClientException;
import com.bogdatech.integration.ShopifyHttpIntegration;
import com.bogdatech.integration.TranslateApiIntegration;
import com.bogdatech.model.controller.request.*;
import com.bogdatech.model.controller.response.BaseResponse;
import com.bogdatech.query.ShopifyQuery;
import com.bogdatech.repository.JdbcRepository;
import com.bogdatech.utils.CharacterCountUtils;
import com.bogdatech.utils.StringUtil;
import com.bogdatech.utils.TypeConversionUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.microsoft.applicationinsights.TelemetryClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.bogdatech.entity.TranslateResourceDTO.TRANSLATION_RESOURCES;
import static com.bogdatech.enums.ErrorEnum.TRANSLATE_ERROR;
import static com.bogdatech.logic.ShopifyService.getVariables;

@Component
@EnableAsync
public class TranslateService {

    @Autowired
    private TranslateApiIntegration translateApiIntegration;

    @Autowired
    private JdbcRepository jdbcRepository;

    @Autowired
    private ShopifyHttpIntegration shopifyApiIntegration;

    @Autowired
    private ShopifyService shopifyService;

    private final TelemetryClient appInsights = new TelemetryClient();

    //百度翻译接口
    public BaseResponse<Object> baiDuTranslate(TranslateRequest request) {
        String result = translateApiIntegration.baiDuTranslate(request);
        if (result != null) {
            return new BaseResponse<>().CreateSuccessResponse(result);
        }
        return new BaseResponse<>().CreateErrorResponse(TRANSLATE_ERROR);
    }

    //google翻译接口
    public BaseResponse<Object> googleTranslate(TranslateRequest request) {
        String result = translateApiIntegration.googleTranslate(request);
        if (result != null) {
            return new BaseResponse<>().CreateSuccessResponse(result);
        }
        return new BaseResponse<>().CreateErrorResponse(TRANSLATE_ERROR);
    }

    @Async
    public void test(TranslatesDO request) {
        appInsights.trackTrace("我要翻译了" + Thread.currentThread().getName());
        //睡眠1分钟
        try {
            Thread.sleep(1000 * 60 * 1);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        appInsights.trackTrace("翻译完成" + Thread.currentThread().getName());
        //更新状态
        jdbcRepository.updateTranslateStatus(request.getShopName(), 0);
    }

    //写死的json
    public BaseResponse<Object> userBDTranslateJsonObject() {
        PathMatchingResourcePatternResolver resourceLoader = new PathMatchingResourcePatternResolver();
        JSONObject data = null;
        try {
            Resource resource = resourceLoader.getResource("classpath:jsonData/project.json");
            InputStream inputStream = resource.getInputStream();
            ObjectMapper objectMapper = new ObjectMapper();
            data = objectMapper.readValue(inputStream, JSONObject.class);
        } catch (IOException e) {
            e.printStackTrace();
        }

        // 对options节点进行递归翻译处理
        translateOptions(data != null ? data.getJSONObject("products") : null);
        // 返回成功响应
        return new BaseResponse<>().CreateSuccessResponse(data);
    }

    private void translateOptions(JSONObject options) {
        if (options.containsKey("values")) {
            JSONArray values = options.getJSONArray("values");
            JSONArray translatedValues = new JSONArray();

            for (Object valueObj : values) {
                String value = (String) valueObj;
                value = StringUtil.replaceSpaces(value, "-");
                try {
                    // 调用翻译接口
                    String translatedValue = translateApiIntegration.baiDuTranslate(new TranslateRequest(0, null, null, "en", "zh", value));
                    translatedValues.add(translatedValue);
                } catch (Exception e) {
                    // 如果翻译失败，则直接返回错误响应
                    throw new RuntimeException("Translation failed", e);
                }
            }

            // 替换原values值为翻译后的值
            options.put("values", translatedValues);
        }

        // 递归处理options内的其他JSON对象
        for (String key : options.keySet()) {
            Object value = options.get(key);
            if (value instanceof JSONObject) {
                translateOptions((JSONObject) value);
            } else if (value instanceof JSONArray) {
                for (Object item : (JSONArray) value) {
                    if (item instanceof JSONObject) {
                        translateOptions((JSONObject) item);
                    }
                }
            }
        }
    }

    //判断数据库是否有该用户如果有将状态改为2（翻译中），如果没有该用户插入用户信息和翻译状态,开始翻译流程
    @Async
    public void translating(TranslateRequest request) {
        //判断用户翻译状态，如果是2，就不翻译了
       if (jdbcRepository.readInfoByShopName(request).get(0).getStatus() == 2) {
           throw new ClientException("Translation is in progress");
       }
        //将翻译状态改为翻译中： 2
        jdbcRepository.updateTranslateStatus(request.getShopName(), 2);
        List<TranslatesDO> translatesDOS = jdbcRepository.readInfoByShopName(request);
        if (translatesDOS.isEmpty()) {
            jdbcRepository.insertShopTranslateInfo(request);
        } else {
            jdbcRepository.updateTranslateStatusByTranslateRequest(request);
        }

        ShopifyRequest shopifyRequest = TypeConversionUtils.convertTranslateRequestToShopifyRequest(request);
        //初始化计数器
        CharacterCountUtils counter = new CharacterCountUtils();


        CloudServiceRequest cloudServiceRequest = TypeConversionUtils.shopifyToCloudServiceRequest(shopifyRequest);
        for (TranslateResourceDTO translateResource : TRANSLATION_RESOURCES) {
            ShopifyQuery shopifyQuery = new ShopifyQuery();
            translateResource.setTarget(request.getTarget());
            String query = shopifyQuery.getFirstQuery(translateResource);
            cloudServiceRequest.setBody(query);
            String infoByShopify = shopifyService.getShopifyData(cloudServiceRequest);
            translateJson(infoByShopify, shopifyRequest, translateResource, counter);
        }

        //将翻译状态改为已翻译： 1
        jdbcRepository.updateTranslateStatus(request.getShopName(), 1);
        jdbcRepository.updateUsedCharsByShopName(new TranslationCounterRequest(0, request.getShopName(), 0, counter.getTotalChars(), 0,0,0));
        appInsights.trackTrace("翻译完成，字符数是：" + counter.getTotalChars());
    }

    //根据返回的json片段，将符合条件的value翻译,并返回json片段
    public void translateJson(String objectData, ShopifyRequest request, TranslateResourceDTO translateResourceDTO, CharacterCountUtils counter) {
         appInsights.trackTrace("现在翻译到： " + translateResourceDTO.getResourceType());

        if (objectData == null) {
            throw new IllegalArgumentException("Argument 'content' cannot be null or empty.");
        }
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode translatedRootNode = null;
        try {
            JsonNode rootNode = objectMapper.readTree(objectData);
            translatedRootNode = translateSingleLineTextFieldsRecursively(rootNode, request, counter);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        // 递归处理下一页数据
        handlePagination(translatedRootNode, request, counter, translateResourceDTO);
    }

    // 递归处理下一页数据
    private void handlePagination(JsonNode translatedRootNode, ShopifyRequest request, CharacterCountUtils counter, TranslateResourceDTO translateResourceDTO) {
        // 获取translatableResources节点
        JsonNode translatableResourcesNode = translatedRootNode.path("translatableResources");
        // 获取pageInfo节点
        JsonNode pageInfoNode = translatableResourcesNode.path("pageInfo");

        if (translatableResourcesNode.hasNonNull("pageInfo")) {
            if (pageInfoNode.hasNonNull("hasNextPage") && pageInfoNode.get("hasNextPage").asBoolean()) {
                JsonNode endCursor = pageInfoNode.get("endCursor");
                translateResourceDTO.setAfter(endCursor.asText());
                translatedRootNode = translateNextPage(request, counter, translateResourceDTO);
            }
        }
    }

    //递归遍历JSON树：使用 translateSingleLineTextFieldsRecursively 方法递归地遍历整个 JSON 树，并对 translatableContent 字段进行特别处理。
    private JsonNode translateSingleLineTextFieldsRecursively(JsonNode node, ShopifyRequest request, CharacterCountUtils counter) {
        if (node.isObject()) {
            ObjectNode objectNode = (ObjectNode) node;
            final String[] resourceId = new String[1]; // 使用单元素数组来存储 resourceId
            node.fieldNames().forEachRemaining(fieldName -> {
                JsonNode fieldValue = node.get(fieldName);
                //获取resourceId的值
                if ("resourceId".equals(fieldName)) {

                    resourceId[0] = fieldValue.asText();
                }
                if ("translatableContent".equals(fieldName)) {
                    ArrayNode translatedContent = translateSingleLineTextFields((ArrayNode) fieldValue, request, counter, resourceId[0]);
                    objectNode.set(fieldName, translatedContent);
                } else {
                    objectNode.set(fieldName, translateSingleLineTextFieldsRecursively(fieldValue, request, counter));
                }
            });
        } else if (node.isArray()) {
            ArrayNode arrayNode = (ArrayNode) node;
            for (int i = 0; i < node.size(); i++) {
                JsonNode element = node.get(i);
                arrayNode.set(i, translateSingleLineTextFieldsRecursively(element, request, counter));
            }
        }
        return node;
    }

    //对符合条件的 SINGLE_LINE_TEXT_FIELD和MULTI_LINE_TEXT_FIELD  类型的 value 进行翻译
    private ArrayNode translateSingleLineTextFields(ArrayNode contentNode, ShopifyRequest request, CharacterCountUtils counter, String resourceId) {
        ArrayNode translatedContent = new ObjectMapper().createArrayNode();
        //初始化存储到shopify本地的数据
        Map<String, Object> variables = new HashMap<>();
        variables.put("resourceId", resourceId);
        Map<String, Object> translation = new HashMap<>();
        for (JsonNode contentItem : contentNode) {
            ObjectNode contentItemNode = (ObjectNode) contentItem;
            //打印当前遍历的值 为什么部分不翻译

            // 跳过 key 为 "handle" 的项
            if ("handle".equals(contentItemNode.get("key").asText())) {
                continue;  // 跳过当前项
            }
//            System.out.println(("当前遍历的值： " + contentItemNode));
            // 准备翻译信息
            translation.put("locale", request.getTarget());
            translation.put("key", contentItemNode.get("key").asText());
            translation.put("translatableContentDigest", contentItemNode.get("digest").asText());

            // 获取 value 和 source
            String value = contentItemNode.get("value").asText();
            String source = contentItemNode.get("locale").asText();

            // 跳过 value 为空的项
            if (value == null || value.isEmpty()) {
                continue;  // 跳过当前项
            }

            try {
                String encodedQuery = URLEncoder.encode(value, StandardCharsets.UTF_8);
                counter.addChars(encodedQuery.length());
                //达到字符限制，更新用户剩余字符数，终止循环
                updateCharsWhenExceedLimit(counter, request.getShopName());
                String translatedValue = translateApiIntegration.googleTranslate(new TranslateRequest(0, null, null, source, request.getTarget(), value));
                contentItemNode.put("value", translatedValue);

                translation.put("value", translatedValue);
                Object[] translations = new Object[]{
                        translation // 将HashMap添加到数组中
                };
                variables.put("translations", translations);
                //将翻译后的内容通过ShopifyAPI记录到shopify本地
                shopifyApiIntegration.registerTransaction(request, variables);
            } catch (Exception e) {
                e.printStackTrace();
            }

            // 添加翻译后的内容到结果列表
            translatedContent.add(contentItem);
        }
        return translatedContent;
    }


    //达到字符限制，更新用户剩余字符数，终止循环
    public void updateCharsWhenExceedLimit(CharacterCountUtils counter, String shopName) {
        TranslationCounterRequest request = new TranslationCounterRequest();
        request.setShopName(shopName);
        int remainingChars = jdbcRepository.readCharsByShopName(request).get(0).getChars();
        if (counter.getTotalChars() >= remainingChars) {
            jdbcRepository.updateUsedCharsByShopName(new TranslationCounterRequest(0, shopName, 0, counter.getTotalChars(), 0, 0,0));
            appInsights.trackTrace("达到字符限制，终止循环.");
            throw new ClientException("翻译字符数超过限制.");
        }
    }

    //修改getTestQuery里面的testQuery，用获取后的的查询语句进行查询
    public JsonNode fetchNextPage(TranslateResourceDTO translateResource, ShopifyRequest request) {
        ShopifyQuery shopifyQuery = new ShopifyQuery();
        CloudServiceRequest cloudServiceRequest = TypeConversionUtils.shopifyToCloudServiceRequest(request);
        String query = shopifyQuery.getAfterQuery(translateResource);
        cloudServiceRequest.setBody(query);
        String infoByShopify = shopifyService.getShopifyData(cloudServiceRequest);
        if (infoByShopify == null) {
            throw new IllegalArgumentException("Argument 'content' cannot be null or empty");
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

    //递归处理下一页数据
    private JsonNode translateNextPage(ShopifyRequest request, CharacterCountUtils counter, TranslateResourceDTO
            translateResource) {
        JsonNode nextPageData = fetchNextPage(translateResource, request);
        // 重新开始翻译流程
        JsonNode translatedNextPage = translateSingleLineTextFieldsRecursively(nextPageData, request, counter);
        // 递归处理下一页数据
        JsonNode translatableResourcesNode = translatedNextPage.path("translatableResources");
        if (translatableResourcesNode.hasNonNull("pageInfo")) {
            JsonNode pageInfo = translatableResourcesNode.path("pageInfo");
            if (pageInfo.hasNonNull("hasNextPage") && pageInfo.path("hasNextPage").asBoolean()) {
                JsonNode newEndCursor = pageInfo.path("endCursor");
                translateResource.setAfter(newEndCursor.asText());
                return translateNextPage(request, counter, translateResource);
            }
        }
        return translatedNextPage;
    }

    //根据translations的情况判断是否翻译
    private Boolean judgeByTranslations(String fieldName, JsonNode fieldValue) {
        boolean shouldTranslate = false;
        if ("translations".equals(fieldName) && !fieldValue.isNull()) {
            shouldTranslate = true;
            for (JsonNode translation : fieldValue) {
                JsonNode outdatedNode = translation.get("outdated");
                if (!outdatedNode.asBoolean()) {
                    shouldTranslate = false;
                    break;
                }
            }
        } else {
            shouldTranslate = true;
        }
        return shouldTranslate;
    }

    // 将翻译后的数据存储到数据库中
    @Async
    public void saveTranslatedData(String objectData, ShopifyRequest request, TranslateResourceDTO translateResourceDTO) {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode rootNode = null;
        try {
            rootNode = objectMapper.readTree(objectData);
            processNodes(rootNode, request, translateResourceDTO);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    //分析node数据进行合并数据，检查是否有下一页
    private void processNodes(JsonNode rootNode, ShopifyRequest request, TranslateResourceDTO translateResourceDTO) {
        System.out.println("rootNode: " + rootNode);
        //用封装后的接口测，没有data节点
//        JsonNode translatableResourcesNode = rootNode.path("data").path("translatableResources").path("nodes");
        JsonNode translatableResourcesNode = rootNode.path("translatableResources").path("nodes");
        for (JsonNode node : translatableResourcesNode) {
            System.out.println("node: " + node);
            String resourceId = node.path("resourceId").asText();
            Map<String, Object> translationsMap = extractTranslations(node);
            Map<String, Object> translatableContentMap = extractTranslatableContent(node);
            // 合并两个Map
            Map<String, Object> mergedMap = mergeMaps(translationsMap, translatableContentMap);
//             将resourceId添加到每个合并后的Map中
            System.out.println(6);
            mergedMap.values().forEach(value -> {
                if (value instanceof Map) {
                    ((Map<String, Object>) value).put("resourceId", resourceId);
                    ((Map<String, Object>) value).put("shopName", request.getShopName());
                    // 将合并后的Map存入SQL中
                    System.out.println(5);
                    updateOrInsertTranslateTextData(((Map<String, Object>) value));
                }
            });
        }

        // 检查是否有下一页
        boolean hasNextPage = rootNode.path("data").path("translatableResources").path("pageInfo").path("hasNextPage").asBoolean();
        if (hasNextPage) {
            String endCursor = rootNode.path("data").path("translatableResources").path("pageInfo").path("endCursor").asText();
            // 调用API获取下一页数据
            translateResourceDTO.setAfter(endCursor);
            JsonNode nextPageData = fetchNextPage(translateResourceDTO, request);
            if (nextPageData != null) {
                processNodes(nextPageData, request, translateResourceDTO);
            }
        }
    }

    //获取一个页面所有Translations集合数据
    private static Map<String, Object> extractTranslations(JsonNode node) {
        Map<String, Object> translations = new HashMap<>();
        JsonNode translationsNode = node.path("translations");
        if (translationsNode.isArray() && !translationsNode.isEmpty()) {
            translationsNode.forEach(translation -> {
                Map<String, String> keys = new HashMap<>();
                keys.put("targetCode", translation.path("locale").asText());
                keys.put("textKey", translation.path("key").asText());
                keys.put("targetText", translation.path("value").asText());
                translations.put(translation.path("key").asText(), keys);
            });
        }
        return translations;
    }

    //获取一个页面所有TranslatableContent集合数据
    private static Map<String, Object> extractTranslatableContent(JsonNode node) {
        Map<String, Object> contents = new HashMap<>();
        JsonNode contentNode = node.path("translatableContent");
        contentNode.forEach(content -> {
            Map<String, String> keys = new HashMap<>();
            keys.put("sourceCode", content.path("locale").asText());
            keys.put("textType", content.path("type").asText());
            keys.put("digest", content.path("digest").asText());
            keys.put("sourceText", content.path("value").asText());
            contents.put(content.path("key").asText(), keys);
        });
        return contents;
    }

    //合并两个map集合数据
    private Map<String, Object> mergeMaps(Map<String, Object> map1, Map<String, Object> map2) {
//        appInsights.trackTrace("进入合并的方法里面");
        Map<String, Object> result = new HashMap<>(map1);
        map2.forEach((key, value) -> {
//            appInsights.trackTrace("当前的key为: " + key);
            if (result.containsKey(key)) {
                // 如果键冲突，合并两个Map
                Map<String, Object> existingValue = (Map<String, Object>) result.get(key);
                Map<String, Object> newValue = (Map<String, Object>) value;
                Map<String, Object> mergedValue = new HashMap<>(existingValue);
                mergedValue.putAll(newValue);
                result.put((String) mergedValue.get("digest"), mergedValue);
            }
        });
        return result;
    }

    //根据数据库中digest进行判断，有更新，无插入
    private void updateOrInsertTranslateTextData(Map<String, Object> data) {
        if (data.get("digest") != null) {
            TranslateTextRequest request = new TranslateTextRequest();
            request.setTargetText(data.get("targetText").toString());
            request.setResourceId(data.get("resourceId").toString());
            request.setDigest(data.get("digest").toString());
            request.setSourceCode(data.get("sourceCode").toString());
            request.setTargetCode(data.get("targetCode").toString());
            request.setShopName(data.get("shopName").toString());
            request.setTextKey(data.get("textKey").toString());
            request.setSourceText(data.get("sourceText").toString());
            request.setTextType(data.get("textType").toString());
            System.out.println("request： " + request);
            // 检查是否存在有digest的数据
            List<TranslateTextRequest> translateText = jdbcRepository.getTranslateText(request);
            System.out.println("未进if语句前： " + translateText);
            if (translateText.isEmpty()) {
                // 插入数据
//                jdbcRepository.insertTranslateText(request);
                System.out.println("插入数据 ： " + translateText);
            } else {
                // 更新数据
//                jdbcRepository.updateTranslateText(request);
                System.out.println("更新数据 ： " + translateText);
            }
        }
    }

    //测试用拉取部分数据（待删）
    private void InsertTranslateTextData(Map<String, Object> data) {
        TranslateTextRequest request = new TranslateTextRequest();
        request.setTargetText("1");
        request.setTargetCode("1");
        request.setResourceId(data.get("resourceId").toString());
        request.setDigest(data.get("digest").toString());
        request.setSourceCode(data.get("sourceCode").toString());
        request.setShopName(data.get("shopName").toString());
        request.setTextKey("1");
        request.setSourceText(data.get("sourceText").toString());
        request.setTextType(data.get("textType").toString());
        jdbcRepository.insertTranslateText(request);
    }

    //循环存数据库
    @Async
    public void saveTranslateText(TranslateRequest request) {
        ShopifyRequest shopifyRequest = new ShopifyRequest();
        shopifyRequest.setTarget(request.getTarget());
        shopifyRequest.setShopName(request.getShopName());
        shopifyRequest.setAccessToken(request.getAccessToken());
        CloudServiceRequest cloudServiceRequest = TypeConversionUtils.shopifyToCloudServiceRequest(shopifyRequest);
        for (TranslateResourceDTO translateResource : TRANSLATION_RESOURCES) {
            ShopifyQuery shopifyQuery = new ShopifyQuery();
            translateResource.setTarget(shopifyRequest.getTarget());
            String query = shopifyQuery.getFirstQuery(translateResource);
            cloudServiceRequest.setBody(query);
            String string = shopifyService.getShopifyData(cloudServiceRequest);
            saveTranslatedData(string, shopifyRequest, translateResource);
        }
    }

    //翻译单个文本数据
    public String translateSingleText(RegisterTransactionRequest request) {
        TranslateRequest translateRequest = TypeConversionUtils.registerTransactionRequestToTranslateRequest(request);
        request.setValue(translateApiIntegration.googleTranslate(translateRequest));
        //保存翻译后的数据到shopify本地
        Map<String, Object> variables = getVariables(request);
        ShopifyRequest shopifyRequest = TypeConversionUtils.convertTranslateRequestToShopifyRequest(translateRequest);
        return shopifyApiIntegration.registerTransaction(shopifyRequest, variables);
    }
}

