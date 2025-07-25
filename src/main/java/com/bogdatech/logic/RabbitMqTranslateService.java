package com.bogdatech.logic;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.bogdatech.Service.*;
import com.bogdatech.entity.DO.*;
import com.bogdatech.entity.VO.RabbitMqTranslateVO;
import com.bogdatech.exception.ClientException;
import com.bogdatech.model.controller.request.*;
import com.bogdatech.requestBody.ShopifyRequestBody;
import com.bogdatech.utils.CharacterCountUtils;
import com.bogdatech.utils.JsoupUtils;
import com.bogdatech.utils.LiquidHtmlTranslatorUtils;
import com.bogdatech.utils.TypeConversionUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;

import static com.bogdatech.constants.TranslateConstants.*;
import static com.bogdatech.entity.DO.TranslateResourceDTO.ALL_RESOURCES;
import static com.bogdatech.enums.ErrorEnum.SHOPIFY_RETURN_ERROR;
import static com.bogdatech.integration.ShopifyHttpIntegration.getInfoByShopify;
import static com.bogdatech.logic.ShopifyService.getShopifyDataByCloud;
import static com.bogdatech.logic.TranslateService.*;
import static com.bogdatech.utils.CaseSensitiveUtils.*;
import static com.bogdatech.utils.JsonUtils.isJson;
import static com.bogdatech.utils.JsonUtils.stringToJson;
import static com.bogdatech.utils.JsoupUtils.*;
import static com.bogdatech.utils.JudgeTranslateUtils.*;
import static com.bogdatech.utils.LiquidHtmlTranslatorUtils.isHtmlEntity;
import static com.bogdatech.utils.ListUtils.convertALL;
import static com.bogdatech.utils.ListUtils.sort;
import static com.bogdatech.utils.PrintUtils.printTranslation;
import static com.bogdatech.utils.RegularJudgmentUtils.isValidString;
import static com.bogdatech.utils.StringUtils.isValueBlank;
import static com.bogdatech.utils.StringUtils.normalizeHtml;

@Service
@EnableAsync
public class RabbitMqTranslateService {

    private final ITranslationCounterService translationCounterService;
    private final ITranslatesService translatesService;
    private final ShopifyService shopifyService;
    private final IVocabularyService vocabularyService;
    private final TencentEmailService tencentEmailService;
    private final GlossaryService glossaryService;
    private final AILanguagePackService aiLanguagePackService;
    private final JsoupUtils jsoupUtils;
    private final LiquidHtmlTranslatorUtils liquidHtmlTranslatorUtils;
    private final ITranslateTasksService translateTasksService;
    private final TaskScheduler taskScheduler;
    private final UserTypeTokenService userTypeTokenService;

    @Autowired
    public RabbitMqTranslateService(ITranslationCounterService translationCounterService, ITranslatesService translatesService, ShopifyService shopifyService, IVocabularyService vocabularyService, TencentEmailService tencentEmailService, GlossaryService glossaryService, AILanguagePackService aiLanguagePackService, JsoupUtils jsoupUtils, LiquidHtmlTranslatorUtils liquidHtmlTranslatorUtils, ITranslateTasksService translateTasksService, TaskScheduler taskScheduler, UserTypeTokenService userTypeTokenService1) {
        this.translationCounterService = translationCounterService;
        this.translatesService = translatesService;
        this.shopifyService = shopifyService;
        this.vocabularyService = vocabularyService;
        this.tencentEmailService = tencentEmailService;
        this.glossaryService = glossaryService;
        this.aiLanguagePackService = aiLanguagePackService;
        this.jsoupUtils = jsoupUtils;
        this.liquidHtmlTranslatorUtils = liquidHtmlTranslatorUtils;
        this.translateTasksService = translateTasksService;
        this.taskScheduler = taskScheduler;
        this.userTypeTokenService = userTypeTokenService1;
    }

    /**
     * MQ翻译
     * 读取该用户的shopify数据，然后循环获取模块数据
     */
    @Async
    public void mqTranslate(ShopifyRequest shopifyRequest, CharacterCountUtils counter, List<String> translateResourceDTOS, TranslateRequest request, int limitChars, int usedChars, boolean handleFlag, String translationModel, boolean isCover, String customKey) {
        //判断是否有同义词
        Map<String, Object> glossaryMap = new HashMap<>();
        glossaryService.getGlossaryByShopName(shopifyRequest, glossaryMap);

        //获取目前所使用的AI语言包
        String languagePackId = aiLanguagePackService.getCategoryByDescription(shopifyRequest.getShopName(), shopifyRequest.getAccessToken(), counter, limitChars);
        //通过判断status和字符判断后 就将状态改为2，则开始翻译流程
        RabbitMqTranslateVO rabbitMqTranslateVO = new RabbitMqTranslateVO(null, shopifyRequest.getShopName(), shopifyRequest.getAccessToken(), request.getSource(), request.getTarget(), languagePackId, handleFlag, glossaryMap, null, limitChars, usedChars, LocalDateTime.now().toString(), translateResourceDTOS, translationModel, isCover, customKey);
        for (TranslateResourceDTO translateResource : ALL_RESOURCES
        ) {
            rabbitMqTranslateVO.setModeType(translateResource.getResourceType());
            if (!translateResourceDTOS.contains(translateResource.getResourceType())) {
                continue;
            }
            if (translateResource.getResourceType().equals(PAYMENT_GATEWAY)) {
                continue;
            }

            if (EXCLUDED_SHOPS.contains(request.getShopName()) && PRODUCT_MODEL.contains(translateResource.getResourceType())) {
                continue;
            }

            // 定期检查是否停止
            if (checkNeedStopped(request.getShopName(), counter)) {
                return;
            }

            translateResource.setTarget(request.getTarget());
            String shopifyData = getShopifyData(shopifyRequest, translateResource);
            rabbitMqTranslateVO.setShopifyData(shopifyData);
            rabbitMqTranslateVO.setModeType(translateResource.getResourceType());
            String query = new ShopifyRequestBody().getFirstQuery(translateResource);

            try {
                RabbitMqTranslateVO dbTranslateVO = new RabbitMqTranslateVO().copy(rabbitMqTranslateVO);
                dbTranslateVO.setShopifyData(query);
                String json = OBJECT_MAPPER.writeValueAsString(dbTranslateVO);
                translateTasksService.save(new TranslateTasksDO(null, 0, json, rabbitMqTranslateVO.getShopName()));
            } catch (Exception e) {
                appInsights.trackTrace("保存翻译任务失败 errors : " + e);
            }
            // 改为将请求数据存储到数据库中
            parseShopifyData(rabbitMqTranslateVO, translateResource);
        }
        rabbitMqTranslateVO.setTranslateList(translateResourceDTOS);
        //当模块都发送后，发送邮件模块
        sendEmailTranslate(rabbitMqTranslateVO);
    }

    /**
     * 当模块都发送完后，发送邮件模块
     */
    public void sendEmailTranslate(RabbitMqTranslateVO rabbitMqTranslateVO) {
        //邮件相关参数
        rabbitMqTranslateVO.setShopifyData(EMAIL);
        try {
            String json = OBJECT_MAPPER.writeValueAsString(rabbitMqTranslateVO);
            translateTasksService.save(new TranslateTasksDO(null, 0, json, rabbitMqTranslateVO.getShopName()));
        } catch (Exception e) {
            appInsights.trackTrace("保存翻译任务失败 errors : " + e);
        }
    }


    /**
     * 解析shopifyData的数据，递归获取，每250条数据作为一个翻译任务发送到队列里面
     */
    public void parseShopifyData(RabbitMqTranslateVO rabbitMqTranslateVO, TranslateResourceDTO translateResource) {
        JsonNode rootNode;
        try {
            rootNode = OBJECT_MAPPER.readTree(rabbitMqTranslateVO.getShopifyData());
        } catch (JsonProcessingException e) {
            appInsights.trackTrace("rootNode errors： " + e.getMessage());
            return;
        }

        translateNextPage(rootNode, translateResource, rabbitMqTranslateVO);
    }

    //获取下一页数据
    public void translateNextPage(JsonNode rootNode, TranslateResourceDTO translateContext, RabbitMqTranslateVO rabbitMqTranslateVO) {
        // 获取translatableResources节点
        JsonNode translatableResourcesNode = rootNode.path("translatableResources");
        // 获取pageInfo节点
        JsonNode pageInfoNode = translatableResourcesNode.path("pageInfo");
        if (translatableResourcesNode.hasNonNull("pageInfo")) {
            if (pageInfoNode.hasNonNull("hasNextPage") && pageInfoNode.get("hasNextPage").asBoolean()) {
                JsonNode endCursor = pageInfoNode.path("endCursor");
                translateContext.setAfter(endCursor.asText(null));
                translateNextPageData(translateContext, rabbitMqTranslateVO);
            }
        }
    }

    //递归处理下一页数据
    private void translateNextPageData(TranslateResourceDTO translateContext, RabbitMqTranslateVO rabbitMqTranslateVO) {
        JsonNode nextPageData;
        try {
            nextPageData = fetchNextPage(translateContext, new ShopifyRequest(rabbitMqTranslateVO.getShopName(), rabbitMqTranslateVO.getAccessToken(), APIVERSION, rabbitMqTranslateVO.getTarget()), rabbitMqTranslateVO);
            if (nextPageData == null) {
                return;
            }
        } catch (Exception e) {
            return;
        }
        rabbitMqTranslateVO.setShopifyData(nextPageData.toString());
        // 重新开始翻译流程
        parseShopifyData(rabbitMqTranslateVO, translateContext);
    }

    public JsonNode fetchNextPage(TranslateResourceDTO translateResource, ShopifyRequest request, RabbitMqTranslateVO rabbitMqTranslateVO) {
        CloudServiceRequest cloudServiceRequest = TypeConversionUtils.shopifyToCloudServiceRequest(request);
        String query = new ShopifyRequestBody().getAfterQuery(translateResource);
        //将请求数据存数据库中
        try {
            RabbitMqTranslateVO dbTranslateVO = new RabbitMqTranslateVO().copy(rabbitMqTranslateVO);
            dbTranslateVO.setShopifyData(query);
            String json = OBJECT_MAPPER.writeValueAsString(dbTranslateVO);
            translateTasksService.save(new TranslateTasksDO(null, 0, json, rabbitMqTranslateVO.getShopName()));
        } catch (Exception e) {
            appInsights.trackTrace("fechNextPage 保存翻译任务失败 errors " + e);
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
     * */
    public void translateByModeType(RabbitMqTranslateVO rabbitMqTranslateVO, CharacterCountUtils countUtils) {
        String modelType = rabbitMqTranslateVO.getModeType();
        appInsights.trackTrace("DB翻译模块：" + modelType + " 用户 ： " + rabbitMqTranslateVO.getShopName() + " targetCode ：" + rabbitMqTranslateVO.getTarget() + " source : " + rabbitMqTranslateVO.getSource());
        commonTranslate(rabbitMqTranslateVO, countUtils);
        //更新用户token
    }

    /**
     * 翻译产品，具体待补充
     */
    public String productTranslate(RabbitMqTranslateVO rabbitMqTranslateVO, CharacterCountUtils counter, TranslateTextDO translateTextDO, String handleType) {
        String textKey = translateTextDO.getTextKey();
        String value = translateTextDO.getSourceText();
        String source = rabbitMqTranslateVO.getSource();
        String shopName = translateTextDO.getShopName();
        TranslateRequest translateRequest = new TranslateRequest(0, shopName, rabbitMqTranslateVO.getAccessToken(), source, rabbitMqTranslateVO.getTarget(), value);
        //根据key值选择翻译方法
        String productKeyByKey = getProductKeyByKey(translateTextDO.getTextKey());
        return switch (textKey) {
            case "title", "body_html", "meta_description", "meta_title" ->
                    jsoupUtils.translateKeyModelAndCount(translateRequest, counter, rabbitMqTranslateVO.getLanguagePack(), rabbitMqTranslateVO.getLimitChars(), productKeyByKey, rabbitMqTranslateVO.getCustomKey(), rabbitMqTranslateVO.getTranslationModel());
            case "handle", "product_type" ->
                    jsoupUtils.checkTranslationApi(translateRequest, counter, rabbitMqTranslateVO.getLimitChars(), rabbitMqTranslateVO.getTranslationModel());
            default ->
                    jsoupUtils.translateAndCount(translateRequest, counter, rabbitMqTranslateVO.getLanguagePack(), handleType, rabbitMqTranslateVO.getLimitChars());
        };
    }

    /**
     * 翻译文章，具体待补充
     */
    public String articleTranslate(RabbitMqTranslateVO rabbitMqTranslateVO, CharacterCountUtils counter, TranslateTextDO translateTextDO, String handleType) {
        String textKey = translateTextDO.getTextKey();
        String value = translateTextDO.getSourceText();
        String source = rabbitMqTranslateVO.getSource();
        String shopName = translateTextDO.getShopName();
        TranslateRequest translateRequest = new TranslateRequest(0, shopName, rabbitMqTranslateVO.getAccessToken(), source, rabbitMqTranslateVO.getTarget(), value);
        //根据key值选择翻译方法
        String articleKeyByKey = getArticleKeyByKey(translateTextDO.getTextKey());
        return switch (textKey) {
            case "title", "body_html", "meta_description", "meta_title", "summary_html" ->
                    jsoupUtils.translateKeyModelAndCount(translateRequest, counter, rabbitMqTranslateVO.getLanguagePack(), rabbitMqTranslateVO.getLimitChars(), articleKeyByKey, rabbitMqTranslateVO.getCustomKey(), rabbitMqTranslateVO.getTranslationModel());
            case "handle" ->
                    jsoupUtils.checkTranslationApi(translateRequest, counter, rabbitMqTranslateVO.getLimitChars(), rabbitMqTranslateVO.getTranslationModel());
            default ->
                    jsoupUtils.translateAndCount(translateRequest, counter, rabbitMqTranslateVO.getLanguagePack(), handleType, rabbitMqTranslateVO.getLimitChars());
        };

    }

    /**
     * 翻译通用模块数据
     */
    public void commonTranslate(RabbitMqTranslateVO rabbitMqTranslateVO, CharacterCountUtils countUtils) {
        //根据DB的请求语句获取对应shopify值
        String shopifyDataByDb = getShopifyDataByDb(rabbitMqTranslateVO);
        Set<TranslateTextDO> needTranslatedData = translatedDataParse(stringToJson(shopifyDataByDb), rabbitMqTranslateVO.getShopName(), rabbitMqTranslateVO.getIsCover());
        if (needTranslatedData == null) {
            return;
        }
        Set<TranslateTextDO> filterTranslateData = filterNeedTranslateSet(rabbitMqTranslateVO, needTranslatedData);
        //将翻译的数据分类，提示词，普通文本，html
        Map<String, Set<TranslateTextDO>> stringSetMap = initTranslateMap();
        //将筛选好的数据分类
        Map<String, Set<TranslateTextDO>> stringSetMap1 = filterTranslateMap(stringSetMap, filterTranslateData, rabbitMqTranslateVO.getGlossaryMap());
        //实现功能： 分析三种类型数据， 添加模块标识，开始翻译
        translateMapData(stringSetMap1, rabbitMqTranslateVO, countUtils);
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
            appInsights.trackTrace("Failed to get Shopify data errors : " + e.getMessage());
        }
        return shopifyData;
    }


    /**
     * 解析shopifyData数据，分析数据是否可用，然后判断出可翻译数据
     */
    public static Set<TranslateTextDO> translatedDataParse(JsonNode shopDataJson, String shopName, Boolean isCover) {
        Set<TranslateTextDO> doubleTranslateTextDOSet = new HashSet<>();
        try {
            // 获取 translatableResources 节点
            JsonNode translatableResourcesNode = shopDataJson.path("translatableResources");
            if (!translatableResourcesNode.isObject()) {
                return null;
            }
            // 处理 nodes 数组
            JsonNode nodesNode = translatableResourcesNode.path("nodes");
            if (!nodesNode.isArray()) {
                return null;
            }
            // nodesArray.size()相当于resourceId的数量，相当于items数
            ArrayNode nodesArray = (ArrayNode) nodesNode;
            for (JsonNode nodeElement : nodesArray) {
                if (nodeElement.isObject()) {
                    Set<TranslateTextDO> stringTranslateTextDOSet = needTranslatedSet(nodeElement, shopName, isCover);
                    doubleTranslateTextDOSet.addAll(stringTranslateTextDOSet);
                }
            }
        } catch (Exception e) {
            appInsights.trackTrace("分析数据失败 errors : " + e);
        }
        return doubleTranslateTextDOSet;
    }

    /**
     * 分析用户需要翻译的数据
     */
    public static Set<TranslateTextDO> needTranslatedSet(JsonNode shopDataJson, String shopName, Boolean isCover) {
        String resourceId;
        Iterator<Map.Entry<String, JsonNode>> fields = shopDataJson.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            String fieldName = field.getKey();
            JsonNode fieldValue = field.getValue();

            // 根据字段名称进行处理
            if ("resourceId".equals(fieldName)) {
                if (fieldValue == null) {
                    continue;
                }
                resourceId = fieldValue.asText(null);
                // 提取翻译内容映射
                Map<String, TranslateTextDO> partTranslateTextDOMap = extractTranslationsByResourceId(shopDataJson, resourceId, shopName);
                Map<String, TranslateTextDO> partTranslatedTextDOMap = extractTranslatedDataByResourceId(shopDataJson, partTranslateTextDOMap, isCover);
                Set<TranslateTextDO> notNeedTranslatedSet = new HashSet<>(partTranslatedTextDOMap.values());
                return new HashSet<>(notNeedTranslatedSet);
            }
        }
        return new HashSet<>();
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
     * 获取所有的resourceId下的已翻译数据
     */
    public static Map<String, TranslateTextDO> extractTranslatedDataByResourceId(JsonNode shopDataJson, Map<String, TranslateTextDO> partTranslateTextDOSet, Boolean isCover) {
        JsonNode contentNode = shopDataJson.path("translations");
        if (contentNode.isArray() && !contentNode.isEmpty() && !isCover) {
            contentNode.forEach(content -> {
                if (partTranslateTextDOSet == null) {
                    return;
                }
                String key = content.path("key").asText(null);
                String outdated = content.path("outdated").asText(null);
                if (outdated == null || "false".equals(outdated)) {
                    partTranslateTextDOSet.remove(key);
                }

            });
        }
        return partTranslateTextDOSet;
    }

    /**
     * 遍历needTranslatedSet, 对Set集合进行通用规则的筛选，返回筛选后的数据
     */
    public static Set<TranslateTextDO> filterNeedTranslateSet(RabbitMqTranslateVO rabbitMqTranslateVO, Set<TranslateTextDO> needTranslateSet) {
        Iterator<TranslateTextDO> iterator = needTranslateSet.iterator();
        String modeType = rabbitMqTranslateVO.getModeType();
        while (iterator.hasNext()) {
            TranslateTextDO translateTextDO = iterator.next();
//            System.out.println("translateTextDO: " + translateTextDO);
            String value = translateTextDO.getSourceText();

            // 当 value 为空时跳过
            if (!isValueBlank(value)) {
                iterator.remove(); //  安全删除
                continue;
            }

            String type = translateTextDO.getTextType();

            // 如果是特定类型，也从集合中移除
            if ("FILE_REFERENCE".equals(type) || "LINK".equals(type)
                    || "LIST_FILE_REFERENCE".equals(type) || "LIST_LINK".equals(type)
                    || "LIST_URL".equals(type)
                    || "JSON".equals(type)
                    || "JSON_STRING".equals(type)
            ) {
                iterator.remove(); // 根据业务条件删除
                continue;
            }

            //判断数据是不是json数据。是的话删除
            if (isJson(value)) {
                iterator.remove(); // 根据业务条件删除
                continue;
            }

            String key = translateTextDO.getTextKey();
            //如果handleFlag为fa;se，则跳过
            if (type.equals(URI) && "handle".equals(key)) {
                if (!rabbitMqTranslateVO.getHandleFlag()) {
                    iterator.remove();
                    continue;
                }
            }

            //通用的不翻译数据
            if (!generalTranslate(key, value)) {
                iterator.remove(); // 根据业务条件删除
                continue;
            }

            //产品的筛选规则
            if (PRODUCT_OPTION.equals(modeType) && "color".equalsIgnoreCase(value) || "size".equalsIgnoreCase(value)) {
                iterator.remove();
                continue;
            }

            //如果是theme模块的数据
            if (TRANSLATABLE_RESOURCE_TYPES.contains(modeType)) {
                //如果是html放html文本里面
                if (isHtml(value)) {
                    continue;
                }

                //对key中含section和general的做key值判断
                if (GENERAL_OR_SECTION_PATTERN.matcher(key).find()) {
                    //进行白名单的确认
                    if (whiteListTranslate(key)) {
                        continue;
                    }

                    //如果包含对应key和value，则跳过
                    if (!shouldTranslate(key, value)) {
                        iterator.remove();
                        continue;
                    }
                }
            }
            //对METAOBJECT字段翻译
            if (modeType.equals(METAOBJECT)) {
                if (isJson(value)) {
                    iterator.remove();
                    continue;
                }
            }

            //对METAFIELD字段翻译
            if (modeType.equals(METAFIELD)) {
                //如UXxSP8cSm，UgvyqJcxm。有大写字母和小写字母的组合。有大写字母，小写字母和数字的组合。 10位 字母和数字不翻译
                if (SUSPICIOUS_PATTERN.matcher(value).matches() || SUSPICIOUS2_PATTERN.matcher(value).matches()) {
                    iterator.remove();
                    continue;
                }
                if (!metaTranslate(value)) {
                    iterator.remove();
                    continue;
                }
                //如果是base64编码的数据，不翻译
                if (BASE64_PATTERN.matcher(value).matches()) {
                    printTranslateReason(value + "是base64编码的数据, key是： " + key);
                    iterator.remove();
                    continue;
                }
                if (isJson(value)) {
                    iterator.remove();
                    continue;
                }
            }

        }
        return needTranslateSet;
    }

    /**
     * 初始化map集合
     */
    public static Map<String, Set<TranslateTextDO>> initTranslateMap() {
        Map<String, Set<TranslateTextDO>> judgeData = new HashMap<>();
        judgeData.put(HTML, new HashSet<>());
        judgeData.put(PLAIN_TEXT, new HashSet<>());
        judgeData.put(GLOSSARY, new HashSet<>());
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
            } else {
                stringSetMap.get(PLAIN_TEXT).add(translateTextDO);
            }

        }
        return stringSetMap;
    }

    /**
     * 分析三种类型数据，添加模块标识，开始翻译
     */
    public void translateMapData(Map<String, Set<TranslateTextDO>> stringSetMap, RabbitMqTranslateVO rabbitMqTranslateVO, CharacterCountUtils countUtils) {
        if (stringSetMap == null || stringSetMap.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Set<TranslateTextDO>> entry : stringSetMap.entrySet()) {
            switch (entry.getKey()) {
                case HTML:
                    translateHtmlData(entry.getValue(), rabbitMqTranslateVO, countUtils);
                    break;
                case PLAIN_TEXT:
                    translatePlainTextData(entry.getValue(), rabbitMqTranslateVO, countUtils);
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
     * HTML文本翻译
     */
    public void translateHtmlData(Set<TranslateTextDO> htmlData, RabbitMqTranslateVO rabbitMqTranslateVO, CharacterCountUtils counter) {
        String shopName = rabbitMqTranslateVO.getShopName();
        String target = rabbitMqTranslateVO.getTarget();
        Integer limitChars = rabbitMqTranslateVO.getLimitChars();
        String accessToken = rabbitMqTranslateVO.getAccessToken();
        ShopifyRequest shopifyRequest = new ShopifyRequest(shopName, accessToken, APIVERSION, target);
        for (TranslateTextDO translateTextDO : htmlData) {
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
            //html翻译
            translateGeneralHtmlData(translateTextDO, rabbitMqTranslateVO, counter, translation, shopifyRequest);
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
            TranslateRequest translateRequest = new TranslateRequest(0, rabbitMqTranslateVO.getShopName(), rabbitMqTranslateVO.getAccessToken(), source, rabbitMqTranslateVO.getTarget(), translateTextDO.getSourceText());
            //判断产品模块用完全翻译，其他模块用分段html翻译
//            if (rabbitMqTranslateVO.getModeType().equals(PRODUCT) && "body_html".equals(key)) {
//                htmlTranslation = liquidHtmlTranslatorUtils.fullTranslateHtmlByQwen(sourceText, rabbitMqTranslateVO.getLanguagePack(), counter, translateRequest.getTarget(), rabbitMqTranslateVO.getShopName(), rabbitMqTranslateVO.getLimitChars());
//            } else
            htmlTranslation = switch (rabbitMqTranslateVO.getModeType()) {
                case SHOP_POLICY ->
                        liquidHtmlTranslatorUtils.fullTranslatePolicyHtmlByQwen(sourceText, counter, rabbitMqTranslateVO.getTarget(), rabbitMqTranslateVO.getShopName(), rabbitMqTranslateVO.getLimitChars());
                case PRODUCT, ARTICLE ->
                        liquidHtmlTranslatorUtils.fullTranslateHtmlByQwen(sourceText, rabbitMqTranslateVO.getLanguagePack(), counter, translateRequest.getTarget(), rabbitMqTranslateVO.getShopName(), rabbitMqTranslateVO.getLimitChars(), rabbitMqTranslateVO.getTranslationModel(), source);
//                        liquidHtmlTranslatorUtils.translateNewHtml(sourceText, translateRequest, counter, rabbitMqTranslateVO.getLanguagePack(), rabbitMqTranslateVO.getLimitChars(), rabbitMqTranslateVO.getModeType(), rabbitMqTranslateVO.getCustomKey(), rabbitMqTranslateVO.getTranslationModel());
                default ->
                        liquidHtmlTranslatorUtils.translateNewHtml(sourceText, translateRequest, counter, rabbitMqTranslateVO.getLanguagePack(), rabbitMqTranslateVO.getLimitChars(), null, null, null);
            };

            if (rabbitMqTranslateVO.getModeType().equals(METAFIELD)) {
                //对翻译后的html做格式处理
                htmlTranslation = normalizeHtml(htmlTranslation);
            }
        } catch (Exception e) {
            appInsights.trackTrace("html translation errors : " + e.getMessage());
            shopifyService.saveToShopify(sourceText, translation, resourceId, shopifyRequest);
            return;
        }
        shopifyService.saveToShopify(htmlTranslation, translation, resourceId, shopifyRequest);
        printTranslation(htmlTranslation, sourceText, translation, shopifyRequest.getShopName(), rabbitMqTranslateVO.getModeType(), resourceId, source);

        //如果翻译数据小于255，存到数据库
        try {
            // 255字符以内 和 数据库内有该数据类型 文本才能插入数据库
            vocabularyService.InsertOne(rabbitMqTranslateVO.getTarget(), htmlTranslation, source, sourceText);
        } catch (Exception e) {
            appInsights.trackTrace("存储失败： " + e.getMessage() + " ，继续翻译");
        }
    }

    /**
     * 判断停止标识
     */
    private boolean checkNeedStopped(String shopName, CharacterCountUtils counter) {
        if (userStopFlags.get(shopName).get()) {
            // 更新数据库中的已使用字符数
            appInsights.trackTrace(shopName + " 用户 消耗的token ： " + counter.getTotalChars());
//            translationCounterService.updateUsedCharsByShopName(new TranslationCounterRequest(0, shopName, 0, counter.getTotalChars(), 0, 0, 0));
            // 将翻译状态为2改为“部分翻译”
            translatesService.update(new UpdateWrapper<TranslatesDO>().eq("shop_name", shopName).eq("status", 2).set("status", 3));
            // 将task表数据都改为 5
            translateTasksService.updateByShopName(shopName, 5);
            return true;
        }
        return false;
    }


    /**
     * 文本翻译
     */
    public void translatePlainTextData(Set<TranslateTextDO> plainTextData, RabbitMqTranslateVO rabbitMqTranslateVO, CharacterCountUtils counter) {
        String shopName = rabbitMqTranslateVO.getShopName();
        String target = rabbitMqTranslateVO.getTarget();
        Integer limitChars = rabbitMqTranslateVO.getLimitChars();
        String accessToken = rabbitMqTranslateVO.getAccessToken();
        ShopifyRequest shopifyRequest = new ShopifyRequest(shopName, accessToken, APIVERSION, target);
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
                translateGeneralTextData(translateTextDO, rabbitMqTranslateVO, counter, translation, shopifyRequest);
            } catch (Exception e) {
                appInsights.trackTrace("value : " + value + " 翻译失败 errors ：" + e.getMessage());
            }
        }
    }

    /**
     * 通用文本翻译
     */
    public void translateGeneralTextData(TranslateTextDO translateTextDO, RabbitMqTranslateVO rabbitMqTranslateVO, CharacterCountUtils counter, Map<String, Object> translation, ShopifyRequest shopifyRequest) {
        String value = translateTextDO.getSourceText();
        String source = rabbitMqTranslateVO.getSource();
        String resourceId = translateTextDO.getResourceId();
        String shopName = translateTextDO.getShopName();
        String modeType = rabbitMqTranslateVO.getModeType();
        String targetString = null;
        String handleType = "null";
        if (rabbitMqTranslateVO.getHandleFlag()) {
            handleType = HANDLE;
        }
        try {
            //在这里做模块判断用什么模型翻译
            targetString = translateTextDataByModeType(translateTextDO, rabbitMqTranslateVO, counter, translation, shopifyRequest, handleType);
        } catch (ClientException e) {
            appInsights.trackTrace("翻译失败 errors ： " + e.getMessage() + " ，继续翻译");
        }

        if (Objects.equals(targetString, METAFIELD)){
            return;
        }

        if (targetString == null) {
            appInsights.trackTrace("翻译失败后的字符 errors ： " + translateTextDO);
            shopifyService.saveToShopify(value, translation, resourceId, shopifyRequest);
            return;
        }

        if (!handleType.equals(HANDLE)) {
            addData(shopifyRequest.getTarget(), value, targetString);
        }
        shopifyService.saveToShopify(targetString, translation, resourceId, shopifyRequest);
        printTranslation(targetString, value, translation, shopName, modeType, resourceId, source);

        //存到数据库中
        try {
            if (handleType.equals(HANDLE)) {
                return;
            }
            // 255字符以内 和 数据库内有该数据类型 文本才能插入数据库
            vocabularyService.InsertOne(rabbitMqTranslateVO.getTarget(), targetString, rabbitMqTranslateVO.getSource(), value);
        } catch (Exception e) {
            appInsights.trackTrace("存储失败 errors ： " + e.getMessage() + " ，继续翻译");
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
        ShopifyRequest shopifyRequest = new ShopifyRequest(shopName, accessToken, APIVERSION, target);

        Map<String, Object> glossaryMap = rabbitMqTranslateVO.getGlossaryMap();
        if (glossaryMap== null || glossaryMap.isEmpty()) {
            return;
        }

        //关键词
        Map<String, String> keyMap1 = new HashMap<>();
        Map<String, String> keyMap0 = new HashMap<>();
        //将glossaryMap中所有caseSensitive为1的数据存到一个Map集合里面
        for (Map.Entry<String, Object> entry : glossaryMap.entrySet()) {
            GlossaryDO glossaryDO = OBJECT_MAPPER.convertValue(entry.getValue(), GlossaryDO.class);
            appInsights.trackTrace("shopName : " + shopName + " , glossaryDO : " + glossaryDO);
//            GlossaryDO glossaryDO = (GlossaryDO) entry.getValue();
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
            addData(shopifyRequest.getTarget(), value, finalText);
            shopifyService.saveToShopify(finalText, translation, resourceId, shopifyRequest);
            printTranslation(finalText, value, translation, shopifyRequest.getShopName(), modeType, resourceId, source);
        } catch (Exception e) {
            appInsights.trackTrace("glossaryTranslationModel errors " + e);
            shopifyService.saveToShopify(value, translation, resourceId, shopifyRequest);
        }
    }

    /**
     * 达到字符限制，更新用户剩余字符数，终止循环
     */
    public void updateCharsWhenExceedLimit(CharacterCountUtils counter, String shopName, int remainingChars, TranslateRequest translateRequest) {
        TranslationCounterRequest request = new TranslationCounterRequest();
        request.setShopName(shopName);

        if (counter.getTotalChars() >= remainingChars) {
            appInsights.trackTrace("shopName 用户 消耗的token : " + shopName + " totalChars : " + counter.getTotalChars() + " limitChars : " + remainingChars);
            translatesService.updateTranslateStatus(shopName, 3, translateRequest.getTarget(), translateRequest.getSource(), translateRequest.getAccessToken());
            //将同一个shopNmae的task任务的状态。除邮件发送模块改为3.
            updateTranslateTasksStatus(shopName);
            throw new ClientException(CHARACTER_LIMIT);
        }
    }

    /**
     * 缓存翻译
     */
    public boolean cacheOrDatabaseTranslateData(String value, String source, Map<String, Object> translation, String resourceId, ShopifyRequest request) {
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
            targetText = vocabularyService.getTranslateTextDataInVocabulary(request.getTarget(), value, source);
        } catch (Exception e) {
            //打印错误信息
            appInsights.trackTrace("translateDataByDatabase errors : " + e.getMessage());
        }
        if (targetText != null) {
            targetText = isHtmlEntity(targetText);
            addData(request.getTarget(), value, targetText);
            shopifyService.saveToShopify(targetText, translation, resourceId, request);
            printTranslation(targetText, value, translation, request.getShopName(), DATABASE, resourceId, source);
            return true;
        }
        return false;
    }

    /**
     * 根据模块选择翻译Text方法
     */
    public String translateTextDataByModeType(TranslateTextDO translateTextDO, RabbitMqTranslateVO rabbitMqTranslateVO, CharacterCountUtils counter, Map<String, Object> translation, ShopifyRequest shopifyRequest, String handleType) {
        TranslateRequest translateRequest = new TranslateRequest(0, translateTextDO.getShopName(), rabbitMqTranslateVO.getAccessToken(), rabbitMqTranslateVO.getSource(), rabbitMqTranslateVO.getTarget(), translateTextDO.getSourceText());
        switch (rabbitMqTranslateVO.getModeType()) {
            case ONLINE_STORE_THEME:
            case ONLINE_STORE_THEME_APP_EMBED:
            case ONLINE_STORE_THEME_JSON_TEMPLATE:
            case ONLINE_STORE_THEME_SECTION_GROUP:
            case ONLINE_STORE_THEME_SETTINGS_CATEGORY:
            case ONLINE_STORE_THEME_SETTINGS_DATA_SECTIONS:
            case ONLINE_STORE_THEME_LOCALE_CONTENT, EMAIL_TEMPLATE, SHOP, SHOP_POLICY:
                //翻译主题
                return jsoupUtils.translateAndCount(translateRequest, counter, rabbitMqTranslateVO.getLanguagePack(), handleType, rabbitMqTranslateVO.getLimitChars());
            case PRODUCT:
                //产品
                return productTranslate(rabbitMqTranslateVO, counter, translateTextDO, handleType);
            case PRODUCT_OPTION:
            case PRODUCT_OPTION_VALUE, BLOG, COLLECTION, PACKING_SLIP_TEMPLATE, MENU, LINK, DELIVERY_METHOD_DEFINITION, FILTER, PAYMENT_GATEWAY, SELLING_PLAN, SELLING_PLAN_GROUP, PAGE:
                //机器翻译
                return jsoupUtils.checkTranslationApi(translateRequest, counter, rabbitMqTranslateVO.getLimitChars(), rabbitMqTranslateVO.getTranslationModel());
            case ARTICLE:
                return articleTranslate(rabbitMqTranslateVO, counter, translateTextDO, handleType);
            case METAFIELD:
                //元字段
                translateMetafieldTextData(translateTextDO, rabbitMqTranslateVO, counter, translation, shopifyRequest);
                return METAFIELD;
            case METAOBJECT:
                //机器翻译
                return jsoupUtils.checkTranslationApi(translateRequest, counter, rabbitMqTranslateVO.getLimitChars(), null);
            default:
                return jsoupUtils.translateAndCount(translateRequest, counter, rabbitMqTranslateVO.getLanguagePack(), handleType, rabbitMqTranslateVO.getLimitChars());
        }
    }

    /**
     * 元字段text翻译
     */
    public void translateMetafieldTextData(TranslateTextDO translateTextDO, RabbitMqTranslateVO rabbitMqTranslateVO, CharacterCountUtils counter, Map<String, Object> translation, ShopifyRequest shopifyRequest) {
        String value = translateTextDO.getSourceText();
        String source = rabbitMqTranslateVO.getSource();
        String resourceId = translateTextDO.getResourceId();
        String shopName = translateTextDO.getShopName();
        String handleType = "null";
        String type = translateTextDO.getTextType();
        if (rabbitMqTranslateVO.getHandleFlag()) {
            handleType = HANDLE;
        }

        if (SINGLE_LINE_TEXT_FIELD.equals(type) && !isHtml(value)) {
            //纯数字字母符号 且有两个  标点符号 以#开头，长度为10 不翻译
            if (isValidString(value)) {
                return;
            }

            //走翻译流程
            String translatedText = jsoupUtils.translateAndCount(new TranslateRequest(0, shopName, shopifyRequest.getAccessToken(), source, shopifyRequest.getTarget(), value), counter, rabbitMqTranslateVO.getLanguagePack(), handleType, rabbitMqTranslateVO.getLimitChars());
            shopifyService.saveToShopify(translatedText, translation, resourceId, shopifyRequest);
            printTranslation(translatedText, value, translation, shopifyRequest.getShopName(), type, resourceId, source);
            //存到数据库中
            try {
                if (handleType.equals(HANDLE)) {
                    return;
                }
                // 255字符以内 和 数据库内有该数据类型 文本才能插入数据库
                vocabularyService.InsertOne(shopifyRequest.getTarget(), translatedText, translateTextDO.getSourceCode(), value);
            } catch (Exception e) {
                appInsights.trackTrace("translateMetafield存储失败 errors ： " + e.getMessage() + " ，继续翻译");
            }
            return;
        }

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
                        String translated = jsoupUtils.translateAndCount(new TranslateRequest(0, shopName, shopifyRequest.getAccessToken(), source, shopifyRequest.getTarget(), value), counter, rabbitMqTranslateVO.getLanguagePack(), handleType, rabbitMqTranslateVO.getLimitChars());
                        //将数据填回去
                        resultList.set(i, translated);
                    }
                }
                //将list数据转为String 再存储到shopify本地
                String translatedValue = OBJECT_MAPPER.writeValueAsString(resultList);
                shopifyService.saveToShopify(translatedValue, translation, resourceId, shopifyRequest);
                printTranslation(translatedValue, value, translation, shopifyRequest.getShopName(), type, resourceId, source);

                //存到数据库中
                try {
                    // 255字符以内 和 数据库内有该数据类型 文本才能插入数据库
                    vocabularyService.InsertOne(shopifyRequest.getTarget(), translatedValue, translateTextDO.getSourceCode(), value);
                } catch (Exception e) {
                    appInsights.trackTrace("translateMetafield存储失败 errors ： " + e.getMessage() + " ，继续翻译");
                }
            } catch (Exception e) {
                //存原数据到shopify本地
                shopifyService.saveToShopify(value, translation, resourceId, shopifyRequest);
                appInsights.trackTrace("LIST errors 错误原因： " + e.getMessage());
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
            if ("ciwishop.myshopify.com".equals(shopName)){
                translatesService.updateTranslateStatus(shopName, 1, target, source, accessToken);
                tencentEmailService.translateSuccessEmail(new TranslateRequest(0, shopName, accessToken, source, target, null), counter, startTime, startChars, limitChars, false);
                translateTasksService.updateByTaskId(task.getTaskId(), 1);
            }else {
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
                if (!rabbitMqTranslateVO.getShopifyData().equals(EMAIL)) {
                    translateTasksService.updateByTaskId(translateTasksDO.getTaskId(), 3);
                }
            } catch (JsonProcessingException e) {
                appInsights.trackTrace(" errors : " + e);
            }
        }
    }

    /**
     * 异步8分钟后，发送翻译成功的邮件
     */
    public void triggerSendEmailLater(String shopName, String target, String source, String accessToken, TranslateTasksDO task, CharacterCountUtils counter, LocalDateTime startTime, Integer startChars, Integer limitChars) {
        // 创建一个任务 Runnable
        Runnable delayedTask = () -> {
            appInsights.trackTrace("date2: " + LocalDateTime.now());
            translatesService.updateTranslateStatus(shopName, 1, target, source, accessToken);
            tencentEmailService.translateSuccessEmail(new TranslateRequest(0, shopName, accessToken, source, target, null), counter, startTime, startChars, limitChars, false);
            translateTasksService.updateByTaskId(task.getTaskId(), 1);
        };

        // 设置执行时间为当前时间 + 10分钟（使用 Instant 代替 Date）
        Instant runAt = Instant.now().plusSeconds(8 * 60);

        // 使用推荐的 API
        taskScheduler.schedule(delayedTask, runAt);
    }

    /**
     * 更新用户token计数
     * */
    public void countAfterTranslated(TranslateRequest request) {
        //更新初始值
        try {
            userTypeTokenService.startTokenCount(request);
        } catch (Exception e) {
            appInsights.trackTrace("重新更新token值失败！！！ errors : " + e.getMessage());
        }
    }
}
