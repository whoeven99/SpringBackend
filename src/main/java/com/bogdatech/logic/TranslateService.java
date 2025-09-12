package com.bogdatech.logic;


import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.bogdatech.Service.*;
import com.bogdatech.entity.DO.*;
import com.bogdatech.entity.VO.SingleTranslateVO;
import com.bogdatech.integration.ALiYunTranslateIntegration;
import com.bogdatech.integration.RedisIntegration;
import com.bogdatech.model.controller.request.*;
import com.bogdatech.model.controller.response.BaseResponse;
import com.bogdatech.requestBody.ShopifyRequestBody;
import com.bogdatech.utils.CharacterCountUtils;
import com.bogdatech.utils.JsoupUtils;
import com.bogdatech.utils.LiquidHtmlTranslatorUtils;
import com.bogdatech.utils.TypeConversionUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static com.bogdatech.constants.TranslateConstants.*;
import static com.bogdatech.integration.ShopifyHttpIntegration.getInfoByShopify;
import static com.bogdatech.integration.ShopifyHttpIntegration.registerTransaction;
import static com.bogdatech.integration.TranslateApiIntegration.getGoogleTranslationWithRetry;
import static com.bogdatech.logic.RabbitMqTranslateService.getShopifyJsonNode;
import static com.bogdatech.logic.ShopifyService.getShopifyDataByCloud;
import static com.bogdatech.logic.ShopifyService.getVariables;
import static com.bogdatech.requestBody.ShopifyRequestBody.getLanguagesQuery;
import static com.bogdatech.utils.CaseSensitiveUtils.*;
import static com.bogdatech.utils.JsoupUtils.*;
import static com.bogdatech.utils.JudgeTranslateUtils.*;
import static com.bogdatech.utils.ProgressBarUtils.getProgressBar;
import static com.bogdatech.utils.RedisKeyUtils.RedisKeyUtil.*;
import static com.bogdatech.utils.StringUtils.normalizeHtml;
import static com.bogdatech.utils.TypeConversionUtils.convertTranslateRequestToShopifyRequest;

@Component
@EnableAsync
public class TranslateService {
    @Autowired
    private ITranslatesService translatesService;
    @Autowired
    private ITranslationCounterService translationCounterService;
    @Autowired
    private IVocabularyService vocabularyService;
    @Autowired
    private LiquidHtmlTranslatorUtils liquidHtmlTranslatorUtils;
    @Autowired
    private JsoupUtils jsoupUtils;
    @Autowired
    private ITranslateTasksService translateTasksService;
    @Autowired
    private IUsersService iUsersService;
    @Autowired
    private ALiYunTranslateIntegration aLiYunTranslateIntegration;
    @Autowired
    private ITranslationCounterService iTranslationCounterService;
    @Autowired
    private RedisProcessService redisProcessService;

    public static ConcurrentHashMap<String, ConcurrentHashMap<String, String>> SINGLE_LINE_TEXT = new ConcurrentHashMap<>();
    public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    //判断是否可以终止翻译流程
    public static ConcurrentHashMap<String, Future<?>> userTasks = new ConcurrentHashMap<>(); // 存储每个用户的翻译任务
    public static ConcurrentHashMap<String, AtomicBoolean> userStopFlags = new ConcurrentHashMap<>(); // 存储每个用户的停止标志
    public static ConcurrentHashMap<String, Map<String, Object>> userTranslate = new ConcurrentHashMap<>(); // 存储每个用户的翻译设置
    public static ConcurrentHashMap<String, Map<String, Object>> beforeUserTranslate = new ConcurrentHashMap<>(); // 存储每个用户的翻译设置
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

    //google翻译接口
    public String googleTranslate(TranslateRequest request) {
        return getGoogleTranslationWithRetry(request);
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


    public static Map<String, Object> createTranslationMap(String target, String key, String translatableContentDigest) {
        Map<String, Object> translation = new HashMap<>();
        translation.put("locale", target);
        translation.put("key", key);
        translation.put("translatableContentDigest", translatableContentDigest);
        return translation;
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

    //将翻译后的数据放入内存中
    public static void addData(String outerKey, String innerKey, String value) {
        // 使用 computeIfAbsent 原子地初始化 innerMap
        ConcurrentHashMap<String, String> innerMap = SINGLE_LINE_TEXT.computeIfAbsent(
                outerKey, key -> new ConcurrentHashMap<>()
        );

        // 安全地放入 innerMap
        innerMap.put(innerKey, value);
    }

    //修改getTestQuery里面的testQuery，用获取后的的查询语句进行查询
    public JsonNode fetchNextPage(TranslateResourceDTO translateResource, ShopifyRequest request) {
        CloudServiceRequest cloudServiceRequest = TypeConversionUtils.shopifyToCloudServiceRequest(request);
        String query = new ShopifyRequestBody().getAfterQuery(translateResource);
        cloudServiceRequest.setBody(query);

        return getShopifyJsonNode(request, cloudServiceRequest, query);
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

        //获取用户数据库翻译状态，如果是已完成，返回1/1
        TranslatesDO translatesServiceOne = translatesService.getOne(new LambdaQueryWrapper<TranslatesDO>().eq(TranslatesDO::getShopName, shopName).eq(TranslatesDO::getTarget, target).eq(TranslatesDO::getSource, source));
        if (translatesServiceOne != null && translatesServiceOne.getStatus() == 1) {
            progressData.put("RemainingQuantity", 1);
            progressData.put("TotalQuantity", 1);
            return progressData;
        }
        //从redis中获取当前用户正在翻译的进度条数据
        Object total = redisProcessService.getFieldProcessData(generateProcessKey(shopName, target), PROGRESS_TOTAL);
        Object done = redisProcessService.getFieldProcessData(generateProcessKey(shopName, target), PROGRESS_DONE);
        if (total == null || done == null) {
            //根据用户当前的模块从静态数据做判断
            TranslatesDO translatesDO = translatesService.getOne(new LambdaQueryWrapper<TranslatesDO>().eq(TranslatesDO::getShopName, shopName).eq(TranslatesDO::getTarget, target).eq(TranslatesDO::getSource, source));
            if (translatesDO == null) {
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

        progressData.put("RemainingQuantity", (Integer) total - (Integer) done);
        progressData.put("TotalQuantity", (Integer) total);
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

