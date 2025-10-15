package com.bogdatech.logic;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.bogdatech.Service.*;
import com.bogdatech.entity.DO.*;
import com.bogdatech.entity.VO.SingleTranslateVO;
import com.bogdatech.integration.ALiYunTranslateIntegration;
import com.bogdatech.logic.redis.TranslationMonitorRedisService;
import com.bogdatech.logic.redis.TranslationParametersRedisService;
import com.bogdatech.mapper.InitialTranslateTasksMapper;
import com.bogdatech.model.controller.request.*;
import com.bogdatech.model.controller.response.BaseResponse;
import com.bogdatech.requestBody.ShopifyRequestBody;
import com.bogdatech.utils.CharacterCountUtils;
import com.bogdatech.utils.JsoupUtils;
import com.bogdatech.utils.LiquidHtmlTranslatorUtils;
import com.bogdatech.utils.TypeConversionUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static com.bogdatech.constants.TranslateConstants.*;
import static com.bogdatech.entity.DO.TranslateResourceDTO.TOKEN_MAP;
import static com.bogdatech.integration.ShopifyHttpIntegration.getInfoByShopify;
import static com.bogdatech.integration.ShopifyHttpIntegration.registerTransaction;
import static com.bogdatech.integration.TranslateApiIntegration.getGoogleTranslationWithRetry;
import static com.bogdatech.logic.RabbitMqTranslateService.CLICK_EMAIL;
import static com.bogdatech.logic.ShopifyService.getShopifyDataByCloud;
import static com.bogdatech.logic.ShopifyService.getVariables;
import static com.bogdatech.logic.redis.TranslationParametersRedisService.TRANSLATION_STATUS;
import static com.bogdatech.logic.redis.TranslationParametersRedisService.generateProgressTranslationKey;
import static com.bogdatech.requestBody.ShopifyRequestBody.getLanguagesQuery;
import static com.bogdatech.utils.CaseSensitiveUtils.*;
import static com.bogdatech.utils.JsonUtils.objectToJson;
import static com.bogdatech.utils.JsoupUtils.*;
import static com.bogdatech.utils.ProgressBarUtils.getProgressBar;
import static com.bogdatech.utils.RedisKeyUtils.*;
import static com.bogdatech.utils.ShopifyUtils.getShopifyByQuery;
import static com.bogdatech.utils.StringUtils.normalizeHtml;
import static com.bogdatech.utils.TypeConversionUtils.ClickTranslateRequestToTranslateRequest;
import static com.bogdatech.utils.TypeConversionUtils.convertTranslateRequestToShopifyRequest;

@Component
@EnableAsync
public class TranslateService {
    @Autowired
    private ITranslatesService translatesService;
    @Autowired
    private ITranslationCounterService translationCounterService;
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
    @Autowired
    private TranslationParametersRedisService translationParametersRedisService;
    @Autowired
    private InitialTranslateTasksMapper initialTranslateTasksMapper;
    @Autowired
    private TranslationMonitorRedisService translationMonitorRedisService;

    public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    // 判断是否可以终止翻译流程
    public static ConcurrentHashMap<String, Future<?>> userTasks = new ConcurrentHashMap<>(); // 存储每个用户的翻译任务
//    public static ConcurrentHashMap<String, AtomicBoolean> userStopFlags = new ConcurrentHashMap<>(); // 存储每个用户的停止标志

    // 使用 ConcurrentHashMap 存储每个用户的邮件发送状态
    public static ConcurrentHashMap<String, AtomicBoolean> userEmailStatus = new ConcurrentHashMap<>();
    public static ExecutorService executorService = Executors.newFixedThreadPool(10);

    // TODO 所有翻译的总入口 目前只有手动翻译
    public BaseResponse<Object> createInitialTask(ClickTranslateRequest request) {
        // 手动点击翻译 则需要 shopName, source, target, resourceTypes
        // 自动翻译, 则需要 shopName, source, target
        // 同样的，私有key翻译也走这里，做一次分发

        appInsights.trackTrace("clickTranslation : " + request);
        String shopName = request.getShopName();
        // 判断前端传的数据是否完整，如果不完整，报错
        if (shopName == null || shopName.isEmpty()
                || request.getAccessToken() == null || request.getAccessToken().isEmpty()
                || request.getSource() == null || request.getSource().isEmpty()
                || request.getTarget() == null || request.getTarget().length == 0) {
            return new BaseResponse<>().CreateErrorResponse("Missing parameters");
        }

        // TODO: 暂时使所有用户的customKey失效
        request.setCustomKey(null);

        // 判断字符是否超限
        TranslationCounterDO counterDO = translationCounterService.readCharsByShopName(shopName);
        Integer remainingChars = translationCounterService.getMaxCharsByShopName(shopName);
        appInsights.trackTrace("clickTranslation 判断字符是否超限 : " + shopName);

        // 如果字符超限，则直接返回字符超限
        if (counterDO.getUsedChars() >= remainingChars) {
            return new BaseResponse<>().CreateErrorResponse("Cannot translate because the character limit has been reached. Please upgrade your plan to continue translating.");
        }
        appInsights.trackTrace("clickTranslation 判断字符不超限 : " + shopName);

        // 一个用户当前只能翻译一条语言，根据用户的status判断
        appInsights.trackTrace("clickTranslation 判断用户是否有语言在翻译 : " + shopName);
        // TODO 这个判断是不是要去掉了，还是怎么处理好一些
        List<Integer> integers = translatesService.readStatusInTranslatesByShopName(shopName);
        if (integers.contains(2)) {
            return new BaseResponse<>().CreateErrorResponse(HAS_TRANSLATED);
        }

        // 判断是否有 handle 模块
        boolean handleFlag = false;
        // TODO 这个前后端的字段名字，重新换一个
        List<String> translateResourceTypesList = request.getTranslateSettings3();
        if (translateResourceTypesList.contains("handle")) {
            translateResourceTypesList.removeIf("handle"::equals);
            handleFlag = true;
        }

        appInsights.trackTrace("clickTranslation " + shopName + " 用户现在开始翻译 要翻译的数据 " + request.getTranslateSettings3()
                + " handleFlag: " + handleFlag + " isCover: " + request.getIsCover());

        // 修改模块的排序
        List<String> translateResourceDTOS = translateResourceTypesList.stream()
                .map(TOKEN_MAP::get)
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .map(TranslateResourceDTO::getResourceType)
                .toList();
        appInsights.trackTrace("clickTranslation 修改模块的排序成功 : " + shopName);

        if (org.springframework.util.CollectionUtils.isEmpty(translateResourceDTOS)) {
            return new BaseResponse<>().CreateErrorResponse("Please select the module to be translated");
        }

        // 将ClickTranslateRequest转换为TranslateRequest
        TranslateRequest translateRequest = ClickTranslateRequestToTranslateRequest(request);

        // 循环同步 判断用户的语言是否在数据库中，在不做操作，不在，进行同步
        // TODO 这个其实是帮用户添加语言到shopify，也可以再task里面异步去做
        this.isExistInDatabase(shopName, request, translateRequest);
        appInsights.trackTrace("clickTranslation 循环同步 : " + shopName);

        // 存翻译参数到db
        ShopifyRequest shopifyRequest = convertTranslateRequestToShopifyRequest(translateRequest);
        appInsights.trackTrace("mqTranslateWrapper开始: " + shopName);

        boolean isCover = request.getIsCover();
        String customKey = request.getCustomKey();
        String[] targets = request.getTarget();
        String source = translateRequest.getSource();

        // 重置用户发送的邮件
        userEmailStatus.put(shopName, new AtomicBoolean(false));

        // 初始化用户的停止标志
        Boolean stopFlag = translationParametersRedisService.delStopTranslationKey(shopName);
        appInsights.trackTrace("mqTranslateWrapper 用户: " + shopName + " 初始化用户的停止标志: " + stopFlag);

        // 修改自定义提示词
        String cleanedText = null;
        if (customKey != null) {
            cleanedText = customKey.replaceAll("\\.{2,}", ".");
        }
        appInsights.trackTrace("修改自定义提示词 : " + shopifyRequest.getShopName());

        // 改为循环遍历，将相关target状态改为2
        List<String> listTargets = Arrays.asList(targets);
        listTargets.forEach(target -> {
            translatesService.updateTranslateStatus(
                    shopName,
                    2,
                    target,
                    source,
                    translateRequest.getAccessToken()
            );
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                appInsights.trackException(e);
            }
        });

        appInsights.trackTrace("修改相关target状态改为2 : " + shopifyRequest.getShopName());

        // 将模块数据List类型转化为Json类型
        String resourceToJson = objectToJson(translateResourceDTOS);
        appInsights.trackTrace("将模块数据List类型转化为Json类型 : " + shopifyRequest.getShopName() + " resourceToJson: " + resourceToJson);

        // 将上一次initial表中taskType为 click的数据逻辑删除
        try {
            initialTranslateTasksMapper.selectList(new LambdaQueryWrapper<InitialTranslateTasksDO>().eq(InitialTranslateTasksDO::getSource, source).eq(InitialTranslateTasksDO::getShopName, shopName).eq(InitialTranslateTasksDO::getTaskType, CLICK_EMAIL).eq(InitialTranslateTasksDO::isDeleted, false)).forEach(initialTranslateTasksDO -> {
                initialTranslateTasksMapper.update(new LambdaUpdateWrapper<InitialTranslateTasksDO>().eq(InitialTranslateTasksDO::getTaskId, initialTranslateTasksDO.getTaskId()).set(InitialTranslateTasksDO::isDeleted, true));
                appInsights.trackTrace("mqTranslateWrapper 用户: " + shopName + " 删除一个任务");
            });
        } catch (Exception e) {
            appInsights.trackTrace("mqTranslateWrapper 用户: " + shopName + " 删除一个任务失败");
            appInsights.trackException(e);
        }

        for (String target : targets) {
            appInsights.trackTrace("MQ翻译开始: " + target + " shopName: " + shopifyRequest.getShopName());
            translationParametersRedisService.hsetTranslatingModule(generateProgressTranslationKey(shopName, source, target), "");
            translationParametersRedisService.hsetTranslationStatus(generateProgressTranslationKey(shopName, source, target), String.valueOf(1));
            translationParametersRedisService.hsetTranslatingString(generateProgressTranslationKey(shopName, source, target), "");
            translationParametersRedisService.hsetProgressNumber(generateProgressTranslationKey(shopName, source, target), generateProcessKey(shopName, target));
            redisProcessService.initProcessData(generateProcessKey(shopName, target));

            // 将翻译项中的模块改为null
            translatesService.update(new LambdaUpdateWrapper<TranslatesDO>().eq(TranslatesDO::getShopName, shopName)
                    .eq(TranslatesDO::getSource, source)
                    .eq(TranslatesDO::getTarget, target)
                    .set(TranslatesDO::getResourceType, null));

            translateRequest.setTarget(target);
            shopifyRequest.setTarget(target);

            // 将线管参数存到数据库中
            InitialTranslateTasksDO initialTranslateTasksDO = new InitialTranslateTasksDO(null, 0, translateRequest.getSource(), target, isCover, request.getTranslateSettings1(), request.getTranslateSettings2(), resourceToJson, cleanedText, request.getShopName(), handleFlag, CLICK_EMAIL, Timestamp.valueOf(LocalDateTime.now()), false);
            try {
                appInsights.trackTrace("将手动翻译参数存到数据库中");
                int insert = initialTranslateTasksMapper.insert(initialTranslateTasksDO);
                appInsights.trackTrace("将手动翻译参数存到数据库后： " + insert);

                // Monitor 记录shop开始的时间（中国区时间）
                String chinaTime = ZonedDateTime.now(ZoneId.of("Asia/Shanghai")).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                translationMonitorRedisService.hsetStartTranslationAt(shopName, chinaTime);
            } catch (Exception e) {
                appInsights.trackTrace("mqTranslateWrapper 每日须看 用户: " + shopName + " 存入数据库失败" + initialTranslateTasksDO);
                appInsights.trackException(e);
            }
        }

        // TODO 把request返回去的意义是什么？
        return new BaseResponse<>().CreateSuccessResponse(request);
    }

    /**
     * 手动停止用户的翻译任务
     * */
    public String stopTranslationManually(String shopName) {
        Boolean stopFlag = translationParametersRedisService.setStopTranslationKey(shopName);
        if (stopFlag) {
            appInsights.trackTrace("stopTranslationManually 用户 " + shopName + " 的翻译标识存储成功");

            //将Task表DB中的status改为5
            translateTasksService.updateStatusAllTo5ByShopName(shopName);

            // 将翻译状态改为“部分翻译” shopName, status=3
            translatesService.updateStatusByShopNameAnd2(shopName);

            Future<?> future = userTasks.get(shopName);

            // 目前能用到的就是私有key
            if (future != null && !future.isDone()) {
                // 中断正在执行的任务
                future.cancel(true);
            }

            return "stopTranslationManually 翻译任务已停止 用户 " + shopName + " 的翻译任务已停止";
        }
        return "已经有停止标识， 需要删除后再次停止";
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

    //翻译单个文本数据
    public String translateSingleText(RegisterTransactionRequest request) {
        TranslateRequest translateRequest = TypeConversionUtils.registerTransactionRequestToTranslateRequest(request);
        request.setValue(getGoogleTranslationWithRetry(translateRequest));
        //保存翻译后的数据到shopify本地
        Map<String, Object> variables = getVariables(request);
        ShopifyRequest shopifyRequest = convertTranslateRequestToShopifyRequest(translateRequest);
        return registerTransaction(shopifyRequest, variables);
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
            if (targetString == null) {
                return new BaseResponse<>().CreateErrorResponse(value);
            }
            appInsights.trackTrace(shopName + " 用户 ，" + value + " 单条翻译 handle模块： " + value + "消耗token数： " + (counter.getTotalChars() - usedChars) + "target为： " + targetString);
            return new BaseResponse<>().CreateSuccessResponse(targetString);
        }

        //开始翻译,判断是普通文本还是html文本
        try {
            if (isHtml(value)) {
                TranslateRequest translateRequest = new TranslateRequest(0, shopName, null, source, target, value);
                //单条翻译html，修改格式
                if (resourceType.equals(METAFIELD)) {
                    String htmlTranslation = liquidHtmlTranslatorUtils.newJsonTranslateHtml(value, translateRequest, counter, null, remainingChars);
                    htmlTranslation = normalizeHtml(htmlTranslation);
                    appInsights.trackTrace(shopName + " 用户 ，" + value + "HTML 单条翻译 消耗token数： " + (counter.getTotalChars() - usedChars) + "target为： " + htmlTranslation);
                    return new BaseResponse<>().CreateSuccessResponse(htmlTranslation);
                }

                String htmlTranslation = liquidHtmlTranslatorUtils.newJsonTranslateHtml(value, translateRequest, counter, null, remainingChars);
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
        // 获取用户数据
        CloudServiceRequest cloudServiceRequest = TypeConversionUtils.translateRequestToCloudServiceRequest(request);
        ShopifyRequest shopifyRequest = TypeConversionUtils.convertTranslateRequestToShopifyRequest(request);
        String query = getLanguagesQuery();
        cloudServiceRequest.setBody(query);
        String shopifyData;
        JsonNode root;
        try {
            shopifyData = getShopifyByQuery(query, request.getShopName(), request.getAccessToken());
            root = OBJECT_MAPPER.readTree(shopifyData);
        } catch (Exception e) {
            appInsights.trackException(e);
            appInsights.trackTrace("syncShopifyAndDatabase Failed to get Shopify data errors : " + e.getMessage());
            return;
        }

        // 分析获取到的数据，然后存储到list集合里面
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
        for (String target : clickTranslateRequest.getTarget()) {
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

        // 获取用户数据库翻译状态，如果是已完成，返回100%进度
        TranslatesDO translatesServiceOne = translatesService.getOne(new LambdaQueryWrapper<TranslatesDO>().eq(TranslatesDO::getShopName, shopName).eq(TranslatesDO::getTarget, target).eq(TranslatesDO::getSource, source));
        if (translatesServiceOne != null && translatesServiceOne.getStatus() == 1) {
            progressData.put("RemainingQuantity", 0);
            progressData.put("TotalQuantity", 1);
            return progressData;
        }

        // 获取userTranslate是否是写入状态，是的话翻译100%
        Map<Object, Object> value = translationParametersRedisService.getProgressTranslationKey(generateProgressTranslationKey(shopName, source, target));
        if (CollectionUtils.isEmpty(value) || "3".equals(value.get(TRANSLATION_STATUS))) {
            progressData.put("RemainingQuantity", 0);
            progressData.put("TotalQuantity", 1);
            return progressData;
        }

        //从redis中获取当前用户正在翻译的进度条数据
        String total = redisProcessService.getFieldProcessData(generateProcessKey(shopName, target), PROGRESS_TOTAL);
        String done = redisProcessService.getFieldProcessData(generateProcessKey(shopName, target), PROGRESS_DONE);
        if (total == null || done == null || "null".equals(total) || "null".equals(done)) {
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
        int totalInt = Integer.parseInt(total);
        int doneInt = Integer.parseInt(done);
        if (doneInt > totalInt) {
            progressData.put("RemainingQuantity", 1);
        } else {
            progressData.put("RemainingQuantity", totalInt - doneInt);
        }

        progressData.put("TotalQuantity", totalInt);
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

