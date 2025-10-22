package com.bogdatech.logic;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.bogdatech.Service.*;
import com.bogdatech.entity.DO.*;
import com.bogdatech.entity.VO.RabbitMqTranslateVO;
import com.bogdatech.exception.ClientException;
import com.bogdatech.integration.ALiYunTranslateIntegration;
import com.bogdatech.integration.RedisIntegration;
import com.bogdatech.logic.redis.TranslationParametersRedisService;
import com.bogdatech.logic.translate.TranslateDataService;
import com.bogdatech.mapper.InitialTranslateTasksMapper;
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
import static com.bogdatech.logic.redis.TranslationParametersRedisService.generateProgressTranslationKey;
import static com.bogdatech.utils.ApiCodeUtils.getLanguageName;
import static com.bogdatech.utils.CaseSensitiveUtils.*;
import static com.bogdatech.utils.JsonUtils.objectToJson;
import static com.bogdatech.utils.JsoupUtils.*;
import static com.bogdatech.utils.LiquidHtmlTranslatorUtils.isHtmlEntity;
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
    @Autowired
    private TranslationParametersRedisService translationParametersRedisService;
    @Autowired
    private ITranslationCounterService iTranslationCounterService;
    @Autowired
    private InitialTranslateTasksMapper initialTranslateTasksMapper;
    @Autowired
    private TranslateDataService translateDataService;

    public static final int BATCH_SIZE = 50;
    public static String CLICK_EMAIL = "click"; // db 设置的10字符， 改动的时候需要注意
    public static String AUTO_EMAIL = "auto";

    /**
     * MQ翻译
     * 读取该用户的shopify数据，然后循环获取模块数据
     * 根据翻译类型选择不同的邮件发送方式: true-> 手动； false-> 自动
     *
     */
    public void initialTasks(ShopifyRequest shopifyRequest, List<String> translateResourceDTOS, TranslateRequest request,
                             boolean handleFlag, String translationModel, boolean isCover, String customKey, String taskType) {
        // 获取已使用字符数和剩余字符数
        TranslationCounterDO translationCounterDO = iTranslationCounterService.readCharsByShopName(shopifyRequest.getShopName());
        Integer limitChars = iTranslationCounterService.getMaxCharsByShopName(shopifyRequest.getShopName());
        int usedChars = translationCounterDO.getUsedChars();

        // 初始化计数器
        CharacterCountUtils counter = new CharacterCountUtils();
        counter.addChars(usedChars);

        // 判断是否有同义词
        Map<String, Object> glossaryMap = new HashMap<>();
        glossaryService.getGlossaryByShopName(shopifyRequest, glossaryMap);
        appInsights.trackTrace("判断是否有同义词 " + shopifyRequest.getShopName());

        // 获取目前所使用的AI语言包
        String languagePackId = aiLanguagePackService.getCategoryByDescription(shopifyRequest.getShopName(), shopifyRequest.getAccessToken(), counter, limitChars, shopifyRequest.getTarget());
        appInsights.trackTrace("获取目前所使用的AI语言包 " + shopifyRequest.getShopName());

        RabbitMqTranslateVO rabbitMqTranslateVO = new RabbitMqTranslateVO(null, shopifyRequest.getShopName(), shopifyRequest.getAccessToken(), request.getSource(), request.getTarget(), languagePackId, handleFlag, glossaryMap, null, limitChars, usedChars, LocalDateTime.now().toString(), translateResourceDTOS, translationModel, isCover, customKey);
        CharacterCountUtils allTasks = new CharacterCountUtils();

        for (TranslateResourceDTO translateResource : ALL_RESOURCES) {
            appInsights.trackTrace("初始化task数据 " + shopifyRequest.getShopName() + " model: " + translateResource.getResourceType());
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
                // TODO: 判断是否重复创建task。 1，判断模块是否正在翻译。 2，判断task是否已经存在
                // 判断db里面，该用户，该模块status为0的task中， 是否存在，存在就不存了；不存在，就存
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

        // 当模块都发送后，发送邮件模块
        appInsights.trackTrace("存邮件数据 " + rabbitMqTranslateVO.getShopName());
        sendEmailTranslate(rabbitMqTranslateVO, taskType, allTasks);
    }

    /**
     * 当模块都发送完后，发送邮件模块
     */
    public void sendEmailTranslate(RabbitMqTranslateVO rabbitMqTranslateVO, String taskType, CharacterCountUtils allTasks) {
        // 邮件相关参数
        switch (taskType) {
            case "click":
                // click会走initialDbtask发送邮件了 这里关掉
//                rabbitMqTranslateVO.setShopifyData(EMAIL);
                break;
            case "auto":
                rabbitMqTranslateVO.setShopifyData(EMAIL_AUTO);
                rabbitMqTranslateVO.setCustomKey(EMAIL_TRANSLATE);
                break;
            default:
                // TODO： 目前就这两个， 以后如果还有其他类型，可以在这里添加（如 私有key）
                System.out.println("sendEmailTranslate taskType 不是click或auto 而是： " + taskType);
                appInsights.trackTrace("sendEmailTranslate 每日须看 taskType 不是click或auto 而是： " + taskType);
                break;
        }

        try {
            String json = OBJECT_MAPPER.writeValueAsString(rabbitMqTranslateVO);
            if (CLICK_EMAIL.equals(taskType)) {
                return;
            }
            translateTasksService.save(new TranslateTasksDO(null, 0, json, rabbitMqTranslateVO.getShopName(), null, null));

            // 计数前面的子任务项，并修改allTasks的字段
            allTasks.addChars(1);

            // 修改之前的所有allTasks的字段
            translateTasksService.update(new LambdaUpdateWrapper<TranslateTasksDO>().eq(TranslateTasksDO::getShopName, rabbitMqTranslateVO.getShopName()).set(TranslateTasksDO::getAllTasks, allTasks.getTotalChars()));

        } catch (Exception e) {
            appInsights.trackTrace("sendEmailTranslate 保存翻译任务失败 errors : " + e);
            appInsights.trackException(e);
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
     * 翻译停止后，进度条就不加了
     */
    public void checkNeedAddProcessData(String shopName, String target) {
        if (!translationParametersRedisService.isStopped(shopName)) {
            redisProcessService.addProcessData(generateProcessKey(shopName, target), PROGRESS_DONE, 1L);
        }
    }

    /**
     * 判断停止标识
     */
    public boolean checkNeedStopped(String shopName, CharacterCountUtils counter) {
        if (translationParametersRedisService.isStopped(shopName)) {
            // 更新数据库中的已使用字符数
            appInsights.trackTrace("checkNeedStopped " + shopName + " 用户 消耗的token ： " + counter.getTotalChars());

            // 将翻译状态为2改为“部分翻译”
            translatesService.update(new UpdateWrapper<TranslatesDO>().eq("shop_name", shopName).eq("status", 2).set("status", 7));

            // 将task表数据都改为 5
            translateTasksService.updateByShopName(shopName, 5);
            return true;
        }

        return false;
    }

    /**
     * 文本翻译
     * 修改翻译的逻辑，暂定获取每50条数据，
     */
    public void translatePlainTextData(Set<TranslateTextDO> plainTextData, RabbitMqTranslateVO vo, CharacterCountUtils counter, String translationKeyType) {
        if (plainTextData.isEmpty()) {
            return;
        }
        String shopName = vo.getShopName();
        String target = vo.getTarget();
        Integer limitChars = vo.getLimitChars();
        String accessToken = vo.getAccessToken();
        String source = vo.getSource();
        ShopifyRequest shopifyRequest = new ShopifyRequest(shopName, accessToken, API_VERSION_LAST, target);

        TranslateRequest translateRequestTemplate = new TranslateRequest(0, shopName, accessToken, source, target, null);
        // 先转成List，方便切片
        List<TranslateTextDO> list = new ArrayList<>(plainTextData);
        for (int i = 0; i < list.size(); i += BATCH_SIZE) {
            if (checkNeedStopped(shopName, counter)) {
                return;
            }

            // TODO： 2.2 翻译前的token校验
            updateCharsWhenExceedLimit(counter, shopName, limitChars, translateRequestTemplate);

            // 取每次的50条（或剩余全部）
            int endIndex = Math.min(i + BATCH_SIZE, list.size());
            List<TranslateTextDO> batch = list.subList(i, endIndex);

            // 筛选出来要翻译的数据
            List<String> untranslatedTexts = batch.stream()
                    // 只保留那些 “newCacheAndDBTranslateData(...) 返回 false” 的元素
                    .filter(t -> !newCacheAndDBTranslateData(source, shopifyRequest, t, target))
                    // 取出每个元素的 sourceText 字段
                    .map(TranslateTextDO::getSourceText)
                    .distinct()
                    // 收集成一个 List<String>
                    .collect(Collectors.toList());

            //TODO：更细致的判断额度是否足够，足够继续，不够返回，开始翻译
            if (untranslatedTexts.isEmpty()) {
                continue;
            }

            Map<String, String> resultMap = translateDataService.translatePlainText(untranslatedTexts, vo, counter,
                    shopName, source, limitChars, translationKeyType, translateRequestTemplate);
            if (CollectionUtils.isEmpty(resultMap)) {
                appInsights.trackTrace("FatalException translatePlainTextData " + shopName + " source: " + source + " untranslatedTexts : " + untranslatedTexts);
                continue;
            }

            // 处理翻译后的数据
            for (TranslateTextDO item : batch) {
                String sourceText = item.getSourceText();
                String targetText = resultMap.get(sourceText);
                if (targetText == null) {
                    appInsights.trackTrace("FatalException translatePlainTextData " + shopifyRequest.getShopName() + " 翻译结果缺失：" + sourceText);
                    continue;
                }

                translationParametersRedisService.hsetTranslationStatus(generateProgressTranslationKey(vo.getShopName(), vo.getSource(), vo.getTarget()), String.valueOf(2));
                translationParametersRedisService.hsetTranslatingString(generateProgressTranslationKey(vo.getShopName(), vo.getSource(), vo.getTarget()), sourceText);

                // 存储翻译后的数据
                Map<String, Object> translation = createTranslationMap(vo.getTarget(), item.getTextKey(), item.getDigest());

                // 添加缓存
                if (!URI.equals(item.getTextType())) {
                    redisProcessService.setCacheData(shopifyRequest.getTarget(), targetText, sourceText);
                }

                // TODO： 3.1 翻译后的存db
                shopifyService.saveToShopify(targetText, translation, item.getResourceId(), shopifyRequest);
                printTranslation(targetText, sourceText, translation, vo.getShopName(), vo.getModeType(), item.getResourceId(), vo.getSource());
                checkNeedAddProcessData(shopifyRequest.getShopName(), shopifyRequest.getTarget());

                try {
                    if (!URI.equals(item.getTextType())) {
                        vocabularyService.InsertOne(vo.getTarget(), targetText, vo.getSource(), sourceText);
                    }
                } catch (Exception e) {
                    appInsights.trackTrace("clickTranslation 存储翻译失败：" + e.getMessage() + " sourceText:" + sourceText);
                }
            }
        }
    }

    public Map<String, Set<TranslateTextDO>> filterTranslateMap(Map<String, Set<TranslateTextDO>> stringSetMap, Set<TranslateTextDO> filterTranslateData, Map<String, Object> glossaryMap) {
        if (filterTranslateData == null || filterTranslateData.isEmpty()) {
            return stringSetMap;
        }
        for (TranslateTextDO translateTextDO : filterTranslateData) {
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
     * 达到字符限制，更新用户剩余字符数，终止循环
     */
    public void updateCharsWhenExceedLimit(CharacterCountUtils counter, String shopName, int remainingChars, TranslateRequest translateRequest) {
        TranslationCounterRequest request = new TranslationCounterRequest();
        request.setShopName(shopName);

        // 获取这个用户的已使用token数
        TranslationCounterDO translationCounterDO = translationCounterService.getOne(new LambdaQueryWrapper<TranslationCounterDO>().eq(TranslationCounterDO::getShopName, shopName));

        // 获取最新的remainingChars
        Integer maxCharsByShopName = translationCounterService.getMaxCharsByShopName(shopName);

        // 已经使用的字符数
        Integer usedChars;
        if (translationCounterDO != null) {
            usedChars = translationCounterDO.getUsedChars();
        } else {
            usedChars = counter.getTotalChars();
        }
        if (usedChars >= maxCharsByShopName) {
            appInsights.trackTrace("clickTranslation shopName 用户 消耗的token : " + shopName + " totalChars : " + usedChars + " limitChars : " + maxCharsByShopName);
            translatesService.updateTranslateStatus(shopName, 3, translateRequest.getTarget(), translateRequest.getSource());
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

            // 翻译进度条加1
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
     * 合并新的缓存和数据库翻译
     */
    public boolean newCacheAndDBTranslateData(String source, ShopifyRequest request, TranslateTextDO translateTextDO, String target) {
        String targetCache = redisProcessService.getCacheData(request.getTarget(), translateTextDO.getSourceText());
        if (targetCache != null) {
            targetCache = isHtmlEntity(targetCache);
            Map<String, Object> translation = createTranslationMap(target, translateTextDO.getTextKey(), translateTextDO.getDigest());
            shopifyService.saveToShopify(targetCache, translation, translateTextDO.getResourceId(), request);
            printTranslation(targetCache, translateTextDO.getSourceText(), translation, request.getShopName(), "Cache", translateTextDO.getResourceId(), source);

            // 翻译进度条加1
            checkNeedAddProcessData(request.getShopName(), request.getTarget());
            return true;
        }

        // 255字符以内才从数据库中获取数据
        String value = translateTextDO.getSourceText();
        if (value.length() > 255) {
            return false;
        }
        String targetText = vocabularyService.getTranslateTextDataInVocabulary(request.getTarget(), value, source);

        if (targetText != null && !targetText.equals(value)) {
            targetText = isHtmlEntity(targetText);
            redisProcessService.setCacheData(request.getTarget(), targetText, value);
            Map<String, Object> translation = createTranslationMap(target, translateTextDO.getTextKey(), translateTextDO.getDigest());
            shopifyService.saveToShopify(targetText, translation, translateTextDO.getResourceId(), request);
            printTranslation(targetText, value, translation, request.getShopName(), DATABASE, translateTextDO.getResourceId(), source);

            // 翻译进度条加1
            checkNeedAddProcessData(request.getShopName(), request.getTarget());
            return true;
        }

        return false;
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
    public void triggerSendEmailLater(String shopName, String target, String source, String accessToken, LocalDateTime startTime, Long costToken, Integer usedChars, Integer limitChars) {
        // 创建一个任务 Runnable
        // 修改进度条是写入
        translationParametersRedisService.hsetTranslationStatus(generateProgressTranslationKey(shopName, source, target), String.valueOf(3));
        translationParametersRedisService.hsetTranslatingString(generateProgressTranslationKey(shopName, source, target), "");

        Runnable delayedTask = () -> {
            appInsights.trackTrace("clickTranslation " + shopName + " 异步发送邮件: " + LocalDateTime.now());
            tencentEmailService.translateSuccessEmail(new TranslateRequest(0, shopName, accessToken, source, target, null), startTime, costToken, usedChars, limitChars);
            appInsights.trackTrace("clickTranslation 用户 " + shopName + " 翻译结束 时间为： " + LocalDateTime.now());
            initialTranslateTasksMapper.update(new LambdaUpdateWrapper<InitialTranslateTasksDO>().eq(InitialTranslateTasksDO::getShopName, shopName).eq(InitialTranslateTasksDO::getTaskType, CLICK_EMAIL).set(InitialTranslateTasksDO::isSendEmail, 1));
        };

        // 设置执行时间为当前时间 + 10分钟（使用 Instant 代替 Date）
        Instant runAt = Instant.now().plusSeconds(8 * 60);

        // 使用推荐的 API
        taskScheduler.schedule(delayedTask, runAt);
    }

    private void prepareForGlossary(RabbitMqTranslateVO vo, String translationKeyType, Map<String, String> keyMap1, Map<String, String> keyMap0) {
        if (translationKeyType.equals(GLOSSARY)) {
            // 将glossaryMap中所有caseSensitive为1的数据存到一个Map集合里面
            appInsights.trackTrace("clickTranslation shopName : " + vo.getShopName() + " , glossaryDO : " + vo.getGlossaryMap());
            for (Map.Entry<String, Object> entry : vo.getGlossaryMap().entrySet()) {
                GlossaryDO glossaryDO = OBJECT_MAPPER.convertValue(entry.getValue(), GlossaryDO.class);

                if (glossaryDO.getCaseSensitive() == 1) {
                    keyMap1.put(glossaryDO.getSourceText(), glossaryDO.getTargetText());
                    continue;
                }
                if (glossaryDO.getCaseSensitive() == 0) {
                    keyMap0.put(glossaryDO.getSourceText(), glossaryDO.getTargetText());
                }
            }
        }
    }

    public void translateData(Set<TranslateTextDO> translateTextDOS, RabbitMqTranslateVO vo, CharacterCountUtils counter, String translationKeyType) {
        String shopName = vo.getShopName();
        String target = vo.getTarget();
        Integer limitChars = vo.getLimitChars();
        String accessToken = vo.getAccessToken();
        ShopifyRequest shopifyRequest = new ShopifyRequest(shopName, accessToken, API_VERSION_LAST, target);

        // For GLOSSARY
        Map<String, String> keyMap1 = new HashMap<>();
        Map<String, String> keyMap0 = new HashMap<>();
        prepareForGlossary(vo, translationKeyType, keyMap1, keyMap0);

        for (TranslateTextDO translateTextDO : translateTextDOS) {
            //根据模块选择翻译方法，先做普通翻译
            //判断是否停止翻译
            // TODO: 2.2 翻译中的token校验
            if (checkNeedStopped(vo.getShopName(), counter)) {
                return;
            }

            String value = translateTextDO.getSourceText();
            String resourceId = translateTextDO.getResourceId();
            String source = vo.getSource();
            String key = translateTextDO.getTextKey();
            String digest = translateTextDO.getDigest();
            String type = translateTextDO.getTextType();
            Map<String, Object> translation = createTranslationMap(target, key, digest);

            // TODO：2.2 翻译中的token校验
            appInsights.trackTrace("判断是否达到额度限制 用户： " + vo.getShopName());
            updateCharsWhenExceedLimit(counter, shopName, limitChars, new TranslateRequest(0, shopName, accessToken, source, target, null));

            //缓存翻译和数据库翻译
            if (cacheOrDatabaseTranslateData(value, source, translation, resourceId, shopifyRequest)) {
                continue;
            }

            //开始翻译
            String translatedValue = null;
            if (translationKeyType.equals(LIST_SINGLE)) {
                translatedValue = translateDataService.translateListSingleData(value, target, vo, counter, shopName, shopifyRequest, source, translation, translateTextDO.getResourceId());
            } else if (translationKeyType.equals(HTML)) {
                translatedValue = translateDataService.translateHtmlData(value, vo, counter, shopifyRequest, source, translation, resourceId);
            } else if (translationKeyType.equals(GLOSSARY)) {
                translatedValue = translateDataService.translateGlossaryData(value, vo, counter, shopifyRequest, source, translation, resourceId, limitChars, keyMap0, keyMap1);
            }

            if (translatedValue == null) {
                appInsights.trackTrace("FatalException translateData " + shopifyRequest.getShopName() + " source: " + source + " value : " + value);
                continue;
            }

            // TODO: 3.1 翻译后的存db
            shopifyService.saveToShopify(translatedValue, translation, resourceId, shopifyRequest);
            printTranslation(translatedValue, value, translation, shopName, type, resourceId, source);
            //翻译进度条加1
            checkNeedAddProcessData(shopName, target);
        }
    }
}
