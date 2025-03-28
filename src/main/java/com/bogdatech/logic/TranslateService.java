package com.bogdatech.logic;


import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.bogdatech.Service.*;
import com.bogdatech.context.TranslateContext;
import com.bogdatech.entity.*;
import com.bogdatech.exception.ClientException;
import com.bogdatech.integration.EmailIntegration;
import com.bogdatech.integration.ShopifyHttpIntegration;
import com.bogdatech.integration.TestingEnvironmentIntegration;
import com.bogdatech.integration.TranslateApiIntegration;
import com.bogdatech.model.controller.request.*;
import com.bogdatech.model.controller.response.TypeSplitResponse;
import com.bogdatech.requestBody.ShopifyRequestBody;
import com.bogdatech.utils.CharacterCountUtils;
import com.bogdatech.utils.JsoupUtils;
import com.bogdatech.utils.TypeConversionUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
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
import org.springframework.transaction.CannotCreateTransactionException;

import java.text.NumberFormat;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.bogdatech.constants.MailChimpConstants.*;
import static com.bogdatech.constants.TranslateConstants.*;
import static com.bogdatech.entity.TranslateResourceDTO.*;
import static com.bogdatech.enums.ErrorEnum.SHOPIFY_RETURN_ERROR;
import static com.bogdatech.enums.ErrorEnum.TRANSLATE_ERROR;
import static com.bogdatech.integration.TranslateApiIntegration.getGoogleTranslationWithRetry;
import static com.bogdatech.logic.ShopifyService.getVariables;
import static com.bogdatech.utils.CalculateTokenUtils.googleCalculateToken;
import static com.bogdatech.utils.CaseSensitiveUtils.*;
import static com.bogdatech.utils.JsoupUtils.isHtml;
import static com.bogdatech.utils.JsoupUtils.translateAndCount;
import static com.bogdatech.utils.LiquidHtmlTranslatorUtils.*;
import static com.bogdatech.utils.RegularJudgmentUtils.isValidString;
import static com.bogdatech.utils.ResourceTypeUtils.splitByType;
import static com.bogdatech.utils.TypeConversionUtils.convertTranslateRequestToShopifyRequest;

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
    private final JsoupUtils jsoupUtils;
    private final IAILanguagePacksService aiLanguagePacksService;
    private final IUsersService usersService;
    private final EmailIntegration emailIntegration;
    private final IEmailService emailService;
    private final IVocabularyService vocabularyService;
    private final IUserTypeTokenService userTypeTokenService;

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
            JsoupUtils jsoupUtils,
            IAILanguagePacksService aiLanguagePacksService,
            IUsersService usersService,
            EmailIntegration emailIntegration,
            IEmailService emailService, IVocabularyService vocabularyService, IUserTypeTokenService userTypeTokenService) {
        this.translateApiIntegration = translateApiIntegration;
        this.shopifyApiIntegration = shopifyApiIntegration;
        this.shopifyService = shopifyService;
        this.testingEnvironmentIntegration = testingEnvironmentIntegration;
        this.translatesService = translatesService;
        this.translationCounterService = translationCounterService;
        this.translateTextService = translateTextService;
        this.glossaryService = glossaryService;
        this.aiLanguagePackService = aiLanguagePackService;
        this.jsoupUtils = jsoupUtils;
        this.aiLanguagePacksService = aiLanguagePacksService;
        this.usersService = usersService;
        this.emailIntegration = emailIntegration;
        this.emailService = emailService;
        this.vocabularyService = vocabularyService;
        this.userTypeTokenService = userTypeTokenService;
    }

    public static Map<String, Map<String, String>> SINGLE_LINE_TEXT = new HashMap<>();
    private static final ObjectMapper objectMapper = new ObjectMapper();
    //判断是否可以终止翻译流程
    public static Map<String, Future<?>> userTasks = new HashMap<>(); // 存储每个用户的翻译任务
    public static Map<String, AtomicBoolean> userStopFlags = new HashMap<>(); // 存储每个用户的停止标志
    private final AtomicBoolean emailSent = new AtomicBoolean(false); // 用于同步发送字符限制邮件
    // 使用 ConcurrentHashMap 存储每个用户的邮件发送状态
    public static ConcurrentHashMap<String, AtomicBoolean> userEmailStatus = new ConcurrentHashMap<>();
    public static ExecutorService executorService = new ThreadPoolExecutor(
            6,   // 核心线程数（比 vCPU 多一点）
            12,  // 最大线程数（vCPU * 4）
            60L, TimeUnit.SECONDS, // 空闲线程存活时间
            new LinkedBlockingQueue<>(50), // 任务队列（避免内存过载）
            new ThreadPoolExecutor.CallerRunsPolicy() // 拒绝策略
    );

    // 启动翻译任务
    public void startTranslation(TranslateRequest request, int remainingChars, CharacterCountUtils counter, int usedChars) {
        // 创建并启动翻译任务
        String shopName = request.getShopName();
        String source = request.getSource();
        String target = request.getTarget();
        Future<?> future = executorService.submit(() -> {
            LocalDateTime begin = LocalDateTime.now();
            appInsights.trackTrace("Task submitted at: " + begin + " for shop: " + shopName);
            try {
                translating(request, remainingChars, counter, usedChars);  // 执行翻译任务
            } catch (ClientException e) {
                if (e.getErrorMessage().equals(HAS_TRANSLATED)) {
                    translationCounterService.updateUsedCharsByShopName(new TranslationCounterRequest(0, shopName, 0, counter.getTotalChars(), 0, 0, 0));
                    appInsights.trackTrace("翻译失败的原因： " + e.getErrorMessage());
                    System.out.println("翻译失败的原因： " + e.getErrorMessage());
                    //更新初始值
                    try {
                        startTokenCount(request);
                    } catch (Exception e2) {
                        appInsights.trackTrace("重新更新token值失败！！！");
                        System.out.println("翻译失败的原因： " + e.getErrorMessage());
                    }
                    return;
                }
                appInsights.trackTrace("startTranslation " + e.getErrorMessage());
                //更新初始值
                try {
                    translatesService.updateTranslateStatus(shopName, 3, target, source, request.getAccessToken());
                    translationCounterService.updateUsedCharsByShopName(new TranslationCounterRequest(0, shopName, 0, counter.getTotalChars(), 0, 0, 0));
//                //发送报错邮件
                    AtomicBoolean emailSent = userEmailStatus.computeIfAbsent(shopName, k -> new AtomicBoolean(false));
//                if (emailSent.compareAndSet(false, true)) {
//                    translateFailEmail(shopName,counter,begin, usedChars, remainingChars, target, source);
//                }
                    startTokenCount(request);
                } catch (Exception e3) {
                    appInsights.trackTrace("重新更新token值失败！！！");
                }
                return;
            } catch (CannotCreateTransactionException e) {
                System.out.println("翻译失败的原因： " + e);
                appInsights.trackTrace("Translation task failed: " + e);
                //更新初始值
                try {
                    startTokenCount(request);
                } catch (Exception e4) {
                    appInsights.trackTrace("重新更新token值失败！！！");
                }
                return;
            } catch (Exception e) {
                System.out.println("翻译失败的原因： " + e);
                appInsights.trackTrace("Translation task failed: " + e);
                //更新初始值
                try {
                    startTokenCount(request);
                } catch (Exception e5) {
                    appInsights.trackTrace("重新更新token值失败！！！");
                }
                translationCounterService.updateUsedCharsByShopName(new TranslationCounterRequest(0, shopName, 0, counter.getTotalChars(), 0, 0, 0));
                translatesService.updateTranslateStatus(shopName, 3, target, source, request.getAccessToken());
                return;
            }
            //         更新数据库中的已使用字符数
            translationCounterService.updateUsedCharsByShopName(new TranslationCounterRequest(0, shopName, 0, counter.getTotalChars(), 0, 0, 0));
            // 将翻译状态改为“已翻译”//
            translatesService.updateTranslateStatus(shopName, 1, request.getTarget(), source, request.getAccessToken());
            //翻译成功后发送翻译成功的邮件

            //更新初始值
            try {
                if (!userStopFlags.get(shopName).get()) {
//                    translateSuccessEmail(request, counter, begin, usedChars, remainingChars);
                }
                startTokenCount(request);
            } catch (Exception e) {
                appInsights.trackTrace("重新更新token值失败！！！");
            }
        });

        userTasks.put(shopName, future);  // 存储用户的任务
        userEmailStatus.put(shopName, new AtomicBoolean(false)); //重置用户发送的邮件
        userStopFlags.put(shopName, new AtomicBoolean(false));  // 初始化用户的停止标志

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
//                 将翻译状态改为“部分翻译” shopName, status=3
                translatesService.updateStatusByShopNameAnd2(shopName);
//                translateFailEmail(shopName, TRANSLATING_STOPPED);
            }
        }
    }

    // 手动停止用户的翻译任务
    public String stopTranslationManually(String shopName) {
        AtomicBoolean stopFlag = userStopFlags.get(shopName);
        if (stopFlag != null) {
            stopFlag.set(true);  // 设置停止标志，任务会在合适的地方检查并终止
            Future<?> future = userTasks.get(shopName);
            if (future != null && !future.isDone()) {
                future.cancel(true);  // 中断正在执行的任务
                appInsights.trackTrace("用户 " + shopName + " 的翻译任务已停止");
//                 将翻译状态改为“部分翻译” shopName, status=3
                translatesService.updateStatusByShopNameAnd2(shopName);
                return "翻译任务已停止";
            }
        }
        return "无法停止翻译任务";
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
        return getGoogleTranslationWithRetry(request);
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
//        ObjectMapper objectMapper = new ObjectMapper();
//        ShopifyRequest request = new ShopifyRequest();
//        request.setShopName(cloudServiceRequest.getShopName());
//        request.setAccessToken(cloudServiceRequest.getAccessToken());
//        request.setTarget(cloudServiceRequest.getTarget());
//        Map<String, Object> body = cloudServiceRequest.getBody();
//
//        try {
//            String requestBody = objectMapper.writeValueAsString(cloudServiceRequest);
//            String env = System.getenv("ApplicationEnv");
//            if ("prod".equals(env) || "dev".equals(env)) {
//                shopifyApiIntegration.registerTransaction(request, body);
//            } else {
//                testingEnvironmentIntegration.sendShopifyPost("translate/insertTranslatedText", requestBody);
//            }
//
//        } catch (JsonProcessingException | ClientException e) {
//            appInsights.trackTrace("Failed to save to Shopify: " + e.getMessage());
//        }
    }


    //判断数据库是否有该用户如果有将状态改为2（翻译中），如果没有该用户插入用户信息和翻译状态,开始翻译流程
    public void translating(TranslateRequest request, int remainingChars, CharacterCountUtils counter, int usedChars) {
        ShopifyRequest shopifyRequest = convertTranslateRequestToShopifyRequest(request);
        CloudServiceRequest cloudServiceRequest = TypeConversionUtils.shopifyToCloudServiceRequest(shopifyRequest);

        //判断是否有同义词
        Map<String, Object> glossaryMap = new HashMap<>();
        getGlossaryByShopName(shopifyRequest, glossaryMap);

        //获取目前所使用的AI语言包
        Integer packId = aiLanguagePacksService.getPackIdByShopName(request.getShopName());
        AILanguagePacksDO aiLanguagePacksDO = aiLanguagePacksService.getPromotByPackId(packId);

        //TRANSLATION_RESOURCES
        for (TranslateResourceDTO translateResource : ALL_RESOURCES) {
            // 定期检查是否停止
            if (checkIsStopped(request.getShopName(), counter, request.getTarget(), request.getSource())) return;
            String completePrompt = aiLanguagePackService.getCompletePrompt(aiLanguagePacksDO, translateResource.getResourceType(), request.getTarget());
            aiLanguagePacksDO.setPromotWord(completePrompt);
            translateResource.setTarget(request.getTarget());
            String query = new ShopifyRequestBody().getFirstQuery(translateResource);
            cloudServiceRequest.setBody(query);
            String shopifyData;
            try {
                String env = System.getenv("ApplicationEnv");
                if ("prod".equals(env) || "dev".equals(env)) {
                    shopifyData = String.valueOf(shopifyApiIntegration.getInfoByShopify(shopifyRequest, query));
                } else {
                    shopifyData = shopifyService.getShopifyData(cloudServiceRequest);
                }
            } catch (Exception e) {
                // 如果出现异常，则跳过, 翻译其他的内容
                //更新当前字符数
                translationCounterService.updateUsedCharsByShopName(new TranslationCounterRequest(0, request.getShopName(), 0, counter.getTotalChars(), 0, 0, 0));
                continue;
            }
            TranslateContext translateContext = new TranslateContext(shopifyData, shopifyRequest, translateResource, counter, remainingChars, glossaryMap, request.getSource(), aiLanguagePacksDO, null);
            translateJson(translateContext);
            // 定期检查是否停止
            if (checkIsStopped(request.getShopName(), counter, request.getTarget(), request.getSource())) return;
        }
        translatesService.updateTranslatesResourceType(request.getShopName(), request.getTarget(), request.getSource(), null);
        System.out.println("翻译结束");
    }

    private boolean checkIsStopped(String shopName, CharacterCountUtils counter, String target, String source) {
        if (userStopFlags.get(shopName).get()) {
            //                更新数据库中的已使用字符数
            translationCounterService.updateUsedCharsByShopName(new TranslationCounterRequest(0, shopName, 0, counter.getTotalChars(), 0, 0, 0));
            // 将翻译状态为2改为“部分翻译”//
            translatesService.updateStatusByShopNameAnd2(shopName);
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

    public Future<Void> translateJson(TranslateContext translateContext) {
        String resourceType = translateContext.getTranslateResource().getResourceType();
        ShopifyRequest request = translateContext.getShopifyRequest();
        System.out.println("现在翻译到： " + resourceType);
        //将目前的状态，添加到数据库中，前端要用这个数据做进度条功能
        translatesService.updateTranslatesResourceType(request.getShopName(), request.getTarget(), translateContext.getSource(), resourceType);

        if (translateContext.getShopifyData() == null) {
            // 返回默认值或空结果
            return null;
        }
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode rootNode;
        try {
            rootNode = objectMapper.readTree(translateContext.getShopifyData());
        } catch (JsonProcessingException e) {
            appInsights.trackTrace("rootNode错误： " + e.getMessage());
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
                JsonNode endCursor = pageInfoNode.path("endCursor");
                translateContext.getTranslateResource().setAfter(endCursor.asText(null));
                translateNextPage(translateContext);
            }
        }
    }

    //递归遍历JSON树：使用 translateSingleLineTe
    //方法递归地遍历整个 JSON 树，并对 translatableContent 字段进行特别处理。
    private void translateSingleLineTextFieldsRecursively(JsonNode node, TranslateContext translateContext) {

        ShopifyRequest shopifyRequest = translateContext.getShopifyRequest();
        String source = translateContext.getSource();
        if (checkIsStopped(shopifyRequest.getShopName(), translateContext.getCharacterCountUtils(), shopifyRequest.getTarget(), source))
            return;
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
        if (checkIsStopped(shopifyRequest.getShopName(), translateContext.getCharacterCountUtils(), shopifyRequest.getTarget(), source))
            return;
        //对judgeData数据进行翻译和存入shopify,除了html
        try {
            translateAndSaveData(judgeData, translateContext);
        } catch (ClientException e) {
            appInsights.trackTrace("翻译过程中抛出的异常" + e.getErrorMessage());
            throw e;
        }
        translationCounterService.updateUsedCharsByShopName(new TranslationCounterRequest(0, shopifyRequest.getShopName(), 0, translateContext.getCharacterCountUtils().getTotalChars(), 0, 0, 0));
    }

    // 处理单个节点元素，提取相关信息并分类存储
    private void processNodeElement(JsonNode nodeElement, ShopifyRequest shopifyRequest, TranslateContext translateContext,
                                    Map<String, List<RegisterTransactionRequest>> judgeData) {
        String resourceId = null;
        ArrayNode translatableContent = null;
        Map<String, TranslateTextDO> translatableContentMap = null;

        if (checkIsStopped(shopifyRequest.getShopName(), translateContext.getCharacterCountUtils(), shopifyRequest.getTarget(), translateContext.getSource()))
            return;
        // 遍历字段，提取 resourceId 和 translatableContent
        Iterator<Map.Entry<String, JsonNode>> fields = nodeElement.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            String fieldName = field.getKey();
            JsonNode fieldValue = field.getValue();

            // 根据字段名称进行处理
            switch (fieldName) {
                case "resourceId":
                    if (fieldValue == null) {
                        break;
                    }
                    resourceId = fieldValue.asText(null);
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
            put(METAFIELD, new ArrayList<>());
        }};
    }

    //对judgeData数据进行翻译和存入shopify,除了html
    private void translateAndSaveData(Map<String, List<RegisterTransactionRequest>> judgeData, TranslateContext translateContext) {
        for (Map.Entry<String, List<RegisterTransactionRequest>> entry : judgeData.entrySet()) {
            if (checkIsStopped(translateContext.getShopifyRequest().getShopName(), translateContext.getCharacterCountUtils(), translateContext.getShopifyRequest().getTarget(), translateContext.getSource()))
                return;
            switch (entry.getKey()) {
//                case PLAIN_TEXT:
//                    translateDataByAPI(entry.getValue(), translateContext);
//                    break;
//                case HTML:
//                    translateHtml(entry.getValue(), translateContext);
//                    break;
//                case JSON_TEXT:
//                    translateJsonText(entry.getValue(), translateContext);
//                    break;
//                case DATABASE:
//                    //处理database数据
//                    translateDataByDatabase(entry.getValue(), translateContext);
//                    break;
//                case GLOSSARY:
//                    //区分大小写
//                    translateDataByGlossary(entry.getValue(), translateContext);
//                    break;
//                case OPENAI:
//                    translateDataByOPENAI(entry.getValue(), translateContext);
//                    break;
                case METAFIELD:
                    translateMetafield(entry.getValue(), translateContext);
                    break;
                default:
                    appInsights.trackTrace("未知的翻译文本： " + entry.getValue());
                    break;
            }
        }
    }

    private void translateMetafield(List<RegisterTransactionRequest> registerTransactionRequests, TranslateContext translateContext) {
        ShopifyRequest request = translateContext.getShopifyRequest();
        CharacterCountUtils counter = translateContext.getCharacterCountUtils();
        //判断是否停止翻译
        if (checkIsStopped(request.getShopName(), counter, request.getTarget(), translateContext.getSource())) return;

        int remainingChars = translateContext.getRemainingChars();
        String target = request.getTarget();
        for (RegisterTransactionRequest registerTransactionRequest : registerTransactionRequests) {
            //判断是否停止翻译
            if (checkIsStopped(request.getShopName(), counter, request.getTarget(), translateContext.getSource()))
                return;
            String value = registerTransactionRequest.getValue();
            String translatableContentDigest = registerTransactionRequest.getTranslatableContentDigest();
            String key = registerTransactionRequest.getKey();
            String source = registerTransactionRequest.getLocale();
            String resourceId = registerTransactionRequest.getResourceId();
            String type = registerTransactionRequest.getTarget();
            Map<String, Object> translation = createTranslationMap(target, key, translatableContentDigest);

            // 判断是否会超限制
            updateCharsWhenExceedLimit(counter, request.getShopName(), remainingChars, new TranslateRequest(0, null, request.getAccessToken(), source, target, null));

            if (value == null || value.trim().isEmpty()) {
                continue;
            }

            if (SINGLE_LINE_TEXT_FIELD.equals(type)) {
                //纯数字字母符号 且有两个  标点符号 不翻译
                if (isValidString(value)) {
                    continue;
                }

                //走翻译流程
                System.out.println("翻译前的文本： " + value);
                String translated = translateSingleText(request, value, type, counter, source);
                System.out.println("翻译后的文本： " + translated);



                continue;
            }

            if (LIST_SINGLE_LINE_TEXT_FIELD.equals(type)) {
                System.out.println("翻译前的文本： " + value);
                //先将list数据由String转为List<String>，循环判断
                try {
                    //如果符合要求，则翻译，不符合要求则返回原值
                    List<String> resultList = objectMapper.readValue(value, new TypeReference<List<String>>() {
                    });
                    for (int i = 0; i < resultList.size(); i++) {
                        String original = resultList.get(i);
                        if (!isValidString(original) && original != null && !original.trim().isEmpty() && !isHtml(value)) {
                            //TODO:走翻译流程
                            String translated = translateSingleText(request, original, type, counter, source);
                            //将数据填回去
                            resultList.set(i, translated);
                        }
                    }
                    //将list数据转为String 再存储到shopify本地
                    String translatedValue = objectMapper.writeValueAsString(resultList);
                    System.out.println("翻译后的文本： " + translatedValue);
//                   saveToShopify(translatedValue, translation, resourceId, request);
                } catch (Exception e) {
                    //存原数据到shopify本地
//                   saveToShopify(value, translation, resourceId, request);
                    appInsights.trackTrace("LIST错误原因： " + e.getMessage());
                }
            }
            if (checkIsStopped(request.getShopName(), counter, request.getTarget(), translateContext.getSource()))
                return;
        }
    }

    //仅翻译单行文本。先缓存，后数据库，再普通翻译
    public String translateSingleText(ShopifyRequest request, String value, String type, CharacterCountUtils counter, String source) {
        //缓存
        String targetCache = translateSingleLine(value, request.getTarget());
        if (targetCache != null) {
            return targetCache;
        }

        //数据库
        String targetText = null;
        try {
            targetText = vocabularyService.getTranslateTextDataInVocabulary(request.getTarget(), value, source);
        } catch (Exception e) {
            //打印错误信息
            appInsights.trackTrace("translateDataByDatabase error: " + e.getMessage());
        }
        if (targetText != null) {
            return targetText;
        }

        //普通翻译
//        String translatedText = translateAndCount(new TranslateRequest(0, null, request.getAccessToken(), source, request.getTarget(), value), counter, type);
//        addData(request.getTarget(), value, translatedText);
//        saveToDatabase(request.getTarget(), translatedText, source, value);
        return value + "-1";
    }

    private void translateDataByOPENAI(List<RegisterTransactionRequest> registerTransactionRequests, TranslateContext translateContext) {
        ShopifyRequest request = translateContext.getShopifyRequest();
        CharacterCountUtils counter = translateContext.getCharacterCountUtils();
        //判断是否停止翻译
        if (checkIsStopped(request.getShopName(), counter, request.getTarget(), translateContext.getSource())) return;

        int remainingChars = translateContext.getRemainingChars();
        String target = request.getTarget();

        for (RegisterTransactionRequest registerTransactionRequest : registerTransactionRequests) {
            //判断是否停止翻译
            if (checkIsStopped(request.getShopName(), counter, request.getTarget(), translateContext.getSource()))
                return;
            String value = registerTransactionRequest.getValue();
            String translatableContentDigest = registerTransactionRequest.getTranslatableContentDigest();
            String key = registerTransactionRequest.getKey();
            String source = registerTransactionRequest.getLocale();
            String resourceId = registerTransactionRequest.getResourceId();
            Map<String, Object> translation = createTranslationMap(target, key, translatableContentDigest);

            // 判断是否会超限制
            updateCharsWhenExceedLimit(counter, request.getShopName(), remainingChars, new TranslateRequest(0, null, request.getAccessToken(), source, target, null));

            //获取缓存数据和数据库数据
            if (translateDataByCacheAndDatabase(request, value, translation, resourceId, target, source)) {
                continue;
            }

            if (value == null) {
                continue;
            }

            // 处理 "handle", "JSON", "HTML" 数据
            if (handleSpecialCases(value, translation, resourceId, request, registerTransactionRequest, counter, translateContext)) {
                continue;
            }

            // TODO: 判断用AI和谷歌翻译
            try {
                translateByGoogleOrAI(request, counter, registerTransactionRequest, translation, translateContext.getTranslateResource().getResourceType());
            } catch (Exception e) {
                appInsights.trackTrace("翻译错误原因： " + e.getMessage());
            }
            if (checkIsStopped(request.getShopName(), counter, request.getTarget(), translateContext.getSource()))
                return;
        }
    }

    public static Map<String, Object> createTranslationMap(String target, String key, String translatableContentDigest) {
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

        if ("HTML".equals(type) || isHtml(value)) {
            String htmlTranslation;
            try {
                TranslateRequest translateRequest = new TranslateRequest(0, null, request.getAccessToken(), translateContext.getSource(), request.getTarget(), value);
                htmlTranslation = translateNewHtml(value, translateRequest, counter, translateContext.getTranslateResource().getResourceType());
                saveToShopify(htmlTranslation, translation, resourceId, request);
                return true;
            } catch (ClientException e) {
                saveToShopify(value, translation, resourceId, request);
                appInsights.trackTrace("accessToken: " + request.getAccessToken() + "，shopName: " + request.getShopName() + "，source: " + registerTransactionRequest.getLocale() + "，target: " + request.getTarget() + "，key: " + key + "，type: " + type + "，value: " + value + ", resourceID: " + registerTransactionRequest.getResourceId() + ", digest: " + registerTransactionRequest.getTranslatableContentDigest());
            }
        }
        return false;
    }

    //对openAI翻译中报错做处理，两次以上直接结束翻译
    public void ChatgptException(ShopifyRequest request, String source) {
        String shopName = request.getShopName();
        //终止翻译。
        AtomicBoolean stopFlag = userStopFlags.get(shopName);
        if (stopFlag != null) {
            translatesService.updateTranslateStatus(shopName, 4, request.getTarget(), source, request.getAccessToken());
            stopFlag.set(true);  // 设置停止标志，任务会在合适的地方检查并终止
            Future<?> future = userTasks.get(shopName);
            if (future != null && !future.isDone()) {
                future.cancel(true);  // 中断正在执行的任务
                appInsights.trackTrace("用户 " + shopName + " 的翻译任务已停止");
            }
        }
    }

    //对词汇表数据进行处理
    public void translateDataByGlossary(List<RegisterTransactionRequest> registerTransactionRequests,
                                        TranslateContext translateContext) {
        ShopifyRequest request = translateContext.getShopifyRequest();
        CharacterCountUtils counter = translateContext.getCharacterCountUtils();

        //判断是否停止翻译
        if (checkIsStopped(request.getShopName(), counter, request.getTarget(), translateContext.getSource())) return;
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
            if (checkIsStopped(request.getShopName(), counter, request.getTarget(), translateContext.getSource()))
                return;
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
            if (registerTransactionRequest.getTarget().equals(HTML) || isHtml(value)) {
                try {
                    targetText = translateGlossaryHtmlText(translateRequest, counter, keyMap1, keyMap0, translateContext.getTranslateResource().getResourceType());
                    targetText = isHtmlEntity(targetText);
                } catch (Exception e) {
                    saveToShopify(value, translation, resourceId, request);
                    continue;
                }
                saveToShopify(targetText, translation, resourceId, request);
                continue;
            }

            //其他数据类型，对数据做处理再翻译
            counter.addChars(googleCalculateToken(value));
            String updateText = extractKeywords(value, placeholderMap, keyMap1, keyMap0);
            translateRequest.setContent(updateText);
            //TODO: 修改翻译调用
            String translatedText = null;
            try {
                translatedText = translateAndCount(translateRequest, counter, translateContext.getTranslateResource().getResourceType());
                translatedText = isHtmlEntity(translatedText);
            } catch (Exception e) {
                appInsights.trackTrace("翻译问题： " + e.getMessage());
            }
            String finalText = restoreKeywords(translatedText, placeholderMap);
            saveToShopify(finalText, translation, resourceId, request);
            if (checkIsStopped(request.getShopName(), counter, request.getTarget(), translateContext.getSource()))
                return;
        }
    }


    //处理JSON_TEXT类型的数据
    private void translateJsonText(List<RegisterTransactionRequest> registerTransactionRequests, TranslateContext translateContext) {

        ShopifyRequest request = translateContext.getShopifyRequest();
        CharacterCountUtils counter = translateContext.getCharacterCountUtils();
        //判断是否停止翻译
        if (checkIsStopped(request.getShopName(), counter, request.getTarget(), translateContext.getSource())) return;
        String target = request.getTarget();
        Map<String, Object> translation = new HashMap<>();
        for (RegisterTransactionRequest registerTransactionRequest : registerTransactionRequests) {
            //判断是否停止翻译
            if (checkIsStopped(request.getShopName(), counter, request.getTarget(), translateContext.getSource()))
                return;
            translation.put("locale", target);
            translation.put("key", registerTransactionRequest.getKey());
            translation.put("translatableContentDigest", registerTransactionRequest.getTranslatableContentDigest());
            //直接存放到shopify本地
            saveToShopify(registerTransactionRequest.getValue(), translation, registerTransactionRequest.getResourceId(), request);
            if (checkIsStopped(request.getShopName(), counter, request.getTarget(), translateContext.getSource()))
                return;
        }
    }

    //处理从数据库获取的数据
    private void translateDataByDatabase(List<RegisterTransactionRequest> registerTransactionRequests, TranslateContext translateContext) {
        ShopifyRequest request = translateContext.getShopifyRequest();
        CharacterCountUtils counter = translateContext.getCharacterCountUtils();

        //判断是否停止翻译
        if (checkIsStopped(request.getShopName(), counter, request.getTarget(), translateContext.getSource())) return;

        int remainingChars = translateContext.getRemainingChars();
        String target = request.getTarget();
        Map<String, Object> translation;
        for (RegisterTransactionRequest registerTransactionRequest : registerTransactionRequests) {
            //判断是否停止翻译
            if (checkIsStopped(request.getShopName(), counter, request.getTarget(), translateContext.getSource()))
                return;

            String value = registerTransactionRequest.getValue();
            String key = registerTransactionRequest.getKey();
            String source = registerTransactionRequest.getLocale();
            String resourceId = registerTransactionRequest.getResourceId();
            String type = registerTransactionRequest.getTarget();
            translation = createTranslationMap(target, registerTransactionRequest);
            updateCharsWhenExceedLimit(counter, request.getShopName(), remainingChars, new TranslateRequest(0, null, request.getAccessToken(), source, target, null));
            //从数据库中获取数据，如果不为空，存入shopify本地；如果为空翻译
            //判断数据类型
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
            //获取缓存数据和数据库数据
            if (translateDataByCacheAndDatabase(request, value, translation, resourceId, target, source)) {
                continue;
            }

            //数据库为空的逻辑
            if ("HTML".equals(type) || isHtml(value)) {
                //存放在html的list集合里面
                // 解析HTML文档
                String htmlTranslation;
                try {
                    TranslateRequest translateRequest = new TranslateRequest(0, null, request.getAccessToken(), source, target, value);
                    htmlTranslation = translateNewHtml(value, translateRequest, counter, translateContext.getTranslateResource().getResourceType());
                } catch (Exception e) {
                    saveToShopify(value, translation, resourceId, request);
                    continue;
                }
                saveToShopify(htmlTranslation, translation, resourceId, request);
                continue;
            }

            //TODO: 改为判断语言代码方法
            try {
                translateByGoogleOrAI(request, counter, registerTransactionRequest, translation, translateContext.getTranslateResource().getResourceType());
            } catch (Exception e) {
                appInsights.trackTrace("accessToken: " + request.getAccessToken() + "，shopName: " + request.getShopName() + "，source: " + registerTransactionRequest.getLocale() + "，target: " + request.getTarget() + "，key: " + key + "，type: " + type + "，value: " + value + ", resourceID: " + registerTransactionRequest.getResourceId() + ", digest: " + registerTransactionRequest.getTranslatableContentDigest());
            }
            if (checkIsStopped(request.getShopName(), counter, request.getTarget(), translateContext.getSource()))
                return;
        }
    }

    //对html数据处理
    private void translateHtml(List<RegisterTransactionRequest> registerTransactionRequests, TranslateContext translateContext) {
        ShopifyRequest request = translateContext.getShopifyRequest();
        CharacterCountUtils counter = translateContext.getCharacterCountUtils();
        //判断是否停止翻译
        if (checkIsStopped(request.getShopName(), counter, request.getTarget(), translateContext.getSource())) return;

        int remainingChars = translateContext.getRemainingChars();
        String target = request.getTarget();
        for (RegisterTransactionRequest registerTransactionRequest : registerTransactionRequests) {
            //判断是否停止翻译
            if (checkIsStopped(request.getShopName(), counter, request.getTarget(), translateContext.getSource()))
                return;
            String value = registerTransactionRequest.getValue();
            String resourceId = registerTransactionRequest.getResourceId();
            String source = registerTransactionRequest.getLocale();
            Map<String, Object> translation = createTranslationMap(target, registerTransactionRequest);
            //判断是否超限
            updateCharsWhenExceedLimit(counter, request.getShopName(), remainingChars, new TranslateRequest(0, null, request.getAccessToken(), source, target, null));

            //存放在html的list集合里面
            // 解析HTML文档
            String htmlTranslation;
            try {
                TranslateRequest translateRequest = new TranslateRequest(0, null, request.getAccessToken(), source, target, value);
                htmlTranslation = translateNewHtml(value, translateRequest, counter, translateContext.getTranslateResource().getResourceType());
            } catch (Exception e) {
                saveToShopify(value, translation, resourceId, request);
                continue;
            }
            saveToShopify(htmlTranslation, translation, resourceId, request);
            if (checkIsStopped(request.getShopName(), counter, request.getTarget(), translateContext.getSource()))
                return;
        }
    }

    //对不同的数据使用不同的翻译api
    private void translateDataByAPI(List<RegisterTransactionRequest> registerTransactionRequests,
                                    TranslateContext translateContext) {
        ShopifyRequest request = translateContext.getShopifyRequest();
        CharacterCountUtils counter = translateContext.getCharacterCountUtils();

        int remainingChars = translateContext.getRemainingChars();
        String target = request.getTarget();
        if (checkIsStopped(request.getShopName(), counter, request.getTarget(), translateContext.getSource())) return;
        for (RegisterTransactionRequest registerTransactionRequest : registerTransactionRequests) {
            if (checkIsStopped(request.getShopName(), counter, request.getTarget(), translateContext.getSource()))
                return;
            String value = registerTransactionRequest.getValue();
            String resourceId = registerTransactionRequest.getResourceId();
            String source = registerTransactionRequest.getLocale();
            Map<String, Object> translation = createTranslationMap(target, registerTransactionRequest);
            //判断是否超限
            updateCharsWhenExceedLimit(counter, request.getShopName(), remainingChars, new TranslateRequest(0, null, request.getAccessToken(), registerTransactionRequest.getLocale(), target, null));

            //获取缓存数据和数据库数据
            if (translateDataByCacheAndDatabase(request, value, translation, resourceId, target, source)) {
                continue;
            }

//            首选谷歌翻译，翻译不了用AI翻译
            try {
                translateByGoogleOrAI(request, counter, registerTransactionRequest, translation, translateContext.getTranslateResource().getResourceType());
            } catch (Exception e) {
                appInsights.trackTrace("翻译失败后的字符数： " + counter.getTotalChars());
                translationCounterService.updateUsedCharsByShopName(new TranslationCounterRequest(0, request.getShopName(), 0, counter.getTotalChars(), 0, 0, 0));
                saveToShopify(value, translation, resourceId, request);
            }
            if (checkIsStopped(request.getShopName(), counter, request.getTarget(), translateContext.getSource()))
                return;
        }
    }

    //从缓存和数据库中获取数据
    public boolean translateDataByCacheAndDatabase(ShopifyRequest request, String value, Map<String, Object> translation, String resourceId, String target, String source) {
        //获取缓存数据
        String targetCache = translateSingleLine(value, request.getTarget());
        if (targetCache != null) {
            saveToShopify(targetCache, translation, resourceId, request);
            return true;
        }
        //TODO: 255字符以内才从数据库中获取数据
        String targetText = null;
        try {
            targetText = vocabularyService.getTranslateTextDataInVocabulary(target, value, source);
        } catch (Exception e) {
            //打印错误信息
            appInsights.trackTrace("translateDataByDatabase error: " + e.getMessage());
        }
        if (targetText != null) {
            addData(target, value, targetText);
            saveToShopify(targetText, translation, resourceId, request);
            return true;
        }
        return false;
    }

    //首选谷歌翻译，翻译不了用AI翻译
    public void translateByGoogleOrAI(ShopifyRequest request, CharacterCountUtils counter,
                                      RegisterTransactionRequest registerTransactionRequest,
                                      Map<String, Object> translation, String resourceType) {
        String value = registerTransactionRequest.getValue();
        String targetString = null;
        try {
            targetString = translateAndCount(new TranslateRequest(0, null, request.getAccessToken(), registerTransactionRequest.getLocale(), request.getTarget(), value), counter, resourceType);
        } catch (ClientException e) {
            appInsights.trackTrace("翻译失败： " + e.getMessage() + " ，继续翻译");
        }

        if (targetString == null) {
            appInsights.trackTrace("翻译失败后的字符： " + registerTransactionRequest);
            saveToShopify(value, translation, registerTransactionRequest.getResourceId(), request);
            return;
        }
        addData(request.getTarget(), value, targetString);
        saveToShopify(targetString, translation, registerTransactionRequest.getResourceId(), request);
        saveToDatabase(request.getTarget(), targetString, registerTransactionRequest.getLocale(), value);
    }

    // 将翻译数据存储到数据库种
    public void saveToDatabase(String target, String targetValue, String source, String sourceValue) {
        //存到数据库中
        try {
            // 255字符以内 和 数据库内有该数据类型 文本才能插入数据库
            vocabularyService.InsertOne(target, targetValue, source, sourceValue);
        } catch (Exception e) {
            appInsights.trackTrace("存储失败： " + e.getMessage() + " ，继续翻译");
        }
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

            if (contentItemNode == null) {
                continue;
            }

            String value;
            String locale;
            String translatableContentDigest;
            String key;
            String type;
            try {
                JsonNode valueNode = contentItemNode.path("value");
                if (valueNode == null) {
                    continue;
                }
                value = contentItemNode.path("value").asText(null);
                locale = contentItemNode.path("locale").asText(null);
                translatableContentDigest = contentItemNode.path("digest").asText(null);
                key = contentItemNode.path("key").asText(null);
                type = contentItemNode.path("type").asText(null);
                if (value == null) {
                    continue;  // 跳过当前项
                }
                if (value.matches("\\p{Zs}")) {
                    continue;
                }
                String clearValue = cleanTextFormat(value);
                if (clearValue.isEmpty()) {
                    continue;
                }
            } catch (Exception e) {
                appInsights.trackTrace("失败的原因： " + e.getMessage());
                continue;
            }


            //如果translatableContentMap里面有该key则不翻译，没有则翻译
//            if (translatableContentMap.containsKey(key) && !translatableContentMap.get(key).getOutdated()) {
//                continue;
//            }

            //如果包含相对路径则跳过
            if (key.contains("icon:") || "handle".equals(key) || type.equals("FILE_REFERENCE") || type.equals("URL") || type.equals("LINK")
                    || type.equals("LIST_FILE_REFERENCE") || type.equals("LIST_LINK")
                    || type.equals(("LIST_URL"))
//                    || resourceType.equals(METAFIELD)
                    || resourceType.equals(SHOP_POLICY)) {
                continue;
            }

            //对METAFIELD字段翻译
            if (resourceType.equals(METAFIELD)) {
                judgeData.get(METAFIELD).add(new RegisterTransactionRequest(null, null, locale, key, value, translatableContentDigest, resourceId, type));
                continue;
            }


//            System.out.println("value: " + value + " ,flag: " + translatableContentMap.get(key).getOutdated());
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


            //对value进行判断 plainText
            if ("HTML".equals(type) || isHtml(value)) {
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
                MENU.equals(resourceType) ||
                ONLINE_STORE_THEME_APP_EMBED.equals(resourceType) ||
                ONLINE_STORE_THEME_JSON_TEMPLATE.equals(resourceType) ||
                ONLINE_STORE_THEME_SECTION_GROUP.equals(resourceType) ||
                ONLINE_STORE_THEME_SETTINGS_CATEGORY.equals(resourceType) ||
                ONLINE_STORE_THEME_SETTINGS_DATA_SECTIONS.equals(resourceType);
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
//        Map<String, Object> variables = new HashMap<>();
//        variables.put("resourceId", resourceId);
//        translatedValue = isHtmlEntity(translatedValue);
//        translation.put("value", translatedValue);
//        Object[] translations = new Object[]{
//                translation // 将HashMap添加到数组中
//        };
//        variables.put("translations", translations);
//        //将翻译后的内容通过ShopifyAPI记录到shopify本地
//        saveToShopify(new CloudInsertRequest(request.getShopName(), request.getAccessToken(), request.getApiVersion(), request.getTarget(), variables));
    }


    //达到字符限制，更新用户剩余字符数，终止循环
    public void updateCharsWhenExceedLimit(CharacterCountUtils counter, String shopName, int remainingChars, TranslateRequest translateRequest) {
        TranslationCounterRequest request = new TranslationCounterRequest();
        request.setShopName(shopName);

        if (counter.getTotalChars() >= remainingChars) {
            translatesService.updateTranslateStatus(shopName, 3, translateRequest.getTarget(), translateRequest.getSource(), translateRequest.getAccessToken());
            translationCounterService.updateUsedCharsByShopName(new TranslationCounterRequest(0, shopName, 0, counter.getTotalChars(), 0, 0, 0));
            throw new ClientException(CHARACTER_LIMIT);
        }
    }

    //修改getTestQuery里面的testQuery，用获取后的的查询语句进行查询
    public JsonNode fetchNextPage(TranslateResourceDTO translateResource, ShopifyRequest request) {
        CloudServiceRequest cloudServiceRequest = TypeConversionUtils.shopifyToCloudServiceRequest(request);
        String query = new ShopifyRequestBody().getAfterQuery(translateResource);
        cloudServiceRequest.setBody(query);

        String env = System.getenv("ApplicationEnv");
        String infoByShopify;
        if ("prod".equals(env) || "dev".equals(env)) {
            infoByShopify = String.valueOf(shopifyApiIntegration.getInfoByShopify(request, query));
        } else {
            infoByShopify = shopifyService.getShopifyData(cloudServiceRequest);
        }
        ObjectMapper objectMapper = new ObjectMapper();

        try {
            return objectMapper.readTree(infoByShopify);
        } catch (JsonProcessingException e) {
            throw new ClientException(SHOPIFY_RETURN_ERROR.getErrMsg());
        }
    }

    //递归处理下一页数据
    private void translateNextPage(TranslateContext translateContext) {
        JsonNode nextPageData;
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
//        System.out.println("现在存储到： " + translateResourceDTO.getResourceType());
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
            if (node == null) {
                continue;
            }
            String resourceId = node.path("resourceId").asText(null);
            if (resourceId == null) {
                continue;
            }
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
            String endCursor = rootNode.path("data").path("translatableResources").path("pageInfo").path("endCursor").asText(null);
            // 调用API获取下一页数据
            translateResourceDTO.setAfter(endCursor);
            JsonNode nextPageData = fetchNextPage(translateResourceDTO, request);
            if (nextPageData != null) {
                processNodes(nextPageData, request, translateResourceDTO);
            }
        }
    }

    //获取一个页面所有Translations集合数据
    public static Map<String, TranslateTextDO> extractTranslations(JsonNode node, String resourceId, ShopifyRequest shopifyRequest) {
        Map<String, TranslateTextDO> translations = new HashMap<>();
        JsonNode translationsNode = node.path("translations");
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
                translateTextDO.setTextKey(translation.path("key").asText(null));
                translateTextDO.setTargetText(translation.path("value").asText(null));
                translateTextDO.setTargetCode(translation.path("locale").asText(null));
                translateTextDO.setOutdated(translation.path("outdated").asBoolean(false));
                translateTextDO.setResourceId(resourceId);
                translateTextDO.setShopName(shopifyRequest.getShopName());
                translations.put(translation.path("key").asText(null), translateTextDO);

            });
        }
        return translations;
    }

    //获取一个页面所有TranslatableContent集合数据
    public static Map<String, TranslateTextDO> extractTranslatableContent(JsonNode node, Map<String, TranslateTextDO> translations) {
        JsonNode contentNode = node.path("translatableContent");
        if (contentNode.isArray() && !contentNode.isEmpty()) {
            contentNode.forEach(content -> {
                if (translations == null) {
                    return;
                }
                TranslateTextDO keys = translations.get(content.path("key").asText(null));
                if (translations.get(content.path("key").asText(null)) != null) {
                    keys.setSourceCode(content.path("locale").asText(null));
                    keys.setTextType(content.path("type").asText(null));
                    keys.setDigest(content.path("digest").asText(null));
                    keys.setSourceText(content.path("value").asText(null));
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
            String string;
            try {
                String env = System.getenv("ApplicationEnv");
                if ("prod".equals(env) || "dev".equals(env)) {
                    string = String.valueOf(shopifyApiIntegration.getInfoByShopify(shopifyRequest, query));
                } else {
                    string = shopifyService.getShopifyData(cloudServiceRequest);
                }
            } catch (Exception e) {
                //如果出现异常，则跳过, 翻译其他的内容
                appInsights.trackTrace("saveTranslateText error: " + e.getMessage());
                continue;
            }
            saveTranslatedData(string, shopifyRequest, translateResource);
        }
        System.out.println("内存存储成功");
    }

    //翻译单个文本数据
    public String translateSingleText(RegisterTransactionRequest request) {
        TranslateRequest translateRequest = TypeConversionUtils.registerTransactionRequestToTranslateRequest(request);
        request.setValue(getGoogleTranslationWithRetry(translateRequest));
        //保存翻译后的数据到shopify本地
        Map<String, Object> variables = getVariables(request);
        ShopifyRequest shopifyRequest = convertTranslateRequestToShopifyRequest(translateRequest);
        return shopifyApiIntegration.registerTransaction(shopifyRequest, variables);
    }

    //翻译词汇表的html文本
    public String translateGlossaryHtmlText(TranslateRequest request, CharacterCountUtils counter, Map<String, String> keyMap, Map<String, String> keyMap0, String resourceType) {
        String html = request.getContent();
        // 解析HTML文档
        Document doc = Jsoup.parseBodyFragment(html);

        // 提取需要翻译的文本
        Map<Element, List<String>> elementTextMap = jsoupUtils.extractTextsToTranslate(doc);
        // 翻译文本
        Map<Element, List<String>> translatedTextMap = jsoupUtils.translateGlossaryTexts(elementTextMap, request, counter, keyMap, keyMap0, resourceType);
        // 替换原始文本为翻译后的文本
        jsoupUtils.replaceOriginalTextsWithTranslated(doc, translatedTextMap);
        return doc.body().html();
    }

    //将数据存入本地Map中
    //优化策略1： 利用翻译后的数据，对singleLine的数据全局匹配并翻译
    public String translateSingleLine(String sourceText, String target) {
        if (SINGLE_LINE_TEXT.get(target) != null) {
            return SINGLE_LINE_TEXT.get(target).get(sourceText);
        }
        return null;
    }

    //插入语言状态
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
            innerMap.forEach((innerKey, value) -> list.add(new TranslateTextDO(null, null, null, null, null, null, innerKey, value, null, outerKey, false)));
        });
        if (!list.isEmpty()) {
//            translateTextService.getExistTranslateTextList(list);
            try {
                vocabularyService.storeTranslationsInVocabulary(list);
            } catch (Exception e) {
                appInsights.trackTrace("存储失败： " + e.getMessage() + " ，继续翻译");
            }
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
        // 定义要移除的后缀
        String suffix = ".myshopify.com";
        String TargetShop;
        TargetShop = request.getShopName().substring(0, request.getShopName().length() - suffix.length());
        templateData.put("shop_name", TargetShop);
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
        appInsights.trackTrace("templateData" + templateData);
        //由腾讯发送邮件
        Boolean b = emailIntegration.sendEmailByTencent(new TencentSendEmailRequest(137353L, templateData, SUCCESSFUL_TRANSLATION_SUBJECT, TENCENT_FROM_EMAIL, usersDO.getEmail()));
        //存入数据库中
        emailService.saveEmail(new EmailDO(0, shopName, TENCENT_FROM_EMAIL, usersDO.getEmail(), SUCCESSFUL_TRANSLATION_SUBJECT, b ? 1 : 0));

    }

    //翻译失败后发送邮件
    public void translateFailEmail(String shopName, CharacterCountUtils counter, LocalDateTime begin, int beginChars, Integer remainingChars, String target, String source) {
        UsersDO usersDO = usersService.getUserByName(shopName);
        Map<String, String> templateData = new HashMap<>();
        templateData.put("language", target);
        templateData.put("user", usersDO.getFirstName());
        // 定义要移除的后缀
        String suffix = ".myshopify.com";
        String TargetShop;
        TargetShop = shopName.substring(0, shopName.length() - suffix.length());
        templateData.put("shop_name", TargetShop);
        //获取用户已翻译的和未翻译的文本
        //通过shopName获取翻译到那个文本
        String resourceType = translatesService.getResourceTypeByshopNameAndTargetAndSource(shopName, target, source);
        TypeSplitResponse typeSplitResponse = splitByType(resourceType);
        templateData.put("translated_content", typeSplitResponse.getBefore().toString());
        templateData.put("remaining_content", typeSplitResponse.getAfter().toString());
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
        //由腾讯发送邮件
        Boolean b = emailIntegration.sendEmailByTencent(new TencentSendEmailRequest(137317L, templateData, TRANSLATION_FAILED_SUBJECT, TENCENT_FROM_EMAIL, usersDO.getEmail()));
        //存入数据库中
        emailService.saveEmail(new EmailDO(0, shopName, TENCENT_FROM_EMAIL, usersDO.getEmail(), TRANSLATION_FAILED_SUBJECT, b ? 1 : 0));
    }

    /**
     * 根据店铺名称和target获取对应的 ID。
     *
     * <p>此方法通过调用 translatesService 服务层方法，根据传入的店铺名称和target查询并返回一个唯一的 ID。</p>
     *
     * @param shopName 店铺名称，用于标识特定的店铺，通常是一个非空的字符串
     * @param target   目标值，语言代码，通常是一个非空的字符串
     * @return int 返回与店铺名称和目标值匹配的 ID，如果未找到匹配记录，通常返回 null
     */
    public int getIdByShopNameAndTargetAndSource(String shopName, String target, String source) {
        // 调用 translatesService 的 getIdByShopNameAndTarget 方法，传入店铺名称和目标值
        // 该方法负责实际的逻辑处理（如数据库查询），并返回对应的 ID
        return translatesService.getIdByShopNameAndTargetAndSource(shopName, target, source);
    }

    /**
     * 根据request和translationId获取对应模块的token。
     *
     * @param shopifyRequest 请求对象，包含shopName、target、source，accessToken等信息
     * @param key            请求对象的类型
     * @param translationId  shopName和target对应的ID
     */
    @Async
    public void updateStatusByTranslation(ShopifyRequest shopifyRequest, String key, int translationId, String method) {
        int tokens = 0;

        for (TranslateResourceDTO translateResourceDTO : TOKEN_MAP.get(key)) {
            int token = shopifyService.getTotalWords(shopifyRequest, method, translateResourceDTO);
            tokens += token;
        }
//        System.out.println("tokens: " + tokens);
        //将tokens存储到UserTypeToken对应的列里面
        userTypeTokenService.updateTokenByTranslationId(translationId, tokens, key);
        if ("collection".equals(key) || "notifications".equals(key) || "theme".equals(key)
                || "article".equals(key) || "blog_titles".equals(key) || "filters".equals(key) || "metaobjects".equals(key)
                || "pages".equals(key) || "products".equals(key) || "navigation".equals(key)
                || "shop".equals(key) || "shipping".equals(key) || "delivery".equals(key)) {
            UpdateWrapper<UserTypeTokenDO> updateWrapper = new UpdateWrapper<>();
            updateWrapper.eq("translation_id", translationId);

            // 根据传入的列名动态设置更新的字段
            updateWrapper.set(key, tokens);
            userTypeTokenService.update(null, updateWrapper);
        } else {
            throw new IllegalArgumentException("Invalid column name");
        }
//        System.out.println("second: " + LocalDateTime.now());
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
            int token = shopifyService.getTotalWords(shopifyRequest, method, translateResourceDTO);
            tokens += token;
        }

        if ("collection".equals(key) || "notifications".equals(key) || "theme".equals(key)
                || "article".equals(key) || "blog_titles".equals(key) || "filters".equals(key) || "metaobjects".equals(key)
                || "pages".equals(key) || "products".equals(key) || "navigation".equals(key)
                || "shop".equals(key) || "shipping".equals(key) || "delivery".equals(key)) {
            UpdateWrapper<UserTypeTokenDO> updateWrapper = new UpdateWrapper<>();
            updateWrapper.eq(SHOP_NAME, shopifyRequest.getShopName());

            // 根据传入的列名动态设置更新的字段
            updateWrapper.set(key, tokens);
            userTypeTokenService.update(null, updateWrapper);
        } else {
            throw new IllegalArgumentException("Invalid column name");
        }

    }

    /**
     * 根据request获取对应模块的token。如果status为2就不计数，如果为其他就开始计数
     *
     * @param request 请求对象，包含shopName、target、source，accessToken等信息
     */
    public void startTokenCount(TranslateRequest request) {
        try {
            //获取translationId
            Integer translationId = translatesService.getIdByShopNameAndTargetAndSource(request.getShopName(), request.getTarget(), request.getSource());

            //判断数据库中UserTypeToken中translationId对应的status是什么 如果是2，则不获取token；如果是除2以外的其他值，获取token
            Integer status = userTypeTokenService.getStatusByTranslationId(translationId);
            if (status != 2) {
                //将UserTypeToken的status修改为2
                userTypeTokenService.updateStatusByTranslationIdAndStatus(translationId, 2);
                ShopifyRequest shopifyRequest = convertTranslateRequestToShopifyRequest(request);
                //循环type获取token
                for (String key : TOKEN_MAP.keySet()
                ) {
                    int tokens = 0;

                    for (TranslateResourceDTO translateResourceDTO : TOKEN_MAP.get(key)) {
                        int token = shopifyService.getTotalWords(shopifyRequest, "tokens", translateResourceDTO);
                        tokens += token;
                    }

                    //将tokens存储到UserTypeToken对应的列里面
                    userTypeTokenService.updateTokenByTranslationId(translationId, tokens, key);
                    if ("collection".equals(key) || "notifications".equals(key) || "theme".equals(key)
                            || "article".equals(key) || "blog_titles".equals(key) || "filters".equals(key) || "metaobjects".equals(key)
                            || "pages".equals(key) || "products".equals(key) || "navigation".equals(key)
                            || "shop".equals(key) || "shipping".equals(key) || "delivery".equals(key)) {
                        UpdateWrapper<UserTypeTokenDO> updateWrapper = new UpdateWrapper<>();
                        updateWrapper.eq("translation_id", translationId);

                        // 根据传入的列名动态设置更新的字段
                        updateWrapper.set(key, tokens);
                        userTypeTokenService.update(null, updateWrapper);
                    } else {
                        appInsights.trackTrace("Invalid column name");
                    }
                }
                //token全部获取完之后修改，UserTypeToken的status==1
                userTypeTokenService.updateStatusByTranslationIdAndStatus(translationId, 1);
            }
        } catch (IllegalArgumentException e) {
            appInsights.trackTrace("错误原因： " + e.getMessage());
        }
    }
}

