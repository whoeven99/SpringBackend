package com.bogdatech.logic;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.bogdatech.Service.ITranslateTasksService;
import com.bogdatech.Service.ITranslatesService;
import com.bogdatech.Service.ITranslationCounterService;
import com.bogdatech.Service.IVocabularyService;
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
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

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
import static com.bogdatech.utils.ModelUtils.translateModel;
import static com.bogdatech.utils.PrintUtils.printTranslation;
import static com.bogdatech.utils.RegularJudgmentUtils.isValidString;
import static com.bogdatech.utils.StringUtils.isValueBlank;
import static com.bogdatech.utils.TypeConversionUtils.*;

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

    @Autowired
    public RabbitMqTranslateService(ITranslationCounterService translationCounterService, ITranslatesService translatesService, ShopifyService shopifyService, IVocabularyService vocabularyService, TencentEmailService tencentEmailService, GlossaryService glossaryService, AILanguagePackService aiLanguagePackService, JsoupUtils jsoupUtils, LiquidHtmlTranslatorUtils liquidHtmlTranslatorUtils, ITranslateTasksService translateTasksService) {
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
    }

    /**
     * MQ翻译
     * 读取该用户的shopify数据，然后循环获取模块数据
     */
    @Async
    public void mqTranslate(ClickTranslateRequest clickTranslateRequest) {
        //判断前端传的数据是否完整，如果不完整，报错
        if (clickTranslateRequest.getShopName() == null || clickTranslateRequest.getShopName().isEmpty()
                || clickTranslateRequest.getAccessToken() == null || clickTranslateRequest.getAccessToken().isEmpty()
                || clickTranslateRequest.getSource() == null || clickTranslateRequest.getSource().isEmpty()
                || clickTranslateRequest.getTarget() == null || clickTranslateRequest.getTarget().isEmpty()) {
            return;
        }

        //将ClickTranslateRequest转换为TranslateRequest
        TranslateRequest request = ClickTranslateRequestToTranslateRequest(clickTranslateRequest);
        ShopifyRequest shopifyRequest = convertTranslateRequestToShopifyRequest(request);
        //判断字符是否超限
        TranslationCounterDO request1 = translationCounterService.readCharsByShopName(request.getShopName());
        Integer limitChars = translationCounterService.getMaxCharsByShopName(request.getShopName());

//        一个用户当前只能翻译一条语言，根据用户的status判断
        List<Integer> integers = translatesService.readStatusInTranslatesByShopName(request.getShopName());
        for (Integer integer : integers) {
            if (integer == 2) {
                return;
            }
        }

        int usedChars = request1.getUsedChars();
        // 如果字符超限，则直接返回字符超限
        if (usedChars >= limitChars) {
            return;
        }

        userEmailStatus.put(clickTranslateRequest.getShopName(), new AtomicBoolean(false)); //重置用户发送的邮件
        userStopFlags.put(clickTranslateRequest.getShopName(), new AtomicBoolean(false));  // 初始化用户的停止标志

        //初始化计数器
        CharacterCountUtils counter = new CharacterCountUtils();
        counter.addChars(usedChars);

        //判断是否有handle
        boolean handleFlag = false;
        List<String> translateModel = clickTranslateRequest.getTranslateSettings3();
        if (translateModel.contains("handle")) {
            translateModel.removeIf("handle"::equals);
            handleFlag = true;
        }
        appInsights.trackTrace(clickTranslateRequest.getShopName() + " 用户 要翻译的数据 " + clickTranslateRequest.getTranslateSettings3() + " handleFlag: " + handleFlag);
        //修改模块的排序
        List<String> translateResourceDTOS = null;
        try {
            translateResourceDTOS = translateModel(translateModel);
        } catch (Exception e) {
            appInsights.trackTrace("translateModel errors : " + e.getMessage());
        }
//      翻译
        if (translateResourceDTOS == null || translateResourceDTOS.isEmpty()) {
            return;
        }

        appInsights.trackTrace("DB普通翻译开始");
        //将初始化用户翻译value为null
        userTranslate.put(request.getShopName(), " and ");
        //判断是否有同义词
        Map<String, Object> glossaryMap = new HashMap<>();
        glossaryService.getGlossaryByShopName(shopifyRequest, glossaryMap);

        //获取目前所使用的AI语言包
        String languagePackId = aiLanguagePackService.getCategoryByDescription(shopifyRequest.getShopName(), shopifyRequest.getAccessToken(), counter, limitChars);
        //通过判断status和字符判断后 就将状态改为2，则开始翻译流程
        translatesService.updateTranslateStatus(request.getShopName(), 2, request.getTarget(), request.getSource(), request.getAccessToken());
        RabbitMqTranslateVO rabbitMqTranslateVO = new RabbitMqTranslateVO(null, shopifyRequest.getShopName(), shopifyRequest.getAccessToken(), request.getSource(), request.getTarget(), languagePackId, handleFlag, glossaryMap, null, limitChars, usedChars, LocalDateTime.now().toString(), translateResourceDTOS);
        for (TranslateResourceDTO translateResource : ALL_RESOURCES
        ) {
            rabbitMqTranslateVO.setModeType(translateResource.getResourceType());
            if (!translateResourceDTOS.contains(translateResource.getResourceType())) {
                continue;
            }
            if (translateResource.getResourceType().equals(SHOP_POLICY) || translateResource.getResourceType().equals(PAYMENT_GATEWAY)) {
                continue;
            }

            if (EXCLUDED_SHOPS.contains(request.getShopName()) && PRODUCT_MODEL.contains(translateResource.getResourceType())) {
                continue;
            }

            // 定期检查是否停止
            if (checkNeedStopped(request.getShopName(), counter)) {
                System.out.println();
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
                appInsights.trackTrace("保存翻译任务失败 errors " + e);
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
            System.out.println("保存翻译任务失败" + e);
        }
//        amqpTemplate.convertAndSend(USER_TRANSLATE_EXCHANGE, USER_TRANSLATE_ROUTING_KEY + rabbitMqTranslateVO.getShopName(), rabbitMqTranslateVO);
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
//                System.out.println("获取下一页： " + endCursor);
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

    //根据模块选择翻译方法
    public void translateByModeType(RabbitMqTranslateVO rabbitMqTranslateVO, CharacterCountUtils countUtils) {
        String modelType = rabbitMqTranslateVO.getModeType();
        appInsights.trackTrace("DB翻译模块：" + modelType);
        commonTranslate(rabbitMqTranslateVO, countUtils);
        //更新用户token

    }

    /**
     * 翻译主题，具体待补充
     */
    public void themeTranslate(RabbitMqTranslateVO rabbitMqTranslateVO, CharacterCountUtils countUtils) {
        //翻译主题

    }

    /**
     * 翻译产品，具体待补充
     */
    public void productTranslate(RabbitMqTranslateVO rabbitMqTranslateVO, CharacterCountUtils countUtils) {

    }

    /**
     * 翻译产品设置，具体待补充
     */
    public void productOptionTranslate(RabbitMqTranslateVO rabbitMqTranslateVO, CharacterCountUtils countUtils) {

    }

    /**
     * 翻译产品设置值，具体待补充
     */
    public void productOptionValueTranslate(RabbitMqTranslateVO rabbitMqTranslateVO, CharacterCountUtils countUtils) {

    }

    /**
     * 翻译页面，
     */
    public void pageTranslate(RabbitMqTranslateVO rabbitMqTranslateVO, CharacterCountUtils countUtils) {

    }

    /**
     * 翻译文章，具体待补充
     */
    public void articleTranslate(RabbitMqTranslateVO rabbitMqTranslateVO, CharacterCountUtils countUtils) {

    }

    /**
     * 翻译元字段，具体待补充
     */
    public void metaFieldTranslate(RabbitMqTranslateVO rabbitMqTranslateVO, CharacterCountUtils countUtils) {

    }

    /**
     * 翻译元对象，具体待补充
     */
    public void metaObjectTranslate(RabbitMqTranslateVO rabbitMqTranslateVO, CharacterCountUtils countUtils) {

    }

    /**
     * 翻译邮件，具体待补充
     */
    public void emailTemplateTranslate(RabbitMqTranslateVO rabbitMqTranslateVO, CharacterCountUtils countUtils) {

    }

    /**
     * 翻译通用模块数据
     */
    public void commonTranslate(RabbitMqTranslateVO rabbitMqTranslateVO, CharacterCountUtils countUtils) {
        //根据DB的请求语句获取对应shopify值
        String shopifyDataByDb = getShopifyDataByDb(rabbitMqTranslateVO);
        Set<TranslateTextDO> needTranslatedData = translatedDataParse(stringToJson(shopifyDataByDb), rabbitMqTranslateVO.getShopName());
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
     * */
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
            System.out.println("Failed to get Shopify data: " + e.getMessage());
        }
        return shopifyData;
    }



    /**
     * 解析shopifyData数据，分析数据是否可用，然后判断出可翻译数据
     */
    public static Set<TranslateTextDO> translatedDataParse(JsonNode shopDataJson, String shopName) {
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
                    Set<TranslateTextDO> stringTranslateTextDOSet = needTranslatedSet(nodeElement, shopName);
                    doubleTranslateTextDOSet.addAll(stringTranslateTextDOSet);
                }
            }
        } catch (Exception e) {
            System.out.println("分析数据失败 errors : " + e);
        }
        return doubleTranslateTextDOSet;
    }

    /**
     * 分析用户需要翻译的数据
     */
    public static Set<TranslateTextDO> needTranslatedSet(JsonNode shopDataJson, String shopName) {
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
                Map<String, TranslateTextDO> partTranslatedTextDOMap = extractTranslatedDataByResourceId(shopDataJson, partTranslateTextDOMap);
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
    public static Map<String, TranslateTextDO> extractTranslatedDataByResourceId(JsonNode shopDataJson, Map<String, TranslateTextDO> partTranslateTextDOSet) {
        JsonNode contentNode = shopDataJson.path("translations");
        if (contentNode.isArray() && !contentNode.isEmpty()) {
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
                    || modeType.equals(SHOP_POLICY)) {
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
                if (!TRANSLATABLE_KEY_PATTERN.matcher(key).matches()) {
                    iterator.remove();
                    continue;
                }
                //如果包含对应key和value，则跳过
                if (!shouldTranslate(key, value)) {
                    iterator.remove();
                    continue;
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
        String htmlTranslation = null;
        String resourceId = translateTextDO.getResourceId();
        String source = rabbitMqTranslateVO.getSource();
        try {
            TranslateRequest translateRequest = new TranslateRequest(0, rabbitMqTranslateVO.getShopName(), rabbitMqTranslateVO.getAccessToken(), source, rabbitMqTranslateVO.getTarget(), translateTextDO.getSourceText());
            htmlTranslation = liquidHtmlTranslatorUtils.translateNewHtml(sourceText, translateRequest, counter, rabbitMqTranslateVO.getLanguagePack(), rabbitMqTranslateVO.getLimitChars());
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
//            translationCounterService.updateUsedCharsByShopName(new TranslationCounterRequest(0, shopName, 0, counter.getTotalChars(), 0, 0, 0));
            // 将翻译状态为2改为“部分翻译”
            translatesService.updateStatusByShopNameAnd2(shopName);
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
                translateTextDataByModeType(translateTextDO, rabbitMqTranslateVO, counter, translation, shopifyRequest);
            } catch (Exception e) {
                appInsights.trackTrace("value : " + value + " 翻译失败 errors ：" + e.getMessage());
                throw new RuntimeException(e);
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
            targetString = jsoupUtils.translateAndCount(new TranslateRequest(0, shopName, shopifyRequest.getAccessToken(), source, shopifyRequest.getTarget(), value), counter, rabbitMqTranslateVO.getLanguagePack(), handleType, rabbitMqTranslateVO.getLimitChars());
        } catch (ClientException e) {
            appInsights.trackTrace("翻译失败 errors ： " + e.getMessage() + " ，继续翻译");
        }

        if (targetString == null) {
            appInsights.trackTrace("翻译失败后的字符 errors ： " + translateTextDO);
            shopifyService.saveToShopify(value, translation, resourceId, shopifyRequest);
            return;
        }
        addData(shopifyRequest.getTarget(), value, targetString);
        shopifyService.saveToShopify(targetString, translation, resourceId, shopifyRequest);
        printTranslation(targetString, value, translation, shopName, modeType, resourceId, source);

        //存到数据库中
        try {
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
            translatesService.updateTranslateStatus(shopName, 3, translateRequest.getTarget(), translateRequest.getSource(), translateRequest.getAccessToken());
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
    public void translateTextDataByModeType(TranslateTextDO translateTextDO, RabbitMqTranslateVO rabbitMqTranslateVO, CharacterCountUtils counter, Map<String, Object> translation, ShopifyRequest shopifyRequest) {
        switch (rabbitMqTranslateVO.getModeType()) {
            case ONLINE_STORE_THEME:
            case ONLINE_STORE_THEME_APP_EMBED:
            case ONLINE_STORE_THEME_JSON_TEMPLATE:
            case ONLINE_STORE_THEME_SECTION_GROUP:
            case ONLINE_STORE_THEME_SETTINGS_CATEGORY:
            case ONLINE_STORE_THEME_SETTINGS_DATA_SECTIONS:
            case ONLINE_STORE_THEME_LOCALE_CONTENT:
                //翻译主题
                //普通文本翻译
                translateGeneralTextData(translateTextDO, rabbitMqTranslateVO, counter, translation, shopifyRequest);
                break;
            case PRODUCT:
                //产品
                //普通文本翻译
                translateGeneralTextData(translateTextDO, rabbitMqTranslateVO, counter, translation, shopifyRequest);
                break;
            case PRODUCT_OPTION:
                //产品设置
                //普通文本翻译
                translateGeneralTextData(translateTextDO, rabbitMqTranslateVO, counter, translation, shopifyRequest);
                break;
            case PRODUCT_OPTION_VALUE:
                //产品设置值
                //普通文本翻译
                translateGeneralTextData(translateTextDO, rabbitMqTranslateVO, counter, translation, shopifyRequest);
                break;
            case PAGE:
                //页面
                //普通文本翻译
                translateGeneralTextData(translateTextDO, rabbitMqTranslateVO, counter, translation, shopifyRequest);
                break;
            case ARTICLE:
            case BLOG:
                //文章
                //普通文本翻译
                translateGeneralTextData(translateTextDO, rabbitMqTranslateVO, counter, translation, shopifyRequest);
                break;
            case METAFIELD:
                //元字段
                translateMetafieldTextData(translateTextDO, rabbitMqTranslateVO, counter, translation, shopifyRequest);
                break;
            case METAOBJECT:
                //元对象
                translateGeneralTextData(translateTextDO, rabbitMqTranslateVO, counter, translation, shopifyRequest);
                break;
            case EMAIL_TEMPLATE:
                //邮件
                //普通文本翻译
                translateGeneralTextData(translateTextDO, rabbitMqTranslateVO, counter, translation, shopifyRequest);
                break;
            case COLLECTION:
            case PACKING_SLIP_TEMPLATE:
            case MENU:
            case LINK:
            case DELIVERY_METHOD_DEFINITION:
            case FILTER:
            case PAYMENT_GATEWAY:
            case SELLING_PLAN:
            case SELLING_PLAN_GROUP:
            case SHOP:
                //普通文本翻译
                translateGeneralTextData(translateTextDO, rabbitMqTranslateVO, counter, translation, shopifyRequest);
                break;
            default:
                break;
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
        String targetString = null;
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
            addData(shopifyRequest.getTarget(), value, translatedText);
            shopifyService.saveToShopify(translatedText, translation, resourceId, shopifyRequest);
            printTranslation(translatedText, value, translation, shopifyRequest.getShopName(), type, resourceId, source);
            //存到数据库中
            try {
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
                List<String> resultList = OBJECT_MAPPER.readValue(value, new TypeReference<List<String>>() {
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
    public void sendTranslateEmail(RabbitMqTranslateVO rabbitMqTranslateVO, TranslateTasksDO task) {
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
            translatesService.updateTranslateStatus(shopName, 1, target, source, accessToken);
            tencentEmailService.translateSuccessEmail(new TranslateRequest(0, shopName, accessToken, source, target, null),counter, startTime, startChars, limitChars, false);
        } else if (nowUserTranslate == 3) {
            //为3，发送部分翻译的邮件
            tencentEmailService.translateFailEmail(shopName, counter, startTime, startChars, limitChars, target, source);
        }

    }

}
