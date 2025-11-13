package com.bogdatech.logic.translate;

import com.alibaba.fastjson.JSONObject;
import com.bogdatech.Service.IUsersService;
import com.bogdatech.Service.impl.InitialTaskV2Repo;
import com.bogdatech.Service.impl.TranslateTaskV2Repo;
import com.bogdatech.entity.DO.*;
import com.bogdatech.integration.ALiYunTranslateIntegration;
import com.bogdatech.integration.ShopifyHttpIntegration;
import com.bogdatech.integration.model.ShopifyGraphResponse;
import com.bogdatech.logic.RedisProcessService;
import com.bogdatech.logic.ShopifyService;
import com.bogdatech.logic.redis.RedisStoppedRepository;
import com.bogdatech.logic.token.UserTokenService;
import com.bogdatech.model.controller.request.ClickTranslateRequest;
import com.bogdatech.model.controller.response.BaseResponse;
import com.bogdatech.utils.JsonUtils;
import com.bogdatech.utils.ShopifyRequestUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.microsoft.applicationinsights.TelemetryClient;
import kotlin.Pair;
import lombok.Getter;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.bogdatech.constants.TranslateConstants.APIVERSION;
import static com.bogdatech.utils.LiquidHtmlTranslatorUtils.isHtmlEntity;

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

    public void createInitialTask(String shopName, String source, String[] targets, List<String> moduleList,
                                  Boolean isCover) {
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

            shopifyService.rotateAllShopifyGraph(shopName, module, userDO.getAccessToken(), 10, target,
                (node -> {
                    if (node != null && !CollectionUtils.isEmpty(node.getTranslatableContent())) {
                        translateTaskV2DO.setResourceId(node.getResourceId());

                        List<TranslateTaskV2DO> existingTasks = translateTaskV2Repo.selectByResourceId(node.getResourceId());
                        // 每个node有几个translatableContent
                        node.getTranslatableContent().forEach(translatableContent -> {
                            if (needTranslate(translatableContent, node.getTranslations(), existingTasks)) {
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
        }

        // 更新数据库并记录初始化时间
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
            List<TranslateTaskV2DO> taskList = translateTaskV2Repo.selectByInitialTaskIdAndTypeAndEmptyValueWithLimit(
                    initialTaskId, textType, 10);
            // ****** 翻译规则 todo
            // 除了html，其他都可以批量的翻译
            // 但是数据长度不确定，所以50个可能会被截掉
            // 根据token限制，动态调整每次翻译的数量
            // ****** 翻译规则

            // 用id作key，方便填回
            Map<Integer, String> idToSourceValueMap = taskList.stream()
                    .collect(Collectors.toMap(TranslateTaskV2DO::getId, TranslateTaskV2DO::getSourceValue));

            // 3.1 过缓存
            Map<Integer, String> cachedMap = new HashMap<>();
            Map<Integer, String> uncachedMap = new HashMap<>();
            this.getCached(idToSourceValueMap, cachedMap, uncachedMap, target);

            // 3.2 调用翻译接口
            Pair<Map<Integer, String>, Integer> translatedAns = this.translate(uncachedMap, textType, target);

            Map<Integer, String> translatedValueMap = translatedAns.getFirst();
            translatedValueMap.putAll(cachedMap);
            for (TranslateTaskV2DO updatedDo : taskList) {
                String targetValue = translatedValueMap.get(updatedDo.getId());
                updatedDo.setTargetValue(targetValue);

                // 3.3 回写数据库 todo 批量
                translateTaskV2Repo.update(updatedDo);

                // 3.4 设置缓存
                setCache(target, targetValue, updatedDo.getSourceValue());

                // 3.5 设置翻译进度条 目前可以直接读取数据库
            }
            // 更新token数据库
            Integer usedTokenByTask = translatedAns.getSecond();
            usedToken = userTokenService.addUsedToken(shopName, initialTaskId, usedTokenByTask);
            maxToken = userTokenService.getMaxToken(shopName); // max token也重新获取，防止期间用户购买
            randomDo = translateTaskV2Repo.selectOneByInitialTaskIdAndEmptyValue(initialTaskId);
        }

        // 这个计算方式有问题， 暂定这样
        long translationTimeInMinutes = (System.currentTimeMillis() - initialTaskV2DO.getUpdatedAt().getTime()) / (1000 * 60);
        initialTaskV2DO.setStatus(InitialTaskStatus.TRANSLATE_DONE_SAVING_SHOPIFY.status);
        initialTaskV2DO.setUsedToken(userTokenService.getUsedTokenByTaskId(shopName, initialTaskId));
        initialTaskV2DO.setTranslationMinutes((int) translationTimeInMinutes);
        initialTaskV2Repo.updateById(initialTaskV2DO);
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
            appInsights.trackTrace("Translation Completed Email sent to user: " + shopName +
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

    // Pair <翻译结果map，使用token数>
    // Map <id, translatedText>
    private Pair<Map<Integer, String>, Integer> translate(Map<Integer, String> idToSourceValueMap,
                                                          String textType, String target) {
        Integer usedToken = 0;
        Map<Integer, String> translatedValueMap = new HashMap<>();
        for (Map.Entry<Integer, String> entry : idToSourceValueMap.entrySet()) {
            Integer id = entry.getKey();
            String value = entry.getValue();
            String prompt = "帮我翻译如下内容，到目标语言：" + target + "。只返回翻译后的内容，不要其他多余的说明。内容如下：\n" + value;

            Pair<String, Integer> pair = aLiYunTranslateIntegration.userTranslate(prompt, target);
            if (pair != null && pair.getFirst() != null) {
                String translatedText = pair.getFirst();
                translatedValueMap.put(id, translatedText);
                usedToken += pair.getSecond();
            } else {
                // 翻译失败，这里后续继续做兜底
                translatedValueMap.put(id, value);
            }
        }

        return new Pair<>(translatedValueMap, usedToken);
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
        // todo 这里加个日志，看看每一批有多少命中缓存的
        appInsights.trackTrace("Translation getCached: total " + idToSourceValueMap.size() +
                " cached " + cachedMap.size() +
                " uncached " + unCachedMap.size());
    }

    private void setCache(String target, String targetValue, String sourceValue) {
        redisProcessService.setCacheData(target, targetValue, sourceValue);
    }

    private boolean needTranslate(ShopifyGraphResponse.TranslatableResources.Node.TranslatableContent translatableContent,
                                  List<ShopifyGraphResponse.TranslatableResources.Node.Translation> translations,
                                  List<TranslateTaskV2DO> existingTasks) {
        if (org.apache.commons.lang.StringUtils.isEmpty(translatableContent.getValue())) {
            return false;
        }

        // 检查本地数据库是否已有该 resourceId + key 的记录（防止初始化时断电造成重复插入）
        if (!CollectionUtils.isEmpty(existingTasks)) {
            for (TranslateTaskV2DO task : existingTasks) {
                if (translatableContent.getKey().equals(task.getNodeKey())) {
                    return false;
                }
            }
        }

        for (ShopifyGraphResponse.TranslatableResources.Node.Translation translation : translations) {
            if (translatableContent.getKey().equals(translation.getKey())) {
                return translation.getOutdated();
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
