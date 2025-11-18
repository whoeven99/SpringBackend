package com.bogdatech.logic.translate;

import com.alibaba.fastjson.JSONObject;
import com.bogdatech.Service.IUsersService;
import com.bogdatech.Service.impl.InitialTaskV2Repo;
import com.bogdatech.Service.impl.TranslateTaskV2Repo;
import com.bogdatech.entity.DO.*;
import com.bogdatech.integration.ALiYunTranslateIntegration;
import com.bogdatech.integration.ShopifyHttpIntegration;
import com.bogdatech.integration.model.ShopifyGraphResponse;
import com.bogdatech.logic.GlossaryService;
import com.bogdatech.logic.RedisProcessService;
import com.bogdatech.logic.ShopifyService;
import com.bogdatech.logic.redis.RedisStoppedRepository;
import com.bogdatech.logic.token.UserTokenService;
import com.bogdatech.model.controller.request.ClickTranslateRequest;
import com.bogdatech.model.controller.response.BaseResponse;
import com.bogdatech.utils.HtmlUtils;
import com.bogdatech.utils.JsonUtils;
import com.bogdatech.utils.JudgeTranslateUtils;
import com.bogdatech.utils.ShopifyRequestUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.microsoft.applicationinsights.TelemetryClient;
import kotlin.Pair;
import kotlin.Triple;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.bogdatech.constants.TranslateConstants.*;
import static com.bogdatech.utils.JsonUtils.isJson;
import static com.bogdatech.utils.JsoupUtils.isHtml;
import static com.bogdatech.utils.JudgeTranslateUtils.*;
import static com.bogdatech.utils.LiquidHtmlTranslatorUtils.*;

@Component
public class TranslateV2Service {
    @Autowired
    private InitialTaskV2Repo initialTaskV2Repo;
    @Autowired
    private TranslateTaskV2Repo translateTaskV2Repo;
    @Autowired
    private IUsersService iUsersService;
    @Autowired
    private ShopifyService shopifyService;
    @Autowired
    private ShopifyHttpIntegration shopifyHttpIntegration;
    @Autowired
    private ALiYunTranslateIntegration aLiYunTranslateIntegration;
    @Autowired
    private RedisStoppedRepository redisStoppedRepository;
    @Autowired
    private RedisProcessService redisProcessService;
    @Autowired
    private UserTokenService userTokenService;
    @Autowired
    private GlossaryService glossaryService;
    public static TelemetryClient appInsights = new TelemetryClient();

    // 翻译 step 1, 用户 -> initial任务创建
    public BaseResponse<Object> createInitialTask(ClickTranslateRequest request) {
        String shopName = request.getShopName();
        String[] targets = request.getTarget();
        List<String> moduleList = request.getTranslateSettings3();

        if (StringUtils.isEmpty(shopName) ||
                targets == null || targets.length == 0 || CollectionUtils.isEmpty(moduleList)) {
            return new BaseResponse<>().CreateErrorResponse("Missing parameters");
        }

        // 收集所有的resourceType到一个列表中
        List<String> resourceTypeList = moduleList.stream()
                .flatMap(module -> TranslateResourceDTO.TOKEN_MAP.get(module).stream())
                .map(TranslateResourceDTO::getResourceType)
                .toList();

        // todo 手动翻译里， 把以前的initial task 设为 isDeleted

        this.createInitialTask(shopName, request.getSource(), targets,
                resourceTypeList, request.getIsCover());

        // 找前端，把这里的返回改了
        return new BaseResponse<>().CreateSuccessResponse(request);
    }

    public void createInitialTask(String shopName, String source, String[] targets,
                                  List<String> moduleList, Boolean isCover) {
        redisStoppedRepository.removeStoppedFlag(shopName);
        for (String target : targets) {
            InitialTaskV2DO initialTask = new InitialTaskV2DO();
            initialTask.setShopName(shopName);
            initialTask.setSource(source);
            initialTask.setTarget(target);
            initialTask.setCover(isCover);
            initialTask.setModuleList(JsonUtils.objectToJson(moduleList));
            initialTask.setStatus(InitialTaskStatus.INIT_READING_SHOPIFY.getStatus());
            initialTaskV2Repo.insert(initialTask);
        }
    }

    // 翻译 step 2, initial -> 查询shopify，翻译任务创建
    public void initialToTranslateTask(InitialTaskV2DO initialTaskV2DO) {
        String shopName = initialTaskV2DO.getShopName();
        String source = initialTaskV2DO.getSource();
        String target = initialTaskV2DO.getTarget();

        List<String> moduleList = JsonUtils.jsonToObject(initialTaskV2DO.getModuleList(), new TypeReference<>() {});
        assert moduleList != null;

        UsersDO userDO = iUsersService.getUserByName(initialTaskV2DO.getShopName());

        for (String module : moduleList) {
            TranslateTaskV2DO translateTaskV2DO = new TranslateTaskV2DO();
            translateTaskV2DO.setModule(module);
            translateTaskV2DO.setInitialTaskId(initialTaskV2DO.getId());

            shopifyService.rotateAllShopifyGraph(shopName, module, userDO.getAccessToken(), 250, target,
                (node -> {
                    if (node != null && !CollectionUtils.isEmpty(node.getTranslatableContent())) {
                        translateTaskV2DO.setResourceId(node.getResourceId());
                        appInsights.trackTrace("TranslateTaskV2 rotating Shopify: " + shopName + " module: " + module +
                                " resourceId: " + node.getResourceId());

                        List<TranslateTaskV2DO> existingTasks = translateTaskV2Repo.selectByResourceId(node.getResourceId());
                        // 每个node有几个translatableContent
                        node.getTranslatableContent().forEach(translatableContent -> {
                            if (needTranslate(translatableContent, node.getTranslations(), existingTasks, module, initialTaskV2DO.isCover())) {
                                translateTaskV2DO.setSourceValue(translatableContent.getValue());
                                translateTaskV2DO.setNodeKey(translatableContent.getKey());
                                translateTaskV2DO.setType(translatableContent.getType());
                                translateTaskV2DO.setDigest(translatableContent.getDigest());
                                translateTaskV2DO.setId(null);
                                translateTaskV2Repo.insert(translateTaskV2DO);
                            }
                        });
                    }
                }));
            appInsights.trackTrace("TranslateTaskV2 rotate Shopify done: " + shopName + " module: " + module);
        }

        // 更新数据库并记录初始化时间
        appInsights.trackTrace("TranslateTaskV2 initialToTranslateTask done: " + shopName);

        long initTimeInMinutes = (System.currentTimeMillis() - initialTaskV2DO.getUpdatedAt().getTime()) / (1000 * 60);
        initialTaskV2DO.setStatus(InitialTaskStatus.READ_DONE_TRANSLATING.status);
        initialTaskV2DO.setInitMinutes((int) initTimeInMinutes);
        initialTaskV2Repo.updateById(initialTaskV2DO);
    }

    // 翻译 step 3, 翻译任务 -> 具体翻译行为 直接对数据库操作
    public void translateEachTask(InitialTaskV2DO initialTaskV2DO) {
        // 这里可以从数据库，直接批量获取各种type，一次性翻译不同模块的数据
        Integer initialTaskId = initialTaskV2DO.getId();
        String target = initialTaskV2DO.getTarget();
        String shopName = initialTaskV2DO.getShopName();

        Map<String, GlossaryDO> glossaryMap = glossaryService.getGlossaryDoByShopName(shopName, target);

        Integer maxToken = userTokenService.getMaxToken(shopName);
        Integer usedToken = userTokenService.getUsedToken(shopName);
        TranslateTaskV2DO randomDo = translateTaskV2Repo.selectOneByInitialTaskIdAndEmptyValue(initialTaskId);
        while (randomDo != null) {
            if (usedToken >= maxToken) {
                // 更新数据库状态为 5，翻译中断
                long translationTimeInMinutes = (System.currentTimeMillis() - initialTaskV2DO.getUpdatedAt().getTime()) / (1000 * 60);
                initialTaskV2DO.setStatus(InitialTaskStatus.STOPPED.status);
                initialTaskV2DO.setUsedToken(userTokenService.getUsedTokenByTaskId(shopName, initialTaskId));
                initialTaskV2DO.setTranslationMinutes((int) translationTimeInMinutes);
                initialTaskV2Repo.updateById(initialTaskV2DO);

                // 记录是因为token limit中断的
                redisStoppedRepository.tokenLimitStopped(shopName);
                break;
            }
            // 还可能是手动中断
            if (redisStoppedRepository.isTaskStopped(shopName)) {
                break;
            }

            // 随机找一个text type出来
            String textType = randomDo.getType();
            List<TranslateTaskV2DO> taskList = new ArrayList<>();
            Integer usedTokenByTask;
            if (PLAIN_TEXT.equals(textType) || TITLE.equals(textType) || META_TITLE.equals(textType) || LOWERCASE_HANDLE.equals(textType)) {
                // 批量翻译
                taskList = translateTaskV2Repo.selectByInitialTaskIdAndTypeAndEmptyValueWithLimit(
                        initialTaskId, textType, 50);
                Map<Integer, String> idToSourceValueMap = taskList.stream()
                        .collect(Collectors.toMap(TranslateTaskV2DO::getId, TranslateTaskV2DO::getSourceValue));

                Pair<Map<Integer, String>, Integer> translatedValueMapPair = translateBatch(idToSourceValueMap, target, glossaryMap);
                if (translatedValueMapPair == null) {
                    usedTokenByTask = 0;
                } else {
                    Map<Integer, String> translatedValueMap = translatedValueMapPair.getFirst();
                    usedTokenByTask = translatedValueMapPair.getSecond();
                    for (TranslateTaskV2DO updatedDo : taskList) {
                        String targetValue = translatedValueMap.get(updatedDo.getId());
                        updatedDo.setTargetValue(targetValue);
                        updatedDo.setHasTargetValue(true);

                        // 3.3 回写数据库 todo 批量
                        translateTaskV2Repo.update(updatedDo);

                        // 3.4 设置缓存
                        setCache(target, targetValue, updatedDo.getSourceValue());
                    }
                }
            } else if (HTML.equals(textType)) {
                // html单独翻译
                String value = randomDo.getSourceValue();

                // 解析html里面待翻译的内容
                List<String> originalTexts = HtmlUtils.parseHtml(value, target);

                // index - sourceValue 方便后续处理以及ai
                Map<Integer, String> idToSourceValueMap = originalTexts.stream().collect(
                        Collectors.toMap(originalTexts::indexOf, text -> text));

                // 开始翻译
                Pair<Map<Integer, String>, Integer> translatedValueMapPair = translateBatch(idToSourceValueMap, target, glossaryMap);
                if (translatedValueMapPair == null) {
                    usedTokenByTask = 0;
                } else {
                    Map<Integer, String> translatedValueMap = translatedValueMapPair.getFirst();
                    usedToken = translatedValueMapPair.getSecond();
                    String translatedValue = HtmlUtils.replaceBack(value, originalTexts, translatedValueMap);

                    // 翻译后更新db
                    randomDo.setHasTargetValue(true);
                    randomDo.setTargetValue(translatedValue);
                    translateTaskV2Repo.update(randomDo);

                    // 设置缓存
                    for (Map.Entry<Integer, String> entry : idToSourceValueMap.entrySet()) {
                        Integer index = entry.getKey();
                        String sourceText = entry.getValue();
                        String translatedText = translatedValueMap.get(index);
                        setCache(target, translatedText, sourceText);
                    }

                    usedTokenByTask = usedToken;
                }
            } else {
                // 先单独处理，加日志看看还有哪些type
                Pair<String, Integer> pair = translateSingle(randomDo.getSourceValue(), target, glossaryMap);
                if (pair == null) {
                    usedTokenByTask = 0;
                } else {
                    usedTokenByTask = pair.getSecond();

                    // 这里的originValue是被glossary替换过的值
                    setCache(target, pair.getFirst(), randomDo.getSourceValue());
                }
            }
            // ****** 翻译规则 todo
            // html 单独处理
            // 除了html，其他都可以批量的翻译
            // 但是数据长度不确定，所以50个可能会被截掉
            // 根据token限制，动态调整每次翻译的数量
            // ****** 翻译规则

            // 更新token数据库
            usedToken = userTokenService.addUsedToken(shopName, initialTaskId, usedTokenByTask);
            maxToken = userTokenService.getMaxToken(shopName); // max token也重新获取，防止期间用户购买
            randomDo = translateTaskV2Repo.selectOneByInitialTaskIdAndEmptyValue(initialTaskId);
            appInsights.trackTrace("TranslateTaskV2 translating: " + shopName + " size: " + taskList.size() +
                    " usedToken: " + usedToken + " maxToken: " + maxToken);
        }
        appInsights.trackTrace("TranslateTaskV2 translating done: " + shopName);

        // 这个计算方式有问题， 暂定这样
        long translationTimeInMinutes = (System.currentTimeMillis() - initialTaskV2DO.getUpdatedAt().getTime()) / (1000 * 60);
        initialTaskV2DO.setStatus(InitialTaskStatus.TRANSLATE_DONE_SAVING_SHOPIFY.status);
        initialTaskV2DO.setUsedToken(userTokenService.getUsedTokenByTaskId(shopName, initialTaskId));
        initialTaskV2DO.setTranslationMinutes((int) translationTimeInMinutes);
        initialTaskV2Repo.updateById(initialTaskV2DO);
    }

    // Pair => translatedMap - usedToken
    // Map => id - translatedValue
    private Pair<Map<Integer, String>, Integer> translateBatch(Map<Integer, String> idToSourceValueMap, String target,
                                                               Map<String, GlossaryDO> glossaryMap) {
        Boolean hasGlossary = false;
        // 替换glossary
        for (Map.Entry<Integer, String> entry : idToSourceValueMap.entrySet()) {
            Pair<String, Boolean> glossaryPair = replaceWithGlossary(entry.getValue(), glossaryMap);
            if (glossaryPair.getSecond()) {
                entry.setValue(glossaryPair.getFirst());
                hasGlossary = true;
            }
        }

        // 3.1 过缓存
        Map<Integer, String> cachedMap = new HashMap<>();
        Map<Integer, String> uncachedMap = new HashMap<>();
        this.getCached(idToSourceValueMap, cachedMap, uncachedMap, target);

        // 3.2 调用翻译接口
        // ************************ //
        // 多条翻译 //
        // ************************ //

        // 对整个uncachedMap调用api开始翻译  批量翻译
        StringBuilder prompt = new StringBuilder("帮我翻译如下内容，到目标语言：" + target + "。");
        prompt.append("我会给你一个json格式的数据，你只翻译里面的value值，将翻译后的值填回到value里面，同样的格式返回给我。");
        if (hasGlossary) {
            prompt.append("其中{[xxx]}形式的字符串跳过，不要翻译，并且返回原样给我。");
        }
        prompt.append("只返回翻译后的内容，不要其他多余的说明。内容如下: ");
        prompt.append(JsonUtils.objectToJson(uncachedMap));

        // translatedValue - usedToken
        Pair<String, Integer> pair = aLiYunTranslateIntegration.userTranslate(prompt.toString(), target);
        if (pair != null && pair.getFirst() != null) {
            // 翻译后 - 还原glossary
            String aiResponse = pair.getFirst();
            if (hasGlossary) {
                aiResponse = getGlossaryReplacedBack(aiResponse);
            }
            Map<Integer, String> translatedValueMap = JsonUtils.jsonToObjectWithNull(aiResponse, new TypeReference<Map<Integer, String>>() {
            });
            if (translatedValueMap != null) {
                // 翻译后 - 设置缓存
                for (Map.Entry<Integer, String> entry : translatedValueMap.entrySet()) {
                    setCache(target, entry.getValue(), idToSourceValueMap.get(entry.getKey()));
                }

                translatedValueMap.putAll(cachedMap);
                return new Pair<>(translatedValueMap, pair.getSecond());
            }
        }
        // FatalException
        return null;
    }

    private Pair<String, Integer> translateSingle(String value, String target,
                                                  Map<String, GlossaryDO> glossaryMap) {
        StringBuilder prompt = new StringBuilder("帮我翻译如下内容，到目标语言：" + target + "。");
        Pair<String, Boolean> glossaryPair = replaceWithGlossary(value, glossaryMap);
        if (glossaryPair.getSecond()) {
            prompt.append("其中{[xxx]}形式的字符串跳过，不要翻译，并且返回原样给我。");
            value = glossaryPair.getFirst();
        }

        // 先替换glossary， 再去做缓存
        String targetCache = redisProcessService.getCacheData(target, value);
        if (targetCache != null) {
            targetCache = isHtmlEntity(targetCache);
            return new Pair<>(targetCache, 0);
        }

        prompt.append("只返回翻译后的内容，不要其他多余的说明。内容如下: ");
        prompt.append(value);

        // translatedValue - usedToken
        Pair<String, Integer> pair = aLiYunTranslateIntegration.userTranslate(prompt.toString(), target);
        if (pair != null && pair.getFirst() != null) {
            String translatedText = pair.getFirst();

            // 把{[xxx]}替换回去 xxx
            if (glossaryPair.getSecond()) {
                translatedText = getGlossaryReplacedBack(translatedText);
            }
            return new Pair<>(translatedText, pair.getSecond());
        }
        return null;
    }

    // 翻译 step 4, 翻译完成 -> 写回shopify
    public void saveToShopify(InitialTaskV2DO initialTaskV2DO) {
        Integer initialTaskId = initialTaskV2DO.getId();
        String shopName = initialTaskV2DO.getShopName();
        UsersDO userDO = iUsersService.getUserByName(shopName);
        String token = userDO.getAccessToken();
        String target = initialTaskV2DO.getTarget();

        TranslateTaskV2DO randomDo = translateTaskV2Repo.selectOneByInitialTaskIdAndNotSaved(initialTaskId);
        while (randomDo != null) {
            String resourceId = randomDo.getResourceId();
            List<TranslateTaskV2DO> taskList = translateTaskV2Repo.selectByResourceId(resourceId);

            // 填回shopify
            ShopifyGraphResponse.TranslatableResources.Node node = new ShopifyGraphResponse.TranslatableResources.Node();
            node.setTranslations(taskList.stream()
                    .map(taskDO -> {
                        ShopifyGraphResponse.TranslatableResources.Node.Translation translation =
                                new ShopifyGraphResponse.TranslatableResources.Node.Translation();
                        translation.setLocale(target);
                        translation.setKey(taskDO.getNodeKey());
                        translation.setTranslatableContentDigest(taskDO.getDigest());
                        translation.setValue(taskDO.getTargetValue());
                        return translation;
                    })
                    .collect(Collectors.toList()));
            node.setResourceId(resourceId);
            String strResponse = shopifyHttpIntegration.sendShopifyPost(
                    shopName, token, APIVERSION, ShopifyRequestUtils.registerTransactionQuery(), node);
            JSONObject jsonObject = JSONObject.parseObject(strResponse);
            if (jsonObject != null && jsonObject.getJSONObject("data") != null) {
                // 回写数据库，标记已写入 TODO 批量
                // 需要data.translationsRegister.translations[]不为空，并且有key，才是最严格的
                for (TranslateTaskV2DO taskDO : taskList) {
                    taskDO.setSavedToShopify(true);
                    translateTaskV2Repo.update(taskDO);
                }
            }
            randomDo = translateTaskV2Repo.selectOneByInitialTaskIdAndNotSaved(initialTaskId);
            appInsights.trackTrace("TranslateTaskV2 saving SHOPIFY: " + shopName + " size: " + taskList.size());
        }

        long savingShopifyTimeInMinutes = (System.currentTimeMillis() - initialTaskV2DO.getUpdatedAt().getTime()) / (1000 * 60);
        initialTaskV2DO.setStatus(InitialTaskStatus.SAVE_DONE_SENDING_EMAIL.status);
        initialTaskV2DO.setSavingShopifyMinutes((int) savingShopifyTimeInMinutes);
        initialTaskV2Repo.updateById(initialTaskV2DO);
    }

    // 翻译 step 5, 翻译写入都完成 -> 发送邮件，is_delete部分数据
    public void sendEmail(InitialTaskV2DO initialTaskV2DO) {
        String shopName = initialTaskV2DO.getShopName();

        Integer usingTimeMinutes = (int) ((System.currentTimeMillis() - initialTaskV2DO.getCreatedAt().getTime()) / (1000 * 60));
        Integer usedToken = userTokenService.getUsedTokenByTaskId(shopName, initialTaskV2DO.getId());

        // 正常结束，发送邮件
        if (InitialTaskStatus.SAVE_DONE_SENDING_EMAIL.status == initialTaskV2DO.getStatus()) {
            appInsights.trackTrace("TranslateTaskV2 Completed Email sent to user: " + shopName +
                    " Total time (minutes): " + usingTimeMinutes +
                    " Total tokens used: " + usedToken);

            initialTaskV2Repo.updateToStatus(initialTaskV2DO, InitialTaskStatus.ALL_DONE.status);
            return;
        }

        // 中断，部分翻译发送邮件
        if (InitialTaskStatus.STOPPED.status == initialTaskV2DO.getStatus()) {
            // 中断翻译的话 token limit才发邮件
            if (redisStoppedRepository.isStoppedByTokenLimit(shopName)) {

//                initialTaskV2Repo.updateToStatus(initialTaskV2DO, InitialTaskStatus.ALL_DONE.status);
            }
        }
    }

    private static Pair<String, Boolean> replaceWithGlossary(String value, Map<String, GlossaryDO> glossaryMap) {
        if (value == null || glossaryMap == null || glossaryMap.isEmpty()) {
            return new Pair<>(value, false);
        }

        Boolean hasGlossary = false;
        for (Map.Entry<String, GlossaryDO> entry : glossaryMap.entrySet()) {
            String key = entry.getKey();
            GlossaryDO glossaryDO = entry.getValue();
            Integer isCaseSensitive = glossaryDO.getCaseSensitive();

            // 当 isCaseSensitive 为 1 时，要求大小写完全一致才替换；否则不区分大小写替换
            String replacement = "{[" + glossaryDO.getTargetText() + "]}";
            if (isCaseSensitive != null && isCaseSensitive == 1) {
                if (value.contains(key)) {
                    value = value.replace(key, replacement);
                    hasGlossary = true;
                }
            } else {
                // 不区分大小写替换：使用正则的 CASE_INSENSITIVE
                Pattern pattern = Pattern.compile(Pattern.quote(key), Pattern.CASE_INSENSITIVE);
                Matcher matcher = pattern.matcher(value);
                if (matcher.find()) {
                    value = matcher.replaceAll(Matcher.quoteReplacement(replacement));
                    hasGlossary = true;
                }
            }
        }

        return new Pair<>(value, hasGlossary);
    }

    private static String getGlossaryReplacedBack(String value) {
        return value.replaceAll("\\{\\[(.*?)]}", "$1");
    }

    private void getCached(Map<Integer, String> idToSourceValueMap,
                           Map<Integer, String> cachedMap,
                           Map<Integer, String> unCachedMap,
                           String target) {
        idToSourceValueMap.forEach((id, sourceValue) -> {
            String targetCache = redisProcessService.getCacheData(target, sourceValue);
            if (targetCache != null) {
                targetCache = isHtmlEntity(targetCache);
                cachedMap.put(id, targetCache);
            } else {
                unCachedMap.put(id, sourceValue);
            }
        });
        appInsights.trackTrace("Translation getCached: total " + idToSourceValueMap.size() +
                " cached " + cachedMap.size() +
                " uncached " + unCachedMap.size());
    }

    private void setCache(String target, String targetValue, String sourceValue) {
        redisProcessService.setCacheData(target, targetValue, sourceValue);
    }

    // 根据翻译规则，不翻译的直接不用存
    private boolean needTranslate(ShopifyGraphResponse.TranslatableResources.Node.TranslatableContent translatableContent,
                                  List<ShopifyGraphResponse.TranslatableResources.Node.Translation> translations,
                                  List<TranslateTaskV2DO> existingTasks, String module, boolean isCover) {
        String value = translatableContent.getValue();
        String type = translatableContent.getType();
        String key = translatableContent.getKey();
        if (org.apache.commons.lang.StringUtils.isEmpty(value)) {
            return false;
        }

        // 先看outdate = false
        if (!isCover) {
            for (ShopifyGraphResponse.TranslatableResources.Node.Translation translation : translations) {
                if (translatableContent.getKey().equals(translation.getKey())) {
                    if(!translation.getOutdated()) {
                        return false;
                    }
                }
            }
        }

        // From TranslateDataService filterNeedTranslateSet
        // 如果是特定类型，也从集合中移除
        if ("FILE_REFERENCE".equals(type) || "LINK".equals(type)
                || "LIST_FILE_REFERENCE".equals(type) || "LIST_LINK".equals(type)
                || "LIST_URL".equals(type)
                || "JSON".equals(type)
                || "JSON_STRING".equals(type)) {
            return false;
        }

        if (JsonUtils.isJson(value)) {
            return false;
        }

        //如果handleFlag为false，则跳过
        if (type.equals(URI) && "handle".equals(key)) {
            // TODO 自动翻译的handle默认为false, 手动的记得添加
//            if (!handleFlag) {
//                return false;
//            }
            return false;
        }

        //通用的不翻译数据
        if (!JudgeTranslateUtils.generalTranslate(key, value)) {
            return false;
        }

        //如果是theme模块的数据
        if (TRANSLATABLE_RESOURCE_TYPES.contains(module)) {
            //如果是html放html文本里面
            if (isHtml(value)) {
                return false;
            }

            //对key中包含slide  slideshow  general.lange 的数据不翻译
            if (key.contains("general.lange")) {
                return false;
            }

            if (key.contains("block") && key.contains("add_button_selector")) {
                return false;
            }
            //对key中含section和general的做key值判断
            if (GENERAL_OR_SECTION_PATTERN.matcher(key).find()) {
                //进行白名单的确认
                if (whiteListTranslate(key)) {
                    return false;
                }

                //如果包含对应key和value，则跳过
                if (!shouldTranslate(key, value)) {
                    return false;
                }
            }
        }

        //对METAFIELD字段翻译
        if (METAFIELD.equals(module)) {
            //如UXxSP8cSm，UgvyqJcxm。有大写字母和小写字母的组合。有大写字母，小写字母和数字的组合。 10位 字母和数字不翻译
            if (SUSPICIOUS_PATTERN.matcher(value).matches() || SUSPICIOUS2_PATTERN.matcher(value).matches()) {
                return false;
            }
            if (!metaTranslate(value)) {
                return false;
            }
            //如果是base64编码的数据，不翻译
            if (BASE64_PATTERN.matcher(value).matches()) {
                return false;
            }
            if (isJson(value)) {
                return false;
            }
        }

        // 最终插入时，检查数据库， todo 还是需要再优化一下
        // 检查本地数据库是否已有该 resourceId + key 的记录（防止初始化时断电造成重复插入）
        if (!CollectionUtils.isEmpty(existingTasks)) {
            for (TranslateTaskV2DO task : existingTasks) {
                if (translatableContent.getKey().equals(task.getNodeKey())) {
                    return false;
                }
            }
        }
        return true;
    }

    @Getter
    public enum TranslationTypeEnum {
        PLAIN_TEXT("PLAIN_TEXT"),
        TITLE("title"),
        META_TITLE("meta_title"),
        LOWERCASE_HANDLE("handle"),
        LIST_SINGLE("LIST_SINGLE_LINE_TEXT_FIELD"),
        HTML("HTML"),
        GLOSSARY("GLOSSARY"),
        ;

        private final String textType;

        TranslationTypeEnum(String textType) {
            this.textType = textType;
        }
    }

    @Getter
    public enum InitialTaskStatus {
        INIT_READING_SHOPIFY(0, "用户刚创建任务，读取shopify数据中"),
        READ_DONE_TRANSLATING(1, "读取shopify数据，存数据库结束，翻译中"),
        TRANSLATE_DONE_SAVING_SHOPIFY(2, "翻译结束，写入中"),
        SAVE_DONE_SENDING_EMAIL(3, "写入shopify结束，待发送邮件，完成任务"),
        ALL_DONE(4, "全部完成"),
        STOPPED(5, "手动中断 or tokenLimit中断"),
        ;

        private final int status;
        private final String desc;

        InitialTaskStatus(int status, String desc) {
            this.status = status;
            this.desc = desc;
        }
    }
}
