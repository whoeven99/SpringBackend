package com.bogdatech.logic;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.bogdatech.Service.*;
import com.bogdatech.entity.DO.*;
import com.bogdatech.entity.VO.RabbitMqTranslateVO;
import com.bogdatech.exception.ClientException;
import com.bogdatech.integration.ALiYunTranslateIntegration;
import com.bogdatech.integration.RedisIntegration;
import com.bogdatech.model.controller.request.*;
import com.bogdatech.requestBody.ShopifyRequestBody;
import com.bogdatech.utils.CharacterCountUtils;
import com.bogdatech.utils.JsoupUtils;
import com.bogdatech.utils.LiquidHtmlTranslatorUtils;
import com.bogdatech.utils.TypeConversionUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import static com.bogdatech.constants.TranslateConstants.*;
import static com.bogdatech.entity.DO.TranslateResourceDTO.ALL_RESOURCES;
import static com.bogdatech.enums.ErrorEnum.SHOPIFY_RETURN_ERROR;
import static com.bogdatech.integration.ShopifyHttpIntegration.getInfoByShopify;
import static com.bogdatech.logic.ShopifyService.getShopifyDataByCloud;
import static com.bogdatech.logic.TranslateService.*;
import static com.bogdatech.utils.ApiCodeUtils.getLanguageName;
import static com.bogdatech.utils.CaseSensitiveUtils.*;
import static com.bogdatech.utils.JsonUtils.objectToJson;
import static com.bogdatech.utils.JsonUtils.stringToJson;
import static com.bogdatech.utils.JsoupUtils.*;
import static com.bogdatech.utils.LiquidHtmlTranslatorUtils.isHtmlEntity;
import static com.bogdatech.utils.ListUtils.convertALL;
import static com.bogdatech.utils.ListUtils.sort;
import static com.bogdatech.utils.MapUtils.getTranslationStatusMap;
import static com.bogdatech.utils.PlaceholderUtils.getListPrompt;
import static com.bogdatech.utils.PrintUtils.printTranslation;
import static com.bogdatech.utils.RedisKeyUtils.*;
import static com.bogdatech.utils.RegularJudgmentUtils.isValidString;
import static com.bogdatech.utils.StringUtils.normalizeHtml;

@Service
@EnableAsync
public class RabbitMqTranslateService {
    @Autowired
    private ITranslationCounterService translationCounterService;
    @Autowired
    private ITranslatesService translatesService;
    @Autowired
    private ShopifyService shopifyService;
    @Autowired
    private IVocabularyService vocabularyService;
    @Autowired
    private TencentEmailService tencentEmailService;
    @Autowired
    private GlossaryService glossaryService;
    @Autowired
    private AILanguagePackService aiLanguagePackService;
    @Autowired
    private JsoupUtils jsoupUtils;
    @Autowired
    private LiquidHtmlTranslatorUtils liquidHtmlTranslatorUtils;
    @Autowired
    private ITranslateTasksService translateTasksService;
    @Autowired
    private TaskScheduler taskScheduler;
    @Autowired
    private UserTypeTokenService userTypeTokenService;
    @Autowired
    private RedisProcessService redisProcessService;
    @Autowired
    private ALiYunTranslateIntegration aLiYunTranslateIntegration;
    @Autowired
    private RedisIntegration redisIntegration;
    public static final int BATCH_SIZE = 50;

    /**
     * 加一层包装MQ翻译，用于自动翻译按顺序添加任务
     */
    @Async
    public void mqTranslateWrapper(ShopifyRequest shopifyRequest, CharacterCountUtils counter, List<String> translateResourceDTOS, TranslateRequest request, int limitChars, int usedChars, boolean handleFlag, String translationModel, boolean isCover, String customKey, boolean emailType, String[] targets) {
        for (String target : targets
        ) {
            appInsights.trackTrace("MQ翻译开始: " + target + " shopName: " + shopifyRequest.getShopName());
            request.setTarget(target);
            shopifyRequest.setTarget(target);
            mqTranslate(shopifyRequest, counter, translateResourceDTOS, request, limitChars, usedChars, handleFlag, translationModel, isCover, customKey, emailType);
        }

    }

    /**
     * MQ翻译
     * 读取该用户的shopify数据，然后循环获取模块数据
     * 根据翻译类型选择不同的邮件发送方式: true-> 手动； false-> 自动
     *
     * @param shopifyRequest        shopify请求参数
     * @param counter               字符计数器
     * @param translateResourceDTOS 翻译模块顺序
     * @param request               翻译请求参数
     * @param limitChars            限制字符
     * @param usedChars             已使用字符
     * @param handleFlag            是否handle翻译
     * @param translationModel      翻译模型
     * @param isCover               是否覆盖
     * @param customKey             自定义key
     * @param emailType             邮件类型
     */
    public void mqTranslate(ShopifyRequest shopifyRequest, CharacterCountUtils counter, List<String> translateResourceDTOS, TranslateRequest request, int limitChars, int usedChars, boolean handleFlag, String translationModel, boolean isCover, String customKey, boolean emailType) {
        //判断是否有同义词
        Map<String, Object> glossaryMap = new HashMap<>();
        glossaryService.getGlossaryByShopName(shopifyRequest, glossaryMap);
        appInsights.trackTrace("判断是否有同义词 " + shopifyRequest.getShopName());
        //获取目前所使用的AI语言包
        String languagePackId = aiLanguagePackService.getCategoryByDescription(shopifyRequest.getShopName(), shopifyRequest.getAccessToken(), counter, limitChars);
        appInsights.trackTrace("获取目前所使用的AI语言包 " + shopifyRequest.getShopName());
        RabbitMqTranslateVO rabbitMqTranslateVO = new RabbitMqTranslateVO(null, shopifyRequest.getShopName(), shopifyRequest.getAccessToken(), request.getSource(), request.getTarget(), languagePackId, handleFlag, glossaryMap, null, limitChars, usedChars, LocalDateTime.now().toString(), translateResourceDTOS, translationModel, isCover, customKey);
        CharacterCountUtils allTasks = new CharacterCountUtils();
        redisProcessService.initProcessData(generateProcessKey(shopifyRequest.getShopName(), shopifyRequest.getTarget()));
        appInsights.trackTrace("初始化进度条数据 " + shopifyRequest.getShopName());
        for (TranslateResourceDTO translateResource : ALL_RESOURCES
        ) {
            appInsights.trackTrace("初始化进度条数据 " + shopifyRequest.getShopName());
            rabbitMqTranslateVO.setModeType(translateResource.getResourceType());
            if (!translateResourceDTOS.contains(translateResource.getResourceType())) {
                continue;
            }
            appInsights.trackTrace("判断模块是否存在 " + shopifyRequest.getShopName());
            if (translateResource.getResourceType().equals(PAYMENT_GATEWAY)) {
                continue;
            }
            appInsights.trackTrace("网关不翻译 " + shopifyRequest.getShopName());
            if (EXCLUDED_SHOPS.contains(request.getShopName()) && PRODUCT_MODEL.contains(translateResource.getResourceType())) {
                continue;
            }
            appInsights.trackTrace("一些shop模块不翻译 " + shopifyRequest.getShopName());
            // 定期检查是否停止
            if (checkNeedStopped(request.getShopName(), counter)) {
                return;
            }
            appInsights.trackTrace("用户翻译不停止 " + shopifyRequest.getShopName());
            translateResource.setTarget(request.getTarget());
            String shopifyData = getShopifyData(shopifyRequest, translateResource);
            appInsights.trackTrace("用户shopify翻译数据 " + shopifyRequest.getShopName());
            rabbitMqTranslateVO.setShopifyData(shopifyData);
            rabbitMqTranslateVO.setModeType(translateResource.getResourceType());
            String query = new ShopifyRequestBody().getFirstQuery(translateResource);

            try {
                RabbitMqTranslateVO dbTranslateVO = new RabbitMqTranslateVO().copy(rabbitMqTranslateVO);
                dbTranslateVO.setShopifyData(query);
                String json = OBJECT_MAPPER.writeValueAsString(dbTranslateVO);
                translateTasksService.save(new TranslateTasksDO(null, 0, json, rabbitMqTranslateVO.getShopName(), null, null));
                appInsights.trackTrace("保存用户翻译数据到db " + shopifyRequest.getShopName());
                allTasks.addChars(1);
            } catch (Exception e) {
                appInsights.trackTrace("clickTranslation 保存翻译任务失败 errors : " + e);
                appInsights.trackException(e);
            }
            // 改为将请求数据存储到数据库中
            parseShopifyData(rabbitMqTranslateVO, translateResource, allTasks);
        }
        rabbitMqTranslateVO.setTranslateList(translateResourceDTOS);
        //当模块都发送后，发送邮件模块
        appInsights.trackTrace("存邮件数据 " + rabbitMqTranslateVO.getShopName());
        sendEmailTranslate(rabbitMqTranslateVO, emailType, allTasks);
    }

    /**
     * 当模块都发送完后，发送邮件模块
     *
     * @Param rabbitMqTranslateVO MQ翻译参数
     * @Param emailType 邮件类型
     * 根据emailType填入不同的邮件类型
     */
    public void sendEmailTranslate(RabbitMqTranslateVO rabbitMqTranslateVO, Boolean emailType, CharacterCountUtils allTasks) {
        //邮件相关参数
        if (emailType) {
            rabbitMqTranslateVO.setShopifyData(EMAIL);
        } else {
            rabbitMqTranslateVO.setShopifyData(EMAIL_AUTO);
            rabbitMqTranslateVO.setCustomKey(EMAIL_TRANSLATE);
        }

        try {
            String json = OBJECT_MAPPER.writeValueAsString(rabbitMqTranslateVO);
            translateTasksService.save(new TranslateTasksDO(null, 0, json, rabbitMqTranslateVO.getShopName(), null, null));
            //计数前面的子任务项，并修改allTasks的字段
            allTasks.addChars(1);
            //修改之前的所有allTasks的字段
            translateTasksService.update(new LambdaUpdateWrapper<TranslateTasksDO>().eq(TranslateTasksDO::getShopName, rabbitMqTranslateVO.getShopName()).set(TranslateTasksDO::getAllTasks, allTasks.getTotalChars()));
            //将进度条状态改为2
            Map<String, Object> translationStatusMap = getTranslationStatusMap("Searching for content to translate…", 2);
            userTranslate.put(rabbitMqTranslateVO.getShopName(), translationStatusMap);
        } catch (Exception e) {
            appInsights.trackTrace("clickTranslation 保存翻译任务失败 errors : " + e);
        }
    }


    /**
     * 解析shopifyData的数据，递归获取，每250条数据作为一个翻译任务发送到队列里面
     */
    public void parseShopifyData(RabbitMqTranslateVO rabbitMqTranslateVO, TranslateResourceDTO translateResource, CharacterCountUtils allTasks) {
        JsonNode rootNode;
        try {
            rootNode = OBJECT_MAPPER.readTree(rabbitMqTranslateVO.getShopifyData());
            appInsights.trackTrace("解析数据 " + rabbitMqTranslateVO.getShopName());
        } catch (JsonProcessingException e) {
            appInsights.trackTrace("clickTranslation rootNode errors： " + e.getMessage());
            return;
        }

        translateNextPage(rootNode, translateResource, rabbitMqTranslateVO, allTasks);
    }

    //获取下一页数据
    public void translateNextPage(JsonNode rootNode, TranslateResourceDTO translateContext, RabbitMqTranslateVO rabbitMqTranslateVO, CharacterCountUtils allTasks) {
        // 获取translatableResources节点
        JsonNode translatableResourcesNode = rootNode.path("translatableResources");

        // 获取nodes节点，再获取translatableContent节点，计数里面的value个数
        countValue(translatableResourcesNode, rabbitMqTranslateVO.getShopName(), rabbitMqTranslateVO.getTarget());
        appInsights.trackTrace("统计完所有进度条数据  " + rabbitMqTranslateVO.getShopName());
        // 获取pageInfo节点
        JsonNode pageInfoNode = translatableResourcesNode.path("pageInfo");
        if (translatableResourcesNode.hasNonNull("pageInfo")) {
            if (pageInfoNode.hasNonNull("hasNextPage") && pageInfoNode.get("hasNextPage").asBoolean()) {
                JsonNode endCursor = pageInfoNode.path("endCursor");
                translateContext.setAfter(endCursor.asText(null));
                translateNextPageData(translateContext, rabbitMqTranslateVO, allTasks);
            }
        }
    }

    /**
     * 根据shopify返回的字段，统计里面的value值
     */
    public void countValue(JsonNode translatableResourcesNode, String shopName, String target) {
        JsonNode nodes = translatableResourcesNode.path("nodes");
        if (nodes == null) {
            return;
        }
        appInsights.trackTrace("统计所有进度条数据  " + shopName);
        for (JsonNode node : nodes) {
            JsonNode translatableContent = node.path("translatableContent");
            redisProcessService.addProcessData(generateProcessKey(shopName, target), PROGRESS_TOTAL, (long) translatableContent.size());
        }
    }


    //递归处理下一页数据
    private void translateNextPageData(TranslateResourceDTO translateContext, RabbitMqTranslateVO rabbitMqTranslateVO, CharacterCountUtils allTasks) {
        JsonNode nextPageData;
        try {
            nextPageData = fetchNextPage(translateContext, new ShopifyRequest(rabbitMqTranslateVO.getShopName(), rabbitMqTranslateVO.getAccessToken(), APIVERSION, rabbitMqTranslateVO.getTarget()), rabbitMqTranslateVO, allTasks);
            if (nextPageData == null) {
                return;
            }
        } catch (Exception e) {
            return;
        }
        rabbitMqTranslateVO.setShopifyData(nextPageData.toString());
        // 重新开始翻译流程
        parseShopifyData(rabbitMqTranslateVO, translateContext, allTasks);
    }

    public JsonNode fetchNextPage(TranslateResourceDTO translateResource, ShopifyRequest request, RabbitMqTranslateVO rabbitMqTranslateVO, CharacterCountUtils allTasks) {
        CloudServiceRequest cloudServiceRequest = TypeConversionUtils.shopifyToCloudServiceRequest(request);
        String query = new ShopifyRequestBody().getAfterQuery(translateResource);
        //将请求数据存数据库中
        try {
            RabbitMqTranslateVO dbTranslateVO = new RabbitMqTranslateVO().copy(rabbitMqTranslateVO);
            dbTranslateVO.setShopifyData(query);
            String json = OBJECT_MAPPER.writeValueAsString(dbTranslateVO);
            translateTasksService.save(new TranslateTasksDO(null, 0, json, rabbitMqTranslateVO.getShopName(), null, null));
            appInsights.trackTrace("保存用户shopify到db  " + request.getShopName());
            allTasks.addChars(1);
        } catch (Exception e) {
            appInsights.trackTrace("clickTranslation fetchNextPage 保存翻译任务失败 errors " + e);
        }
        cloudServiceRequest.setBody(query);
        return getShopifyJsonNode(request, cloudServiceRequest, query);
    }

    public static JsonNode getShopifyJsonNode(ShopifyRequest request, CloudServiceRequest cloudServiceRequest, String query) {
        String env = System.getenv("ApplicationEnv");
        String infoByShopify;
        if ("prod".equals(env) || "dev".equals(env)) {
            infoByShopify = String.valueOf(getInfoByShopify(request, query));
        } else {
            infoByShopify = getShopifyDataByCloud(cloudServiceRequest);
        }

        try {
            if (infoByShopify == null || infoByShopify.isEmpty()) {
                return null;
            }
            return OBJECT_MAPPER.readTree(infoByShopify);
        } catch (JsonProcessingException e) {
            throw new ClientException(SHOPIFY_RETURN_ERROR.getErrMsg());
        }
    }

    /**
     * 根据模块选择翻译方法
     */
    public void translateByModeType(RabbitMqTranslateVO rabbitMqTranslateVO, CharacterCountUtils countUtils) {
        String modelType = rabbitMqTranslateVO.getModeType();
        appInsights.trackTrace("clickTranslation DB翻译模块：" + modelType + " 用户 ： " + rabbitMqTranslateVO.getShopName() + " targetCode ：" + rabbitMqTranslateVO.getTarget() + " source : " + rabbitMqTranslateVO.getSource());

        //根据DB的请求语句获取对应shopify值
        String shopifyDataByDb = getShopifyDataByDb(rabbitMqTranslateVO);
        if (shopifyDataByDb == null) {
            appInsights.trackTrace("clickTranslation " + rabbitMqTranslateVO.getShopName() + " shopifyDataByDb is null" + rabbitMqTranslateVO);
            return;
        }
        Set<TranslateTextDO> needTranslatedData = jsoupUtils.translatedDataParse(stringToJson(shopifyDataByDb), rabbitMqTranslateVO.getShopName(), rabbitMqTranslateVO.getIsCover(), rabbitMqTranslateVO.getTarget());
        if (needTranslatedData == null) {
            return;
        }
        Set<TranslateTextDO> filterTranslateData = jsoupUtils.filterNeedTranslateSet(rabbitMqTranslateVO.getModeType(), rabbitMqTranslateVO.getHandleFlag(), needTranslatedData, rabbitMqTranslateVO.getShopName(), rabbitMqTranslateVO.getTarget());
        //将筛选好的数据分类
        Map<String, Set<TranslateTextDO>> stringSetMap = filterTranslateMap(initTranslateMap(), filterTranslateData, rabbitMqTranslateVO.getGlossaryMap());
        //实现功能： 分析三种类型数据， 添加模块标识，开始翻译
        if (stringSetMap.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Set<TranslateTextDO>> entry : stringSetMap.entrySet()) {
            switch (entry.getKey()) {
                case HTML:
                    translateHtmlData(entry.getValue(), rabbitMqTranslateVO, countUtils);
                    break;
                case PLAIN_TEXT:
                case TITLE:
                case META_TITLE:
                case LOWERCASE_HANDLE:
                    translatePlainTextData(entry.getValue(), rabbitMqTranslateVO, countUtils, entry.getKey());
                    break;
                case LIST_SINGLE:
                    translateListSingleData(entry.getValue(), rabbitMqTranslateVO, countUtils);
                    break;
                case GLOSSARY:
                    translateGlossaryData(entry.getValue(), rabbitMqTranslateVO, countUtils);
                    break;
                default:
                    break;
            }
        }
    }

    /**
     * 根据从数据库获取的数据，请求shopify获取数据
     */
    public String getShopifyDataByDb(RabbitMqTranslateVO rabbitMqTranslateVO) {
        CloudServiceRequest cloudServiceRequest = new CloudServiceRequest(rabbitMqTranslateVO.getShopName(), rabbitMqTranslateVO.getAccessToken(), APIVERSION, rabbitMqTranslateVO.getTarget(), null);
        cloudServiceRequest.setBody(rabbitMqTranslateVO.getShopifyData());
        ShopifyRequest shopifyRequest = new ShopifyRequest(rabbitMqTranslateVO.getShopName(), rabbitMqTranslateVO.getAccessToken(), APIVERSION, rabbitMqTranslateVO.getTarget());
        String shopifyData = null;
        try {
            String env = System.getenv("ApplicationEnv");
            if ("prod".equals(env) || "dev".equals(env)) {
                shopifyData = String.valueOf(getInfoByShopify(shopifyRequest, rabbitMqTranslateVO.getShopifyData()));
            } else {
                shopifyData = getShopifyDataByCloud(cloudServiceRequest);
            }
        } catch (Exception e) {
            // 如果出现异常，则跳过, 翻译其他的内容
            //更新当前字符数
            appInsights.trackTrace("clickTranslation " + rabbitMqTranslateVO.getShopName() + " Failed to get Shopify data errors : " + e.getMessage());
        }
        return shopifyData;
    }


    /**
     * 同一个resourceId下的获取所有数据
     */
    public static Map<String, TranslateTextDO> extractTranslationsByResourceId(JsonNode shopDataJson, String resourceId, String shopName) {
        Map<String, TranslateTextDO> translations = new HashMap<>();
        JsonNode translationsNode = shopDataJson.path("translatableContent");
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
                String key = translation.path("key").asText(null);
                translateTextDO.setTextKey(key);
                translateTextDO.setSourceText(translation.path("value").asText(null));
                translateTextDO.setSourceCode(translation.path("locale").asText(null));
                translateTextDO.setDigest(translation.path("digest").asText(null));
                translateTextDO.setTextType(translation.path("type").asText(null));
                translateTextDO.setResourceId(resourceId);
                translateTextDO.setShopName(shopName);
                translations.put(key, translateTextDO);
            });
        }
        return translations;
    }


    /**
     * 初始化map集合
     */
    public static Map<String, Set<TranslateTextDO>> initTranslateMap() {
        Map<String, Set<TranslateTextDO>> judgeData = new HashMap<>();
        judgeData.put(HTML, new HashSet<>());
        judgeData.put(PLAIN_TEXT, new HashSet<>());
        judgeData.put(GLOSSARY, new HashSet<>());
        //对于不同key值，存不同的Set里面
        judgeData.put(TITLE, new HashSet<>());
        judgeData.put(META_TITLE, new HashSet<>());
        judgeData.put(LOWERCASE_HANDLE, new HashSet<>());
        //对元字段的LIST_SINGLE_LINE_TEXT_FIELD数据单独处理
        judgeData.put(LIST_SINGLE, new HashSet<>());

        return judgeData;
    }


    /**
     * 遍历筛选后的数据，然后将数据分为词汇表数据，普通文本，html文本
     */
    public static Map<String, Set<TranslateTextDO>> filterTranslateMap(Map<String, Set<TranslateTextDO>> stringSetMap, Set<TranslateTextDO> filterTranslateData, Map<String, Object> glossaryMap) {
        if (filterTranslateData == null || filterTranslateData.isEmpty()) {
            return stringSetMap;
        }
        for (TranslateTextDO translateTextDO : filterTranslateData
        ) {
            String value = translateTextDO.getSourceText();
            String key = translateTextDO.getTextKey();
            String textType = translateTextDO.getTextType();
            //判断是否是词汇表数据
            if (!glossaryMap.isEmpty()) {
                boolean success = false;
                for (Map.Entry<String, Object> entry : glossaryMap.entrySet()) {
                    String glossaryKey = entry.getKey();
                    if (containsValue(value, glossaryKey) || containsValueIgnoreCase(value, glossaryKey)) {
                        stringSetMap.get(GLOSSARY).add(translateTextDO);
                        success = true;
                        break;
                    }
                }
                if (success) {
                    continue;
                }
            }

            //判断是html还是普通文本
            if (isHtml(value)) {
                stringSetMap.get(HTML).add(translateTextDO);
                continue;
            }
            switch (key) {
                case TITLE -> stringSetMap.get(TITLE).add(translateTextDO);
                case META_TITLE -> stringSetMap.get(META_TITLE).add(translateTextDO);
                case LOWERCASE_HANDLE -> stringSetMap.get(LOWERCASE_HANDLE).add(translateTextDO);
                default -> {
                    if (textType.equals(LIST_SINGLE)) {
                        stringSetMap.get(LIST_SINGLE).add(translateTextDO);
                    } else {
                        stringSetMap.get(PLAIN_TEXT).add(translateTextDO);
                    }
                }
            }
        }
        return stringSetMap;
    }

    /**
     * HTML文本翻译
     */
    public void translateHtmlData(Set<TranslateTextDO> htmlData, RabbitMqTranslateVO rabbitMqTranslateVO, CharacterCountUtils counter) {
        String shopName = rabbitMqTranslateVO.getShopName();
        String target = rabbitMqTranslateVO.getTarget();
        Integer limitChars = rabbitMqTranslateVO.getLimitChars();
        String accessToken = rabbitMqTranslateVO.getAccessToken();
        ShopifyRequest shopifyRequest = new ShopifyRequest(shopName, accessToken, API_VERSION_LAST, target);
        for (TranslateTextDO translateTextDO : htmlData) {
            //根据模块选择翻译方法，先做普通翻译
            //判断是否停止翻译
            if (checkNeedStopped(rabbitMqTranslateVO.getShopName(), counter)) {
                return;
            }
            appInsights.trackTrace("解析数据 用户： " + rabbitMqTranslateVO.getShopName());
            String value = translateTextDO.getSourceText();
            String resourceId = translateTextDO.getResourceId();
            String source = rabbitMqTranslateVO.getSource();
            String key = translateTextDO.getTextKey();
            String digest = translateTextDO.getDigest();
            Map<String, Object> translation = createTranslationMap(target, key, digest);

            //判断是否达到额度限制
            appInsights.trackTrace("判断是否达到额度限制 用户： " + rabbitMqTranslateVO.getShopName());
            updateCharsWhenExceedLimit(counter, shopName, limitChars, new TranslateRequest(0, shopName, accessToken, source, target, null));

            //开始翻译
            //缓存翻译和数据库翻译
            if (cacheOrDatabaseTranslateData(value, source, translation, resourceId, shopifyRequest)) {
                continue;
            }
            //html翻译
            translateGeneralHtmlData(translateTextDO, rabbitMqTranslateVO, counter, translation, shopifyRequest);
            appInsights.trackTrace("html翻译完 用户： " + rabbitMqTranslateVO.getShopName());
            //翻译进度条加1
            //判断当翻译停止后就不加了
            appInsights.trackTrace("翻译进度条加1 用户： " + rabbitMqTranslateVO.getShopName());
            checkNeedAddProcessData(shopName, target);
        }
    }

    /**
     * 翻译停止后，进度条就不加了
     */
    public void checkNeedAddProcessData(String shopName, String target) {
        if (userStopFlags.get(shopName).get()) {
            redisProcessService.addProcessData(generateProcessKey(shopName, target), PROGRESS_DONE, 1L);
        }
    }

    /**
     * 通用html翻译
     */
    public void translateGeneralHtmlData(TranslateTextDO translateTextDO, RabbitMqTranslateVO rabbitMqTranslateVO, CharacterCountUtils counter, Map<String, Object> translation, ShopifyRequest shopifyRequest) {
        String sourceText = translateTextDO.getSourceText();
        String htmlTranslation;
        String resourceId = translateTextDO.getResourceId();
        String source = rabbitMqTranslateVO.getSource();
        try {
            appInsights.trackTrace("定义translateRequest 用户： " + rabbitMqTranslateVO.getShopName() + "，sourceText: " + sourceText);
            TranslateRequest translateRequest = new TranslateRequest(0, rabbitMqTranslateVO.getShopName(), rabbitMqTranslateVO.getAccessToken(), source, rabbitMqTranslateVO.getTarget(), translateTextDO.getSourceText());
            //判断产品模块用完全翻译，其他模块用分段html翻译
            Map<String, Object> translationStatusMap = getTranslationStatusMap(sourceText, 2);
            userTranslate.put(rabbitMqTranslateVO.getShopName(), translationStatusMap);
            appInsights.trackTrace("修改进度条的数据 用户： " + rabbitMqTranslateVO.getShopName() + "，sourceText: " + sourceText);
            htmlTranslation = liquidHtmlTranslatorUtils.newJsonTranslateHtml(sourceText, translateRequest, counter, rabbitMqTranslateVO.getLanguagePack(), rabbitMqTranslateVO.getLimitChars());
            appInsights.trackTrace("完成翻译html 用户： " + rabbitMqTranslateVO.getShopName() + "，sourceText: " + sourceText);
            if (rabbitMqTranslateVO.getModeType().equals(METAFIELD)) {
                //对翻译后的html做格式处理
                appInsights.trackTrace("html所在模块是METAFIELD 用户： " + rabbitMqTranslateVO.getShopName() + "，sourceText: " + sourceText);
                htmlTranslation = normalizeHtml(htmlTranslation);
            }

        } catch (Exception e) {
            appInsights.trackTrace("clickTranslation " + shopifyRequest.getShopName() + " html translation errors : " + e.getMessage() + " sourceText: " + sourceText);
            shopifyService.saveToShopify(sourceText, translation, resourceId, shopifyRequest);
            return;
        }
        appInsights.trackTrace("存到shopify数据到数据库 用户： " + rabbitMqTranslateVO.getShopName() + "，sourceText: " + sourceText);
        shopifyService.saveToShopify(htmlTranslation, translation, resourceId, shopifyRequest);
        printTranslation(htmlTranslation, sourceText, translation, shopifyRequest.getShopName(), rabbitMqTranslateVO.getModeType(), resourceId, source);

        //如果翻译数据小于255，存到数据库
        try {
            appInsights.trackTrace("存到数据库 用户： " + rabbitMqTranslateVO.getShopName() + "，sourceText: " + sourceText);
            if (htmlTranslation.length() >= 255) {
                return;
            }
            // 255字符以内 和 数据库内有该数据类型 文本才能插入数据库
            vocabularyService.InsertOne(rabbitMqTranslateVO.getTarget(), htmlTranslation, source, sourceText);
        } catch (Exception e) {
            appInsights.trackTrace("clickTranslation " + shopifyRequest.getShopName() + "存储失败： " + e.getMessage() + " ，继续翻译");
        }
    }

    /**
     * 判断停止标识
     */
    private boolean checkNeedStopped(String shopName, CharacterCountUtils counter) {
        if (userStopFlags.get(shopName).get()) {
            // 更新数据库中的已使用字符数
            appInsights.trackTrace("checkNeedStopped " + shopName + " 用户 消耗的token ： " + counter.getTotalChars());
//            translationCounterService.updateUsedCharsByShopName(new TranslationCounterRequest(0, shopName, 0, counter.getTotalChars(), 0, 0, 0));
            // 将翻译状态为2改为“部分翻译”
            translatesService.update(new UpdateWrapper<TranslatesDO>().eq("shop_name", shopName).eq("status", 2).set("status", 7));
            // 将task表数据都改为 5
            translateTasksService.updateByShopName(shopName, 5);
            return true;
        }
        return false;
    }

    /**
     * 元字段文本翻译
     */
    public void translateListSingleData(Set<TranslateTextDO> plainTextData, RabbitMqTranslateVO rabbitMqTranslateVO, CharacterCountUtils counter) {
        String shopName = rabbitMqTranslateVO.getShopName();
        String target = rabbitMqTranslateVO.getTarget();
        Integer limitChars = rabbitMqTranslateVO.getLimitChars();
        String accessToken = rabbitMqTranslateVO.getAccessToken();
        ShopifyRequest shopifyRequest = new ShopifyRequest(shopName, accessToken, API_VERSION_LAST, target);
        for (TranslateTextDO translateTextDO : plainTextData) {
            //根据模块选择翻译方法，先做普通翻译
            //判断是否停止翻译
            if (checkNeedStopped(rabbitMqTranslateVO.getShopName(), counter)) {
                return;
            }

            String value = translateTextDO.getSourceText();
            String resourceId = translateTextDO.getResourceId();
            String source = rabbitMqTranslateVO.getSource();
            String key = translateTextDO.getTextKey();
            String digest = translateTextDO.getDigest();
            Map<String, Object> translation = createTranslationMap(target, key, digest);

            //判断是否达到额度限制
            updateCharsWhenExceedLimit(counter, shopName, limitChars, new TranslateRequest(0, shopName, accessToken, source, target, null));

            //开始翻译
            //缓存翻译和数据库翻译
            if (cacheOrDatabaseTranslateData(value, source, translation, resourceId, shopifyRequest)) {
                continue;
            }

            //swicth根据模块类型选择翻译
            try {
                translateMetafieldTextData(translateTextDO, rabbitMqTranslateVO, counter, translation, shopifyRequest);
            } catch (Exception e) {
                appInsights.trackTrace("clickTranslation " + shopName + " value : " + value + " 翻译失败 errors ：" + e.getMessage() + " sourceText: " + value);
            }
        }
    }

    /**
     * 文本翻译
     * 修改翻译的逻辑，暂定获取每50条数据，
     *
     * @param plainTextData       翻译数据
     * @param rabbitMqTranslateVO 翻译参数
     * @param counter             字符计数器
     * @param translationKeyType  翻译类型
     */
    public void translatePlainTextData(Set<TranslateTextDO> plainTextData, RabbitMqTranslateVO rabbitMqTranslateVO, CharacterCountUtils counter, String translationKeyType) {
        if (plainTextData.isEmpty()) {
            return;
        }
        String shopName = rabbitMqTranslateVO.getShopName();
        String target = rabbitMqTranslateVO.getTarget();
        Integer limitChars = rabbitMqTranslateVO.getLimitChars();
        String accessToken = rabbitMqTranslateVO.getAccessToken();
        String source = rabbitMqTranslateVO.getSource();
        String languagePack = rabbitMqTranslateVO.getLanguagePack();
        ShopifyRequest shopifyRequest = new ShopifyRequest(shopName, accessToken, API_VERSION_LAST, target);
        String modelType = rabbitMqTranslateVO.getModeType();
        TranslateRequest translateRequestTemplate = new TranslateRequest(0, shopName, accessToken, source, target, null);
        // 先转成List，方便切片
        List<TranslateTextDO> list = new ArrayList<>(plainTextData);
        for (int i = 0; i < list.size(); i += BATCH_SIZE) {
            if (checkNeedStopped(shopName, counter)) {
                return;
            }

            updateCharsWhenExceedLimit(counter, shopName, limitChars, translateRequestTemplate);

            // 取每次的50条（或剩余全部）
            int endIndex = Math.min(i + BATCH_SIZE, list.size());
            List<TranslateTextDO> batch = list.subList(i, endIndex);
            //筛选出来要翻译的数据
            List<String> untranslatedTexts = collectUntranslatedTexts(batch, source, shopifyRequest, target);
            //TODO：更细致的判断额度是否足够，足够继续，不够返回，开始翻译
            if (untranslatedTexts.isEmpty()) {
                continue;
            }

            //根据不同的key类型，生成对应提示词，后翻译
            String prompt = getListPrompt(getLanguageName(target), languagePack, translationKeyType, modelType);
            appInsights.trackTrace(shopName + " translatePlainTextData 翻译类型 : " + translationKeyType + " 提示词 : " + prompt + " 未翻译文本 : " + untranslatedTexts);
            String translatedJson = translateBatch(translateRequestTemplate, untranslatedTexts, counter, limitChars, prompt);
            //对null的处理
            if (translatedJson == null) {
                String json = objectToJson(untranslatedTexts);
                translatedJson = aLiYunTranslateIntegration.userTranslate(json, prompt, counter, target, shopName, limitChars);
            }
            appInsights.trackTrace("translatePlainTextData " + shopName + " source: " + source + " translatedJson : " + translatedJson);

            // 处理翻译后的数据
            if (translatedJson != null) {
                handleTranslationResults(translatedJson, batch, shopifyRequest, rabbitMqTranslateVO);
            }else {
                // 翻译有问题： 先打印看下情况
                appInsights.trackTrace("每日须看 translatePlainTextData " + shopName + " source: " + source + " translatedJson : " + translatedJson);
            }
        }
    }

    /**
     * 获取最多50条数据，进行翻译
     *
     * @param batch          翻译数据
     * @param source         源语言
     * @param shopifyRequest shopify请求
     * @param target         目标语言
     */
    private List<String> collectUntranslatedTexts(List<TranslateTextDO> batch,
                                                  String source,
                                                  ShopifyRequest shopifyRequest,
                                                  String target) {
        return batch.stream()
                // 只保留那些 “newCacheAndDBTranslateData(...) 返回 false” 的元素
                .filter(t -> !newCacheAndDBTranslateData(source, shopifyRequest, t, target))
                // 取出每个元素的 sourceText 字段
                .map(TranslateTextDO::getSourceText)
                .distinct()
                // 收集成一个 List<String>
                .collect(Collectors.toList());
    }

    /**
     * list翻译
     *
     * @param translateRequest  翻译相关参数
     * @param untranslatedTexts 未翻译文本
     * @param counter           字符计数器
     * @param limitChars        字符限制
     * @param prompt            提示词
     */
    private String translateBatch(TranslateRequest translateRequest,
                                  List<String> untranslatedTexts,
                                  CharacterCountUtils counter,
                                  Integer limitChars,
                                  String prompt) {
        try {
            String json = OBJECT_MAPPER.writeValueAsString(untranslatedTexts);
            return jsoupUtils.translateByCiwiUserModel(translateRequest.getTarget(), json, translateRequest.getShopName(), translateRequest.getSource(), counter, limitChars, prompt);
        } catch (Exception e) {
            appInsights.trackTrace("clickTranslation translateBatch 调用翻译接口失败: " + e.getMessage());
            appInsights.trackException(e);
            return null;
        }
    }

    /**
     * 将翻译后的json文本处理后，存储到DB
     * 缓存和打印
     *
     * @param translatedJson      翻译后的json文本
     * @param batch               批量数据
     * @param shopifyRequest      shopify请求
     * @param rabbitMqTranslateVO rabbitMq翻译参数
     */
    private void handleTranslationResults(String translatedJson,
                                          List<TranslateTextDO> batch,
                                          ShopifyRequest shopifyRequest,
                                          RabbitMqTranslateVO rabbitMqTranslateVO) {
        try {
            Map<String, String> resultMap = OBJECT_MAPPER.readValue(translatedJson, new TypeReference<>() {
            });
            for (TranslateTextDO item : batch) {
                String sourceText = item.getSourceText();
                String targetText = resultMap.get(sourceText);
                if (targetText == null) {
                    appInsights.trackTrace("clickTranslation " + shopifyRequest.getShopName() + " 翻译结果缺失：" + sourceText);
                    continue;
                }
                Map<String, Object> translationStatusMap = getTranslationStatusMap(sourceText, 2);
                userTranslate.put(shopifyRequest.getShopName(), translationStatusMap);
                saveTranslation(targetText, sourceText, item, shopifyRequest, item.getTextType(), rabbitMqTranslateVO);
                //翻译进度条加1
                checkNeedAddProcessData(shopifyRequest.getShopName(), shopifyRequest.getTarget());
            }
        } catch (Exception e) {
            appInsights.trackTrace("clickTranslation 解析翻译结果失败: " + e.getMessage());
            appInsights.trackException(e);
        }
    }

    /**
     * 存储翻译后的单条数据
     *
     * @param targetText          翻译后的文本
     * @param sourceText          原文
     * @param translateTextDO     翻译文本DO
     * @param shopifyRequest      shopify请求
     * @param textType            handle类型
     * @param rabbitMqTranslateVO rabbitMq翻译参数
     */
    private void saveTranslation(String targetText,
                                 String sourceText,
                                 TranslateTextDO translateTextDO,
                                 ShopifyRequest shopifyRequest,
                                 String textType,
                                 RabbitMqTranslateVO rabbitMqTranslateVO) {
        Map<String, Object> translation = createTranslationMap(
                rabbitMqTranslateVO.getTarget(),
                translateTextDO.getTextKey(),
                translateTextDO.getDigest());

        if (!URI.equals(textType)) {
            redisProcessService.setCacheData(shopifyRequest.getTarget(), targetText, sourceText);
        }

        shopifyService.saveToShopify(targetText, translation,
                translateTextDO.getResourceId(), shopifyRequest);

        printTranslation(targetText, sourceText, translation,
                rabbitMqTranslateVO.getShopName(),
                rabbitMqTranslateVO.getModeType(),
                translateTextDO.getResourceId(),
                rabbitMqTranslateVO.getSource());

        try {
            if (!URI.equals(textType)) {
                vocabularyService.InsertOne(rabbitMqTranslateVO.getTarget(),
                        targetText, rabbitMqTranslateVO.getSource(), sourceText);
            }
        } catch (Exception e) {
            appInsights.trackTrace("clickTranslation 存储翻译失败：" + e.getMessage() + " sourceText:" + sourceText);
        }
    }

    /**
     * 词汇表翻译
     */
    public void translateGlossaryData(Set<TranslateTextDO> glossaryData, RabbitMqTranslateVO rabbitMqTranslateVO, CharacterCountUtils counter) {
        String shopName = rabbitMqTranslateVO.getShopName();
        String target = rabbitMqTranslateVO.getTarget();
        Integer limitChars = rabbitMqTranslateVO.getLimitChars();
        String accessToken = rabbitMqTranslateVO.getAccessToken();
        ShopifyRequest shopifyRequest = new ShopifyRequest(shopName, accessToken, API_VERSION_LAST, target);

        Map<String, Object> glossaryMap = rabbitMqTranslateVO.getGlossaryMap();
        if (glossaryMap == null || glossaryMap.isEmpty()) {
            return;
        }

        //关键词
        Map<String, String> keyMap1 = new HashMap<>();
        Map<String, String> keyMap0 = new HashMap<>();
        //将glossaryMap中所有caseSensitive为1的数据存到一个Map集合里面
        appInsights.trackTrace("clickTranslation shopName : " + shopName + " , glossaryDO : " + glossaryMap);
        for (Map.Entry<String, Object> entry : glossaryMap.entrySet()) {
            GlossaryDO glossaryDO = OBJECT_MAPPER.convertValue(entry.getValue(), GlossaryDO.class);

            if (glossaryDO.getCaseSensitive() == 1) {
                keyMap1.put(glossaryDO.getSourceText(), glossaryDO.getTargetText());
                continue;
            }
            if (glossaryDO.getCaseSensitive() == 0) {
                keyMap0.put(glossaryDO.getSourceText(), glossaryDO.getTargetText());
            }
        }

        for (TranslateTextDO translateTextDO : glossaryData) {
            //根据模块选择翻译方法，先做普通翻译
            //判断是否停止翻译
            if (checkNeedStopped(rabbitMqTranslateVO.getShopName(), counter)) {
                return;
            }

            String value = translateTextDO.getSourceText();
            String resourceId = translateTextDO.getResourceId();
            String source = rabbitMqTranslateVO.getSource();
            String key = translateTextDO.getTextKey();
            String digest = translateTextDO.getDigest();
            Map<String, Object> translation = createTranslationMap(target, key, digest);

            //判断是否达到额度限制
            updateCharsWhenExceedLimit(counter, shopName, limitChars, new TranslateRequest(0, shopName, accessToken, source, target, null));

            //开始翻译
            //缓存翻译和数据库翻译
            if (cacheOrDatabaseTranslateData(value, source, translation, resourceId, shopifyRequest)) {
                continue;
            }

            //词汇表翻译
            translateAllGlossaryData(source, value, resourceId, counter, translation, shopifyRequest, keyMap1, keyMap0, rabbitMqTranslateVO.getLanguagePack(), rabbitMqTranslateVO.getModeType(), limitChars);
            //翻译进度条加1
            checkNeedAddProcessData(shopName, target);
        }
    }

    /**
     * 词汇表翻译
     */
    public void translateAllGlossaryData(String source, String value, String resourceId, CharacterCountUtils counter
            , Map<String, Object> translation, ShopifyRequest shopifyRequest, Map<String, String> keyMap1, Map<String
            , String> keyMap0, String languagePack, String modeType, Integer limitChars) {
        String targetText;
        TranslateRequest translateRequest = new TranslateRequest(0, shopifyRequest.getShopName(), shopifyRequest.getAccessToken(), source, shopifyRequest.getTarget(), value);
        //判断是否为HTML
        if (isHtml(value)) {
            try {
                targetText = jsoupUtils.translateGlossaryHtml(value, translateRequest, counter, null, keyMap0, keyMap1, languagePack, limitChars);
                targetText = isHtmlEntity(targetText);
            } catch (Exception e) {
                shopifyService.saveToShopify(value, translation, resourceId, shopifyRequest);
                return;
            }
            shopifyService.saveToShopify(targetText, translation, resourceId, shopifyRequest);
            printTranslation(targetText, value, translation, shopifyRequest.getShopName(), modeType, resourceId, source);
            return;
        }

        String finalText;
        //其他数据类型，对数据做处理再翻译
        try {
            //用大模型翻译
            String glossaryString = glossaryText(keyMap1, keyMap0, value);
            //根据关键词生成对应的提示词
            finalText = jsoupUtils.glossaryTranslationModel(translateRequest, counter, glossaryString, languagePack, limitChars);
            //对null的处理， 不翻译，看下打印情况
            if (finalText == null) {
                appInsights.trackTrace("每日须看 clickTranslation " + shopifyRequest.getShopName() + " glossaryTranslationModel finalText is null " + " sourceText: " + value);
                return;
            }
            redisProcessService.setCacheData(shopifyRequest.getTarget(), finalText, value);
            shopifyService.saveToShopify(finalText, translation, resourceId, shopifyRequest);
            printTranslation(finalText, value, translation, shopifyRequest.getShopName(), modeType, resourceId, source);
        } catch (Exception e) {
            appInsights.trackTrace("clickTranslation " + shopifyRequest.getShopName() + " glossaryTranslationModel errors " + e + " sourceText: " + value);
            shopifyService.saveToShopify(value, translation, resourceId, shopifyRequest);
        }
    }

    /**
     * 达到字符限制，更新用户剩余字符数，终止循环
     */
    public void updateCharsWhenExceedLimit(CharacterCountUtils counter, String shopName, int remainingChars, TranslateRequest translateRequest) {
        TranslationCounterRequest request = new TranslationCounterRequest();
        request.setShopName(shopName);

        // 获取这个用户的已使用token数
        TranslationCounterDO translationCounterDO = translationCounterService.getOne(new LambdaQueryWrapper<TranslationCounterDO>().eq(TranslationCounterDO::getShopName, shopName));
        //已经使用的字符数
        Integer usedChars;
        if (translationCounterDO != null) {
            usedChars = translationCounterDO.getUsedChars();
        } else {
            usedChars = counter.getTotalChars();
        }
        if (usedChars >= remainingChars) {
            appInsights.trackTrace("clickTranslation shopName 用户 消耗的token : " + shopName + " totalChars : " + usedChars + " limitChars : " + remainingChars);
            translatesService.updateTranslateStatus(shopName, 3, translateRequest.getTarget(), translateRequest.getSource(), translateRequest.getAccessToken());
            //将同一个shopName的task任务的状态。除邮件发送模块改为3.
            updateTranslateTasksStatus(shopName);
            throw new ClientException(CHARACTER_LIMIT);
        }
    }

    /**
     * 缓存翻译
     */
    public boolean cacheOrDatabaseTranslateData(String value, String source, Map<String, Object> translation, String resourceId, ShopifyRequest request) {
        //获取缓存数据
        String targetCache = redisProcessService.getCacheData(request.getTarget(), value);
        if (targetCache != null) {
            targetCache = isHtmlEntity(targetCache);
            shopifyService.saveToShopify(targetCache, translation, resourceId, request);
            printTranslation(targetCache, value, translation, request.getShopName(), "Cache", resourceId, source);
            //翻译进度条加1
            //翻译进度条加1
            checkNeedAddProcessData(request.getShopName(), request.getTarget());
            return true;
        }

        //255字符以内才从数据库中获取数据
        if (value.length() > 255) {
            return false;
        }
        String targetText = null;
        try {
            targetText = vocabularyService.getTranslateTextDataInVocabulary(request.getTarget(), value, source);
        } catch (Exception e) {
            //打印错误信息
            appInsights.trackTrace("clickTranslation translateDataByDatabase errors : " + e.getMessage());
        }
        if (targetText != null && !targetText.equals(value)) {
            targetText = isHtmlEntity(targetText);
            redisProcessService.setCacheData(request.getTarget(), targetText, value);
            shopifyService.saveToShopify(targetText, translation, resourceId, request);
            printTranslation(targetText, value, translation, request.getShopName(), DATABASE, resourceId, source);
            //翻译进度条加1
            checkNeedAddProcessData(request.getShopName(), request.getTarget());
            return true;
        }
        return false;
    }

    /**
     * 缓存翻译
     */
    public boolean cacheTranslateData(String source, ShopifyRequest request, TranslateTextDO translateTextDO, String target) {
        String targetCache = redisProcessService.getCacheData(request.getTarget(), translateTextDO.getSourceText());
        if (targetCache != null) {
            targetCache = isHtmlEntity(targetCache);
            Map<String, Object> translation = createTranslationMap(target, translateTextDO.getTextKey(), translateTextDO.getDigest());
            shopifyService.saveToShopify(targetCache, translation, translateTextDO.getResourceId(), request);
            printTranslation(targetCache, translateTextDO.getSourceText(), translation, request.getShopName(), "Cache", translateTextDO.getResourceId(), source);
            //翻译进度条加1
            checkNeedAddProcessData(request.getShopName(), request.getTarget());
            return true;
        }
        return false;
    }

    /**
     * DB翻译
     */
    public boolean translateDataByDatabase(String source, ShopifyRequest request, TranslateTextDO translateTextDO, String target) {
        //255字符以内才从数据库中获取数据
        String value = translateTextDO.getSourceText();
        if (value.length() > 255) {
            return false;
        }
        String targetText = null;
        try {
            targetText = vocabularyService.getTranslateTextDataInVocabulary(request.getTarget(), value, source);
        } catch (Exception e) {
            //打印错误信息
            appInsights.trackTrace("clickTranslation translateDataByDatabase errors : " + e.getMessage());
        }
        if (targetText != null && !targetText.equals(value)) {
            targetText = isHtmlEntity(targetText);
            redisProcessService.setCacheData(request.getTarget(), targetText, value);
            Map<String, Object> translation = createTranslationMap(target, translateTextDO.getTextKey(), translateTextDO.getDigest());
            shopifyService.saveToShopify(targetText, translation, translateTextDO.getResourceId(), request);
            printTranslation(targetText, value, translation, request.getShopName(), DATABASE, translateTextDO.getResourceId(), source);
            //翻译进度条加1
            //翻译进度条加1
            checkNeedAddProcessData(request.getShopName(), request.getTarget());
            return true;
        }
        return false;
    }

    /**
     * 合并新的缓存和数据库翻译
     */
    public boolean newCacheAndDBTranslateData(String source, ShopifyRequest request, TranslateTextDO translateTextDO, String target) {
        boolean flag1 = cacheTranslateData(source, request, translateTextDO, target);
        boolean flag2 = translateDataByDatabase(source, request, translateTextDO, target);
        return flag1 || flag2;
    }

    /**
     * 元字段text翻译
     */
    public void translateMetafieldTextData(TranslateTextDO translateTextDO, RabbitMqTranslateVO rabbitMqTranslateVO, CharacterCountUtils counter, Map<String, Object> translation, ShopifyRequest shopifyRequest) {
        String value = translateTextDO.getSourceText();
        String source = rabbitMqTranslateVO.getSource();
        String resourceId = translateTextDO.getResourceId();
        String shopName = translateTextDO.getShopName();
        String type = translateTextDO.getTextType();
        String target = rabbitMqTranslateVO.getTarget();

        if (LIST_SINGLE_LINE_TEXT_FIELD.equals(type)) {
            //翻译list类型文本
            try {
                //如果符合要求，则翻译，不符合要求则返回原值
                List<String> resultList = OBJECT_MAPPER.readValue(value, new TypeReference<>() {
                });
                for (int i = 0; i < resultList.size(); i++) {
                    String original = resultList.get(i);
                    if (!isValidString(original) && original != null && !original.trim().isEmpty() && !isHtml(value)) {
                        //走翻译流程
                        String targetCache = redisProcessService.getCacheData(target, value);
                        if (targetCache != null) {
                            resultList.set(i, targetCache);
                            continue;
                        }
                        String translated = jsoupUtils.translateByModel(new TranslateRequest(0, shopName, shopifyRequest.getAccessToken(), source, shopifyRequest.getTarget(), value), counter, rabbitMqTranslateVO.getLanguagePack(), rabbitMqTranslateVO.getLimitChars());
                        //对null的处理
                        if (translated == null) {
                            appInsights.trackTrace("每日须看 translateMetafieldTextData 用户： " + shopName + " 翻译失败，翻译内容为空 value: " + value);
                            translated = jsoupUtils.checkTranslationModel(new TranslateRequest(0, shopName, shopifyRequest.getAccessToken(), source, shopifyRequest.getTarget(), value), counter, rabbitMqTranslateVO.getLanguagePack(), rabbitMqTranslateVO.getLimitChars());
                            resultList.set(i, translated);
                            continue;
                        }
                        redisProcessService.setCacheData(target, translated, value);
                        //将数据填回去
                        resultList.set(i, translated);
                    }
                }
                //将list数据转为String 再存储到shopify本地
                String translatedValue = OBJECT_MAPPER.writeValueAsString(resultList);
                shopifyService.saveToShopify(translatedValue, translation, resourceId, shopifyRequest);
                printTranslation(translatedValue, value, translation, shopifyRequest.getShopName(), type, resourceId, source);
                //翻译进度条加1
                checkNeedAddProcessData(shopifyRequest.getShopName(), shopifyRequest.getTarget());
                //存到数据库中
                try {
                    // 255字符以内 和 数据库内有该数据类型 文本才能插入数据库
                    vocabularyService.InsertOne(shopifyRequest.getTarget(), translatedValue, translateTextDO.getSourceCode(), value);
                } catch (Exception e) {
                    appInsights.trackTrace("clickTranslation " + shopName + " translateMetafield存储失败 errors ： " + e.getMessage() + " ，继续翻译");
                }
            } catch (Exception e) {
                //存原数据到shopify本地
                shopifyService.saveToShopify(value, translation, resourceId, shopifyRequest);
                appInsights.trackTrace("clickTranslation " + shopName + " LIST errors 错误原因： " + e.getMessage());
            }
        }
    }

    /**
     * 邮件发送功能
     * 1，完成翻译
     * 2，未完成翻译
     * 3，出现错误
     */
    public void sendTranslateEmail(RabbitMqTranslateVO rabbitMqTranslateVO, TranslateTasksDO task, List<String> translationList) {
        String shopName = rabbitMqTranslateVO.getShopName();
        String source = rabbitMqTranslateVO.getSource();
        String target = rabbitMqTranslateVO.getTarget();
        String accessToken = rabbitMqTranslateVO.getAccessToken();
        LocalDateTime startTime = LocalDateTime.parse(rabbitMqTranslateVO.getStartTime());
        Integer startChars = rabbitMqTranslateVO.getStartChars();
        Integer limitChars = rabbitMqTranslateVO.getLimitChars();
        //获取用户翻译状态
        Integer nowUserTranslate = translatesService.getStatusByShopNameAndTargetAndSource(shopName, target, source);
        //获取现在的token
        Integer nowUserToken = translationCounterService.getOne(new QueryWrapper<TranslationCounterDO>().eq("shop_name", shopName)).getUsedChars();
        CharacterCountUtils counter = new CharacterCountUtils();
        counter.addChars(nowUserToken);
        if (nowUserTranslate == 2) {
            //将2改为1， 发送翻译成功的邮件
            if ("ciwishop.myshopify.com".equals(shopName)) {
                translatesService.updateTranslateStatus(shopName, 1, target, source, accessToken);
                tencentEmailService.translateSuccessEmail(new TranslateRequest(0, shopName, accessToken, source, target, null), counter, startTime, startChars, limitChars, false);
                translateTasksService.updateByTaskId(task.getTaskId(), 1);
                translatesService.update(new LambdaUpdateWrapper<TranslatesDO>().eq(TranslatesDO::getShopName, shopName)
                        .eq(TranslatesDO::getSource, source)
                        .eq(TranslatesDO::getTarget, target)
                        .set(TranslatesDO::getResourceType, null));
                appInsights.trackTrace("clickTranslation 用户 " + shopName + " ciwi翻译结束 时间为： " + LocalDateTime.now());
                //删除redis该用户相关进度条数据
                redisIntegration.delete(generateProcessKey(shopName, target));
            } else {
                triggerSendEmailLater(shopName, target, source, accessToken, task, counter, startTime, startChars, limitChars);
            }
        } else if (nowUserTranslate == 3) {
            //为3，发送部分翻译的邮件
            //将List<String> 转化位 List<TranslateResourceDTO>
            List<String> sort = sort(translationList);
            List<TranslateResourceDTO> convertALL = convertALL(sort);
            tencentEmailService.translateFailEmail(shopName, counter, startTime, startChars, convertALL, target, source);
            translateTasksService.updateByTaskId(task.getTaskId(), 1);
        }

    }

    /**
     * 将同一个shopNmae的task任务的状态。除邮件发送模块改为3.
     */
    public void updateTranslateTasksStatus(String shopName) {
        //获取shopName所有status为0的task
        List<TranslateTasksDO> list = translateTasksService.list(new QueryWrapper<TranslateTasksDO>().eq("status", 0).eq("shop_name", shopName));
        //遍历task，然后解析数据，将除email的task都改为3
        for (TranslateTasksDO translateTasksDO : list
        ) {
            //解析数据
            String payload = translateTasksDO.getPayload();
            try {
                RabbitMqTranslateVO rabbitMqTranslateVO = OBJECT_MAPPER.readValue(payload, RabbitMqTranslateVO.class);
                if (rabbitMqTranslateVO.getShopifyData().equals(EMAIL)) {
                    continue;
                }
                if (rabbitMqTranslateVO.getShopifyData().equals(EMAIL_AUTO)) {
                    continue;
                }
                translateTasksService.updateByTaskId(translateTasksDO.getTaskId(), 3);
            } catch (Exception e) {
                appInsights.trackTrace("clickTranslation " + shopName + " errors : " + e);
                appInsights.trackException(e);
            }
        }
    }

    /**
     * 异步8分钟后，发送翻译成功的邮件
     */
    public void triggerSendEmailLater(String shopName, String target, String source, String accessToken, TranslateTasksDO task, CharacterCountUtils counter, LocalDateTime startTime, Integer startChars, Integer limitChars) {
        // 创建一个任务 Runnable
        Runnable delayedTask = () -> {
            appInsights.trackTrace("clickTranslation " + shopName + " 异步发送邮件: " + LocalDateTime.now());
            translatesService.updateTranslateStatus(shopName, 1, target, source, accessToken);
            tencentEmailService.translateSuccessEmail(new TranslateRequest(0, shopName, accessToken, source, target, null), counter, startTime, startChars, limitChars, false);
            translateTasksService.updateByTaskId(task.getTaskId(), 1);
            //将翻译项中的模块改为null
            translatesService.update(new LambdaUpdateWrapper<TranslatesDO>().eq(TranslatesDO::getShopName, shopName)
                    .eq(TranslatesDO::getSource, source)
                    .eq(TranslatesDO::getTarget, target)
                    .set(TranslatesDO::getResourceType, null));
            appInsights.trackTrace("clickTranslation 用户 " + shopName + " 翻译结束 时间为： " + LocalDateTime.now());
            //删除redis该用户相关进度条数据
            redisIntegration.delete(generateProcessKey(shopName, target));
        };

        // 设置执行时间为当前时间 + 10分钟（使用 Instant 代替 Date）
        Instant runAt = Instant.now().plusSeconds(8 * 60);

        // 使用推荐的 API
        taskScheduler.schedule(delayedTask, runAt);
    }

    /**
     * 更新用户token计数
     */
    public void countAfterTranslated(TranslateRequest request) {
        //更新初始值
        try {
            userTypeTokenService.startTokenCount(request);
        } catch (Exception e) {
            appInsights.trackTrace("countAfterTranslated 重新更新token值失败！！！ errors : " + e.getMessage());
        }
    }
}
