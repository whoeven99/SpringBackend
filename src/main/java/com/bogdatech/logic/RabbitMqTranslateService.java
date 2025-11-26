package com.bogdatech.logic;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.bogdatech.Service.*;
import com.bogdatech.entity.DO.*;
import com.bogdatech.entity.VO.RabbitMqTranslateVO;
import com.bogdatech.exception.ClientException;
import com.bogdatech.integration.model.ShopifyGraphResponse;
import com.bogdatech.logic.redis.TranslationParametersRedisService;
import com.bogdatech.logic.translate.TranslateDataService;
import com.bogdatech.model.controller.request.*;
import com.bogdatech.utils.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import java.time.LocalDateTime;
import java.util.*;
import static com.bogdatech.constants.TranslateConstants.*;
import static com.bogdatech.entity.DO.TranslateResourceDTO.ALL_RESOURCES;
import static com.bogdatech.logic.TranslateService.*;
import static com.bogdatech.utils.CaseSensitiveUtils.*;
import static com.bogdatech.utils.JsoupUtils.*;
import static com.bogdatech.utils.LiquidHtmlTranslatorUtils.isHtmlEntity;
import static com.bogdatech.utils.RedisKeyUtils.*;

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
    private GlossaryService glossaryService;
    @Autowired
    private AILanguagePackService aiLanguagePackService;
    @Autowired
    private ITranslateTasksService translateTasksService;
    @Autowired
    private RedisProcessService redisProcessService;
    @Autowired
    private TranslationParametersRedisService translationParametersRedisService;
    @Autowired
    private ITranslationCounterService iTranslationCounterService;
    @Autowired
    private TranslateDataService translateDataService;

    public static final int BATCH_SIZE = 50;
    public static String MANUAL = "click"; // db 设置的10字符， 改动的时候需要注意
    public static String AUTO = "auto";

    public void initialTasks(String shopName, String accessToken,
                             String source, String target,
                             List<String> translateResourceDTOS,
                             boolean handleFlag,
                             String translationModel,
                             boolean isCover,
                             String customKey,
                             String taskType) {
        // 初始化计数器、词汇表和语言包
        TranslationCounterDO translationCounterDO = iTranslationCounterService.readCharsByShopName(shopName);
        Integer limitChars = iTranslationCounterService.getMaxCharsByShopName(shopName);
        int usedChars = translationCounterDO.getUsedChars();

        CharacterCountUtils counter = new CharacterCountUtils();
        counter.addChars(usedChars);

        Map<String, Object> glossaryMap = glossaryService.getGlossaryByShopName(shopName, target);
        appInsights.trackTrace("判断是否有同义词 " + shopName);

        String languagePackId = aiLanguagePackService.getCategoryByDescription(shopName, accessToken, counter
                , limitChars, target, MANUAL);
        appInsights.trackTrace("获取目前所使用的AI语言包 " + shopName);

        CharacterCountUtils allTasks = new CharacterCountUtils();
        // 处理每个翻译资源
        for (TranslateResourceDTO translateResource : ALL_RESOURCES) {
            String resourceType = translateResource.getResourceType();
            if (!translateResourceDTOS.contains(resourceType)) {
                continue;
            }

            if (shouldSkipResource(resourceType, shopName)) {
                continue;
            }

            if (checkNeedStopped(shopName, counter)) {
                return;
            }

            appInsights.trackTrace("初始化task数据 " + shopName + " model: " + resourceType);

            // 扫描商店数据，创建子任务
            parseShopifyData(
                    shopName, accessToken,
                    source, target, languagePackId, handleFlag, glossaryMap,
                    translateResource.getResourceType(), limitChars, usedChars,
                    translateResourceDTOS, translationModel, isCover, AUTO.equals(taskType) ? AUTO : customKey,
                    resourceType, translateResource.getFirst(), allTasks);
        }
    }

    /**
     * 检查是否需要跳过该资源
     */
    private boolean shouldSkipResource(String resourceType, String shopName) {
        if (resourceType.equals(PAYMENT_GATEWAY)) {
            appInsights.trackTrace("网关不翻译 " + shopName);
            return true;
        }
        if (EXCLUDED_SHOPS.contains(shopName) && PRODUCT_MODEL.contains(resourceType)) {
            appInsights.trackTrace("一些shop模块不翻译 " + shopName);
            return true;
        }
        return false;
    }

    public void parseShopifyData(
            String shopName, String accessToken,
            String source, String target, String languagePackId, boolean handleFlag, Map<String, Object> glossaryMap,
            String modelType, Integer limitChars, int usedChars,
            List<String> translateResourceDTOS, String translationModel, boolean isCover, String customKey,
            String resourceType, String first, CharacterCountUtils allTasks) {
        // 第一个节点
        String graphQuery = ShopifyRequestUtils.getQuery(resourceType, first, target);
        String shopifyData = shopifyService.getShopifyData(shopName, accessToken, API_VERSION_LAST, graphQuery);
        ShopifyGraphResponse shopifyRes = JsonUtils.jsonToObject(shopifyData, ShopifyGraphResponse.class);

        while (shopifyRes != null) {
            // 存db
            try {

                RabbitMqTranslateVO vo = new RabbitMqTranslateVO(graphQuery, shopName, accessToken,
                        source, target, languagePackId, handleFlag, glossaryMap, modelType, limitChars, usedChars,
                        LocalDateTime.now().toString(), translateResourceDTOS, translationModel, isCover, customKey);
                translateTasksService.save(new TranslateTasksDO(null, 0, JsonUtils.objectToJson(vo), shopName, null, null));
                allTasks.addChars(1);
            } catch (Exception e) {
                appInsights.trackTrace("clickTranslation 保存翻译任务失败 errors : " + e);
                appInsights.trackException(e);
            }

            // 收集数据总数 -> 给进度条使用
            if (shopifyRes.getTranslatableResources() != null
                    && shopifyRes.getTranslatableResources().getNodes() != null) {
                shopifyRes.getTranslatableResources().getNodes().forEach(node -> {
                        if (node.getTranslatableContent() != null) {
                            redisProcessService.addProcessData(generateProcessKey(shopName, target), PROGRESS_TOTAL,
                                    (long) node.getTranslatableContent().size());
                        }
                    }
                );
            }

            // 保存数据 并轮询下一页
            if (shopifyRes.getTranslatableResources() != null
                    && shopifyRes.getTranslatableResources().getPageInfo() != null
                    && shopifyRes.getTranslatableResources().getPageInfo().isHasNextPage()) {
                String endCursor = shopifyRes.getTranslatableResources().getPageInfo().getEndCursor();
                graphQuery = ShopifyRequestUtils.getQuery(resourceType, first, target, endCursor);

                String nextShopifyData = shopifyService.getShopifyData(shopName, accessToken, APIVERSION, graphQuery);
                shopifyRes = JsonUtils.jsonToObject(nextShopifyData, ShopifyGraphResponse.class);
            } else {
                break;
            }
        }
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

    public void translatePlainTextData(Set<TranslateTextDO> plainTextData,
                                       String shopName, String source, String target, String accessToken, Integer limitChars,
                                       String languagePack, String modelType, String translationModel,
                                       CharacterCountUtils counter, String translationKeyType, String translateType) {
        if (plainTextData.isEmpty()) {
            return;
        }
        // 先转成List，方便切片
        List<TranslateTextDO> list = new ArrayList<>(plainTextData);
        for (int i = 0; i < list.size(); i += BATCH_SIZE) {
            if (checkNeedStopped(shopName, counter)) {
                return;
            }

            // TODO： 2.2 翻译前的token校验
            updateCharsWhenExceedLimit(counter, shopName, source, target);

            // 取每次的50条（或剩余全部）
            int endIndex = Math.min(i + BATCH_SIZE, list.size());
            List<TranslateTextDO> batch = list.subList(i, endIndex);

            Map<String, String> cachedMap = new HashMap<>();
            List<String> untranslatedTexts = new ArrayList<>();
            for (TranslateTextDO translateTextDO : batch) {
                String cache = isCached(translateTextDO.getSourceText(), target);
                if (cache != null) {
                    cachedMap.put(translateTextDO.getSourceText(), cache);
                } else {
                    untranslatedTexts.add(translateTextDO.getSourceText());
                }
            }

            Map<String, String> resultMap = translateDataService.translatePlainText(untranslatedTexts,
                    source, target, languagePack, modelType, translationModel,
                    counter, shopName, limitChars, translationKeyType, translateType);

            resultMap.putAll(cachedMap);

            // 处理翻译后的数据
            for (TranslateTextDO item : batch) {
                String sourceText = item.getSourceText();
                String targetText = resultMap.get(sourceText);
                if (targetText == null) {
                    appInsights.trackTrace("FatalException translatePlainTextData " + shopName + " source: " + source +
                            " missing translation for: " + sourceText);
                    continue;
                }

                // 存储翻译后的数据
                Map<String, Object> translation = createTranslationMap(target, item.getTextKey(), item.getDigest());

                // TODO： 3.1 翻译后的存db
                if (!URI.equals(item.getTextType())) {
                    redisProcessService.setCacheData(target, targetText, sourceText);
                }
                shopifyService.saveToShopify(targetText, translation, item.getResourceId(), shopName, accessToken, target, API_VERSION_LAST);
                AppInsightsUtils.printTranslation(targetText, sourceText, translation, shopName, modelType, item.getResourceId(), source);
                checkNeedAddProcessData(shopName, target);
            }
        }
    }

    public Map<String, Set<TranslateTextDO>> filterTranslateMap(Map<String, Set<TranslateTextDO>> stringSetMap,
                                                                Set<TranslateTextDO> filterTranslateData,
                                                                Map<String, Object> glossaryMap) {
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
                    if (CaseSensitiveUtils.containsValue(value, glossaryKey) || CaseSensitiveUtils.containsValueIgnoreCase(value, glossaryKey)) {
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
    public void updateCharsWhenExceedLimit(CharacterCountUtils counter, String shopName,
                                           String source, String target) {
        TranslationCounterRequest request = new TranslationCounterRequest();
        request.setShopName(shopName);

        // 获取这个用户的已使用token数
        TranslationCounterDO translationCounterDO = translationCounterService.getOne(
                new LambdaQueryWrapper<TranslationCounterDO>().eq(TranslationCounterDO::getShopName, shopName));

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
            appInsights.trackTrace("clickTranslation shopName 用户 消耗的token : " + shopName + " totalChars : " +
                    usedChars + " limitChars : " + maxCharsByShopName);
            translatesService.updateTranslateStatus(shopName, 3, target, source);
            //将同一个shopName的task任务的状态。除邮件发送模块改为3.
            updateTranslateTasksStatus(shopName);
            throw new ClientException(CHARACTER_LIMIT);
        }
    }

    // TODO 都改成isCached
    public boolean cacheOrDatabaseTranslateData(String value, String source, Map<String, Object> translation,
                                                String resourceId, ShopifyRequest request) {
        //获取缓存数据
        String targetCache = redisProcessService.getCacheData(request.getTarget(), value);
        if (targetCache != null) {
            targetCache = isHtmlEntity(targetCache);

            // TODO 这里要挪出去
            shopifyService.saveToShopify(targetCache, translation, resourceId, request);
            AppInsightsUtils.printTranslation(targetCache, value, translation, request.getShopName(), "Cache", resourceId, source);

            // 翻译进度条加1
            checkNeedAddProcessData(request.getShopName(), request.getTarget());
            return true;
        }
        return false;
    }

    public String isCached(String value, String target) {
        //获取缓存数据
        String targetCache = redisProcessService.getCacheData(target, value);
        if (targetCache != null) {
            targetCache = isHtmlEntity(targetCache);
            return targetCache;
        }
        return null;
    }

    /**
     * 将同一个shopNmae的task任务的状态。除邮件发送模块改为3.
     */
    public void updateTranslateTasksStatus(String shopName) {
        //获取shopName所有status为0的task
        List<TranslateTasksDO> list = translateTasksService.list(new QueryWrapper<TranslateTasksDO>()
                .eq("status", 0)
                .eq("shop_name", shopName));
        //遍历task，然后解析数据，将除email的task都改为3
        for (TranslateTasksDO translateTasksDO : list) {
            // TODO 改成批量的
            translateTasksService.updateByTaskId(translateTasksDO.getTaskId(), 3);
        }
    }

    private void prepareForGlossary(String shopName, Map<String, Object> glossaryMap,
                                    String translationKeyType, Map<String, String> keyMap1, Map<String, String> keyMap0) {
        if (translationKeyType.equals(GLOSSARY)) {
            // 将glossaryMap中所有caseSensitive为1的数据存到一个Map集合里面
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
        }
    }

    public void translateData(Set<TranslateTextDO> translateTextDOS,
                              String shopName, String source, String target, Integer limitChars,
                              String accessToken, String languagePack, String modeType,
                              String translationModel, Map<String, Object> glossaryMap,
                              CharacterCountUtils counter, String translationKeyType, String translateType) {
        if (PLAIN_TEXT.equals(translationKeyType) || TITLE.equals(translationKeyType)
                || META_TITLE.equals(translationKeyType) || LOWERCASE_HANDLE.equals(translationKeyType)) {
            // Plain Text采用了50个的list翻译
            translatePlainTextData(translateTextDOS, shopName, source, target, accessToken, limitChars,
                    languagePack, modeType, translationModel, counter, translationKeyType, translateType);
            return;
        }

        // For GLOSSARY
        Map<String, String> keyMap1 = new HashMap<>();
        Map<String, String> keyMap0 = new HashMap<>();
        if (GLOSSARY.equals(translationKeyType)) {
            if (CollectionUtils.isEmpty(glossaryMap)) {
                return;
            }
            prepareForGlossary(shopName, glossaryMap, translationKeyType, keyMap1, keyMap0);
        }
        for (TranslateTextDO translateTextDO : translateTextDOS) {
            //根据模块选择翻译方法，先做普通翻译
            //判断是否停止翻译
            // TODO: 2.2 翻译中的token校验
            if (checkNeedStopped(shopName, counter)) {
                return;
            }

            String value = translateTextDO.getSourceText();
            String resourceId = translateTextDO.getResourceId();
            String key = translateTextDO.getTextKey();
            String digest = translateTextDO.getDigest();
            String type = translateTextDO.getTextType();
            Map<String, Object> translation = createTranslationMap(target, key, digest);

            // TODO：2.2 翻译中的token校验
            appInsights.trackTrace("判断是否达到额度限制 用户： " + shopName);
            updateCharsWhenExceedLimit(counter, shopName, source, target);

            //开始翻译
            String translatedValue = isCached(value, target);
            if (translatedValue == null) {
                if (translationKeyType.equals(LIST_SINGLE)) {
                    translatedValue = translateDataService.translateListSingleData(
                            value, target, languagePack, limitChars, counter, shopName, accessToken,
                            source, translation, translateTextDO.getResourceId(), translateType);
                } else if (translationKeyType.equals(HTML)) {
                    translatedValue = translateDataService.translateHtmlData(
                            value, shopName, target, accessToken, languagePack, limitChars, modeType,
                            counter, source, translation, resourceId, translationModel, translateType);
                } else if (translationKeyType.equals(GLOSSARY)) {
                    translatedValue = translateDataService.translateGlossaryData(
                            value, shopName, languagePack, accessToken, counter, source, target,
                            translation, resourceId, limitChars, keyMap0, keyMap1, translateType);
                }
            }

            //翻译进度条加1
            checkNeedAddProcessData(shopName, target);
            if (translatedValue == null) {
                appInsights.trackTrace("FatalException translateData is null: " + shopName + " source: " + source +
                        " value : " + value);
                continue;
            }

            // TODO: 3.1 翻译后的存db
            shopifyService.saveToShopify(translatedValue, translation, resourceId, shopName, accessToken, target, API_VERSION_LAST);
            AppInsightsUtils.printTranslation(translatedValue, value, translation, shopName, type, resourceId, source);
        }
    }
}
