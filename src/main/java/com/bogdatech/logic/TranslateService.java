package com.bogdatech.logic;


import com.bogdatech.Service.*;
import com.bogdatech.context.TranslateContext;
import com.bogdatech.entity.*;
import com.bogdatech.exception.ClientException;
import com.bogdatech.integration.*;
import com.bogdatech.model.controller.request.*;
import com.bogdatech.requestBody.ShopifyRequestBody;
import com.bogdatech.utils.CharacterCountUtils;
import com.bogdatech.utils.JsoupUtils;
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

import java.text.NumberFormat;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.bogdatech.constants.MailChimpConstants.*;
import static com.bogdatech.constants.TranslateConstants.*;
import static com.bogdatech.entity.TranslateResourceDTO.ALL_RESOURCES;
import static com.bogdatech.entity.TranslateResourceDTO.DATABASE_RESOURCES;
import static com.bogdatech.enums.ErrorEnum.*;
import static com.bogdatech.logic.ShopifyService.getVariables;
import static com.bogdatech.utils.CalculateTokenUtils.calculateToken;
import static com.bogdatech.utils.CaseSensitiveUtils.*;

@Component
@EnableAsync
//@Transactional
public class TranslateService {
    private final TranslateApiIntegration translateApiIntegration;
    private final ShopifyHttpIntegration shopifyApiIntegration;
    private final ShopifyService shopifyService;
    private final TestingEnvironmentIntegration testingEnvironmentIntegration;
    private final ITranslatesService translatesService;
    private final ITranslationCounterService translationCounterService;
    private final ITranslateTextService translateTextService;
    private final IGlossaryService glossaryService;
    TelemetryClient appInsights = new TelemetryClient();
    private final AILanguagePackService aiLanguagePackService;
    private final ChatGptIntegration chatGptIntegration;
    private final JsoupUtils jsoupUtils;
    private final IAILanguagePacksService aiLanguagePacksService;
    private final IUsersService usersService;
    private final EmailIntegration emailIntegration;
    private final IEmailService emailService;

    @Autowired
    public TranslateService(
            TranslateApiIntegration translateApiIntegration,
            ShopifyHttpIntegration shopifyApiIntegration,
            ShopifyService shopifyService,
            TestingEnvironmentIntegration testingEnvironmentIntegration,
            ITranslatesService translatesService,
            ITranslationCounterService translationCounterService,
            ITranslateTextService translateTextService,
            IGlossaryService glossaryService,
            AILanguagePackService aiLanguagePackService,
            ChatGptIntegration chatGptIntegration,
            JsoupUtils jsoupUtils,
            IAILanguagePacksService aiLanguagePacksService,
            IUsersService usersService,
            EmailIntegration emailIntegration, IEmailService emailService) {
        this.translateApiIntegration = translateApiIntegration;
        this.shopifyApiIntegration = shopifyApiIntegration;
        this.shopifyService = shopifyService;
        this.testingEnvironmentIntegration = testingEnvironmentIntegration;
        this.translatesService = translatesService;
        this.translationCounterService = translationCounterService;
        this.translateTextService = translateTextService;
        this.glossaryService = glossaryService;
        this.aiLanguagePackService = aiLanguagePackService;
        this.chatGptIntegration = chatGptIntegration;
        this.jsoupUtils = jsoupUtils;
        this.aiLanguagePacksService = aiLanguagePacksService;
        this.usersService = usersService;
        this.emailIntegration = emailIntegration;
        this.emailService = emailService;
    }

    public static Map<String, Map<String, String>> SINGLE_LINE_TEXT = new HashMap<>();

    //判断是否可以终止翻译流程
    private ExecutorService executorService = Executors.newFixedThreadPool(50); // 使用线程池来处理多个用户任务
    private Map<String, Future<?>> userTasks = new HashMap<>(); // 存储每个用户的翻译任务
    private Map<String, AtomicBoolean> userStopFlags = new HashMap<>(); // 存储每个用户的停止标志
    private final AtomicBoolean emailSent = new AtomicBoolean(false); // 用于同步发送字符限制邮件
    // 使用 ConcurrentHashMap 存储每个用户的邮件发送状态
    private final ConcurrentHashMap<String, AtomicBoolean> userEmailStatus = new ConcurrentHashMap<>();

    // 启动翻译任务
    public void startTranslation(TranslateRequest request, int remainingChars, CharacterCountUtils counter, int usedChars) {
        // 创建并启动翻译任务
        Future<?> future = executorService.submit(() -> {
            LocalDateTime begin = LocalDateTime.now();
            try {
                translating(request, remainingChars, counter, usedChars);  // 执行翻译任务
            } catch (ClientException e) {
                if (e.getErrorMessage().equals(HAS_TRANSLATED)) {
                    translationCounterService.updateUsedCharsByShopName(new TranslationCounterRequest(0, request.getShopName(), 0, counter.getTotalChars(), 0, 0, 0));
                    translateFailEmail(request.getShopName(), e.getErrorMessage());
                    throw e;
                }
                translatesService.updateTranslateStatus(request.getShopName(), 3, request.getTarget(), request.getSource(), request.getAccessToken());
                translationCounterService.updateUsedCharsByShopName(new TranslationCounterRequest(0, request.getShopName(), 0, counter.getTotalChars(), 0, 0, 0));
                translateFailEmail(request.getShopName(), e.getErrorMessage());
                throw e;
            }
            //         更新数据库中的已使用字符数
            translationCounterService.updateUsedCharsByShopName(new TranslationCounterRequest(0, request.getShopName(), 0, counter.getTotalChars(), 0, 0, 0));
            // 将翻译状态改为“已翻译”//
            translatesService.updateTranslateStatus(request.getShopName(), 1, request.getTarget(), request.getSource(), request.getAccessToken());
            //翻译成功后发送翻译成功的邮件
            translateSuccessEmail(request, counter, begin, usedChars, remainingChars);
        });

        userTasks.put(request.getShopName(), future);  // 存储用户的任务
        userEmailStatus.put(request.getShopName(), new AtomicBoolean(false)); //重置用户发送的邮件
        userStopFlags.put(request.getShopName(), new AtomicBoolean(false));  // 初始化用户的停止标志
    }

    // 用户卸载停止指定用户的翻译任务
    public void stopTranslation(String shopName) {
        AtomicBoolean stopFlag = userStopFlags.get(shopName);
        if (stopFlag != null) {
            stopFlag.set(true);  // 设置停止标志，任务会在合适的地方检查并终止
            Future<?> future = userTasks.get(shopName);
            if (future != null && !future.isDone()) {
                future.cancel(true);  // 中断正在执行的任务
                appInsights.trackTrace("用户 " + shopName + " 的翻译任务已停止");
//                 将翻译状态改为“部分翻译” shopName, status=2
                translatesService.updateStatusByShopNameAnd2(shopName);
                translateFailEmail(shopName, TRANSLATING_STOPPED);
            }
        }
    }


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
        } catch (Exception e) {
//            throw new RuntimeException(e);
            appInsights.trackTrace("Failed to get Google Translate data: " + e.getMessage());
            return request.getContent();
        }
        return string;
    }

    //封装调用云服务器实现将数据存入shopify本地的方法
    public void saveToShopify(CloudInsertRequest cloudServiceRequest) {
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            String requestBody = objectMapper.writeValueAsString(cloudServiceRequest);
            testingEnvironmentIntegration.sendShopifyPost("translate/insertTranslatedText", requestBody);
        } catch (JsonProcessingException | ClientException e) {
            appInsights.trackTrace("Failed to save to Shopify: " + e.getMessage());
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
    public void translating(TranslateRequest request, int remainingChars, CharacterCountUtils counter, int usedChars) {

//        System.out.println("翻译中");
        ShopifyRequest shopifyRequest = TypeConversionUtils.convertTranslateRequestToShopifyRequest(request);
        CloudServiceRequest cloudServiceRequest = TypeConversionUtils.shopifyToCloudServiceRequest(shopifyRequest);

        //一个用户当前只能翻译一条语言，根据用户的status判断
        List<Integer> integers = translatesService.readStatusInTranslatesByShopName(request);
        for (Integer integer : integers) {
            if (integer == 2) {
                throw new ClientException(HAS_TRANSLATED);
            }
        }

        //判断是否有同义词
        Map<String, Object> glossaryMap = new HashMap<>();
        getGlossaryByShopName(shopifyRequest, glossaryMap);

        //获取目前所使用的AI语言包
        Integer packId = aiLanguagePacksService.getPackIdByShopName(request.getShopName());
        AILanguagePacksDO aiLanguagePacksDO = aiLanguagePacksService.getPromotByPackId(packId);

        // 如果没有超限，则开始翻译流程
        translatesService.updateTranslateStatus(request.getShopName(), 2, request.getTarget(), request.getSource(), request.getAccessToken());
        //TRANSLATION_RESOURCES
        for (TranslateResourceDTO translateResource : ALL_RESOURCES) {
            // 定期检查是否停止
            if (checkIsStopped(request.getShopName(), counter)) return;
            String completePrompt = aiLanguagePackService.getCompletePrompt(aiLanguagePacksDO, translateResource.getResourceType(), request.getTarget());
            aiLanguagePacksDO.setPromotWord(completePrompt);
            translateResource.setTarget(request.getTarget());
            String query = new ShopifyRequestBody().getFirstQuery(translateResource);
            cloudServiceRequest.setBody(query);
            String shopifyData;
            try {
                shopifyData = shopifyService.getShopifyData(cloudServiceRequest);
            } catch (Exception e) {
                // 如果出现异常，则跳过, 翻译其他的内容
                //更新当前字符数
                translationCounterService.updateUsedCharsByShopName(new TranslationCounterRequest(0, request.getShopName(), 0, counter.getTotalChars(), 0, 0, 0));
                continue;
            }
            TranslateContext translateContext = new TranslateContext(shopifyData, shopifyRequest, translateResource, counter, remainingChars, glossaryMap, aiLanguagePacksDO);
            translateJson(translateContext);
            // 定期检查是否停止
            if (checkIsStopped(request.getShopName(), counter)) return;
        }
    }

    private boolean checkIsStopped(String shopName, CharacterCountUtils counter) {
        if (userStopFlags.get(shopName).get()) {
//                更新数据库中的已使用字符数
            translationCounterService.updateUsedCharsByShopName(new TranslationCounterRequest(0, shopName, 0, counter.getTotalChars(), 0, 0, 0));
            // 将翻译状态为2改为“部分翻译”//
            translatesService.updateStatusByShopNameAnd2(shopName);
//            userStopFlags.get(shopName).set(false);
            return true;
        }
        return false;
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
    public Future<Void> translateJson(TranslateContext translateContext) {
//        System.out.println("现在翻译到： " + translateContext.getTranslateResource().getResourceType());
        if (translateContext.getShopifyData() == null) {
            // 返回默认值或空结果
            return null;
        }
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode rootNode;
        try {
            rootNode = objectMapper.readTree(translateContext.getShopifyData());
        } catch (JsonProcessingException e) {
            appInsights.trackTrace(e.getMessage());
            return null;
        }
        translateSingleLineTextFieldsRecursively(rootNode, translateContext);
        // 递归处理下一页数据
        handlePagination(rootNode, translateContext);
        return null;
    }

    // 递归处理下一页数据
    private void handlePagination(JsonNode translatedRootNode, TranslateContext translateContext) {
        // 获取translatableResources节点
        JsonNode translatableResourcesNode = translatedRootNode.path("translatableResources");
        // 获取pageInfo节点
        JsonNode pageInfoNode = translatableResourcesNode.path("pageInfo");

        if (translatableResourcesNode.hasNonNull("pageInfo")) {
            if (pageInfoNode.hasNonNull("hasNextPage") && pageInfoNode.get("hasNextPage").asBoolean()) {
                JsonNode endCursor = pageInfoNode.get("endCursor");
                translateContext.getTranslateResource().setAfter(endCursor.asText());
                translateNextPage(translateContext);
            }
        }
    }

    //递归遍历JSON树：使用 translateSingleLineTextFieldsRecursively 方法递归地遍历整个 JSON 树，并对 translatableContent 字段进行特别处理。
    private void translateSingleLineTextFieldsRecursively(JsonNode node, TranslateContext translateContext) {

        ShopifyRequest shopifyRequest = translateContext.getShopifyRequest();
        if (checkIsStopped(shopifyRequest.getShopName(), translateContext.getCharacterCountUtils())) return;
        //定义HashMap存放判断后的对应数据
        // 初始化 judgeData 用于分类存储数据
        Map<String, List<RegisterTransactionRequest>> judgeData = initializeJudgeData();
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
            if (nodeElement.isObject()) {
                processNodeElement(nodeElement, shopifyRequest, translateContext, judgeData);
            }
        }
        if (checkIsStopped(shopifyRequest.getShopName(), translateContext.getCharacterCountUtils())) return;
        //对judgeData数据进行翻译和存入shopify,除了html
        try {
            translateAndSaveData(judgeData, translateContext);
        } catch (Exception e) {
            appInsights.trackTrace("翻译过程中抛出的异常" + e.getMessage());
        }
        translationCounterService.updateUsedCharsByShopName(new TranslationCounterRequest(0, shopifyRequest.getShopName(), 0, translateContext.getCharacterCountUtils().getTotalChars(), 0, 0, 0));
    }

    // 处理单个节点元素，提取相关信息并分类存储
    private void processNodeElement(JsonNode nodeElement, ShopifyRequest shopifyRequest, TranslateContext translateContext,
                                    Map<String, List<RegisterTransactionRequest>> judgeData) {
        String resourceId = null;
        ArrayNode translatableContent = null;
        Map<String, TranslateTextDO> translatableContentMap = null;
        if (checkIsStopped(shopifyRequest.getShopName(), translateContext.getCharacterCountUtils())) return;
        // 遍历字段，提取 resourceId 和 translatableContent
        Iterator<Map.Entry<String, JsonNode>> fields = nodeElement.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            String fieldName = field.getKey();
            JsonNode fieldValue = field.getValue();

            // 根据字段名称进行处理
            switch (fieldName) {
                case "resourceId":
                    resourceId = fieldValue.asText();
                    // 提取翻译内容映射
                    translatableContentMap = extractTranslations(nodeElement, resourceId, shopifyRequest);
                    translatableContentMap = extractTranslatableContent(nodeElement, translatableContentMap);
                    break;
                case "translatableContent":
                    if (fieldValue.isArray()) {
                        translatableContent = (ArrayNode) fieldValue;
                    }
                    break;
            }

            // 如果 resourceId 和 translatableContent 都已提取，则存储并准备翻译
            if (resourceId != null && translatableContent != null) {
                judgeAndStoreData(translatableContent, resourceId, judgeData, translateContext.getTranslateResource().getResourceType(),
                        translatableContentMap, translateContext.getGlossaryMap());
            }
        }
    }

    // 初始化 judgeData，用于存储不同类型的数据
    private Map<String, List<RegisterTransactionRequest>> initializeJudgeData() {
        return new HashMap<>() {{
            put(PLAIN_TEXT, new ArrayList<>());
            put(HTML, new ArrayList<>());
            put(DATABASE, new ArrayList<>());
            put(JSON_TEXT, new ArrayList<>());
            put(GLOSSARY, new ArrayList<>());
            put(OPENAI, new ArrayList<>());
        }};
    }

    //对judgeData数据进行翻译和存入shopify,除了html
    private void translateAndSaveData(Map<String, List<RegisterTransactionRequest>> judgeData, TranslateContext translateContext) {
        for (Map.Entry<String, List<RegisterTransactionRequest>> entry : judgeData.entrySet()) {
            if (checkIsStopped(translateContext.getShopifyRequest().getShopName(), translateContext.getCharacterCountUtils()))
                return;
            switch (entry.getKey()) {
                case PLAIN_TEXT:
                    translateDataByAPI(entry.getValue(), translateContext);
                    break;
                case HTML:
                    translateHtml(entry.getValue(), translateContext);
                    break;
                case JSON_TEXT:
                    translateJsonText(entry.getValue(), translateContext);
                    break;
                case DATABASE:
                    //处理database数据
                    try {
                        translateDataByDatabase(entry.getValue(), translateContext);
                    } catch (Exception e) {
                        appInsights.trackTrace(e.getMessage());
                        continue;
                    }
                    break;
                case GLOSSARY:
                    try {
                        //区分大小写
                        translateDataByGlossary(entry.getValue(), translateContext);
                    } catch (Exception e) {
                        appInsights.trackTrace(e.getMessage());
                        continue;
                    }
                    break;
                case OPENAI:
                    try {
                        translateDataByOPENAI(entry.getValue(), translateContext);
                    } catch (Exception e) {
                        appInsights.trackTrace(e.getMessage());
                        continue;
                    }
                    break;
                default:
                    appInsights.trackTrace("未知的翻译文本： " + entry.getValue());
//                    System.out.println("未知的翻译文本： " + entry.getValue());
                    break;
            }
        }
    }

    private void translateDataByOPENAI(List<RegisterTransactionRequest> registerTransactionRequests, TranslateContext translateContext) {
        ShopifyRequest request = translateContext.getShopifyRequest();
        CharacterCountUtils counter = translateContext.getCharacterCountUtils();
        AILanguagePacksDO aiLanguagePacksDO = translateContext.getAiLanguagePacksDO();
        //判断是否停止翻译
        if (checkIsStopped(request.getShopName(), counter)) return;

        int remainingChars = translateContext.getRemainingChars();
        String target = request.getTarget();

        for (RegisterTransactionRequest registerTransactionRequest : registerTransactionRequests) {
            //判断是否停止翻译
            if (checkIsStopped(request.getShopName(), counter)) return;
            String value = registerTransactionRequest.getValue();
            String translatableContentDigest = registerTransactionRequest.getTranslatableContentDigest();
            String key = registerTransactionRequest.getKey();
            String source = registerTransactionRequest.getLocale();
            String resourceId = registerTransactionRequest.getResourceId();

            Map<String, Object> translation = createTranslationMap(target, key, translatableContentDigest);

            // 判断是否会超限制
            updateCharsWhenExceedLimit(counter, request.getShopName(), remainingChars, new TranslateRequest(0, null, request.getAccessToken(), source, target, null));

            // 从缓存中获取翻译结果
            String targetCache = translateSingleLine(value, target);
            if (targetCache != null) {
                counter.addChars(calculateToken(value, 1));
                saveToShopify(targetCache, translation, resourceId, request);
                continue;
            }

            if (value.isEmpty()) {
                continue;
            }

            // 处理 "handle", "JSON", "HTML" 数据
            if (handleSpecialCases(value, translation, resourceId, request, registerTransactionRequest, counter, translateContext)) {
                continue;
            }

            // TODO: 判断用AI和谷歌翻译
            translateByGoogleOrAI(request, counter, aiLanguagePacksDO, registerTransactionRequest, translation);
//            String translatedText = getTranslatedText(value, source, target, translateContext, counter);
//            if (translatedText != null) {
//                saveToShopify(translatedText, translation, resourceId, request);
//            } else {
//                saveToShopify(value, translation, resourceId, request);
//            }
            if (checkIsStopped(request.getShopName(), counter)) return;
        }
    }

    private Map<String, Object> createTranslationMap(String target, String key, String translatableContentDigest) {
        Map<String, Object> translation = new HashMap<>();
        translation.put("locale", target);
        translation.put("key", key);
        translation.put("translatableContentDigest", translatableContentDigest);
        return translation;
    }

    private boolean handleSpecialCases(String value, Map<String, Object> translation, String resourceId, ShopifyRequest request,
                                       RegisterTransactionRequest registerTransactionRequest, CharacterCountUtils counter, TranslateContext translateContext) {
        String key = registerTransactionRequest.getKey();
        String type = registerTransactionRequest.getTarget();

        // Handle specific cases
        if ("handle".equals(key) || "JSON".equals(type) || "JSON_STRING".equals(type)) {
            saveToShopify(value, translation, resourceId, request);
            return true;
        }

        if ("HTML".equals(type)) {
            try {
                String targetText = jsoupUtils.translateHtml(value, new TranslateRequest(0, null, null, registerTransactionRequest.getLocale(), request.getTarget(), value), counter, translateContext.getAiLanguagePacksDO());
                saveToShopify(targetText, translation, resourceId, request);
                return true;
            } catch (Exception e) {
                saveToShopify(value, translation, resourceId, request);
                return true;
            }
        }
        return false;
    }

    private String getTranslatedText(String value, String source, String target, TranslateContext translateContext, CharacterCountUtils counter) {
        try {
            String translatedText;
            if (value.length() > 40) {
                counter.addChars(calculateToken(translateContext.getAiLanguagePacksDO().getPromotWord() + value, translateContext.getAiLanguagePacksDO().getDeductionRate()));
                translatedText = chatGptIntegration.chatWithGpt(translateContext.getAiLanguagePacksDO().getPromotWord() + value);
                counter.addChars(calculateToken(translatedText, translateContext.getAiLanguagePacksDO().getDeductionRate()));
            } else {
                counter.addChars(calculateToken(value, 1));
                translatedText = getGoogleTranslateData(new TranslateRequest(0, null, null, source, target, value));
            }
            return translatedText;
        } catch (Exception e) {
            //如果AI翻译失败再用google翻译
            return getGoogleTranslateData(new TranslateRequest(0, null, null, source, target, value));
        }
    }

    //对词汇表数据进行处理
    public void translateDataByGlossary(List<RegisterTransactionRequest> registerTransactionRequests,
                                        TranslateContext translateContext) {
        ShopifyRequest request = translateContext.getShopifyRequest();
        CharacterCountUtils counter = translateContext.getCharacterCountUtils();

        //判断是否停止翻译
        if (checkIsStopped(request.getShopName(), counter)) return;
        int remainingChars = translateContext.getRemainingChars();
        Map<String, Object> glossaryMap = translateContext.getGlossaryMap();
        String target = request.getTarget();
        Map<String, Object> translation = new HashMap<>();
        //关键词
        Map<String, String> keyMap1 = new HashMap<>();
        Map<String, String> keyMap0 = new HashMap<>();
        //占位符
        Map<String, String> placeholderMap = new HashMap<>();
        //将glossaryMap中所有caseSensitive为1的数据存到一个Map集合里面
        for (Map.Entry<String, Object> entry : glossaryMap.entrySet()) {
            GlossaryDO glossaryDO = (GlossaryDO) entry.getValue();
            if (glossaryDO.getCaseSensitive() == 1) {
                keyMap1.put(glossaryDO.getSourceText(), glossaryDO.getTargetText());
                continue;
            }
            if (glossaryDO.getCaseSensitive() == 0) {
                keyMap0.put(glossaryDO.getSourceText(), glossaryDO.getTargetText());
            }
        }
        //对caseSensitiveMap集合中的数据进行翻译
        for (RegisterTransactionRequest registerTransactionRequest : registerTransactionRequests) {
            //判断是否停止翻译
            if (checkIsStopped(request.getShopName(), counter)) return;
            String value = registerTransactionRequest.getValue();
            String source = registerTransactionRequest.getLocale();
            String resourceId = registerTransactionRequest.getResourceId();
            translation.put("locale", target);
            translation.put("key", registerTransactionRequest.getKey());
            translation.put("translatableContentDigest", registerTransactionRequest.getTranslatableContentDigest());
            //判断是否超限
            updateCharsWhenExceedLimit(counter, request.getShopName(), remainingChars, new TranslateRequest(0, null, request.getAccessToken(), source, target, null));

            String targetText;
            TranslateRequest translateRequest = new TranslateRequest(0, null, request.getAccessToken(), source, target, value);
            //判断是否为HTML
            if (registerTransactionRequest.getTarget().equals(HTML)) {
                try {
                    targetText = translateGlossaryHtmlText(translateRequest, counter, keyMap1, keyMap0, translateContext.getAiLanguagePacksDO());
                } catch (Exception e) {
                    saveToShopify(value, translation, resourceId, request);
                    continue;
                }
                saveToShopify(targetText, translation, resourceId, request);
                continue;
            }

            //其他数据类型，对数据做处理再翻译
            counter.addChars(calculateToken(value, 1));
            String updateText = extractKeywords(value, placeholderMap, keyMap1, keyMap0);
            translateRequest.setContent(updateText);
            String translatedText = getGoogleTranslateData(translateRequest);
            String finalText = restoreKeywords(translatedText, placeholderMap);
            saveToShopify(finalText, translation, resourceId, request);
            if (checkIsStopped(request.getShopName(), counter)) return;
        }
    }


    //处理JSON_TEXT类型的数据
    private void translateJsonText(List<RegisterTransactionRequest> registerTransactionRequests, TranslateContext translateContext) {

        ShopifyRequest request = translateContext.getShopifyRequest();
        CharacterCountUtils counter = translateContext.getCharacterCountUtils();
        //判断是否停止翻译
        if (checkIsStopped(request.getShopName(), counter)) return;
        String target = request.getTarget();
        Map<String, Object> translation = new HashMap<>();
        for (RegisterTransactionRequest registerTransactionRequest : registerTransactionRequests) {
            if (checkIsStopped(request.getShopName(), counter)) return;
            //判断是否停止翻译
            if (checkIsStopped(request.getShopName(), counter)) return;
            translation.put("locale", target);
            translation.put("key", registerTransactionRequest.getKey());
            translation.put("translatableContentDigest", registerTransactionRequest.getTranslatableContentDigest());
            //直接存放到shopify本地
            saveToShopify(registerTransactionRequest.getValue(), translation, registerTransactionRequest.getResourceId(), request);
            if (checkIsStopped(request.getShopName(), counter)) return;
        }
    }

    //处理从数据库获取的数据
    private void translateDataByDatabase(List<RegisterTransactionRequest> registerTransactionRequests, TranslateContext translateContext) {
        ShopifyRequest request = translateContext.getShopifyRequest();
        CharacterCountUtils counter = translateContext.getCharacterCountUtils();
        AILanguagePacksDO aiLanguagePacksDO = translateContext.getAiLanguagePacksDO();
        //判断是否停止翻译
        if (checkIsStopped(request.getShopName(), counter)) return;

        int remainingChars = translateContext.getRemainingChars();
        String target = request.getTarget();
        Map<String, Object> translation = new HashMap<>();
        for (RegisterTransactionRequest registerTransactionRequest : registerTransactionRequests) {
            //判断是否停止翻译
            if (checkIsStopped(request.getShopName(), counter)) return;

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

            if (targetCache != null) {
                counter.addChars(calculateToken(value, 1));
                saveToShopify(targetCache, translation, resourceId, request);
                continue;
            }

            String targetText;
            try {
                targetText = translateTextService.getTargetTextByDigest(translatableContentDigest, target)[0];
                if (targetText != null) {
                    counter.addChars(calculateToken(value, 1));
                    addData(target, value, targetText);
                    saveToShopify(targetText, translation, resourceId, request);
                    continue;
                }
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
                // 解析HTML文档
                Document doc = Jsoup.parse(value);
                try {
                    TranslateRequest translateRequest = new TranslateRequest(0, null, request.getAccessToken(), source, target, value);
                    // 提取需要翻译的文本
                    Map<Element, List<String>> elementTextMap = jsoupUtils.extractTextsToTranslate(doc);
                    // 翻译文本
                    Map<Element, List<String>> translatedTextMap = jsoupUtils.translateTexts(elementTextMap, translateRequest, counter, aiLanguagePacksDO);
                    // 替换原始文本为翻译后的文本
                    jsoupUtils.replaceOriginalTextsWithTranslated(doc, translatedTextMap);
                } catch (Exception e) {
                    saveToShopify(doc.toString(), translation, resourceId, request);
                    continue;
                }
                saveToShopify(doc.toString(), translation, resourceId, request);
                continue;
            }

            counter.addChars(calculateToken(value, 1));

            //TODO: 改为判断语言代码方法
            translateByGoogleOrAI(request, counter, aiLanguagePacksDO, registerTransactionRequest, translation);
//                        String targetString = getGoogleTranslateData(new TranslateRequest(0, null, null, source, target, value));
//            addData(target, value, targetString);
//            saveToShopify(targetString, translation, resourceId, request);
            if (checkIsStopped(request.getShopName(), counter)) return;
        }

    }

    //对html数据处理
    private void translateHtml(List<RegisterTransactionRequest> registerTransactionRequests, TranslateContext translateContext) {
        ShopifyRequest request = translateContext.getShopifyRequest();
        CharacterCountUtils counter = translateContext.getCharacterCountUtils();
        AILanguagePacksDO aiLanguagePacksDO = translateContext.getAiLanguagePacksDO();
        //判断是否停止翻译
        if (checkIsStopped(request.getShopName(), counter)) return;

        int remainingChars = translateContext.getRemainingChars();
        String target = request.getTarget();
        Map<String, Object> translation = new HashMap<>();
        for (RegisterTransactionRequest registerTransactionRequest : registerTransactionRequests) {
            //判断是否停止翻译
            if (checkIsStopped(request.getShopName(), counter)) return;
            String value = registerTransactionRequest.getValue();
            String source = registerTransactionRequest.getLocale();
            String resourceId = registerTransactionRequest.getResourceId();
            translation.put("locale", target);
            translation.put("key", registerTransactionRequest.getKey());
            translation.put("translatableContentDigest", registerTransactionRequest.getTranslatableContentDigest());
            //判断是否超限
            updateCharsWhenExceedLimit(counter, request.getShopName(), remainingChars, new TranslateRequest(0, null, request.getAccessToken(), source, target, null));

            //存放在html的list集合里面
            // 解析HTML文档
            Document doc = Jsoup.parse(value);
            try {
                TranslateRequest translateRequest = new TranslateRequest(0, null, request.getAccessToken(), source, target, value);
                // 提取需要翻译的文本
                Map<Element, List<String>> elementTextMap = jsoupUtils.extractTextsToTranslate(doc);
                // 翻译文本
                Map<Element, List<String>> translatedTextMap = jsoupUtils.translateTexts(elementTextMap, translateRequest, counter, aiLanguagePacksDO);
                // 替换原始文本为翻译后的文本
                jsoupUtils.replaceOriginalTextsWithTranslated(doc, translatedTextMap);
            } catch (Exception e) {
                saveToShopify(doc.toString(), translation, resourceId, request);
                continue;
            }
            saveToShopify(doc.toString(), translation, resourceId, request);
            if (checkIsStopped(request.getShopName(), counter)) return;
        }
    }

    //对不同的数据使用不同的翻译api
    private void translateDataByAPI(List<RegisterTransactionRequest> registerTransactionRequests,
                                    TranslateContext translateContext) {
        ShopifyRequest request = translateContext.getShopifyRequest();
        CharacterCountUtils counter = translateContext.getCharacterCountUtils();
        AILanguagePacksDO aiLanguagePacksDO = translateContext.getAiLanguagePacksDO();

        int remainingChars = translateContext.getRemainingChars();
        String target = request.getTarget();
        if (checkIsStopped(request.getShopName(), counter)) return;
        for (RegisterTransactionRequest registerTransactionRequest : registerTransactionRequests) {
            if (checkIsStopped(request.getShopName(), counter)) return;
            String value = registerTransactionRequest.getValue();
            String resourceId = registerTransactionRequest.getResourceId();

            Map<String, Object> translation = createTranslationMap(target, registerTransactionRequest);
            //判断是否超限
            updateCharsWhenExceedLimit(counter, request.getShopName(), remainingChars, new TranslateRequest(0, null, request.getAccessToken(), registerTransactionRequest.getLocale(), target, null));
            //获取缓存数据
            String targetCache = translateSingleLine(value, request.getTarget());
            if (targetCache != null) {
                counter.addChars(calculateToken(targetCache, 1));
                saveToShopify(targetCache, translation, resourceId, request);
                continue;
            }

//            首选谷歌翻译，翻译不了用AI翻译
            try {
                //TODO: 修改为判断语言代码的方法
//                String targetString = getGoogleTranslateData(new TranslateRequest(0, null, request.getAccessToken(), registerTransactionRequest.getLocale(), request.getTarget(), value));
                translateByGoogleOrAI(request, counter, aiLanguagePacksDO, registerTransactionRequest, translation);
            } catch (Exception e) {
                appInsights.trackTrace("翻译失败后的字符数： " + counter.getTotalChars());
                translationCounterService.updateUsedCharsByShopName(new TranslationCounterRequest(0, request.getShopName(), 0, counter.getTotalChars(), 0, 0, 0));
                saveToShopify(value, translation, resourceId, request);
            }
            if (checkIsStopped(request.getShopName(), counter)) return;
        }
    }


    //首选谷歌翻译，翻译不了用AI翻译
    public void translateByGoogleOrAI(ShopifyRequest request, CharacterCountUtils counter, AILanguagePacksDO aiLanguagePacksDO, RegisterTransactionRequest registerTransactionRequest, Map<String, Object> translation) {
        String value = registerTransactionRequest.getValue();
        List<String> strings = jsoupUtils.googleTranslateJudgeCode(new TranslateRequest(0, null, request.getAccessToken(), registerTransactionRequest.getLocale(), request.getTarget(), value), aiLanguagePacksDO);
        String targetString = strings.get(0);
        if (targetString.isEmpty()){
            saveToShopify(value, translation, registerTransactionRequest.getResourceId(), request);
            return;
        }
        String flag = strings.get(1);
        if ("0".equals(flag)) {
            counter.addChars(calculateToken(aiLanguagePacksDO.getPromotWord() + value, aiLanguagePacksDO.getDeductionRate()));
            counter.addChars(calculateToken(targetString, aiLanguagePacksDO.getDeductionRate()));
            addData(request.getTarget(), value, targetString);
            saveToShopify(targetString, translation, registerTransactionRequest.getResourceId(), request);
            return;
        }
        counter.addChars(calculateToken(value, 1));
        addData(request.getTarget(), value, targetString);
        saveToShopify(targetString, translation, registerTransactionRequest.getResourceId(), request);
    }

    //创建存储翻译项的Map
    private Map<String, Object> createTranslationMap(String target, RegisterTransactionRequest registerTransactionRequest) {
        Map<String, Object> translation = new HashMap<>();
        translation.put("locale", target);
        translation.put("key", registerTransactionRequest.getKey());
        translation.put("translatableContentDigest", registerTransactionRequest.getTranslatableContentDigest());
        return translation;
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
                continue;
            }

            //对从数据库中获取的数据单独处理
            if (isDatabaseResourceType(resourceType)) {
                //先将type存在target里面
                judgeData.get(DATABASE).add(new RegisterTransactionRequest(null, null, locale, key, value, translatableContentDigest, resourceId, type));
                continue;
            }

            //对product和blog的type用AI翻译
            if (isAiTranslateResourceType(resourceType)) {
                judgeData.get(OPENAI).add(new RegisterTransactionRequest(null, null, locale, key, value, translatableContentDigest, resourceId, type));
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
            if (!glossaryMap.isEmpty()) {
                boolean success = false;
                for (Map.Entry<String, Object> entry : glossaryMap.entrySet()) {
                    String glossaryKey = entry.getKey();
                    if (containsValue(value, glossaryKey) || containsValueIgnoreCase(value, glossaryKey)) {
                        judgeData.get(GLOSSARY).add(new RegisterTransactionRequest(null, null, locale, key, value, translatableContentDigest, resourceId, type));
                        success = true;
                        break;
                    }
                }
                if (success) {
                    continue;
                }
            }

            //对value进行判断 plainText
            if ("HTML".equals(type)) {
                //存放在html的list集合里面
                judgeData.get(HTML).add(new RegisterTransactionRequest(null, null, locale, key, value, translatableContentDigest, resourceId, null));
            } else {
                //存放在plainText的list集合里面
                judgeData.get(PLAIN_TEXT).add(new RegisterTransactionRequest(null, null, locale, key, value, translatableContentDigest, resourceId, null));
            }


        }

    }

    // 判断是否为数据库资源类型
    private boolean isDatabaseResourceType(String resourceType) {
        return ONLINE_STORE_THEME.equals(resourceType) ||
                ONLINE_STORE_THEME_LOCALE_CONTENT.equals(resourceType) ||
                SHOP_POLICY.equals(resourceType) ||
                PACKING_SLIP_TEMPLATE.equals(resourceType) ||
                EMAIL_TEMPLATE.equals(resourceType) ||
                LINK.equals(resourceType) ||
                MENU.equals(resourceType);
    }

    // 判断是否需要 AI 翻译的资源类型
    private boolean isAiTranslateResourceType(String resourceType) {
        return PRODUCT.equals(resourceType) ||
                PRODUCT_OPTION.equals(resourceType) ||
                PRODUCT_OPTION_VALUE.equals(resourceType) ||
                BLOG.equals(resourceType) ||
                ARTICLE.equals(resourceType);
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
            //发送报错邮件
            AtomicBoolean emailSent = userEmailStatus.computeIfAbsent(shopName, k -> new AtomicBoolean(false));
            if (emailSent.compareAndSet(false, true)) {
                translateFailEmail(shopName, CHARACTER_LIMIT);
            }
            throw new ClientException(CHARACTER_LIMIT);
        }
    }

    //修改getTestQuery里面的testQuery，用获取后的的查询语句进行查询
    public JsonNode fetchNextPage(TranslateResourceDTO translateResource, ShopifyRequest request) {
        CloudServiceRequest cloudServiceRequest = TypeConversionUtils.shopifyToCloudServiceRequest(request);
        String query = new ShopifyRequestBody().getAfterQuery(translateResource);
        cloudServiceRequest.setBody(query);

        String infoByShopify = shopifyService.getShopifyData(cloudServiceRequest);
        ObjectMapper objectMapper = new ObjectMapper();

        try {
            return objectMapper.readTree(infoByShopify);
        } catch (JsonProcessingException e) {
            throw new ClientException(SHOPIFY_RETURN_ERROR.getErrMsg());
        }
    }

    //递归处理下一页数据
    private void translateNextPage(TranslateContext translateContext) {
        JsonNode nextPageData = null;
        try {
            nextPageData = fetchNextPage(translateContext.getTranslateResource(), translateContext.getShopifyRequest());
        } catch (Exception e) {
            return;
        }
        // 重新开始翻译流程
        translateSingleLineTextFieldsRecursively(nextPageData, translateContext);
        // 递归处理下一页数据
        handlePagination(nextPageData, translateContext);
    }

    // 将翻译后的数据存储到数据库中
//    @Async
    public void saveTranslatedData(String objectData, ShopifyRequest request, TranslateResourceDTO translateResourceDTO) {
        System.out.println("现在存储到： " + translateResourceDTO.getResourceType());
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode rootNode;
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
//        List<TranslateTextDO> depositContents = new ArrayList<>();
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
                //当用户修改数据后，outdated的状态为true，将该数据放入要翻译的集合中
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
    public String translateGlossaryHtmlText(TranslateRequest request, CharacterCountUtils counter, Map<String, String> keyMap, Map<String, String> keyMap0, AILanguagePacksDO aiLanguagePacksDO) {
        String html = request.getContent();
        // 解析HTML文档
        Document doc = Jsoup.parse(html);

        // 提取需要翻译的文本
        Map<Element, List<String>> elementTextMap = jsoupUtils.extractTextsToTranslate(doc);
        // 翻译文本
        Map<Element, List<String>> translatedTextMap = jsoupUtils.translateGlossaryTexts(elementTextMap, request, counter, keyMap, keyMap0, aiLanguagePacksDO);
        // 替换原始文本为翻译后的文本
        jsoupUtils.replaceOriginalTextsWithTranslated(doc, translatedTextMap);
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

    //将缓存的数据存到数据库中
    public void saveToTranslates() {
        //添加数据
        // 遍历外层的 Map
        List<TranslateTextDO> list = new ArrayList<>();
        SINGLE_LINE_TEXT.forEach((outerKey, innerMap) -> {
            // 使用流来遍历内部的 Map
//            System.out.println("outerKey: " + outerKey);
            innerMap.forEach((innerKey, value) -> {
//                appInsights.trackTrace("Key: " + outerKey + ", Inner Key: " + innerKey + ", Value: " + value);
//                translateTextService.insertTranslateText(new TranslateTextDO(null,null, null, null, null, innerKey, value, null, outerKey));
                list.add(new TranslateTextDO(null, null, null, null, null, innerKey, value, null, outerKey));
            });
        });
        if (!list.isEmpty()) {
            translateTextService.getExistTranslateTextList(list);
        }
    }

    //翻译成功后发送邮件
    public void translateSuccessEmail(TranslateRequest request, CharacterCountUtils counter, LocalDateTime begin, int beginChars, Integer remainingChars) {
        String shopName = request.getShopName();
        //通过shopName获取用户信息 需要 {{user}} {{language}} {{credit_count}} {{time}} {{remaining_credits}}
        UsersDO usersDO = usersService.getUserByName(shopName);
        Map<String, String> templateData = new HashMap<>();
        templateData.put("user", usersDO.getFirstName());
        templateData.put("language", request.getTarget());

        //获取更新前后的时间
        LocalDateTime end = LocalDateTime.now();

        Duration duration = Duration.between(begin, end);
        long costTime = duration.toMinutes();
        templateData.put("time", costTime + " minutes");

        //共消耗的字符数
        NumberFormat formatter = NumberFormat.getNumberInstance(Locale.US);
        int endChars = counter.getTotalChars();
        int costChars = endChars - beginChars;
        String formattedNumber = formatter.format(costChars);
        templateData.put("credit_count", formattedNumber);

        //还剩下的字符数
        int remaining = remainingChars - endChars;
        if (remaining < 0) {
            templateData.put("remaining_credits", "0");

        } else {
            String formattedNumber2 = formatter.format(remaining);
            templateData.put("remaining_credits", formattedNumber2);
        }

        //由腾讯发送邮件
        Boolean b = emailIntegration.sendEmailByTencent(new TencentSendEmailRequest(133301L, templateData, SUCCESSFUL_TRANSLATION_SUBJECT, TENCENT_FROM_EMAIL, usersDO.getEmail()));
        //存入数据库中
        emailService.saveEmail(new EmailDO(0, shopName, TENCENT_FROM_EMAIL, usersDO.getEmail(), SUCCESSFUL_TRANSLATION_SUBJECT, b ? 1 : 0));

    }

    //翻译失败后发送邮件
    public void translateFailEmail(String shopName, String errorReason) {
        UsersDO usersDO = usersService.getUserByName(shopName);
        Map<String, String> templateData = new HashMap<>();
        templateData.put("user", usersDO.getFirstName());

        //错误原因
        templateData.put("reason", errorReason);

        //由腾讯发送邮件
        Boolean b = emailIntegration.sendEmailByTencent(new TencentSendEmailRequest(133321L, templateData, TRANSLATION_FAILED_SUBJECT, TENCENT_FROM_EMAIL, usersDO.getEmail()));
        //存入数据库中
        emailService.saveEmail(new EmailDO(0, shopName, TENCENT_FROM_EMAIL, usersDO.getEmail(), TRANSLATION_FAILED_SUBJECT, b ? 1 : 0));
    }


    //插入翻译状态
//    @Async
//    public void insertTranslateStatus(TranslateRequest request) {
//        Integer status = translatesService.readStatus(request);
//        if (status == null) {
//            translatesService.insertShopTranslateInfo(request, 0);
//        }
//    }
}

