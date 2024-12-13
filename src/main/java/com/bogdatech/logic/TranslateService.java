package com.bogdatech.logic;


import com.bogdatech.Service.IGlossaryService;
import com.bogdatech.Service.ITranslateTextService;
import com.bogdatech.Service.ITranslatesService;
import com.bogdatech.Service.ITranslationCounterService;
import com.bogdatech.entity.GlossaryDO;
import com.bogdatech.entity.TranslateResourceDTO;
import com.bogdatech.entity.TranslateTextDO;
import com.bogdatech.entity.TranslatesDO;
import com.bogdatech.exception.ClientException;
import com.bogdatech.integration.ShopifyHttpIntegration;
import com.bogdatech.integration.TestingEnvironmentIntegration;
import com.bogdatech.integration.TranslateApiIntegration;
import com.bogdatech.model.controller.request.*;
import com.bogdatech.requestBody.ShopifyRequestBody;
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
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Component;

import java.util.*;

import static com.bogdatech.constants.TranslateConstants.*;
import static com.bogdatech.entity.TranslateResourceDTO.ALL_RESOURCES;
import static com.bogdatech.entity.TranslateResourceDTO.DATABASE_RESOURCES;
import static com.bogdatech.enums.ErrorEnum.*;
import static com.bogdatech.logic.ShopifyService.getVariables;
import static com.bogdatech.utils.CaseSensitiveUtils.containsValue;
import static com.bogdatech.utils.CaseSensitiveUtils.containsValueIgnoreCase;

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
    private IGlossaryService glossaryService;
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
            testingEnvironmentIntegration.sendShopifyPost("translate/insertTranslatedText", requestBody);
        } catch (JsonProcessingException e) {
            appInsights.trackTrace("Failed to save to Shopify: " + e.getMessage());
//            throw new ClientException("Failed to deposit locally");
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
        translatesService.updateTranslateStatus(request.getShopName(), 1, request.getTarget(), request.getSource(), request.getAccessToken());
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

        //判断是否有同义词
        Map<String, Object> glossaryMap = new HashMap<>();
        getGlossaryByShopName(shopifyRequest, glossaryMap);
        System.out.println("同义词：" + glossaryMap);

//        // 如果没有超限，则开始翻译流程
        translatesService.updateTranslateStatus(request.getShopName(), 2, request.getTarget(), request.getSource(), request.getAccessToken());
        //TRANSLATION_RESOURCES
        for (TranslateResourceDTO translateResource : ALL_RESOURCES) {
            translateResource.setTarget(request.getTarget());
            String query = new ShopifyRequestBody().getFirstQuery(translateResource);
            cloudServiceRequest.setBody(query);
            String shopifyData = shopifyService.getShopifyData(cloudServiceRequest);
            translateJson(shopifyData, shopifyRequest, translateResource, counter, remainingChars, glossaryMap);
//            System.out.println("已经使用了： " + counter.getTotalChars() + "个字符");
        }


//         更新数据库中的已使用字符数
        translationCounterService.updateUsedCharsByShopName(new TranslationCounterRequest(0, request.getShopName(), 0, counter.getTotalChars(), 0, 0, 0));
        // 将翻译状态改为“已翻译”// TODO: 正常来说是部分翻译，逻辑后面再改
        translatesService.updateTranslateStatus(request.getShopName(), 1, request.getTarget(), request.getSource(), request.getAccessToken());

    }

    //判断词汇表中要判断的词
    public void getGlossaryByShopName(ShopifyRequest request, Map<String, Object> glossaryMap) {
        GlossaryDO[] glossaryDOS = glossaryService.getGlossaryByShopName(request.getShopName());
        if (glossaryDOS == null) {
            return; // 如果术语表为空，直接返回
        }

        for (GlossaryDO glossaryDO : glossaryDOS) {
            // 判断语言范围是否符合
            if (glossaryDO.getRangeCode().equals(request.getTarget()) || glossaryDO.getRangeCode().equals("ALL")) {
                // 判断术语是否启用
                if (glossaryDO.getStatus() != 1) {
                    continue;
                }

                // 存储术语数据
                glossaryMap.put(glossaryDO.getSourceText(), new GlossaryDO(glossaryDO.getSourceText(), glossaryDO.getTargetText(), glossaryDO.getCaseSensitive()));
            }
        }
    }

    //根据返回的json片段，将符合条件的value翻译,并返回json片段
    @Async
    public void translateJson(String objectData, ShopifyRequest request, TranslateResourceDTO translateResourceDTO, CharacterCountUtils counter, int remainingChars, Map<String, Object> glossaryMap) {
//        System.out.println("现在翻译到： " + translateResourceDTO.getResourceType());
        if (objectData == null) {
            // 返回默认值或空结果
            return;
        }
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode rootNode;
        try {
            rootNode = objectMapper.readTree(objectData);
            translateSingleLineTextFieldsRecursively(rootNode, request, counter, remainingChars, translateResourceDTO.getResourceType(), glossaryMap);
        } catch (JsonProcessingException e) {
            throw new ClientException(e.getMessage());
        }
        // 递归处理下一页数据
        handlePagination(rootNode, request, counter, translateResourceDTO, remainingChars, glossaryMap);
    }

    // 递归处理下一页数据
    private void handlePagination(JsonNode translatedRootNode, ShopifyRequest request, CharacterCountUtils counter, TranslateResourceDTO translateResourceDTO, int remainingChars, Map<String, Object> glossaryMap) {
        // 获取translatableResources节点
        JsonNode translatableResourcesNode = translatedRootNode.path("translatableResources");
        // 获取pageInfo节点
        JsonNode pageInfoNode = translatableResourcesNode.path("pageInfo");

        if (translatableResourcesNode.hasNonNull("pageInfo")) {
            if (pageInfoNode.hasNonNull("hasNextPage") && pageInfoNode.get("hasNextPage").asBoolean()) {
                JsonNode endCursor = pageInfoNode.get("endCursor");
                translateResourceDTO.setAfter(endCursor.asText());
                translateNextPage(request, counter, translateResourceDTO, remainingChars, glossaryMap);
            }
        }
    }

    //递归遍历JSON树：使用 translateSingleLineTextFieldsRecursively 方法递归地遍历整个 JSON 树，并对 translatableContent 字段进行特别处理。
    private void translateSingleLineTextFieldsRecursively(JsonNode node, ShopifyRequest request, CharacterCountUtils counter,
                                                          int remainingChars, String resourceType, Map<String, Object> glossaryMap) {
        //定义HashMap存放判断后的对应数据
        HashMap<String, List<RegisterTransactionRequest>> judgeData = new HashMap<>();
        judgeData.put(DOUBLE_BRACES, new ArrayList<>());
        judgeData.put(PERCENTAGE_CURLY_BRACES, new ArrayList<>());
        judgeData.put(CURLY_BRACKET_ARRAY, new ArrayList<>());
        judgeData.put(DOUBLE_CURLY_BRACKET_AND_HUNDRED, new ArrayList<>());
        judgeData.put(PLAIN_TEXT, new ArrayList<>());
        judgeData.put(HTML, new ArrayList<>());
        judgeData.put(DATABASE, new ArrayList<>());
        judgeData.put(JSON_TEXT, new ArrayList<>());
        judgeData.put(GLOSSARY_0, new ArrayList<>());
        judgeData.put(GLOSSARY_1, new ArrayList<>());
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
            Map<String, TranslateTextDO> translatableContentMap = null;
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                String fieldName = field.getKey();
                JsonNode fieldValue = field.getValue();
                if ("resourceId".equals(fieldName)) {
                    resourceId = fieldValue.asText();
                    //筛选出没有翻译的数据的数据
                    Map<String, TranslateTextDO> translationsMap = extractTranslations(nodeElement, resourceId, request);
                    translatableContentMap = extractTranslatableContent(nodeElement, translationsMap);
                    //对translatableContentMap遍历
                } else if ("translatableContent".equals(fieldName) && fieldValue.isArray()) {
                    translatableContent = (ArrayNode) fieldValue;
                }
                if (resourceId != null && translatableContent != null) {
                    //存储集合在翻译
                    judgeAndStoreData(translatableContent, resourceId, judgeData, resourceType, translatableContentMap, glossaryMap);
                }
                //对judgeData数据进行翻译和存入shopify,除了html
                translateAndSaveData(judgeData, request, counter, remainingChars, glossaryMap);
            }
        }


    }

    //对judgeData数据进行翻译和存入shopify,除了html
    private void translateAndSaveData(HashMap<String, List<RegisterTransactionRequest>> judgeData, ShopifyRequest request,
                                      CharacterCountUtils counter, int remainingChars, Map<String, Object> glossaryMap) {
        for (Map.Entry<String, List<RegisterTransactionRequest>> entry : judgeData.entrySet()) {
            switch (entry.getKey()) {
//                case CURLY_BRACKET_ARRAY:
//                    translateDataByAPI(entry.getValue(), request, counter, remainingChars, 1);
//                    break;
//                case DOUBLE_BRACES:
//                    translateDataByAPI(entry.getValue(), request, counter, remainingChars, 2);
//                    break;
//                case PERCENTAGE_CURLY_BRACES:
//                    translateDataByAPI(entry.getValue(), request, counter, remainingChars, 3);
//                    break;
//                case DOUBLE_CURLY_BRACKET_AND_HUNDRED:
//                    translateDataByAPI(entry.getValue(), request, counter, remainingChars, 4);
//                    break;
//                case PLAIN_TEXT:
//                    translateDataByAPI(entry.getValue(), request, counter, remainingChars, 5);
//                    break;
//                case HTML:
//                    translateHtml(entry.getValue(), request, counter, remainingChars);
//                    break;
//                case JSON_TEXT:
//                    translateJsonText(entry.getValue(), request);
//                    break;
//                case DATABASE:
//                    //处理database数据
//                    try {
//                        translateDataByDatabase(entry.getValue(), request, counter, remainingChars);
//                    } catch (Exception e) {
//                        appInsights.trackTrace(e.getMessage());
//                        continue;
//                    }
//                    break;
                case GLOSSARY_0:
                    try {
                        //不区分大小写
                        translateDataByGlossary0(entry.getValue(), request, counter, remainingChars, glossaryMap);
                    } catch (Exception e) {
                        appInsights.trackTrace(e.getMessage());
                        continue;
                    }
                    break;
                case GLOSSARY_1:
                    try {
                        //区分大小写
                        translateDataByGlossary1(entry.getValue(), request, counter, remainingChars, glossaryMap);
                    } catch (Exception e) {
                        appInsights.trackTrace(e.getMessage());
                        continue;
                    }
                    break;
                default:

                    break;
            }
        }
    }

    //区分大小写
    public void translateDataByGlossary1(List<RegisterTransactionRequest> registerTransactionRequests,
                                         ShopifyRequest request, CharacterCountUtils counter, int remainingChars, Map<String, Object> glossaryMap) {
        String target = request.getTarget();
        Map<String, Object> translation = new HashMap<>();
        //关键词
        Map<String, String> keyMap = new HashMap<>();
        //占位符
        Map<String, String> placeholderMap = new HashMap<>();
        //将glossaryMap中所有caseSensitive为1的数据存到一个Map集合里面
        for (Map.Entry<String, Object> entry : glossaryMap.entrySet()) {
            GlossaryDO glossaryDO = (GlossaryDO) entry.getValue();
            if (glossaryDO.getCaseSensitive() == 1) {
                keyMap.put(glossaryDO.getSourceText(), glossaryDO.getTargetText());
            }
        }
        //对caseSensitiveMap集合中的数据进行翻译
        for (RegisterTransactionRequest registerTransactionRequest : registerTransactionRequests) {
            String value = registerTransactionRequest.getValue();
            String translatableContentDigest = registerTransactionRequest.getTranslatableContentDigest();
            String key = registerTransactionRequest.getKey();
            String source = registerTransactionRequest.getLocale();
            String resourceId = registerTransactionRequest.getResourceId();
            String type = registerTransactionRequest.getTarget();
            translation.put("locale", target);
            translation.put("key", key);
            translation.put("translatableContentDigest", translatableContentDigest);
            //判断是否超限
            updateCharsWhenExceedLimit(counter, request.getShopName(), remainingChars, new TranslateRequest(0, null, request.getAccessToken(), source, target, null));
            counter.addChars(value.length());
            String targetText;
            //判断是否为HTML
            if (type.equals(HTML)) {
                try {
                    targetText = translateGlossaryHtmlText(new TranslateRequest(0, null, null, source, target, value), counter);
                } catch (Exception e) {
                    saveToShopify(value, translation, resourceId, request);
                    continue;
                }
                saveToShopify(targetText, translation, resourceId, request);
                continue;
            }
            //其他语言，对数据做处理再翻译
//            System.out.println("value: " + value);
//            String updateText = extractKeywords(value, placeholderMap, keyMap);
//            System.out.println("updateText: " + updateText);
//            String translatedText = getGoogleTranslateData(new TranslateRequest(0, null, null, source, target, updateText));
//            String finalText = restoreKeywords(translatedText, placeholderMap);
//            System.out.println("finalText: " + finalText);
//            saveToShopify(finalText, translation, resourceId, request);

        }
    }

    //不区分大小写
    private void translateDataByGlossary0(List<RegisterTransactionRequest> registerTransactionRequests, ShopifyRequest request, CharacterCountUtils counter, int remainingChars, Map<String, Object> glossaryMap) {
        //对包含词汇表的数据进行翻译
    }

    //处理JSON_TEXT类型的数据
    private void translateJsonText(List<RegisterTransactionRequest> registerTransactionRequests, ShopifyRequest request) {

        String target = request.getTarget();
        Map<String, Object> translation = new HashMap<>();
        for (RegisterTransactionRequest registerTransactionRequest : registerTransactionRequests) {
            String value = registerTransactionRequest.getValue();
            String translatableContentDigest = registerTransactionRequest.getTranslatableContentDigest();
            String key = registerTransactionRequest.getKey();
            String resourceId = registerTransactionRequest.getResourceId();
            translation.put("locale", target);
            translation.put("key", key);
            translation.put("translatableContentDigest", translatableContentDigest);
            //直接存放到shopify本地
            saveToShopify(value, translation, resourceId, request);
        }
    }

    //处理从数据库获取的数据
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
            updateCharsWhenExceedLimit(counter, request.getShopName(), remainingChars, new TranslateRequest(0, null, request.getAccessToken(), source, target, null));
            //从数据库中获取数据，如果不为空，存入shopify本地；如果为空翻译

            String targetCache = translateSingleLine(value, request.getTarget());
            String type = registerTransactionRequest.getTarget();
            counter.addChars(value.length());
            if (targetCache != null) {
                saveToShopify(targetCache, translation, resourceId, request);
                continue;
            }

            String targetText;
            try {
                targetText = translateTextService.getTargetTextByDigest(translatableContentDigest, target)[0];
                addData(target, value, targetText);
                saveToShopify(targetText, translation, resourceId, request);
                continue;
            } catch (Exception e) {
                //打印错误信息
                saveToShopify(value, translation, resourceId, request);
                appInsights.trackTrace(e.getMessage());
            }
            //数据库为空的逻辑
            //判断数据类型
            if (value.isEmpty()) {
                continue;
            }
            if ("handle".equals(key)
            ) {
                saveToShopify(value, translation, resourceId, request);
                continue;
            }
            if ("JSON".equals(type)
                    || "JSON_STRING".equals(type)) {
                //对于json和json_string的数据直接存原文
                //存放在json的集合里面
                saveToShopify(value, translation, resourceId, request);
                continue;
            }
            if ("HTML".equals(type)) {
                //存放在html的list集合里面
                try {
                    targetText = jsoupUtils.translateHtml(value, new TranslateRequest(0, null, null, source, target, value), counter, request.getTarget());
                } catch (Exception e) {
                    saveToShopify(value, translation, resourceId, request);
                    continue;
                }
                saveToShopify(targetText, translation, resourceId, request);
            }
            String targetString = getGoogleTranslateData(new TranslateRequest(0, null, null, source, target, value));
//                targetString = translateApiIntegration.microsoftTranslate(new TranslateRequest(0, null, null, source, target, value));
            addData(target, value, targetString);
            saveToShopify(targetString, translation, resourceId, request);
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
            updateCharsWhenExceedLimit(counter, request.getShopName(), remainingChars, new TranslateRequest(0, null, request.getAccessToken(), source, target, null));
            counter.addChars(value.length());
            String targetText;
            try {
                targetText = jsoupUtils.translateHtml(value, new TranslateRequest(0, null, null, source, target, value), counter, request.getTarget());
            } catch (Exception e) {
                saveToShopify(value, translation, resourceId, request);
                continue;
            }
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
            updateCharsWhenExceedLimit(counter, request.getShopName(), remainingChars, new TranslateRequest(0, null, request.getAccessToken(), source, target, null));
            //获取缓存数据
            String targetCache = translateSingleLine(value, request.getTarget());
            counter.addChars(value.length());
            if (targetCache != null) {
                saveToShopify(targetCache, translation, resourceId, request);
            } else {
                //根据chooseData来判断用什么翻译API
                try {
                    chooseTranslateApi(translation, registerTransactionRequest, request, chooseData);
                } catch (Exception e) {
                    System.out.println("翻译失败后的字符数： " + counter.getTotalChars());
                    translationCounterService.updateUsedCharsByShopName(new TranslationCounterRequest(0, request.getShopName(), 0, counter.getTotalChars(), 0, 0, 0));
                    saveToShopify(value, translation, resourceId, request);
                }
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
//                targetString = baiDuTranslate(new TranslateRequest(0, null, null, source, target, value));
                addData(target, value, targetString);
                saveToShopify(targetString, translation, resourceId, request);
                break;
        }
    }

    //将获得的TRANSLATION_RESOURCES数据进行判断 存储到不同集合， 对不同集合的数据进行特殊处理
    private void judgeAndStoreData(ArrayNode contentNode, String resourceId, Map<String, List<RegisterTransactionRequest>> judgeData,
                                   String resourceType, Map<String, TranslateTextDO> translatableContentMap, Map<String, Object> glossaryMap) {
        for (JsonNode contentItem : contentNode) {
            ObjectNode contentItemNode = (ObjectNode) contentItem;
            // 跳过 value 为空的项
            String value = contentItemNode.get("value").asText();

            if (value == null || value.isEmpty()) {
                continue;  // 跳过当前项
            }
            String locale = contentItemNode.get("locale").asText();
            String translatableContentDigest = contentItemNode.get("digest").asText();
            String key = contentItemNode.get("key").asText();
            String type = contentItemNode.get("type").asText();
            //如果translatableContentMap里面有该key则不翻译，没有则翻译
            if (translatableContentMap.containsKey(key)) {
//                System.out.println("key = " + value);
                continue;
            }


            //            //对从数据库中获取的数据单独处理
            if ("ONLINE_STORE_THEME".equals(resourceType) ||
                    "ONLINE_STORE_THEME_LOCALE_CONTENT".equals(resourceType)
                    || "SHOP_POLICY".equals(resourceType)
                    || "PACKING_SLIP_TEMPLATE".equals(resourceType)
                    || "EMAIL_TEMPLATE".equals(resourceType)) {
                //先将type存在target里面
                judgeData.get(DATABASE).add(new RegisterTransactionRequest(null, null, locale, key, value, translatableContentDigest, resourceId, type));
                continue;
            }

            //跳过 key 为 "handle" 的项
            if ("handle".equals(key)
            ) {
                continue;  // 跳过当前项
            }
            //对于json和json_string的数据直接存原文
            if ("JSON".equals(type)
                    || "JSON_STRING".equals(type)) {
                //存放在json的集合里面
                judgeData.get(JSON_TEXT).add(new RegisterTransactionRequest(null, null, locale, key, value, translatableContentDigest, resourceId, null));
                continue;
            }

            //对词汇表数据做判断
            //判断词汇表里面是否有数据
//            System.out.println("value = " + value);
            if (!glossaryMap.isEmpty()) {
                for (Map.Entry<String, Object> entry : glossaryMap.entrySet()) {
                    String glossaryKey = entry.getKey();
                    GlossaryDO glossaryDO = (GlossaryDO) entry.getValue();
//                    System.out.println("glossaryKey = " + glossaryKey);
                    //判断是否区分大小写
                    Integer caseSensitive = glossaryDO.getCaseSensitive();
                    if (caseSensitive.equals(1) && containsValue(value, glossaryKey)) {
                        //TODO: 还是要修改传参
                        judgeData.get(GLOSSARY_1).add(new RegisterTransactionRequest(null, null, locale, key, value, translatableContentDigest, resourceId, type));
                        break;
                    }
                    if (caseSensitive.equals(0) && containsValueIgnoreCase(value, glossaryKey)) {
                        judgeData.get(GLOSSARY_0).add(new RegisterTransactionRequest(null, null, locale, key, value, translatableContentDigest, resourceId, type));
                        break;
                    }
                }
//                System.out.println("judgeDate: " + judgeData.get(GLOSSARY).toString());
                continue;
            }

            //对value进行判断 plainText
            if ("HTML".equals(type)) {
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

    //将翻译后的数据放入内存中
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

        if (counter.getTotalChars() >= remainingChars) {
            translatesService.updateTranslateStatus(shopName, 3, translateRequest.getTarget(), translateRequest.getSource(), translateRequest.getAccessToken());
            translationCounterService.updateUsedCharsByShopName(new TranslationCounterRequest(0, shopName, 0, counter.getTotalChars(), 0, 0, 0));
            throw new ClientException("Character Limit Reached");
        }
    }

    //修改getTestQuery里面的testQuery，用获取后的的查询语句进行查询
    public JsonNode fetchNextPage(TranslateResourceDTO translateResource, ShopifyRequest request) {
        CloudServiceRequest cloudServiceRequest = TypeConversionUtils.shopifyToCloudServiceRequest(request);
        String query = new ShopifyRequestBody().getAfterQuery(translateResource);
        cloudServiceRequest.setBody(query);

        String infoByShopify = shopifyService.getShopifyData(cloudServiceRequest);
        if (infoByShopify == null) {
            throw new ClientException(SHOPIFY_CONNECT_ERROR.getErrMsg());
        }
        ObjectMapper objectMapper = new ObjectMapper();

        try {
            return objectMapper.readTree(infoByShopify);
        } catch (JsonProcessingException e) {
            throw new ClientException(SHOPIFY_RETURN_ERROR.getErrMsg());
        }
    }

    //递归处理下一页数据
    private void translateNextPage(ShopifyRequest request, CharacterCountUtils counter, TranslateResourceDTO
            translateResource, int remainingChars, Map<String, Object> glossaryMap) {
        JsonNode nextPageData = fetchNextPage(translateResource, request);
        // 重新开始翻译流程
        translateSingleLineTextFieldsRecursively(nextPageData, request, counter, remainingChars, translateResource.getResourceType(), glossaryMap);
        // 递归处理下一页数据
        handlePagination(nextPageData, request, counter, translateResource, remainingChars, glossaryMap);
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
        return translations;
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
            ShopifyRequestBody shopifyRequestBody = new ShopifyRequestBody();
            translateResource.setTarget(shopifyRequest.getTarget());
            String query = shopifyRequestBody.getFirstQuery(translateResource);
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

    //翻译词汇表的html文本
    public String translateGlossaryHtmlText(TranslateRequest request, CharacterCountUtils counter) {
        String html = "<div><p>1 Carat Moissanite 4-Prong Ring</p></div>\n<ul>\n<li>Color may be different according to different lights.</li>\n<li>Includes:</li>\n</ul><p style=\"padding-left: 40px;\">Moissanite jewelry over 0.3 carats includes a certificate of stone properties. Limited warranty included, please contact us for any issues related to your purchase.</p><p style=\"padding-left: 40px;\">A matching box.</p><ul>\n<li>Style: Modern</li>\n<li>Material: 925 sterling silver, platinum-plated, moissanite, zircon (accent stones)</li>\n<li>Care: Avoid wearing during exercise, as sweat will react with the jewelry to produce silver chloride and copper sulfide, which causes the jewelry to deteriorate and corrode over time.</li>\n<li>Imported</li>\n<li>Product measurements:</li>\n</ul><p style=\"padding-left: 40px;\">5: Circumference 2 in</p><p style=\"padding-left: 40px;\">6: Circumference 2.1 in</p><p style=\"padding-left: 40px;\">7: Circumference 2.2 in</p><p style=\"padding-left: 40px;\">8: Circumference 2.3 in</p><p style=\"padding-left: 40px;\">9: Circumference 2.4 in</p><p style=\"padding-left: 40px;\">Main stone: 1 carat</p><p style=\"padding-left: 40px;\">Weight: 0.1 oz (3 g)</p>";
        // 解析HTML文档
        Document doc = Jsoup.parse(html);

        // 提取需要翻译的文本
        Map<Element, String> elementTextMap = jsoupUtils.extractTextsToTranslate(doc);
        List<String> textsToTranslate = new ArrayList<>(elementTextMap.values());

        // 翻译文本
        List<String> translatedTexts = jsoupUtils.translateTexts(textsToTranslate, request, counter);

        // 替换原始文本为翻译后的文本
        jsoupUtils.replaceOriginalTextsWithTranslated(doc, elementTextMap, translatedTexts.iterator());
        return doc.toString();
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

    //插入语言状态
    @Async
    public void insertLanguageStatus(TranslateRequest request) {
        Integer status = translatesService.readStatus(request);
        if (status == null) {
            translatesService.insertShopTranslateInfo(request, 0);
        }
    }

    //插入翻译状态
    @Async
    public void insertTranslateStatus(TranslateRequest request) {
        Integer status = translatesService.readStatus(request);
        if (status == null) {
            Integer result = translatesService.insertShopTranslateInfo(request, 0);
        }
    }
}

