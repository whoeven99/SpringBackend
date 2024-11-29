package com.bogdatech.logic;


import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.bogdatech.Service.ITranslateTextService;
import com.bogdatech.Service.ITranslatesService;
import com.bogdatech.Service.ITranslationCounterService;
import com.bogdatech.entity.TranslateResourceDTO;
import com.bogdatech.entity.TranslateTextDO;
import com.bogdatech.entity.TranslatesDO;
import com.bogdatech.exception.ClientException;
import com.bogdatech.integration.ALiYunTranslateIntegration;
import com.bogdatech.integration.ShopifyHttpIntegration;
import com.bogdatech.integration.TestingEnvironmentIntegration;
import com.bogdatech.integration.TranslateApiIntegration;
import com.bogdatech.model.controller.request.*;
import com.bogdatech.model.controller.response.BaseResponse;
import com.bogdatech.query.ShopifyQuery;
import com.bogdatech.utils.CharacterCountUtils;
import com.bogdatech.utils.JsoupUtils;
import com.bogdatech.utils.StringUtils;
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
import java.util.*;

import static com.bogdatech.constants.TranslateConstants.*;
import static com.bogdatech.entity.TranslateResourceDTO.DATABASE_RESOURCES;
import static com.bogdatech.entity.TranslateResourceDTO.TRANSLATION_RESOURCES;
import static com.bogdatech.enums.ErrorEnum.SHOPIFY_CONNECT_ERROR;
import static com.bogdatech.enums.ErrorEnum.TRANSLATE_ERROR;
import static com.bogdatech.logic.ShopifyService.getVariables;

@Component
@EnableAsync
//@Transactional
public class TranslateService {

    @Autowired
    private TranslateApiIntegration translateApiIntegration;

    @Autowired
    private ShopifyHttpIntegration shopifyApiIntegration;

    @Autowired
    private ShopifyService shopifyService;

    @Autowired
    private TestingEnvironmentIntegration testingEnvironmentIntegration;

    @Autowired
    private ITranslatesService translatesService;

    @Autowired
    private ITranslationCounterService translationCounterService;

    @Autowired
    private ITranslateTextService translateTextService;

    @Autowired
    private ALiYunTranslateIntegration aliYunTranslateIntegration;
    private final TelemetryClient appInsights = new TelemetryClient();

    public static Map<String, Map<String, String>> SINGLE_LINE_TEXT = new HashMap<>();

    @Autowired
    private JsoupUtils jsoupUtils;

    //百度翻译接口
    public String baiDuTranslate(TranslateRequest request) {
        String result = translateApiIntegration.baiDuTranslate(request);
        if (result != null) {
            return result;
        }
        return TRANSLATE_ERROR.getErrMsg();
    }

    //google翻译接口
    public String googleTranslate(TranslateRequest request) {
        return translateApiIntegration.googleTranslate(request);
    }

    //封装调用云服务器实现获取谷歌翻译数据的方法
    public String getGoogleTranslateData(TranslateRequest request) {
        // 使用 ObjectMapper 将对象转换为 JSON 字符串
        ObjectMapper objectMapper = new ObjectMapper();
        String string;
        try {
            String requestBody = objectMapper.writeValueAsString(request);
            string = testingEnvironmentIntegration.sendShopifyPost("translate/googleTranslate", requestBody);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        return string;
    }

    //封装调用云服务器实现将数据存入shopify本地的方法
    public void saveToShopify(CloudInsertRequest cloudServiceRequest) {
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            String requestBody = objectMapper.writeValueAsString(cloudServiceRequest);
//            System.out.println("requestBody: " + requestBody);
            testingEnvironmentIntegration.sendShopifyPost("translate/insertTranslatedText", requestBody);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @Async
    public void test(TranslatesDO request) {
        appInsights.trackTrace("我要翻译了" + Thread.currentThread().getName());
        //睡眠1分钟
        try {
            Thread.sleep(1000 * 60);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        appInsights.trackTrace("翻译完成" + Thread.currentThread().getName());
        //更新状态
        translatesService.updateTranslateStatus(request.getShopName(), 1, request.getTarget(), request.getSource());
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
            throw new RuntimeException(e);
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
                value = StringUtils.replaceSpaces(value, "-");
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
    public void translating(TranslateRequest request, int remainingChars, CharacterCountUtils counter) {
        ShopifyRequest shopifyRequest = TypeConversionUtils.convertTranslateRequestToShopifyRequest(request);
        CloudServiceRequest cloudServiceRequest = TypeConversionUtils.shopifyToCloudServiceRequest(shopifyRequest);

        //一个用户当前只能翻译一条语言，根据用户的status判断
        List<Integer> integers = translatesService.readStatusInTranslatesByShopName(request);
        for (Integer integer : integers) {
            if (integer == 2) {
                throw new ClientException("The translation task is in progress. Please try translating again later.");
            }
        }

        // 如果没有超限，则开始翻译流程
        translatesService.updateTranslateStatus(request.getShopName(), 2, request.getTarget(), request.getSource());
        //TRANSLATION_RESOURCES
        for (TranslateResourceDTO translateResource : TRANSLATION_RESOURCES) {
            translateResource.setTarget(request.getTarget());
            String query = new ShopifyQuery().getFirstQuery(translateResource);
            cloudServiceRequest.setBody(query);
            String shopifyData = shopifyService.getShopifyData(cloudServiceRequest);
            translateJson(shopifyData, shopifyRequest, translateResource, counter, remainingChars);
            System.out.println("已经使用了： " + counter.getTotalChars() + "个字符");
        }

//         更新数据库中的已使用字符数
        translationCounterService.updateUsedCharsByShopName(new TranslationCounterRequest(0, request.getShopName(), 0, counter.getTotalChars(), 0, 0, 0));
        // 将翻译状态改为“部分翻译”
        translatesService.updateTranslateStatus(request.getShopName(), 3, request.getTarget(), request.getSource());

    }

    //根据返回的json片段，将符合条件的value翻译,并返回json片段
    @Async
    public void translateJson(String objectData, ShopifyRequest request, TranslateResourceDTO translateResourceDTO, CharacterCountUtils counter, int remainingChars) {
        System.out.println("现在翻译到： " + translateResourceDTO.getResourceType());
        if (objectData == null) {
            // 返回默认值或空结果
            return;
        }
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode rootNode;
        try {
            rootNode = objectMapper.readTree(objectData);
            translateSingleLineTextFieldsRecursively(rootNode, request, counter, remainingChars);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        // 递归处理下一页数据
        handlePagination(rootNode, request, counter, translateResourceDTO, remainingChars);
    }

    // 递归处理下一页数据
    private void handlePagination(JsonNode translatedRootNode, ShopifyRequest request, CharacterCountUtils counter, TranslateResourceDTO translateResourceDTO, int remainingChars) {
        // 获取translatableResources节点
        JsonNode translatableResourcesNode = translatedRootNode.path("translatableResources");
        // 获取pageInfo节点
        JsonNode pageInfoNode = translatableResourcesNode.path("pageInfo");

        if (translatableResourcesNode.hasNonNull("pageInfo")) {
            if (pageInfoNode.hasNonNull("hasNextPage") && pageInfoNode.get("hasNextPage").asBoolean()) {
                JsonNode endCursor = pageInfoNode.get("endCursor");
                translateResourceDTO.setAfter(endCursor.asText());
                translateNextPage(request, counter, translateResourceDTO, remainingChars);
            }
        }
    }

    //递归遍历JSON树：使用 translateSingleLineTextFieldsRecursively 方法递归地遍历整个 JSON 树，并对 translatableContent 字段进行特别处理。
    private void translateSingleLineTextFieldsRecursively(JsonNode node, ShopifyRequest request, CharacterCountUtils counter, int remainingChars) {
        //定义HashMap存放判断后的对应数据
        HashMap<String, List<RegisterTransactionRequest>> judgeData = new HashMap<>();
        judgeData.put(DOUBLE_BRACES, new ArrayList<>());
        judgeData.put(PERCENTAGE_CURLY_BRACES, new ArrayList<>());
        judgeData.put(CURLY_BRACKET_ARRAY, new ArrayList<>());
        judgeData.put(DOUBLE_CURLY_BRACKET_AND_HUNDRED, new ArrayList<>());
        judgeData.put(PLAIN_TEXT, new ArrayList<>());
        judgeData.put(HTML, new ArrayList<>());
        judgeData.put(DATABASE, new ArrayList<>());
        // 获取 translatableResources 节点
        JsonNode translatableResourcesNode = node.path("translatableResources");
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

            String resourceId = null;
            ArrayNode translatableContent = null;

            Iterator<Map.Entry<String, JsonNode>> fields = nodeElement.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                String fieldName = field.getKey();
                JsonNode fieldValue = field.getValue();

                if ("resourceId".equals(fieldName)) {
                    resourceId = fieldValue.asText();
                } else if ("translatableContent".equals(fieldName) && fieldValue.isArray()) {
                    translatableContent = (ArrayNode) fieldValue;
                }
            }

            if (resourceId != null && translatableContent != null) {
//                translateSingleLineTextFields(translatableContent, request, counter, resourceId, remainingChars);
                //存储集合在翻译
                judgeAndStoreData(translatableContent, resourceId, judgeData);
            }
        }

        //对judgeData数据进行翻译和存入shopify,除了html
        translateAndSaveData(judgeData, request, counter, remainingChars);
    }

    //对judgeData数据进行翻译和存入shopify,除了html
    private void translateAndSaveData(HashMap<String, List<RegisterTransactionRequest>> judgeData, ShopifyRequest request, CharacterCountUtils counter, int remainingChars) {
        for (Map.Entry<String, List<RegisterTransactionRequest>> entry : judgeData.entrySet()) {
//            System.out.println("Key: " + entry.getKey() + ", Value: " + entry.getValue().toString());
            switch (entry.getKey()) {
                case CURLY_BRACKET_ARRAY:
                    translateDataByAPI(entry.getValue(), request, counter, remainingChars, 1);
                    break;
                case DOUBLE_BRACES:
                    translateDataByAPI(entry.getValue(), request, counter, remainingChars, 2);
                    break;
                case PERCENTAGE_CURLY_BRACES:
                    translateDataByAPI(entry.getValue(), request, counter, remainingChars, 3);
                    break;
                case DOUBLE_CURLY_BRACKET_AND_HUNDRED:
                    translateDataByAPI(entry.getValue(), request, counter, remainingChars, 4);
                    break;
                case PLAIN_TEXT:
                    translateDataByAPI(entry.getValue(), request, counter, remainingChars, 5);
                    break;
                case HTML:
                    translateHtml(entry.getValue(), request, counter, remainingChars);
                    break;
                default:
                    //处理database数据
                    translateDataByDatabase(entry.getValue(), request, counter, remainingChars);
                    break;
            }
        }
    }

    private void translateDataByDatabase(List<RegisterTransactionRequest> registerTransactionRequests, ShopifyRequest request, CharacterCountUtils counter, int remainingChars) {
        String target = request.getTarget();
        Map<String, Object> translation = new HashMap<>();
        for (RegisterTransactionRequest registerTransactionRequest : registerTransactionRequests) {
            String value = registerTransactionRequest.getValue();
            String translatableContentDigest = registerTransactionRequest.getTranslatableContentDigest();
            String key = registerTransactionRequest.getKey();
            String source = registerTransactionRequest.getLocale();
            String resourceId = registerTransactionRequest.getResourceId();
            translation.put("locale", target);
            translation.put("key", key);
            translation.put("translatableContentDigest", translatableContentDigest);
            updateCharsWhenExceedLimit(counter, request.getShopName(), remainingChars, new TranslateRequest(0, null, null, source, target, null));
            //从数据库中获取数据，如果不为空，存入shopify本地；如果为空翻译
            String targetText = translateTextService.getTargetTextByDigest(translatableContentDigest, target)[0];
            String targetCache = translateSingleLine(value, request.getTarget());
            counter.addChars(value.length());
            if (targetCache != null) {
                saveToShopify(targetCache, translation, resourceId, request);
                continue;
            } else if (targetText != null) {
                addData(target, value, targetText);
                saveToShopify(targetText, translation, resourceId, request);
                continue;
            }
        }
    }

    //对html数据处理
    private void translateHtml(List<RegisterTransactionRequest> registerTransactionRequests, ShopifyRequest request, CharacterCountUtils counter, int remainingChars) {
        String target = request.getTarget();
        Map<String, Object> translation = new HashMap<>();
        for (RegisterTransactionRequest registerTransactionRequest : registerTransactionRequests) {
            String value = registerTransactionRequest.getValue();
            String translatableContentDigest = registerTransactionRequest.getTranslatableContentDigest();
            String key = registerTransactionRequest.getKey();
            String source = registerTransactionRequest.getLocale();
            String resourceId = registerTransactionRequest.getResourceId();
            translation.put("locale", target);
            translation.put("key", key);
            translation.put("translatableContentDigest", translatableContentDigest);
            //判断是否超限
            updateCharsWhenExceedLimit(counter, request.getShopName(), remainingChars, new TranslateRequest(0, null, null, source, target, null));
            counter.addChars(value.length());
            String targetText = jsoupUtils.translateHtml(value, new TranslateRequest(0, null, null, source, target, value), counter, request.getTarget());
            saveToShopify(targetText, translation, resourceId, request);
        }
    }

    //对不同的数据使用不同的翻译api
    private void translateDataByAPI(List<RegisterTransactionRequest> registerTransactionRequests, ShopifyRequest request, CharacterCountUtils counter, int remainingChars, int chooseData) {
        String target = request.getTarget();
        Map<String, Object> translation = new HashMap<>();
        for (RegisterTransactionRequest registerTransactionRequest : registerTransactionRequests) {
            String value = registerTransactionRequest.getValue();
            String translatableContentDigest = registerTransactionRequest.getTranslatableContentDigest();
            String key = registerTransactionRequest.getKey();
            String source = registerTransactionRequest.getLocale();
            String resourceId = registerTransactionRequest.getResourceId();
            translation.put("locale", target);
            translation.put("key", key);
            translation.put("translatableContentDigest", translatableContentDigest);
            //判断是否超限
            updateCharsWhenExceedLimit(counter, request.getShopName(), remainingChars, new TranslateRequest(0, null, null, source, target, null));
            //获取缓存数据
            String targetCache = translateSingleLine(value, request.getTarget());
            counter.addChars(value.length());
//            System.out.println("是否超限： " + counter.getTotalChars());
            if (targetCache != null) {
                saveToShopify(targetCache, translation, resourceId, request);
            } else {
                //根据chooseData来判断用什么翻译API
                chooseTranslateApi(translation, registerTransactionRequest, request, chooseData);
            }
        }
    }

    //根据chooseData来判断用什么翻译API
    private void chooseTranslateApi(Map<String, Object> translation, RegisterTransactionRequest registerTransactionRequest, ShopifyRequest request, int chooseData) {
        String targetString;
        String resourceId = registerTransactionRequest.getResourceId();
        String value = registerTransactionRequest.getValue();
        String target = request.getTarget();
        String source = registerTransactionRequest.getLocale();
        switch (chooseData) {
            case 1, 2:
                targetString = getGoogleTranslateData(new TranslateRequest(0, null, null, source, target, value));
//                targetString = translateApiIntegration.microsoftTranslate(new TranslateRequest(0, null, null, source, target, value));
                addData(target, value, targetString);
                saveToShopify(targetString, translation, resourceId, request);
                break;
            case 3:
                //阿里云API 待接入
                targetString = getGoogleTranslateData(new TranslateRequest(0, null, null, source, target, value));
//                targetString = aliYunTranslateIntegration.aliyunTranslate(new TranslateRequest(0, null, null, source, target, value));
                addData(target, value, targetString);
                saveToShopify(targetString, translation, resourceId, request);
                break;
            case 4:
                //谷歌API
//                targetString = translateApiIntegration.microsoftTranslate(new TranslateRequest(0, null, null, source, target, value));
                targetString = getGoogleTranslateData(new TranslateRequest(0, null, null, source, target, value));
                addData(target, value, targetString);
                saveToShopify(targetString, translation, resourceId, request);
                break;
            default:
                //百度API，火山API等等
//                targetString = translateApiIntegration.microsoftTranslate(new TranslateRequest(0, null, null, source, target, value));
                targetString = getGoogleTranslateData(new TranslateRequest(0, null, null, source, target, value));
                addData(target, value, targetString);
                saveToShopify(targetString, translation, resourceId, request);
                break;
        }
    }

    //将获得的TRANSLATION_RESOURCES数据进行判断 存储到不同集合， 对不同集合的数据进行特殊处理
    private void judgeAndStoreData(ArrayNode contentNode, String resourceId, Map<String, List<RegisterTransactionRequest>> judgeData) {
        for (JsonNode contentItem : contentNode) {
            ObjectNode contentItemNode = (ObjectNode) contentItem;

            //             跳过 key 为 "handle" 的项
            if ("handle".equals(contentItemNode.get("key").asText())
                    || "JSON".equals(contentItemNode.get("type").asText())
                    || "JSON_STRING".equals(contentItemNode.get("type").asText())
            ) {
                continue;  // 跳过当前项
            }

            String value = contentItemNode.get("value").asText();
//            System.out.println("当前正在翻译： " + value);
            // 跳过 value 为空的项
            if (value == null || value.isEmpty()) {
                continue;  // 跳过当前项
            }
            String locale = contentItemNode.get("locale").asText();
            String translatableContentDigest = contentItemNode.get("digest").asText();
            String key = contentItemNode.get("key").asText();

            //            //对从数据库中获取的数据单独处理
            if ("ONLINE_STORE_THEME".equals(contentItemNode.get("type").asText()) ||
                    "ONLINE_STORE_THEME_LOCALE_CONTENT".equals(contentItemNode.get("type").asText())
                    || "SHOP_POLICY".equals(contentItemNode.get("type").asText())
                    || "PACKING_SLIP_TEMPLATE".equals(contentItemNode.get("type").asText())
                    || "EMAIL_TEMPLATE".equals(contentItemNode.get("type").asText())) {
                judgeData.get(DATABASE).add(new RegisterTransactionRequest(null, null, locale, key, value, translatableContentDigest, resourceId, null));
                continue;
            }
            //对value进行判断 plainText
            if ("HTML".equals(contentItemNode.get("type").asText())) {
                //存放在html的list集合里面
                judgeData.get(HTML).add(new RegisterTransactionRequest(null, null, locale, key, value, translatableContentDigest, resourceId, null));
                continue;
            }

            //用switch语句判断类型
            switch (StringUtils.judgeStringType(value)) {
                case CURLY_BRACKET_ARRAY:
                    judgeData.get(CURLY_BRACKET_ARRAY).add(new RegisterTransactionRequest(null, null, locale, key, value, translatableContentDigest, resourceId, null));
                    break;
                case DOUBLE_BRACES:
                    judgeData.get(DOUBLE_BRACES).add(new RegisterTransactionRequest(null, null, locale, key, value, translatableContentDigest, resourceId, null));
                    break;
                case PERCENTAGE_CURLY_BRACES:
                    judgeData.get(PERCENTAGE_CURLY_BRACES).add(new RegisterTransactionRequest(null, null, locale, key, value, translatableContentDigest, resourceId, null));
                    break;
                case DOUBLE_CURLY_BRACKET_AND_HUNDRED:
                    judgeData.get(DOUBLE_CURLY_BRACKET_AND_HUNDRED).add(new RegisterTransactionRequest(null, null, locale, key, value, translatableContentDigest, resourceId, null));
                    break;
                default:
                    judgeData.get(PLAIN_TEXT).add(new RegisterTransactionRequest(null, null, locale, key, value, translatableContentDigest, resourceId, null));
                    break;
            }

        }

    }

    //将获得的DATABASE_RESOURCES数据进行判断 存储到不同集合， 对不同集合的数据进行特殊处理


    //对符合条件的  value 进行翻译并存储到shopify本地
    private void translateSingleLineTextFields(ArrayNode contentNode, ShopifyRequest request, CharacterCountUtils counter, String resourceId, int remainingChars) {
        //初始化存储到shopify本地的数据
        Map<String, Object> translation = new HashMap<>();
        String target = request.getTarget();
        for (JsonNode contentItem : contentNode) {
            ObjectNode contentItemNode = (ObjectNode) contentItem;
            //打印当前遍历的值 为什么部分不翻译

//             跳过 key 为 "handle" 的项
            if ("handle".equals(contentItemNode.get("key").asText())
                    || "JSON".equals(contentItemNode.get("type").asText())
                    || "JSON_STRING".equals(contentItemNode.get("type").asText())
            ) {
                continue;  // 跳过当前项
            }

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
//            System.out.println("翻译内容：" + value);
//            System.out.println("翻译字符数：" + counter.getTotalChars());

            //处理SINGLE_LINE_TEXT_FIELD的情况
            if ("SINGLE_LINE_TEXT_FIELD".equals(contentItemNode.get("type").asText())) {
                //达到字符限制，更新用户剩余字符数，终止循环
                updateCharsWhenExceedLimit(counter, request.getShopName(), remainingChars, new TranslateRequest(0, null, null, source, target, null));
                String targetCache = translateSingleLine(value, request.getTarget());
                counter.addChars(value.length());
                if (targetCache != null) {
                    saveToShopify(targetCache, translation, resourceId, request);
                    continue;
                } else {
//                    String target = getGoogleTranslateData(new TranslateRequest(0, null, null, source, target, value));
                    String targetString = translateApiIntegration.microsoftTranslate(new TranslateRequest(0, null, null, source, target, value));
                    addData(target, value, targetString);
                    saveToShopify(targetString, translation, resourceId, request);
                }
                continue;
            }

            //处理type为HTML的情况
            if ("HTML".equals(contentItemNode.get("type").asText())) {
                //达到字符限制，更新用户剩余字符数，终止循环
                updateCharsWhenExceedLimit(counter, request.getShopName(), remainingChars, new TranslateRequest());
                if (jsoupUtils.isHtml(value)) {
                    String targetText = jsoupUtils.translateHtml(value, new TranslateRequest(0, null, null, source, target, value), counter, request.getTarget());
                    saveToShopify(targetText, translation, resourceId, request);
                    continue;
                }
            }

            //处理type为ONLINE_STORE_THEME的情况
            if ("ONLINE_STORE_THEME".equals(contentItemNode.get("type").asText()) ||
                    "ONLINE_STORE_THEME_LOCALE_CONTENT".equals(contentItemNode.get("type").asText())
                    || "SHOP_POLICY".equals(contentItemNode.get("type").asText())
                    || "PACKING_SLIP_TEMPLATE".equals(contentItemNode.get("type").asText())
                    || "EMAIL_TEMPLATE".equals(contentItemNode.get("type").asText())) {
                updateCharsWhenExceedLimit(counter, request.getShopName(), remainingChars, new TranslateRequest(0, null, null, source, target, null));
                //从数据库中获取数据，如果不为空，存入shopify本地；如果为空翻译
                String targetText = translateTextService.getTargetTextByDigest(contentItemNode.get("digest").asText(), target)[0];
                String targetCache = translateSingleLine(value, request.getTarget());
                counter.addChars(value.length());
                if (targetCache != null) {
                    saveToShopify(targetCache, translation, resourceId, request);
                    continue;
                } else if (targetText != null) {
                    addData(target, value, targetText);
                    saveToShopify(targetText, translation, resourceId, request);
                    continue;
                } else {
//                    String target = getGoogleTranslateData(new TranslateRequest(0, null, null, source, target, value));
                    String targetString = translateApiIntegration.microsoftTranslate(new TranslateRequest(0, null, null, source, target, value));
                    addData(target, value, targetString);
                    saveToShopify(targetString, translation, resourceId, request);
                    continue;
                }
            }

            //达到字符限制，更新用户剩余字符数，终止循环
            updateCharsWhenExceedLimit(counter, request.getShopName(), remainingChars, new TranslateRequest(0, null, null, source, target, null));
            counter.addChars(value.length());
            //做一个缓存判断
            String targetCache = translateSingleLine(value, request.getTarget());
            if (targetCache != null) {
                saveToShopify(targetCache, translation, resourceId, request);
                continue;
            } else {
//            String translatedValue = getGoogleTranslateData(new TranslateRequest(0, null, null, source, target, value));;
                String targetString = translateApiIntegration.microsoftTranslate(new TranslateRequest(0, null, null, source, target, value));
                addData(target, value, targetString);
                saveToShopify(targetString, translation, resourceId, request);
            }
        }
    }

    //
    public static void addData(String outerKey, String innerKey, String value) {
        // 获取外层键对应的内层 Map
        Map<String, String> innerMap = SINGLE_LINE_TEXT.get(outerKey);

        // 如果外层键不存在，则创建一个新的内层 Map
        if (innerMap == null) {
            innerMap = new HashMap<>();
            SINGLE_LINE_TEXT.put(outerKey, innerMap);
        }

        // 将新的键值对添加到内层 Map 中
        innerMap.put(innerKey, value);
    }

    //将翻译后的数据存shopify本地中
    public void saveToShopify(String translatedValue, Map<String, Object> translation, String resourceId, ShopifyRequest request) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("resourceId", resourceId);
        translation.put("value", translatedValue);
        Object[] translations = new Object[]{
                translation // 将HashMap添加到数组中
        };
        variables.put("translations", translations);
        //将翻译后的内容通过ShopifyAPI记录到shopify本地
        saveToShopify(new CloudInsertRequest(request.getShopName(), request.getAccessToken(), request.getApiVersion(), request.getTarget(), variables));
    }


    //达到字符限制，更新用户剩余字符数，终止循环
    public void updateCharsWhenExceedLimit(CharacterCountUtils counter, String shopName, int remainingChars, TranslateRequest translateRequest) {
        TranslationCounterRequest request = new TranslationCounterRequest();
        request.setShopName(shopName);
//        System.out.println("counter " + counter.getTotalChars() );
        if (counter.getTotalChars() >= remainingChars) {
            translatesService.updateTranslateStatus(shopName, 3, translateRequest.getTarget(), translateRequest.getSource());
            translationCounterService.updateUsedCharsByShopName(new TranslationCounterRequest(0, shopName, 0, counter.getTotalChars(), 0, 0, 0));
            throw new ClientException("Character Limit Reached");
        }
    }

    //修改getTestQuery里面的testQuery，用获取后的的查询语句进行查询
    public JsonNode fetchNextPage(TranslateResourceDTO translateResource, ShopifyRequest request) {
        CloudServiceRequest cloudServiceRequest = TypeConversionUtils.shopifyToCloudServiceRequest(request);
        String query = new ShopifyQuery().getAfterQuery(translateResource);
        cloudServiceRequest.setBody(query);

        String infoByShopify = shopifyService.getShopifyData(cloudServiceRequest);
        if (infoByShopify == null) {
            throw new IllegalArgumentException(SHOPIFY_CONNECT_ERROR.getErrMsg());
        }
        ObjectMapper objectMapper = new ObjectMapper();

        try {
            return objectMapper.readTree(infoByShopify);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse JSON response", e);
        }
    }

    //递归处理下一页数据
    private void translateNextPage(ShopifyRequest request, CharacterCountUtils counter, TranslateResourceDTO
            translateResource, int remainingChars) {
        JsonNode nextPageData = fetchNextPage(translateResource, request);
        // 重新开始翻译流程
        translateSingleLineTextFieldsRecursively(nextPageData, request, counter, remainingChars);
        // 递归处理下一页数据
        handlePagination(nextPageData, request, counter, translateResource, remainingChars);
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
        System.out.println("现在存储到： " + translateResourceDTO.getResourceType());
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
        //用封装后的接口测，没有data节点
//        JsonNode translatableResourcesNode = rootNode.path("data").path("translatableResources").path("nodes");
        JsonNode translatableResourcesNode = rootNode.path("translatableResources").path("nodes");
        Map<String, TranslateTextDO> translatableContentMap = null;
        List<TranslateTextDO> depositContents = new ArrayList<>();
        for (JsonNode node : translatableResourcesNode) {
            String resourceId = node.path("resourceId").asText();
            Map<String, TranslateTextDO> translationsMap = extractTranslations(node, resourceId, request);
            translatableContentMap = extractTranslatableContent(node, translationsMap);
//            System.out.println("合并后的map数据为： " + translatableContentMap);

        }
        if (translatableContentMap != null) {
            List<TranslateTextDO> translateTextDOList = new ArrayList<>(translatableContentMap.values());
            translateTextService.getExistTranslateTextList(translateTextDOList);
        }
//        translatesService.readExistTranslateText(translatableContentMap.values());
//        for (TranslateTextDO translateTextDO : translatableContentMap.values()) {
//            // 查询数据库以获取所有已存在的实体
////            System.out.println("当前遍历数据为： " + translateTextDO);
//            if (translateTextService.getTranslateText(translateTextDO) == null) {
//                System.out.println("新增数据为： " + translateTextDO);
//                depositContents.add(translateTextDO);
//            }
//        }
//        //将translatableContentMap里的数据存数据库里
//        System.out.println("存入数据库的数据为： " + depositContents);
//        translateTextService.saveBatch(depositContents);

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
    private static Map<String, TranslateTextDO> extractTranslations(JsonNode node, String resourceId, ShopifyRequest shopifyRequest) {
        Map<String, TranslateTextDO> translations = new HashMap<>();
        JsonNode translationsNode = node.path("translations");
        if (translationsNode.isArray() && !translationsNode.isEmpty()) {
            translationsNode.forEach(translation -> {
                if (translation.path("value").asText().isEmpty() || translation.path("key").asText().isEmpty()) {
                    return;
                }
                TranslateTextDO translateTextDO = new TranslateTextDO();
                translateTextDO.setTextKey(translation.path("key").asText());
                translateTextDO.setTargetText(translation.path("value").asText());
                translateTextDO.setTargetCode(translation.path("locale").asText());
                translateTextDO.setResourceId(resourceId);
                translateTextDO.setShopName(shopifyRequest.getShopName());
                translations.put(translation.path("key").asText(), translateTextDO);

            });
        }
        return translations;
    }

    //获取一个页面所有TranslatableContent集合数据
    private static Map<String, TranslateTextDO> extractTranslatableContent(JsonNode node, Map<String, TranslateTextDO> translations) {
        JsonNode contentNode = node.path("translatableContent");
        if (contentNode.isArray() && !contentNode.isEmpty()) {
            contentNode.forEach(content -> {
                TranslateTextDO keys = translations.get(content.path("key").asText());
                if (translations.get(content.path("key").asText()) != null) {
                    keys.setSourceCode(content.path("locale").asText());
                    keys.setTextType(content.path("type").asText());
                    keys.setDigest(content.path("digest").asText());
                    keys.setSourceText(content.path("value").asText());
                }
            });
        }
        //存入数据库
        return translations;
    }

    //合并两个map集合数据
//    private Map<String, Object> mergeMaps(Map<String, Object> map1, Map<String, Object> map2) {
////        appInsights.trackTrace("进入合并的方法里面");
//        Map<String, Object> result = new HashMap<>(map1);
//        map2.forEach((key, value) -> {
////            appInsights.trackTrace("当前的key为: " + key);
//            if (result.containsKey(key)) {
//                // 如果键冲突，合并两个Map
//                Map<String, Object> existingValue = (Map<String, Object>) result.get(key);
//                Map<String, Object> newValue = (Map<String, Object>) value;
//                Map<String, Object> mergedValue = new HashMap<>(existingValue);
//                mergedValue.putAll(newValue);
//                result.put((String) mergedValue.get("digest"), mergedValue);
//            }
//        });
//        return result;
//    }

    //根据数据库中digest进行判断，有更新，无插入
    private void updateOrInsertTranslateTextData(Map<String, Object> data, ShopifyRequest shopifyRequest) {
        if (data.get("digest") != null) {
            TranslateTextRequest request = new TranslateTextRequest();
            request.setTargetText(data.get("targetText").toString());
            request.setResourceId(data.get("resourceId").toString());
            request.setDigest(data.get("digest").toString());
            request.setSourceCode(data.get("sourceCode").toString());
            request.setTargetCode(shopifyRequest.getTarget());
            request.setShopName(data.get("shopName").toString());
            request.setTextKey(data.get("textKey").toString());
            request.setSourceText(data.get("sourceText").toString());
            request.setTextType(data.get("textType").toString());
            // 检查是否存在有digest的数据
//            TranslateTextDO translateText = translateTextService.getTranslateText(request);

//            //将获取的数据存入SINGLE_LINE_TEXT map中
//            if (!SINGLE_LINE_TEXT.containsKey(request.getSourceText())){
////                System.out.println("sourceText: " + request.getSourceText());
//                SINGLE_LINE_TEXT.put(request.getSourceText(), request.getTargetText());
//            }

            //TODO 可以考虑一下，一次插入多条数据
            //将获取的数据存入数据库中
            //            if (translateText.isEmpty()) {
            //                System.out.println("text: " + request.getTargetText());
            //                // 插入数据
            //                jdbcRepository.insertTranslateText(request);
            ////                System.out.println("插入数据 ： " + translateText);
            //            } else {
            //                // 更新数据
            //                jdbcRepository.updateTranslateText(request);
            ////                System.out.println("更新数据 ： " + translateText);
            //            }
        }
    }


    //循环存数据库
    @Async
    public void saveTranslateText(TranslateRequest request) {
        ShopifyRequest shopifyRequest = new ShopifyRequest();
        shopifyRequest.setTarget(request.getTarget());
        shopifyRequest.setShopName(request.getShopName());
        shopifyRequest.setAccessToken(request.getAccessToken());
        CloudServiceRequest cloudServiceRequest = TypeConversionUtils.shopifyToCloudServiceRequest(shopifyRequest);
        for (TranslateResourceDTO translateResource : DATABASE_RESOURCES) {
            ShopifyQuery shopifyQuery = new ShopifyQuery();
            translateResource.setTarget(shopifyRequest.getTarget());
            String query = shopifyQuery.getFirstQuery(translateResource);
            cloudServiceRequest.setBody(query);
            String string = shopifyService.getShopifyData(cloudServiceRequest);
            saveTranslatedData(string, shopifyRequest, translateResource);
        }
        System.out.println("内存存储成功");
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

    //测试翻译html文本(待删)
    public String translateHtmlText(TranslateRequest request) {
        String html = "<html><body>" +
                "<h1>Hello, World!</h1><p>This is a test.</p>" +
                "<img alt=\\\"儿童化妆玩具\\\" src=\\\"https://zinnbuy2407-1327177217.cos.na-ashburn.myqcloud.com/images3_spmp/2024/01/06/eb/17045336072733e45098feb673430f91e1bbc1eebc_square.jpg\\\">" +
                "<p>Last updated: {{ last_updated }}</p></body></html>";
        return jsoupUtils.translateHtml(html, request, null, request.getTarget());
    }


    //将数据存入本地Map中
    //优化策略1： 利用翻译后的数据，对singleLine的数据全局匹配并翻译
    public String translateSingleLine(String sourceText, String target) {
        if (SINGLE_LINE_TEXT.get(target) != null) {
//            System.out.println("translateSingleLine2: " + SINGLE_LINE_TEXT.get(target).get(sourceText));
            return SINGLE_LINE_TEXT.get(target).get(sourceText);

        }
        return null;
    }


}

