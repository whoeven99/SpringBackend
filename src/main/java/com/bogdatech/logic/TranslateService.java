package com.bogdatech.logic;


import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.bogdatech.Service.*;
import com.bogdatech.context.TranslateContext;
import com.bogdatech.entity.DO.*;
import com.bogdatech.entity.VO.SingleTranslateVO;
import com.bogdatech.exception.ClientException;
import com.bogdatech.integration.ALiYunTranslateIntegration;
import com.bogdatech.integration.TranslateApiIntegration;
import com.bogdatech.model.controller.request.*;
import com.bogdatech.model.controller.response.BaseResponse;
import com.bogdatech.requestBody.ShopifyRequestBody;
import com.bogdatech.utils.CharacterCountUtils;
import com.bogdatech.utils.JsoupUtils;
import com.bogdatech.utils.LiquidHtmlTranslatorUtils;
import com.bogdatech.utils.TypeConversionUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Component;
import org.springframework.transaction.CannotCreateTransactionException;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static com.bogdatech.constants.TranslateConstants.*;
import static com.bogdatech.entity.DO.TranslateResourceDTO.*;
import static com.bogdatech.integration.ShopifyHttpIntegration.getInfoByShopify;
import static com.bogdatech.integration.ShopifyHttpIntegration.registerTransaction;
import static com.bogdatech.integration.TranslateApiIntegration.getGoogleTranslationWithRetry;
import static com.bogdatech.logic.RabbitMqTranslateService.getShopifyJsonNode;
import static com.bogdatech.logic.ShopifyService.getShopifyDataByCloud;
import static com.bogdatech.logic.ShopifyService.getVariables;
import static com.bogdatech.requestBody.ShopifyRequestBody.getLanguagesQuery;
import static com.bogdatech.utils.CaseSensitiveUtils.*;
import static com.bogdatech.utils.JsonUtils.isJson;
import static com.bogdatech.utils.JsoupUtils.*;
import static com.bogdatech.utils.JudgeTranslateUtils.*;
import static com.bogdatech.utils.LiquidHtmlTranslatorUtils.isHtmlEntity;
import static com.bogdatech.utils.MapUtils.getTranslationStatusMap;
import static com.bogdatech.utils.PrintUtils.printTranslation;
import static com.bogdatech.utils.ProgressBarUtils.getProgressBar;
import static com.bogdatech.utils.RegularJudgmentUtils.isValidString;
import static com.bogdatech.utils.StringUtils.normalizeHtml;
import static com.bogdatech.utils.TypeConversionUtils.convertTranslateRequestToShopifyRequest;

@Component
@EnableAsync
//@Transactional
public class TranslateService {
    private final TranslateApiIntegration translateApiIntegration;
    private final ShopifyService shopifyService;
    private final ITranslatesService translatesService;
    private final ITranslationCounterService translationCounterService;
    private final ITranslateTextService translateTextService;
    private final GlossaryService glossaryService;
    private final AILanguagePackService aiLanguagePackService;
    private final IVocabularyService vocabularyService;
    private final UserTypeTokenService userTypeTokensService;
    private final TencentEmailService tencentEmailService;
    private final LiquidHtmlTranslatorUtils liquidHtmlTranslatorUtils;
    private final JsoupUtils jsoupUtils;
    private final ITranslateTasksService translateTasksService;
    private final IUsersService iUsersService;
    private final ALiYunTranslateIntegration aLiYunTranslateIntegration;
    private final ITranslationCounterService iTranslationCounterService;

    @Autowired
    public TranslateService(
            TranslateApiIntegration translateApiIntegration,
            ShopifyService shopifyService,
            ITranslatesService translatesService,
            ITranslationCounterService translationCounterService,
            ITranslateTextService translateTextService,
            GlossaryService glossaryService,
            AILanguagePackService aiLanguagePackService,
            IVocabularyService vocabularyService,
            UserTypeTokenService userTypeTokensService,
            TencentEmailService tencentEmailService, LiquidHtmlTranslatorUtils liquidHtmlTranslatorUtils, JsoupUtils jsoupUtils, ITranslateTasksService translateTasksService, IUsersService iUsersService, ALiYunTranslateIntegration aLiYunTranslateIntegration, ITranslationCounterService iTranslationCounterService) {
        this.translateApiIntegration = translateApiIntegration;
        this.shopifyService = shopifyService;
        this.translatesService = translatesService;
        this.translationCounterService = translationCounterService;
        this.translateTextService = translateTextService;
        this.glossaryService = glossaryService;
        this.aiLanguagePackService = aiLanguagePackService;
        this.liquidHtmlTranslatorUtils = liquidHtmlTranslatorUtils;
        this.vocabularyService = vocabularyService;
        this.userTypeTokensService = userTypeTokensService;
        this.tencentEmailService = tencentEmailService;
        this.jsoupUtils = jsoupUtils;
        this.translateTasksService = translateTasksService;
        this.iUsersService = iUsersService;
        this.aLiYunTranslateIntegration = aLiYunTranslateIntegration;
        this.iTranslationCounterService = iTranslationCounterService;
    }

    public static ConcurrentHashMap<String, ConcurrentHashMap<String, String>> SINGLE_LINE_TEXT = new ConcurrentHashMap<>();
    public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    //判断是否可以终止翻译流程
    public static ConcurrentHashMap<String, Future<?>> userTasks = new ConcurrentHashMap<>(); // 存储每个用户的翻译任务
    public static ConcurrentHashMap<String, AtomicBoolean> userStopFlags = new ConcurrentHashMap<>(); // 存储每个用户的停止标志
    public static ConcurrentHashMap<String, Map<String, Object>> userTranslate = new ConcurrentHashMap<>(); // 存储每个用户的翻译设置
    public static ConcurrentHashMap<String, Map<String, Object>> beforeUserTranslate = new ConcurrentHashMap<>(); // 存储每个用户的翻译设置
    //    private final AtomicBoolean emailSent = new AtomicBoolean(false); // 用于同步发送字符限制邮件
    // 使用 ConcurrentHashMap 存储每个用户的邮件发送状态
    public static ConcurrentHashMap<String, AtomicBoolean> userEmailStatus = new ConcurrentHashMap<>();
    static ThreadFactory threadFactory = runnable -> {
        Thread thread = new Thread(runnable);
        thread.setName("my-thread-" + thread.getId());
        thread.setUncaughtExceptionHandler((t, e) -> appInsights.trackTrace("线程 " + t.getName() + " 抛出异常: " + e.getMessage()));
        return thread;
    };
    public static ExecutorService executorService = new ThreadPoolExecutor(
            5,   // 核心线程数（比 vCPU 多一点）
            10,  // 最大线程数（vCPU * 4）
            60L, TimeUnit.SECONDS, // 空闲线程存活时间
            new LinkedBlockingQueue<>(50), // 任务队列（避免内存过载）
            threadFactory,
            new ThreadPoolExecutor.CallerRunsPolicy() // 拒绝策略
    );

    //部分翻译的处理
    public void translate3Handle(TranslateRequest request, CharacterCountUtils counter, LocalDateTime begin, int remainingChars, int usedChars, List<TranslateResourceDTO> translateSettings3) {
        //更新初始值
//        translationCounterService.updateUsedCharsByShopName(new TranslationCounterRequest(0, request.getShopName(), 0, counter.getTotalChars(), 0, 0, 0));
        translatesService.updateTranslateStatus(request.getShopName(), 3, request.getTarget(), request.getSource(), request.getAccessToken());
        //发送报错邮件
        tencentEmailService.translateFailEmail(request.getShopName(), counter, begin, usedChars, translateSettings3, request.getTarget(), request.getSource());
        translateFailHandle(request, counter);
    }

    //翻译失败的通用处理
    public void translateFailHandle(TranslateRequest request, CharacterCountUtils counter) {
        //更新初始值
        try {
            userTypeTokensService.startTokenCount(request);
        } catch (Exception e) {
            appInsights.trackTrace("重新更新token值失败！！！" + e.getMessage());
        }
    }

    //翻译成功的处理
    public void translateSuccessHandle(TranslateRequest request, CharacterCountUtils counter, LocalDateTime begin, int remainingChars, int usedChars, Boolean isTask) {
        //更新数据库中的已使用字符数
//        translationCounterService.updateUsedCharsByShopName(new TranslationCounterRequest(0, request.getShopName(), 0, counter.getTotalChars(), 0, 0, 0));
        // 将翻译状态改为“已翻译”//
        translatesService.updateTranslateStatus(request.getShopName(), 1, request.getTarget(), request.getSource(), request.getAccessToken());
        //翻译成功后发送翻译成功的邮件,中断翻译不发送邮件
        //更新初始值
        try {
            if (!userStopFlags.get(request.getShopName()).get()) {
                tencentEmailService.translateSuccessEmail(request, counter, begin, usedChars, remainingChars, isTask);
            }
            userTypeTokensService.startTokenCount(request);
        } catch (Exception e) {
            appInsights.trackTrace("errors 重新更新token值失败！！！" + e.getMessage());
        }
    }

    // 用户卸载停止指定用户的翻译任务
    public void stopTranslation(String shopName) {
        AtomicBoolean stopFlag = userStopFlags.get(shopName);
        if (stopFlag != null) {
            stopFlag.set(true);  // 设置停止标志，任务会在合适的地方检查并终止
            Future<?> future = userTasks.get(shopName);
            if (future != null && !future.isDone()) {
                future.cancel(true);  // 中断正在执行的任务
                appInsights.trackTrace("stopTranslation 用户 " + shopName + " 的翻译任务已停止");
                //将Task表DB中的status改为5
                translateTasksService.updateStatusAllTo5ByShopName(shopName);
//                 将翻译状态改为“部分翻译” shopName, status=3
                translatesService.updateStatusByShopNameAnd2(shopName);
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
                //将Task表DB中的status改为5
                translateTasksService.updateStatusAllTo5ByShopName(shopName);
//                 将翻译状态改为“部分翻译” shopName, status=3
                translatesService.updateStatusByShopNameAnd2(shopName);
                return "翻译任务已停止";
            }
        }
        return "无法停止翻译任务";
    }

    //百度翻译接口
//    public String baiDuTranslate(TranslateRequest request) {
//        String result = translateApiIntegration.baiDuTranslate(request);
//        if (result != null) {
//            return result;
//        }
//        return TRANSLATE_ERROR.getErrMsg();
//    }

    //google翻译接口
    public String googleTranslate(TranslateRequest request) {
        return getGoogleTranslationWithRetry(request);
    }

    //对自动翻译的异常捕获
    public void autoTranslateException(TranslateRequest request, int remainingChars, CharacterCountUtils counter, int usedChars) {
        String shopName = request.getShopName();
        String source = request.getSource();
        String target = request.getTarget();
        Boolean isTask = true;
        userStopFlags.put(shopName, new AtomicBoolean(false));  // 初始化用户的停止标志
        LocalDateTime begin = LocalDateTime.now();
        try {
            taskTranslating(request, remainingChars, counter, new ArrayList<>(), false);
        } catch (ClientException e) {
            appInsights.trackTrace("startTranslation " + e.getErrorMessage());
            //设置自动翻译模块邮件
            translate3Handle(request, counter, begin, remainingChars, usedChars, PRODUCT_RESOURCES);
            return;
        } catch (CannotCreateTransactionException e) {
            appInsights.trackTrace("CannotCreateTransactionException Translation task failed: " + e);
            //更新初始值
            translateFailHandle(request, counter);
            return;
        } catch (Exception e) {
            translatesService.updateTranslateStatus(shopName, 3, target, source, request.getAccessToken());
            appInsights.trackTrace("Exception Translation task failed: " + e);
            //更新初始值
            translateFailHandle(request, counter);
            return;
        }
        translateSuccessHandle(request, counter, begin, remainingChars, usedChars, isTask);
    }


    //定时任务自动翻译
    public void taskTranslating(TranslateRequest request, int remainingChars, CharacterCountUtils counter, List<String> list, boolean handleFlag) {
        ShopifyRequest shopifyRequest = convertTranslateRequestToShopifyRequest(request);
        appInsights.trackTrace("定时任务自动翻译开启");
        //将初始化用户翻译value为null
        Map<String, Object> translationStatusMap = getTranslationStatusMap(" and ", 1);
        userTranslate.put(request.getShopName(), translationStatusMap);
        //判断是否有同义词
        Map<String, Object> glossaryMap = new HashMap<>();
        glossaryService.getGlossaryByShopName(shopifyRequest, glossaryMap);
        int usedChar = counter.getTotalChars();
        CharacterCountUtils usedCharCounter = new CharacterCountUtils();
        usedCharCounter.addChars(usedChar);
        String shopName = request.getShopName();
        //只翻译product模块
        for (TranslateResourceDTO translateResource : PRODUCT_RESOURCES) {
            if (EXCLUDED_SHOPS.contains(shopName) && PRODUCT_MODEL.contains(translateResource.getResourceType())) {
                continue;
            }
            // 定期检查是否停止
            if (checkIsStopped(request.getShopName(), counter, request.getTarget(), request.getSource())) {
                return;
            }
            translateResource.setTarget(request.getTarget());
            String shopifyData = getShopifyData(shopifyRequest, translateResource);
            TranslateContext translateContext = new TranslateContext(shopifyData, shopifyRequest, translateResource, counter, remainingChars, glossaryMap, request.getSource(), null, null, handleFlag, null, null, null, null);
            translateJson(translateContext, usedCharCounter);
            // 定期检查是否停止
            if (checkIsStopped(request.getShopName(), counter, request.getTarget(), request.getSource())) {
                return;
            }
        }
        translatesService.updateTranslatesResourceType(request.getShopName(), request.getTarget(), request.getSource(), null);
        appInsights.trackTrace("翻译结束");
    }

    //获取用户对应模块的文本数据
    public static String getShopifyData(ShopifyRequest shopifyRequest, TranslateResourceDTO translateResource) {
        CloudServiceRequest cloudServiceRequest = TypeConversionUtils.shopifyToCloudServiceRequest(shopifyRequest);
        String query = new ShopifyRequestBody().getFirstQuery(translateResource);
        cloudServiceRequest.setBody(query);
        String shopifyData = null;
        try {
            String env = System.getenv("ApplicationEnv");
            if ("prod".equals(env) || "dev".equals(env)) {
                shopifyData = String.valueOf(getInfoByShopify(shopifyRequest, query));
            } else {
                shopifyData = getShopifyDataByCloud(cloudServiceRequest);
            }
        } catch (Exception e) {
            // 如果出现异常，则跳过, 翻译其他的内容
            //更新当前字符数
            appInsights.trackTrace("Failed to get Shopify data: " + e.getMessage());
        }
        return shopifyData;
    }

    public static final Set<String> EXCLUDED_SHOPS = new HashSet<>(Arrays.asList(
            "qnxrrk-2n.myshopify.com",
            "gemxco.myshopify.com"
    ));

    public static final Set<String> PRODUCT_MODEL = new HashSet<>(Arrays.asList(
            PRODUCT_OPTION,
            PRODUCT_OPTION_VALUE
    ));

    //判断数据库是否有该用户如果有将状态改为2（翻译中），如果没有该用户插入用户信息和翻译状态,开始翻译流程
    public void translating(TranslateRequest request, int remainingChars, CharacterCountUtils counter, int usedChars, List<String> translateSettings3, boolean handleFlag) {
        ShopifyRequest shopifyRequest = convertTranslateRequestToShopifyRequest(request);
        appInsights.trackTrace("普通翻译开始");
        //将初始化用户翻译value为null
        Map<String, Object> translationStatusMap = getTranslationStatusMap(" and ", 1);
        userTranslate.put(request.getShopName(), translationStatusMap);
        //判断是否有同义词
        Map<String, Object> glossaryMap = new HashMap<>();
        glossaryService.getGlossaryByShopName(shopifyRequest, glossaryMap);

        //获取目前所使用的AI语言包
        String languagePackId = aiLanguagePackService.getCategoryByDescription(shopifyRequest.getShopName(), shopifyRequest.getAccessToken(), counter, remainingChars);
        CharacterCountUtils usedCharCounter = new CharacterCountUtils();
        usedCharCounter.addChars(usedChars);
        String shopName = request.getShopName();
        //循环翻译ALL_RESOURCES里面所有的模块
        for (TranslateResourceDTO translateResource : ALL_RESOURCES
        ) {
            if (!translateSettings3.contains(translateResource.getResourceType())) {
                continue;
            }
            if (translateResource.getResourceType().equals(PAYMENT_GATEWAY)) {
                continue;
            }

            if (EXCLUDED_SHOPS.contains(shopName) && PRODUCT_MODEL.contains(translateResource.getResourceType())) {
                continue;
            }

            // 定期检查是否停止
            if (checkIsStopped(request.getShopName(), counter, request.getTarget(), request.getSource())) {
                return;
            }
            translateResource.setTarget(request.getTarget());
            String shopifyData = getShopifyData(shopifyRequest, translateResource);
            TranslateContext translateContext = new TranslateContext(shopifyData, shopifyRequest, translateResource, counter, remainingChars, glossaryMap, request.getSource(), languagePackId, null, handleFlag, null, null, null, null);
            try {
                translateJson(translateContext, usedCharCounter);
            } catch (ClientException e) {
                throw e;
            } catch (Exception e) {
                appInsights.trackTrace(shopName + " 用户 translateJson failed errors : " + e);
            }
            // 定期检查是否停止
            if (checkIsStopped(request.getShopName(), counter, request.getTarget(), request.getSource())) {
                return;
            }
        }
        translatesService.updateTranslatesResourceType(request.getShopName(), request.getTarget(), request.getSource(), null);
        appInsights.trackTrace("用户的手动翻译token： " + usedCharCounter.getTotalChars());
        appInsights.trackTrace("用户翻译token： " + counter.getTotalChars());
        appInsights.trackTrace("翻译结束");
    }

    //判断翻译是否要停止
    private boolean checkIsStopped(String shopName, CharacterCountUtils counter, String target, String source) {
        if (userStopFlags.get(shopName).get()) {
            //                更新数据库中的已使用字符数
//            translationCounterService.updateUsedCharsByShopName(new TranslationCounterRequest(0, shopName, 0, counter.getTotalChars(), 0, 0, 0));
            // 将翻译状态为2改为“部分翻译”//
            translatesService.updateStatusByShopNameAnd2(shopName);
            //将Task表DB中的status改为5
            translateTasksService.updateStatusAllTo5ByShopName(shopName);
            return true;
        }
        return false;
    }


    //根据返回的json片段，将符合条件的value翻译,并返回json片段

    public Future<Void> translateJson(TranslateContext translateContext, CharacterCountUtils usedCharCounter) {
        String resourceType = translateContext.getTranslateResource().getResourceType();
        ShopifyRequest request = translateContext.getShopifyRequest();
        appInsights.trackTrace("现在翻译到： " + resourceType + " shopName:" + request.getShopName() + " accessToken: " + request.getAccessToken() + " target: " + request.getTarget());
        //将目前的状态，添加到数据库中，前端要用这个数据做进度条功能
        translatesService.updateTranslatesResourceType(request.getShopName(), request.getTarget(), translateContext.getSource(), resourceType);
        if (translateContext.getShopifyData() == null) {
            // 返回默认值或空结果
            //在数据库中更新这些差值
//            translationCounterService.updateUsedCharsByShopName(new TranslationCounterRequest(0, request.getShopName(), 0, translateContext.getCharacterCountUtils().getTotalChars(), 0, 0, 0));
            return null;
        }
        JsonNode rootNode;
        try {
            rootNode = OBJECT_MAPPER.readTree(translateContext.getShopifyData());
        } catch (JsonProcessingException e) {
            appInsights.trackTrace("rootNode errors： " + e.getMessage());
            return null;
        }
        translateSingleLineTextFieldsRecursively(rootNode, translateContext);
        // 判断数据库的token数是否发生改变，如果改变，将新的和旧的counter做对比，如果一致
//        judgeCounterByOldAndNew(usedCharCounter, request.getShopName(), translateContext.getCharacterCountUtils());  // 递归处理下一页数据

        handlePagination(rootNode, translateContext, usedCharCounter);
        return null;
    }

    /**
     * 判断数据库的token数是否发生改变，如果改变，将新的和旧的counter做对比，如果一致
     *
     * @param shopName        店铺名称
     * @param usedCharCounter 一开始已经使用的字符数
     * @param counter         字符计数器
     */
    public void judgeCounterByOldAndNew(CharacterCountUtils usedCharCounter, String shopName, CharacterCountUtils counter) {
        TranslationCounterDO translationCounterDO = translationCounterService.readCharsByShopName(shopName);
        int usedCharsFromDb = translationCounterDO.getUsedChars();
        int currentUsedChars = usedCharCounter.getTotalChars();

        if (currentUsedChars == usedCharsFromDb) {
            // 没有差异，无需更新
            return;
        }

        //发生改变，需要修改原有值
        int difference = Math.abs(translationCounterDO.getUsedChars() - usedCharCounter.getTotalChars());
//            appInsights.trackTrace("差值： " + difference);
        //在翻译的计数器中添加这些差值
        counter.addChars(difference);
//            appInsights.trackTrace("翻译的计数器： " + counter.getTotalChars());
        usedCharCounter.reset();
        usedCharCounter.addChars(counter.getTotalChars());
//            appInsights.trackTrace("使用的计数器： " + usedCharCounter.getTotalChars());
        //在数据库中更新这些差值
        translationCounterService.updateUsedCharsByShopName(new TranslationCounterRequest(0, shopName, 0, counter.getTotalChars(), 0, 0, 0));

    }

    // 递归处理下一页数据
    private void handlePagination(JsonNode translatedRootNode, TranslateContext translateContext, CharacterCountUtils usedCharCounter) {
        // 获取translatableResources节点
        JsonNode translatableResourcesNode = translatedRootNode.path("translatableResources");
        // 获取pageInfo节点
        JsonNode pageInfoNode = translatableResourcesNode.path("pageInfo");

        if (translatableResourcesNode.hasNonNull("pageInfo")) {
            if (pageInfoNode.hasNonNull("hasNextPage") && pageInfoNode.get("hasNextPage").asBoolean()) {
                JsonNode endCursor = pageInfoNode.path("endCursor");
                translateContext.getTranslateResource().setAfter(endCursor.asText(null));
                translateNextPage(translateContext, usedCharCounter);
            }
        }
    }

    //递归遍历JSON树：使用 translateSingleLineTe
    //方法递归地遍历整个 JSON 树，并对 translatableContent 字段进行特别处理。
    private void translateSingleLineTextFieldsRecursively(JsonNode node, TranslateContext translateContext) {

        ShopifyRequest shopifyRequest = translateContext.getShopifyRequest();
        String source = translateContext.getSource();

        if (checkIsStopped(shopifyRequest.getShopName(), translateContext.getCharacterCountUtils(), shopifyRequest.getTarget(), source)) {
            return;
        }

        //定义HashMap存放判断后的对应数据
        // 初始化 judgeData 用于分类存储数据
        Map<String, List<RegisterTransactionRequest>> judgeData = initializeJudgeData(translateContext.getHandleFlag());
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
        try {
            for (JsonNode nodeElement : nodesArray) {
                if (nodeElement.isObject()) {
                    processNodeElement(nodeElement, shopifyRequest, translateContext, judgeData);
                }
            }
        } catch (Exception e) {
            appInsights.trackTrace("翻译过程中抛出的异常 errors " + e.getMessage());
        }
        if (checkIsStopped(shopifyRequest.getShopName(), translateContext.getCharacterCountUtils(), shopifyRequest.getTarget(), source)) {
            return;
        }
        //对judgeData数据进行翻译和存入shopify,除了html
        try {
            translateAndSaveData(judgeData, translateContext);
        } catch (ClientException e) {
            appInsights.trackTrace("翻译过程中抛出的异常" + e.getErrorMessage());
            throw e;
        }
    }

    // 处理单个节点元素，提取相关信息并分类存储
    private void processNodeElement(JsonNode nodeElement, ShopifyRequest shopifyRequest, TranslateContext translateContext,
                                    Map<String, List<RegisterTransactionRequest>> judgeData) {
        String resourceId = null;
        ArrayNode translatableContent = null;
        Map<String, TranslateTextDO> translatableContentMap = null;

        if (checkIsStopped(shopifyRequest.getShopName(), translateContext.getCharacterCountUtils(), shopifyRequest.getTarget(), translateContext.getSource())) {
            return;
        }
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
                    translatableContentMap = extractTranslations(nodeElement, resourceId, shopifyRequest.getShopName());
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
                        translatableContentMap, translateContext.getGlossaryMap(), translateContext.getHandleFlag(), shopifyRequest.getShopName());
            }
        }
    }

    // 初始化 judgeData，用于存储不同类型的数据
    public static Map<String, List<RegisterTransactionRequest>> initializeJudgeData(Boolean handleFlag) {
        HashMap<String, List<RegisterTransactionRequest>> hashMap = new HashMap<>() {{
            put(PLAIN_TEXT, new ArrayList<>());
            put(HTML, new ArrayList<>());
            put(DATABASE, new ArrayList<>());
            put(GLOSSARY, new ArrayList<>());
            put(OPENAI, new ArrayList<>());
            put(METAFIELD, new ArrayList<>());
            put(HANDLE, new ArrayList<>());
        }};
        return hashMap;
    }

    //对judgeData数据进行翻译和存入shopify,除了html
    private void translateAndSaveData(Map<String, List<RegisterTransactionRequest>> judgeData, TranslateContext translateContext) {
        for (Map.Entry<String, List<RegisterTransactionRequest>> entry : judgeData.entrySet()) {
            if (checkIsStopped(translateContext.getShopifyRequest().getShopName(), translateContext.getCharacterCountUtils(), translateContext.getShopifyRequest().getTarget(), translateContext.getSource())) {
                return;
            }
            switch (entry.getKey()) {
                case PLAIN_TEXT:
                    translateDataByAPI(entry.getValue(), translateContext);
                    break;
                case HTML:
                    translateHtml(entry.getValue(), translateContext);
                    break;
                case DATABASE:
                    //处理database数据
                    translateDataByDatabase(entry.getValue(), translateContext);
                    break;
                case GLOSSARY:
                    //区分大小写
                    translateDataByGlossary(entry.getValue(), translateContext);
                    break;
                case OPENAI:
                    translateDataByOPENAI(entry.getValue(), translateContext);
                    break;
                case METAFIELD:
                    try {
                        translateMetafield(entry.getValue(), translateContext);
                    } catch (ClientException e) {
                        throw e;
                    } catch (Exception e) {
                        appInsights.trackTrace(translateContext.getShopifyRequest().getShopName() + " 用户 metafield 翻译 errors ：" + e);
                    }
                    break;
                case HANDLE:
                    if (translateContext.getHandleFlag()) {
                        translateDataByHandle(entry.getValue(), translateContext);
                    }
                    break;
                default:
                    appInsights.trackTrace("未知的翻译文本： " + entry.getValue());
                    break;
            }
        }
    }

    //翻译handle字段的数据
    private void translateDataByHandle(List<RegisterTransactionRequest> registerTransactionRequests, TranslateContext translateContext) {
        ShopifyRequest request = translateContext.getShopifyRequest();
        CharacterCountUtils counter = translateContext.getCharacterCountUtils();
        //判断是否停止翻译
        if (checkIsStopped(request.getShopName(), counter, request.getTarget(), translateContext.getSource())) {
            return;
        }

        int remainingChars = translateContext.getRemainingChars();
        String target = request.getTarget();

        for (RegisterTransactionRequest registerTransactionRequest : registerTransactionRequests) {
            //判断是否停止翻译
            if (checkIsStopped(request.getShopName(), counter, request.getTarget(), translateContext.getSource())) {
                return;
            }
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

            //用AI翻译handle数据
            try {
                translateByGoogleOrAI(request, counter, registerTransactionRequest, translation, translateContext.getLanguagePackId(), translateContext.getTranslateResource().getResourceType(), HANDLE, translateContext.getRemainingChars());
            } catch (Exception e) {
                appInsights.trackTrace("translateDataByHandle翻译错误原因： " + e.getMessage());
            }
            if (checkIsStopped(request.getShopName(), counter, request.getTarget(), translateContext.getSource())) {
                return;
            }
        }
    }

    //翻译元字段的数据
    private void translateMetafield(List<RegisterTransactionRequest> registerTransactionRequests, TranslateContext translateContext) {
        ShopifyRequest request = translateContext.getShopifyRequest();
        CharacterCountUtils counter = translateContext.getCharacterCountUtils();
        //判断是否停止翻译
        if (checkIsStopped(request.getShopName(), counter, request.getTarget(), translateContext.getSource())) {
            return;
        }

        int remainingChars = translateContext.getRemainingChars();
        String target = request.getTarget();
        for (RegisterTransactionRequest registerTransactionRequest : registerTransactionRequests) {
            //判断是否停止翻译
            if (checkIsStopped(request.getShopName(), counter, request.getTarget(), translateContext.getSource())) {
                return;
            }
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

            //元字段Html翻译
            if (isHtml(value) && !isJson(value)) {
                htmlTranslate(translateContext, request, counter, target, value, source, resourceId, translation, METAFIELD);
                continue;
            }

            if (SINGLE_LINE_TEXT_FIELD.equals(type) && !isHtml(value)) {
                //纯数字字母符号 且有两个  标点符号 以#开头，长度为10 不翻译
                if (isValidString(value)) {
                    continue;
                }

                //走翻译流程
                String translatedText = translateSingleText(request, value, translateContext.getLanguagePackId(), counter, source, translateContext.getRemainingChars());
                addData(request.getTarget(), value, translatedText);
                shopifyService.saveToShopify(translatedText, translation, resourceId, request);
                printTranslation(translatedText, value, translation, request.getShopName(), type, resourceId, source);
                //存到数据库中
                try {
                    // 255字符以内 和 数据库内有该数据类型 文本才能插入数据库
                    vocabularyService.InsertOne(request.getTarget(), translatedText, registerTransactionRequest.getLocale(), value);
                } catch (Exception e) {
                    appInsights.trackTrace("translateMetafield存储失败： " + e.getMessage() + " ，继续翻译");
                }
                continue;
            }

            if (LIST_SINGLE_LINE_TEXT_FIELD.equals(type)) {
                //翻译list类型文本
                translateListData(value, request, type, counter, source, resourceId, translation, translateContext.getLanguagePackId(), translateContext.getRemainingChars());
            }
            if (checkIsStopped(request.getShopName(), counter, request.getTarget(), translateContext.getSource())) {
                return;
            }
        }
    }

    //元字段list类型翻译
    private void translateListData(String value, ShopifyRequest request, String type, CharacterCountUtils counter, String source, String resourceId, Map<String, Object> translation, String languagePackId, Integer limitChars) {
        //先将list数据由String转为List<String>，循环判断
        try {
            //如果符合要求，则翻译，不符合要求则返回原值
            List<String> resultList = OBJECT_MAPPER.readValue(value, new TypeReference<List<String>>() {
            });
            for (int i = 0; i < resultList.size(); i++) {
                String original = resultList.get(i);
                if (!isValidString(original) && original != null && !original.trim().isEmpty() && !isHtml(value)) {
                    //走翻译流程
                    String translated = translateSingleText(request, original, languagePackId, counter, source, limitChars);
                    //将数据填回去
                    resultList.set(i, translated);
                }
            }
            //将list数据转为String 再存储到shopify本地
            String translatedValue = OBJECT_MAPPER.writeValueAsString(resultList);
            shopifyService.saveToShopify(translatedValue, translation, resourceId, request);
            printTranslation(translatedValue, value, translation, request.getShopName(), type, resourceId, source);
        } catch (Exception e) {
            //存原数据到shopify本地
            shopifyService.saveToShopify(value, translation, resourceId, request);
            appInsights.trackTrace("LIST errors 错误原因： " + e.getMessage());
//                    appInsights.trackTrace("LIST错误原因： " + e.getMessage());
        }
    }

    //html的翻译
    private void htmlTranslate(TranslateContext translateContext, ShopifyRequest request, CharacterCountUtils counter,
                               String target, String value, String source, String resourceId,
                               Map<String, Object> translation, String model) {
        String htmlTranslation;
        try {
            TranslateRequest translateRequest = new TranslateRequest(0, request.getShopName(), request.getAccessToken(), source, target, value);
            htmlTranslation = liquidHtmlTranslatorUtils.translateNewHtml(value, translateRequest, counter, translateContext.getLanguagePackId(), translateContext.getRemainingChars(), null, null, null);
//            appInsights.trackTrace("htmlTranslation: " + htmlTranslation);
            if (model.equals(METAFIELD)) {
                //对翻译后的html做格式处理
                htmlTranslation = normalizeHtml(htmlTranslation);
            }
            //对翻译后的html做格式处理
        } catch (Exception e) {
            shopifyService.saveToShopify(value, translation, resourceId, request);
            return;
        }
        shopifyService.saveToShopify(htmlTranslation, translation, resourceId, request);
        printTranslation(htmlTranslation, value, translation, request.getShopName(), translateContext.getTranslateResource().getResourceType(), resourceId, source);
    }

    /**
     * 仅翻译单行文本。先缓存，后数据库，再普通翻译
     *
     * @return String
     * @Param request shopName，accessToken，target
     * @Param value 原文本
     * @Param languagePackId 语言包
     * @Param counter 计数器
     * @Param source 源语言
     */
    public String translateSingleText(ShopifyRequest request, String value, String languagePackId, CharacterCountUtils counter, String source, Integer limitChars) {
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
            appInsights.trackTrace("translateDataByDatabase errors : " + e.getMessage());
        }
        if (targetText != null) {
            return targetText;
        }

        //普通翻译
        return jsoupUtils.translateAndCount(new TranslateRequest(0, request.getShopName(), request.getAccessToken(), source, request.getTarget(), value), counter, languagePackId, GENERAL, limitChars);
    }

    private void translateDataByOPENAI(List<RegisterTransactionRequest> registerTransactionRequests, TranslateContext translateContext) {
        ShopifyRequest request = translateContext.getShopifyRequest();
        CharacterCountUtils counter = translateContext.getCharacterCountUtils();
        //判断是否停止翻译
        if (checkIsStopped(request.getShopName(), counter, request.getTarget(), translateContext.getSource())) {
            return;
        }

        int remainingChars = translateContext.getRemainingChars();
        String target = request.getTarget();

        for (RegisterTransactionRequest registerTransactionRequest : registerTransactionRequests) {
            //判断是否停止翻译
            if (checkIsStopped(request.getShopName(), counter, request.getTarget(), translateContext.getSource())) {
                return;
            }
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

            // 处理 "handle", "JSON", "HTML" 数据
            if (handleSpecialCases(value, translation, resourceId, request, registerTransactionRequest, counter, translateContext)) {
                continue;
            }

            //用AI翻译
            try {
                translateByGoogleOrAI(request, counter, registerTransactionRequest, translation, translateContext.getLanguagePackId(), translateContext.getTranslateResource().getResourceType(), GENERAL, translateContext.getRemainingChars());
            } catch (Exception e) {
                appInsights.trackTrace("translateDataByOPENAI errors ： " + e.getMessage());
            }
            if (checkIsStopped(request.getShopName(), counter, request.getTarget(), translateContext.getSource())) {
                return;
            }
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
        String source = registerTransactionRequest.getLocale();

        if ("HTML".equals(type) || isHtml(value)) {
            String htmlTranslation;
            try {
                TranslateRequest translateRequest = new TranslateRequest(0, request.getShopName(), request.getAccessToken(), translateContext.getSource(), request.getTarget(), value);
                htmlTranslation = liquidHtmlTranslatorUtils.translateNewHtml(value, translateRequest, counter, translateContext.getLanguagePackId(), translateContext.getRemainingChars(), null, null, null);
                shopifyService.saveToShopify(htmlTranslation, translation, resourceId, request);
                printTranslation(htmlTranslation, value, translation, request.getShopName(), translateContext.getTranslateResource().getResourceType(), resourceId, source);
                return true;
            } catch (ClientException e) {
                shopifyService.saveToShopify(value, translation, resourceId, request);
                appInsights.trackTrace("HTML accessToken: " + request.getAccessToken() + "，shopName: " + request.getShopName() + "，source: " + registerTransactionRequest.getLocale() + "，target: " + request.getTarget() + "，key: " + key + "，type: " + type + "，value: " + value + ", resourceID: " + registerTransactionRequest.getResourceId() + ", digest: " + registerTransactionRequest.getTranslatableContentDigest());
            }
        }
        return false;
    }


    //对词汇表数据进行处理
    public void translateDataByGlossary(List<RegisterTransactionRequest> registerTransactionRequests,
                                        TranslateContext translateContext) {
        ShopifyRequest request = translateContext.getShopifyRequest();
        CharacterCountUtils counter = translateContext.getCharacterCountUtils();

        //判断是否停止翻译
        if (checkIsStopped(request.getShopName(), counter, request.getTarget(), translateContext.getSource())) {
            return;
        }
        int remainingChars = translateContext.getRemainingChars();
        Map<String, Object> glossaryMap = translateContext.getGlossaryMap();
        String target = request.getTarget();
        Map<String, Object> translation = new HashMap<>();
        //关键词
        Map<String, String> keyMap1 = new HashMap<>();
        Map<String, String> keyMap0 = new HashMap<>();
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
            if (checkIsStopped(request.getShopName(), counter, request.getTarget(), translateContext.getSource())) {
                return;
            }
            String value = registerTransactionRequest.getValue();
            String source = registerTransactionRequest.getLocale();
            String resourceId = registerTransactionRequest.getResourceId();
            translation.put("locale", target);
            translation.put("key", registerTransactionRequest.getKey());
            translation.put("translatableContentDigest", registerTransactionRequest.getTranslatableContentDigest());
            //判断是否超限
            updateCharsWhenExceedLimit(counter, request.getShopName(), remainingChars, new TranslateRequest(0, null, request.getAccessToken(), source, target, null));

            String targetText;
            TranslateRequest translateRequest = new TranslateRequest(0, request.getShopName(), request.getAccessToken(), source, target, value);
            //判断是否为HTML
            if (registerTransactionRequest.getTarget().equals(HTML) || isHtml(value)) {
                try {
                    targetText = jsoupUtils.translateGlossaryHtml(value, translateRequest, counter, translateContext.getTranslateResource().getResourceType(), keyMap0, keyMap1, translateContext.getLanguagePackId(), translateContext.getRemainingChars());
                    targetText = isHtmlEntity(targetText);
                } catch (Exception e) {
                    shopifyService.saveToShopify(value, translation, resourceId, request);
                    continue;
                }
                shopifyService.saveToShopify(targetText, translation, resourceId, request);
                printTranslation(targetText, value, translation, request.getShopName(), translateContext.getTranslateResource().getResourceType(), resourceId, source);
                continue;
            }

            String finalText;
            //其他数据类型，对数据做处理再翻译
            try {
                //用大模型翻译
                String glossaryString = glossaryText(keyMap1, keyMap0, value);
                //根据关键词生成对应的提示词
                finalText = jsoupUtils.glossaryTranslationModel(translateRequest, counter, glossaryString, translateContext.getLanguagePackId(), translateContext.getRemainingChars());
                addData(request.getTarget(), value, finalText);
                shopifyService.saveToShopify(finalText, translation, resourceId, request);
                printTranslation(finalText, value, translation, request.getShopName(), translateContext.getTranslateResource().getResourceType(), resourceId, source);
            } catch (Exception e) {
                appInsights.trackTrace("glossaryTranslationModel errors " + e);
                shopifyService.saveToShopify(value, translation, resourceId, request);
            }
            if (checkIsStopped(request.getShopName(), counter, request.getTarget(), translateContext.getSource())) {
                return;
            }
        }
    }


    //处理从数据库获取的数据
    private void translateDataByDatabase(List<RegisterTransactionRequest> registerTransactionRequests, TranslateContext translateContext) {
        ShopifyRequest request = translateContext.getShopifyRequest();
        CharacterCountUtils counter = translateContext.getCharacterCountUtils();

        //判断是否停止翻译
        if (checkIsStopped(request.getShopName(), counter, request.getTarget(), translateContext.getSource())) {
            return;
        }

        int remainingChars = translateContext.getRemainingChars();
        String target = request.getTarget();
        Map<String, Object> translation;
        for (RegisterTransactionRequest registerTransactionRequest : registerTransactionRequests) {
            //判断是否停止翻译
            if (checkIsStopped(request.getShopName(), counter, request.getTarget(), translateContext.getSource())) {
                return;
            }

            String value = registerTransactionRequest.getValue();
            String key = registerTransactionRequest.getKey();
            String source = registerTransactionRequest.getLocale();
            String resourceId = registerTransactionRequest.getResourceId();
            String type = registerTransactionRequest.getTarget();
            translation = createTranslationMap(target, registerTransactionRequest);
            updateCharsWhenExceedLimit(counter, request.getShopName(), remainingChars, new TranslateRequest(0, null, request.getAccessToken(), source, target, null));
            //从数据库中获取数据，如果不为空，存入shopify本地；如果为空翻译
            //判断数据类型
            if ("JSON".equals(type)
                    || "JSON_STRING".equals(type)
            ) {
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
                htmlTranslate(translateContext, request, counter, target, value, source, resourceId, translation, null);
                continue;
            }

            //改为判断语言代码方法
            try {
                translateByGoogleOrAI(request, counter, registerTransactionRequest, translation, translateContext.getLanguagePackId(), translateContext.getTranslateResource().getResourceType(), GENERAL, translateContext.getRemainingChars());
            } catch (Exception e) {
                appInsights.trackTrace("accessToken: " + request.getAccessToken() + "，shopName: " + request.getShopName() + "，source: " + registerTransactionRequest.getLocale() + "，target: " + request.getTarget() + "，key: " + key + "，type: " + type + "，value: " + value + ", resourceID: " + registerTransactionRequest.getResourceId() + ", digest: " + registerTransactionRequest.getTranslatableContentDigest());
            }
            if (checkIsStopped(request.getShopName(), counter, request.getTarget(), translateContext.getSource())) {
                return;
            }
        }
    }

    //对html数据处理
    private void translateHtml(List<RegisterTransactionRequest> registerTransactionRequests, TranslateContext translateContext) {
        ShopifyRequest request = translateContext.getShopifyRequest();
        CharacterCountUtils counter = translateContext.getCharacterCountUtils();
        //判断是否停止翻译
        if (checkIsStopped(request.getShopName(), counter, request.getTarget(), translateContext.getSource())) {
            return;
        }

        int remainingChars = translateContext.getRemainingChars();
        String target = request.getTarget();
        for (RegisterTransactionRequest registerTransactionRequest : registerTransactionRequests) {
            //判断是否停止翻译
            if (checkIsStopped(request.getShopName(), counter, request.getTarget(), translateContext.getSource())) {
                return;
            }
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
                TranslateRequest translateRequest = new TranslateRequest(0, request.getShopName(), request.getAccessToken(), source, target, value);
                htmlTranslation = liquidHtmlTranslatorUtils.translateNewHtml(value, translateRequest, counter, translateContext.getLanguagePackId(), translateContext.getRemainingChars(), null, null, null);
            } catch (Exception e) {
                appInsights.trackTrace("html translation errors : " + e.getMessage());
                shopifyService.saveToShopify(value, translation, resourceId, request);
                continue;
            }
            shopifyService.saveToShopify(htmlTranslation, translation, resourceId, request);
            printTranslation(htmlTranslation, value, translation, request.getShopName(), translateContext.getTranslateResource().getResourceType(), resourceId, source);
            if (checkIsStopped(request.getShopName(), counter, request.getTarget(), translateContext.getSource())) {
                return;
            }
        }
    }

    //对不同的数据使用不同的翻译api
    private void translateDataByAPI(List<RegisterTransactionRequest> registerTransactionRequests,
                                    TranslateContext translateContext) {
        ShopifyRequest request = translateContext.getShopifyRequest();
        CharacterCountUtils counter = translateContext.getCharacterCountUtils();

        int remainingChars = translateContext.getRemainingChars();
        String target = request.getTarget();
        if (checkIsStopped(request.getShopName(), counter, request.getTarget(), translateContext.getSource())) {
            return;
        }
        for (RegisterTransactionRequest registerTransactionRequest : registerTransactionRequests) {
            if (checkIsStopped(request.getShopName(), counter, request.getTarget(), translateContext.getSource())) {
                return;
            }
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
                translateByGoogleOrAI(request, counter, registerTransactionRequest, translation, translateContext.getLanguagePackId(), translateContext.getTranslateResource().getResourceType(), GENERAL, translateContext.getRemainingChars());
            } catch (Exception e) {
                appInsights.trackTrace("translateDataByAPI errors : " + e.getMessage());
//                translationCounterService.updateUsedCharsByShopName(new TranslationCounterRequest(0, request.getShopName(), 0, counter.getTotalChars(), 0, 0, 0));
                shopifyService.saveToShopify(value, translation, resourceId, request);
            }
            if (checkIsStopped(request.getShopName(), counter, request.getTarget(), translateContext.getSource())) {
                return;
            }
        }
    }

    //从缓存和数据库中获取数据
    public boolean translateDataByCacheAndDatabase(ShopifyRequest request, String value, Map<String, Object> translation, String resourceId, String target, String source) {
        //获取缓存数据
        String targetCache = translateSingleLine(value, request.getTarget());
        if (targetCache != null) {
            targetCache = isHtmlEntity(targetCache);
            shopifyService.saveToShopify(targetCache, translation, resourceId, request);
            printTranslation(targetCache, value, translation, request.getShopName(), "Cache", resourceId, source);
            return true;
        }
        //255字符以内才从数据库中获取数据
        if (value.length() > 255) {
            return false;
        }
        String targetText = null;
        try {
            targetText = vocabularyService.getTranslateTextDataInVocabulary(target, value, source);
        } catch (Exception e) {
            //打印错误信息
            appInsights.trackTrace("translateDataByDatabase errors : " + e.getMessage());
        }
        if (targetText != null) {
            targetText = isHtmlEntity(targetText);
            addData(target, value, targetText);
            shopifyService.saveToShopify(targetText, translation, resourceId, request);
            printTranslation(targetText, value, translation, request.getShopName(), "Database", resourceId, source);
            return true;
        }
        return false;
    }

    //用AI翻译
    public void translateByGoogleOrAI(ShopifyRequest request, CharacterCountUtils counter,
                                      RegisterTransactionRequest registerTransactionRequest,
                                      Map<String, Object> translation, String languagePackId,
                                      String resourceType, String translateType, Integer limitChars) {

        String value = registerTransactionRequest.getValue();
        String source = registerTransactionRequest.getLocale();
        String targetString = null;
        try {
            targetString = jsoupUtils.translateAndCount(new TranslateRequest(0, request.getShopName(), request.getAccessToken(), registerTransactionRequest.getLocale(), request.getTarget(), value), counter, languagePackId, translateType, limitChars);
        } catch (ClientException e) {
            appInsights.trackTrace("翻译失败： " + e.getMessage() + " ，继续翻译");
        }

        if (targetString == null) {
            appInsights.trackTrace("翻译失败后的字符： " + registerTransactionRequest);
            shopifyService.saveToShopify(value, translation, registerTransactionRequest.getResourceId(), request);
            return;
        }
        if (!translateType.equals(HANDLE)) {
            addData(request.getTarget(), value, targetString);
        }

        shopifyService.saveToShopify(targetString, translation, registerTransactionRequest.getResourceId(), request);
        printTranslation(targetString, value, translation, request.getShopName(), resourceType, registerTransactionRequest.getResourceId(), source);
        //判断是否是handle再决定是否存到数据库中
        try {
            if (translateType.equals(HANDLE)) {
                return;
            }
            // 255字符以内 和 数据库内有该数据类型 文本才能插入数据库
            vocabularyService.InsertOne(request.getTarget(), targetString, registerTransactionRequest.getLocale(), value);
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
                                   String resourceType, Map<String, TranslateTextDO> translatableContentMap, Map<String, Object> glossaryMap
            , Boolean handleFlag, String shopName) {
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
                if (value.matches("\\p{Zs}+")) {
                    continue;
                }
                // 匹配仅包含不可见字符，例如 ZWSP、ZWJ、ZWNJ 等
                // 如果去除所有空白和不可见字符后为空
                if (value.isEmpty() || value.replaceAll("[^\\u200B]", "").length() == value.length()) {
                    continue;
                }
            } catch (Exception e) {
                appInsights.trackTrace("失败的原因： " + e.getMessage());
                continue;
            }

            //如果translatableContentMap里面有该key则不翻译，没有则翻译
            if (translatableContentMap.containsKey(key)) {
                Boolean outdated = translatableContentMap.get(key).getOutdated();
                if (Boolean.FALSE.equals(outdated)) {
                    continue;
                }
            }

            //如果包含相对路径则跳过
            if ("FILE_REFERENCE".equals(type) || "LINK".equals(type)
                    || "LIST_FILE_REFERENCE".equals(type) || "LIST_LINK".equals(type)
                    || "LIST_URL".equals(type)
                    || "JSON".equals(type)
                    || "JSON_STRING".equals(type)
            ) {
                continue;
            }

            if (type.equals(URI) && "handle".equals(key)) {
                if (handleFlag) {
                    judgeData.get(HANDLE).add(new RegisterTransactionRequest(shopName, null, locale, key, value, translatableContentDigest, resourceId, null));
                    continue;
                }
                continue;
            }

            //通用的不翻译数据
            if (!generalTranslate(key, value)) {
                continue;
            }

            if (PRODUCT_OPTION.equals(resourceType) && "color".equalsIgnoreCase(value) || "size".equalsIgnoreCase(value)) {
                continue;
            }

            //如果是theme模块的数据
            if (TRANSLATABLE_RESOURCE_TYPES.contains(resourceType)) {
                //如果是html放html文本里面
                if (isHtml(value)) {
                    judgeData.get(HTML).add(new RegisterTransactionRequest(shopName, null, locale, key, value, translatableContentDigest, resourceId, null));
                    continue;
                }

                //对key中含section和general的做key值判断
                if (GENERAL_OR_SECTION_PATTERN.matcher(key).find()) {
                    //进行白名单的确认
                    if (whiteListTranslate(key)) {
                        judgeData.get(PLAIN_TEXT).add(new RegisterTransactionRequest(shopName, null, locale, key, value, translatableContentDigest, resourceId, null));
                        continue;
                    }

                    //如果包含对应key和value，则跳过
                    if (!shouldTranslate(key, value)) {
                        continue;
                    }
                }
            }

            //对METAOBJECT字段翻译
            if (resourceType.equals(METAOBJECT)) {
                if (isJson(value)) {
                    continue;
                }
            }

            //对METAFIELD字段翻译
            if (resourceType.equals(METAFIELD)) {
                //如UXxSP8cSm，UgvyqJcxm。有大写字母和小写字母的组合。有大写字母，小写字母和数字的组合。 10位 字母和数字不翻译
                if (SUSPICIOUS_PATTERN.matcher(value).matches() || SUSPICIOUS2_PATTERN.matcher(value).matches()) {
                    printTranslateReason(value + " 如UXxSP8cSm，在METAFIELD模块");
                    continue;
                }
                if (!metaTranslate(value)) {
                    printTranslateReason(value + " 包含top,left,right,bottom，在METAFIELD模块");
                    continue;
                }
                //如果是base64编码的数据，不翻译
                if (BASE64_PATTERN.matcher(value).matches()) {
                    printTranslateReason(value + "是base64编码的数据, key是： " + key);
                    continue;
                }
                judgeData.get(METAFIELD).add(new RegisterTransactionRequest(shopName, null, locale, key, value, translatableContentDigest, resourceId, type));
                continue;
            }

            //对词汇表数据做判断
            //判断词汇表里面是否有数据
            if (!glossaryMap.isEmpty()) {
                boolean success = false;
                for (Map.Entry<String, Object> entry : glossaryMap.entrySet()) {
                    String glossaryKey = entry.getKey();
                    if (containsValue(value, glossaryKey) || containsValueIgnoreCase(value, glossaryKey)) {
                        judgeData.get(GLOSSARY).add(new RegisterTransactionRequest(shopName, null, locale, key, value, translatableContentDigest, resourceId, type));
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
                judgeData.get(DATABASE).add(new RegisterTransactionRequest(shopName, null, locale, key, value, translatableContentDigest, resourceId, type));
                continue;
            }

            //对product和blog的type用AI翻译
            if (isAiTranslateResourceType(resourceType)) {
                if (isHtml(value)) {
                    judgeData.get(HTML).add(new RegisterTransactionRequest(shopName, null, locale, key, value, translatableContentDigest, resourceId, null));
                    continue;
                }
                judgeData.get(OPENAI).add(new RegisterTransactionRequest(shopName, null, locale, key, value, translatableContentDigest, resourceId, type));
                continue;
            }


            //对value进行判断 plainText
            if ("HTML".equals(type) || isHtml(value)) {
                //存放在html的list集合里面
                judgeData.get(HTML).add(new RegisterTransactionRequest(shopName, null, locale, key, value, translatableContentDigest, resourceId, null));
            } else {
                //存放在plainText的list集合里面
                judgeData.get(PLAIN_TEXT).add(new RegisterTransactionRequest(shopName, null, locale, key, value, translatableContentDigest, resourceId, null));
            }
        }

    }

    //判断是否翻译的通用逻辑
    public static boolean translationLogic(String key, String value, String type, String resourceType) {
        //如果包含相对路径则跳过
        if ("FILE_REFERENCE".equals(type) || "LINK".equals(type)
                || "LIST_FILE_REFERENCE".equals(type) || "LIST_LINK".equals(type)
                || type.equals(("LIST_URL"))
                || "JSON".equals(type)
                || "JSON_STRING".equals(type)
        ) {
            return true;
        }

        //通用的不翻译数据
        if (!generalTranslate(key, value)) {
            return true;
        }

        if (PRODUCT_OPTION.equals(resourceType)) {
            return "color".equalsIgnoreCase(value) || "size".equalsIgnoreCase(value);
        }
        return false;
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
        // 使用 computeIfAbsent 原子地初始化 innerMap
        ConcurrentHashMap<String, String> innerMap = SINGLE_LINE_TEXT.computeIfAbsent(
                outerKey, key -> new ConcurrentHashMap<>()
        );

        // 安全地放入 innerMap
        innerMap.put(innerKey, value);
    }

    //达到字符限制，更新用户剩余字符数，终止循环
    public void updateCharsWhenExceedLimit(CharacterCountUtils counter, String shopName, int remainingChars, TranslateRequest translateRequest) {
        TranslationCounterRequest request = new TranslationCounterRequest();
        request.setShopName(shopName);

        if (counter.getTotalChars() >= remainingChars) {
            //获取用户当前的token，
            translatesService.updateTranslateStatus(shopName, 3, translateRequest.getTarget(), translateRequest.getSource(), translateRequest.getAccessToken());
//            translationCounterService.updateUsedCharsByShopName(new TranslationCounterRequest(0, shopName, 0, counter.getTotalChars(), 0, 0, 0));
            throw new ClientException(CHARACTER_LIMIT);
        }
    }

    //修改getTestQuery里面的testQuery，用获取后的的查询语句进行查询
    public JsonNode fetchNextPage(TranslateResourceDTO translateResource, ShopifyRequest request) {
        CloudServiceRequest cloudServiceRequest = TypeConversionUtils.shopifyToCloudServiceRequest(request);
        String query = new ShopifyRequestBody().getAfterQuery(translateResource);
        cloudServiceRequest.setBody(query);

        return getShopifyJsonNode(request, cloudServiceRequest, query);
    }

    //递归处理下一页数据
    private void translateNextPage(TranslateContext translateContext, CharacterCountUtils usedCharCounter) {
        JsonNode nextPageData;
        try {
            nextPageData = fetchNextPage(translateContext.getTranslateResource(), translateContext.getShopifyRequest());
            if (nextPageData == null) {
                return;
            }
        } catch (Exception e) {
            return;
        }
        // 重新开始翻译流程
        translateSingleLineTextFieldsRecursively(nextPageData, translateContext);
        // 判断数据库的token数是否发生改变，如果改变，将新的和旧的counter做对比，如果一致
//        judgeCounterByOldAndNew(usedCharCounter, translateContext.getShopifyRequest().getShopName(), translateContext.getCharacterCountUtils());  // 递归处理下一页数据

        // 递归处理下一页数据
        handlePagination(nextPageData, translateContext, usedCharCounter);
    }

    // 将翻译后的数据存储到数据库中
//    @Async
    public void saveTranslatedData(String objectData, ShopifyRequest request, TranslateResourceDTO translateResourceDTO) {
//        appInsights.trackTrace("现在存储到： " + translateResourceDTO.getResourceType());
        JsonNode rootNode;
        try {
            rootNode = OBJECT_MAPPER.readTree(objectData);
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
            Map<String, TranslateTextDO> translationsMap = extractTranslations(node, resourceId, request.getShopName());
            translatableContentMap = extractTranslatableContent(node, translationsMap);
//            appInsights.trackTrace("合并后的map数据为： " + translatableContentMap);

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
    public static Map<String, TranslateTextDO> extractTranslations(JsonNode node, String resourceId, String shopName) {
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
                translateTextDO.setShopName(shopName);
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

    //翻译单个文本数据
    public String translateSingleText(RegisterTransactionRequest request) {
        TranslateRequest translateRequest = TypeConversionUtils.registerTransactionRequestToTranslateRequest(request);
        request.setValue(getGoogleTranslationWithRetry(translateRequest));
        //保存翻译后的数据到shopify本地
        Map<String, Object> variables = getVariables(request);
        ShopifyRequest shopifyRequest = convertTranslateRequestToShopifyRequest(translateRequest);
        return registerTransaction(shopifyRequest, variables);
    }


    //将数据存入本地Map中
    //优化策略1： 利用翻译后的数据，对singleLine的数据全局匹配并翻译
    public String translateSingleLine(String sourceText, String target) {
        if (SINGLE_LINE_TEXT.get(target) != null) {
            return SINGLE_LINE_TEXT.get(target).get(sourceText);
        }
        return null;
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
            try {
                vocabularyService.storeTranslationsInVocabulary(list);
            } catch (Exception e) {
                appInsights.trackTrace("存储失败： " + e.getMessage() + " ，继续翻译");
            }
        }
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
     * 单条文本翻译，判断是否在翻译逻辑里面，是否额度充足，扣额度，返回翻译后的文本
     */
    public BaseResponse<Object> singleTextTranslate(SingleTranslateVO singleTranslateVO) {
        //判断是否为空
        String value = singleTranslateVO.getContext();
        if (value == null) {
            return new BaseResponse<>().CreateErrorResponse(NOT_TRANSLATE);
        }
        //判断额度是否足够
        String shopName = singleTranslateVO.getShopName();
        TranslationCounterDO request1 = translationCounterService.readCharsByShopName(shopName);
        Integer remainingChars = translationCounterService.getMaxCharsByShopName(shopName);
        int usedChars = request1.getUsedChars();
        // 如果字符超限，则直接返回字符超限
        if (usedChars >= remainingChars) {
            return new BaseResponse<>().CreateErrorResponse(CHARACTER_LIMIT);
        }

        //根据模块判断是否翻译
        String key = singleTranslateVO.getKey();
        String source = singleTranslateVO.getSource();
        String target = singleTranslateVO.getTarget();
        String resourceType = singleTranslateVO.getResourceType();
        String type = singleTranslateVO.getType();


        //获取当前翻译token数
        CharacterCountUtils counter = new CharacterCountUtils();
        counter.addChars(usedChars);
        if (type.equals(URI) && "handle".equals(key)) {
            // 如果 key 为 "handle"，这里是要处理的代码
            String targetString = jsoupUtils.translateAndCount(new TranslateRequest(0, shopName, null, source, target, value), counter, null, HANDLE, remainingChars);
            appInsights.trackTrace(shopName + " 用户 ，" + value + " 单条翻译 handle模块： " + value + "消耗token数： " + (counter.getTotalChars() - usedChars) + "target为： " + targetString);
            return new BaseResponse<>().CreateSuccessResponse(targetString);
        }

        //开始翻译,判断是普通文本还是html文本
        try {
            if (isHtml(value)) {
                //单条翻译html，修改格式
                if (resourceType.equals(METAFIELD)) {
//                    String htmlTranslation = liquidHtmlTranslatorUtils.translateNewHtml(value, new TranslateRequest(0, shopName, null, source, target, value), counter, null, remainingChars, null, null, null);
                    String htmlTranslation = liquidHtmlTranslatorUtils.fullTranslateHtmlByQwen(value, "General", counter, target, shopName, remainingChars, "1", source);
                    htmlTranslation = normalizeHtml(htmlTranslation);
                    appInsights.trackTrace(shopName + " 用户 ，" + value + "HTML 单条翻译 消耗token数： " + (counter.getTotalChars() - usedChars) + "target为： " + htmlTranslation);
                    return new BaseResponse<>().CreateSuccessResponse(htmlTranslation);
                }
//                String htmlTranslation = liquidHtmlTranslatorUtils.translateNewHtml(value, new TranslateRequest(0, shopName, null, source, target, value), counter, null, remainingChars, null, null, null);
                String htmlTranslation = liquidHtmlTranslatorUtils.fullTranslateHtmlByQwen(value, "General", counter, target, shopName, remainingChars, "1", source);
                appInsights.trackTrace(shopName + " 用户 ，" + value + " HTML 单条翻译 消耗token数： " + (counter.getTotalChars() - usedChars) + "target为： " + htmlTranslation);
                return new BaseResponse<>().CreateSuccessResponse(htmlTranslation);
            } else {
                String targetString = jsoupUtils.translateAndCount(new TranslateRequest(0, shopName, null, source, target, value), counter, null, GENERAL, remainingChars);
                appInsights.trackTrace(shopName + " 用户 ，" + " 单条翻译： " + value + "消耗token数： " + (counter.getTotalChars() - usedChars) + "target为： " + targetString);
                return new BaseResponse<>().CreateSuccessResponse(targetString);
            }
        } catch (Exception e) {
            appInsights.trackTrace("singleTranslate errors : " + e.getMessage());
            appInsights.trackException(e);
        }

        return new BaseResponse<>().CreateErrorResponse(value);
    }

    /**
     * 用户shopify和数据库同步的方法
     */
    public void syncShopifyAndDatabase(TranslateRequest request) {
        //获取用户数据
        CloudServiceRequest cloudServiceRequest = TypeConversionUtils.translateRequestToCloudServiceRequest(request);
        ShopifyRequest shopifyRequest = TypeConversionUtils.convertTranslateRequestToShopifyRequest(request);
        String query = getLanguagesQuery();
        cloudServiceRequest.setBody(query);
        String shopifyData = null;
        try {
            String env = System.getenv("ApplicationEnv");
            if ("prod".equals(env) || "dev".equals(env)) {
                shopifyData = String.valueOf(getInfoByShopify(shopifyRequest, query));
            } else {
                shopifyData = getShopifyDataByCloud(cloudServiceRequest);
            }
        } catch (Exception e) {
            // 如果出现异常，则跳过, 翻译其他的内容
            //更新当前字符数
            appInsights.trackTrace("syncShopifyAndDatabase Failed to get Shopify data errors : " + e.getMessage());
        }

        //分析获取到的数据，然后存储到list集合里面
        JsonNode root = null;
        try {
            root = OBJECT_MAPPER.readTree(shopifyData);
        } catch (JsonProcessingException e) {
            appInsights.trackTrace("syncShopifyAndDatabase Failed to parse Shopify data errors : " + e.getMessage());
            return;
        }

        JsonNode shopLocales = root.path("shopLocales");
        if (shopLocales.isArray()) {
            for (JsonNode node : shopLocales) {
                String locale = node.path("locale").asText(null);
                if (locale != null) {
                    //存储到数据库中
                    translatesService.insertShopTranslateInfoByShopify(shopifyRequest, locale, request.getSource());
                }
            }
        }
    }

    /**
     * 循环遍历数据库中是否有该条数据
     */
    public void isExistInDatabase(String shopName, ClickTranslateRequest clickTranslateRequest, TranslateRequest request) {
        for (String target : clickTranslateRequest.getTarget()
        ) {
            request.setTarget(target);
            TranslatesDO one = translatesService.getOne(new QueryWrapper<TranslatesDO>().eq("shop_name", shopName).eq("source", clickTranslateRequest.getSource()).eq("target", request.getTarget()));
            if (one == null) {
                //走同步逻辑
                syncShopifyAndDatabase(request);
            }
        }
    }

    public Map<String, Integer> getProgressData(String shopName, String target, String source) {
        Map<String, Integer> progressData = new HashMap<>();
        AtomicReference<Integer> other = new AtomicReference<>(0);
        //从数据库中升序获取所有的同shopName和target对应的数据
        List<TranslateTasksDO> list = translateTasksService.list(new LambdaQueryWrapper<TranslateTasksDO>()
                .eq(TranslateTasksDO::getShopName, shopName)
                .eq(TranslateTasksDO::getStatus, 0)
                .like(TranslateTasksDO::getPayload, "\"target\":\"" + target + "\"")
                .orderByAsc(TranslateTasksDO::getCreatedAt));
        if (list.isEmpty()) {
            //根据用户当前的模块从静态数据做判断
            TranslatesDO translatesDO = translatesService.getOne(new LambdaQueryWrapper<TranslatesDO>().eq(TranslatesDO::getShopName, shopName).eq(TranslatesDO::getTarget, target).eq(TranslatesDO::getSource, source));
            if (translatesDO == null){
                appInsights.trackTrace("getProgressData 用户： " + shopName + " target: " + target + " 数据库中不存在该条数据");
                return null;
            }
            if (translatesDO.getResourceType() == null) {
                appInsights.trackTrace("getProgressData 用户： " + shopName + " target: " + target + " 数据库中该条数据的resourceType数据为null");
                progressData.put("RemainingQuantity", 0);
                progressData.put("TotalQuantity", 0);
                return progressData;
            }
            return getProgressBar(translatesDO.getResourceType());
        }

        for (TranslateTasksDO task : list) {
            other.getAndSet(other.get() + 1);
            if (task.getPayload().contains("\"shopifyData\":\"EMAIL\"")){
                break;
            }
        }
        progressData.put("RemainingQuantity", other.get());
        progressData.put("TotalQuantity", list.get(0).getAllTasks());
        appInsights.trackTrace("getProgressData 用户： " + shopName + " target: " + target + " 正在翻译的进度条： " + progressData);
        return progressData;
    }

    public String imageTranslate(String sourceCode, String targetCode, String imageUrl, String shopName, String accessToken) {
        appInsights.trackTrace("imageTranslate 用户 " + shopName + " sourceCode " + sourceCode + " targetCode " + targetCode + " imageUrl " + imageUrl + " accessToken " + accessToken);
        //获取用户token，判断是否和数据库中一致再选择是否调用
        UsersDO usersDO = iUsersService.getOne(new LambdaQueryWrapper<UsersDO>().eq(UsersDO::getShopName, shopName));
        if (!usersDO.getAccessToken().equals(accessToken)) {
            return null;
        }

        //获取用户最大额度限制
        Integer maxCharsByShopName = iTranslationCounterService.getMaxCharsByShopName(shopName);
        //调用图片翻译方法
        return aLiYunTranslateIntegration.callWithPic(sourceCode, targetCode, imageUrl, shopName, maxCharsByShopName);
    }
}

