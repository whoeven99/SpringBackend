package com.bogdatech.logic.translate;

import com.alibaba.fastjson.JSONObject;
import com.bogdatech.Service.ITranslatesService;
import com.bogdatech.Service.IUsersService;
import com.bogdatech.context.TranslateContext;
import com.bogdatech.entity.VO.SingleReturnVO;
import com.bogdatech.entity.VO.SingleTranslateVO;
import com.bogdatech.logic.TencentEmailService;
import com.bogdatech.logic.redis.TranslateTaskMonitorV2RedisService;
import com.bogdatech.logic.translate.stragety.ITranslateStrategyService;
import com.bogdatech.logic.translate.stragety.TranslateStrategyFactory;
import com.bogdatech.model.controller.response.ProgressResponse;
import com.bogdatech.model.controller.response.TypeSplitResponse;
import com.bogdatech.repository.entity.InitialTaskV2DO;
import com.bogdatech.repository.entity.TranslateTaskV2DO;
import com.bogdatech.repository.repo.InitialTaskV2Repo;
import com.bogdatech.repository.repo.TranslateTaskV2Repo;
import com.bogdatech.entity.DO.*;
import com.bogdatech.integration.ShopifyHttpIntegration;
import com.bogdatech.integration.model.ShopifyGraphResponse;
import com.bogdatech.logic.GlossaryService;
import com.bogdatech.logic.ShopifyService;
import com.bogdatech.logic.redis.RedisStoppedRepository;
import com.bogdatech.logic.token.UserTokenService;
import com.bogdatech.model.controller.request.ClickTranslateRequest;
import com.bogdatech.model.controller.response.BaseResponse;
import com.bogdatech.utils.*;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.Getter;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.stream.Collectors;

import static com.bogdatech.constants.TranslateConstants.*;
import static com.bogdatech.utils.CaseSensitiveUtils.appInsights;
import static com.bogdatech.utils.JsonUtils.isJson;
import static com.bogdatech.utils.JsoupUtils.isHtml;
import static com.bogdatech.utils.JudgeTranslateUtils.*;
import static com.bogdatech.utils.ListUtils.convertALL;
import static com.bogdatech.utils.ResourceTypeUtils.splitByType;

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
    private RedisStoppedRepository redisStoppedRepository;
    @Autowired
    private UserTokenService userTokenService;
    @Autowired
    private GlossaryService glossaryService;
    @Autowired
    private TranslateStrategyFactory translateStrategyFactory;
    @Autowired
    private TranslateTaskMonitorV2RedisService translateTaskMonitorV2RedisService;
    @Autowired
    private ITranslatesService iTranslatesService;

    public BaseResponse<Object> continueTranslating(String shopName, Integer taskId) {
        InitialTaskV2DO initialTaskV2DO = initialTaskV2Repo.selectById(taskId);
        if (initialTaskV2DO != null) {
            initialTaskV2Repo.updateToStatus(initialTaskV2DO, InitialTaskStatus.READ_DONE_TRANSLATING.getStatus());
            redisStoppedRepository.removeStoppedFlag(shopName);
            // return true;
        }
        // 否则 操作失败，前端展示错误码

        // 删除用户停止标识
        return new BaseResponse<>().CreateSuccessResponse(true);
    }

    public void continueTranslating(String shopName) {
        List<InitialTaskV2DO> list = initialTaskV2Repo.selectByShopName(shopName);
        if (!list.isEmpty() && redisStoppedRepository.isTaskStopped(shopName)) {
            redisStoppedRepository.removeStoppedFlag(shopName);
            for (InitialTaskV2DO initialTaskV2DO : list) {
                initialTaskV2DO.setStatus(TranslateV2Service.InitialTaskStatus.READ_DONE_TRANSLATING.getStatus());
                initialTaskV2Repo.updateById(initialTaskV2DO);
            }
        }
    }

    // 单条翻译入口
    public BaseResponse<SingleReturnVO> singleTextTranslate(SingleTranslateVO request) {
        if (request.getContext() == null || request.getTarget() == null
                || request.getType() == null || request.getKey() == null
                || StringUtils.isEmpty(request.getShopName())) {
            return BaseResponse.FailedResponse("Missing parameters");
        }

        String shopName = request.getShopName();
        Integer maxToken = userTokenService.getMaxToken(shopName);
        Integer usedToken = userTokenService.getUsedToken(shopName);
        if (usedToken >= maxToken) {
            return BaseResponse.FailedResponse("Token limit reached");
        }

        TranslateContext context = singleTranslate(shopName, request.getContext(), request.getTarget(),
                request.getType(), request.getKey(), glossaryService.getGlossaryDoByShopName(shopName, request.getTarget()));

        SingleReturnVO returnVO = new SingleReturnVO();
        returnVO.setTargetText(context.getTranslatedContent());
        returnVO.setTranslateVariables(context.getTranslateVariables());
        return BaseResponse.SuccessResponse(returnVO);
    }

    public TranslateContext singleTranslate(String shopName, String content, String target,
                                            String type, String key,
                                            Map<String, GlossaryDO> glossaryMap) {
        TranslateContext context = TranslateContext.startNewTranslate(content, target, type, key);
        ITranslateStrategyService service = translateStrategyFactory.getServiceByContext(context);
        context.setGlossaryMap(glossaryMap);

        service.translate(context);
        service.finishAndGetJsonRecord(context);

        userTokenService.addUsedToken(shopName, context.getUsedToken());
        return context;
    }

    // 手动开启翻译任务入口
    // 翻译 step 1, 用户 -> initial任务创建
    public BaseResponse<Object> createInitialTask(ClickTranslateRequest request) {
        String shopName = request.getShopName();
        String[] targets = request.getTarget();
        List<String> moduleList = request.getTranslateSettings3();

        if (StringUtils.isEmpty(shopName) ||
                targets == null || targets.length == 0 || CollectionUtils.isEmpty(moduleList)) {
            return new BaseResponse<>().CreateErrorResponse("Missing parameters");
        }

        // 判断用户语言是否正在翻译，翻译的不管；没翻译的翻译。
        List<InitialTaskV2DO> initialTaskV2DOS =
                initialTaskV2Repo.selectByShopNameSourceManual(shopName, request.getSource());
        Set<String> targetSet = new HashSet<>(Arrays.asList(targets));

        // 找出已经 ALL_DONE 或 STOPPED 的 target
        Set<String> filteredTargets = initialTaskV2DOS.stream()
                .filter(it -> it.getStatus() == InitialTaskStatus.INIT_READING_SHOPIFY.getStatus()
                        || it.getStatus() == InitialTaskStatus.READ_DONE_TRANSLATING.getStatus()
                        || it.getStatus() == InitialTaskStatus.TRANSLATE_DONE_SAVING_SHOPIFY.getStatus()
                        || it.getStatus() == InitialTaskStatus.SAVE_DONE_SENDING_EMAIL.getStatus())
                .map(InitialTaskV2DO::getTarget)
                .collect(Collectors.toSet());

        // 将filteredTargets和targets对比，去除targets里和filteredTargets相同的值
        Set<String> finalTargets = targetSet.stream()
                .filter(t -> !filteredTargets.contains(t))
                .collect(Collectors.toSet());

        if (finalTargets.isEmpty()) {
            return new BaseResponse<>().CreateSuccessResponse(request);
        }

        // 收集所有的resourceType到一个列表中
        List<String> resourceTypeList = moduleList.stream()
                .flatMap(module -> TranslateResourceDTO.TOKEN_MAP.get(module).stream())
                .map(TranslateResourceDTO::getResourceType)
                .toList();

        this.createInitialTask(shopName, request.getSource(), finalTargets.toArray(new String[0]),
                resourceTypeList, request.getIsCover(), "manual");

        // 找前端，把这里的返回改了
        return new BaseResponse<>().CreateSuccessResponse(request);
    }

    // 获取进度条
    public BaseResponse<ProgressResponse> getProcess(String shopName, String source) {
        List<InitialTaskV2DO> taskList = initialTaskV2Repo.selectByShopNameSource(shopName, source);

        ProgressResponse response = new ProgressResponse();
        List<ProgressResponse.Progress> list = new ArrayList<>();
        response.setList(list);
        if (taskList.isEmpty()) {
            return new BaseResponse<ProgressResponse>().CreateSuccessResponse(response);
        }

        for (InitialTaskV2DO task : taskList) {
            Map<String, Integer> defaultProgressTranslateData = new HashMap<>();
            defaultProgressTranslateData.put("TotalQuantity", 1);
            defaultProgressTranslateData.put("RemainingQuantity", 0);
            Map<String, String> taskContext = translateTaskMonitorV2RedisService.getAllByTaskId(task.getId());

            if (task.getStatus().equals(InitialTaskStatus.INIT_READING_SHOPIFY.getStatus())) {
                ProgressResponse.Progress progress = new ProgressResponse.Progress();
                progress.setTarget(task.getTarget());
                progress.setStatus(2);
                progress.setTranslateStatus("translation_process_init");
                progress.setProgressData(defaultProgressTranslateData);
                progress.setTaskId(task.getId());
                list.add(progress);

            } else if (task.getStatus().equals(InitialTaskStatus.READ_DONE_TRANSLATING.getStatus())) {
                ProgressResponse.Progress progress = new ProgressResponse.Progress();
                progress.setTarget(task.getTarget());
                progress.setStatus(2);
                progress.setTranslateStatus("translation_process_translating");
                progress.setTaskId(task.getId());

                Long count = Long.valueOf(taskContext.get("totalCount"));
                Long translatedCount = Long.valueOf(taskContext.get("translatedCount"));

                Map<String, Integer> progressData = new HashMap<>();
                progressData.put("TotalQuantity", count.intValue());
                progressData.put("RemainingQuantity", count.intValue() - translatedCount.intValue());

                progress.setProgressData(progressData);
                list.add(progress);
            } else if (task.getStatus().equals(InitialTaskStatus.TRANSLATE_DONE_SAVING_SHOPIFY.getStatus())) {
                ProgressResponse.Progress progress = new ProgressResponse.Progress();
                progress.setTarget(task.getTarget());
                progress.setStatus(1);
                progress.setTranslateStatus("translation_process_saving_shopify");
                progress.setTaskId(task.getId());

                Long count = Long.valueOf(taskContext.get("totalCount"));
                Long savedCount = Long.valueOf(taskContext.get("savedCount"));

                Map<String, Integer> progressWriteData = new HashMap<>();
                progressWriteData.put("write_total", count.intValue());
                progressWriteData.put("write_done", savedCount.intValue());

                progress.setWritingData(progressWriteData);
                progress.setProgressData(defaultProgressTranslateData);
                list.add(progress);
            } else if (task.getStatus().equals(InitialTaskStatus.ALL_DONE.getStatus())) {
                ProgressResponse.Progress progress = new ProgressResponse.Progress();
                progress.setTarget(task.getTarget());
                progress.setStatus(1);
                progress.setTranslateStatus("translation_process_saved");
                progress.setProgressData(defaultProgressTranslateData);
                progress.setTaskId(task.getId());
                list.add(progress);
            } else if (task.getStatus().equals(InitialTaskStatus.STOPPED.getStatus())) {
                ProgressResponse.Progress progress = new ProgressResponse.Progress();
                progress.setTarget(task.getTarget());
                progress.setTaskId(task.getId());

                Long count = Long.valueOf(taskContext.get("totalCount"));
                Long translatedCount = Long.valueOf(taskContext.get("translatedCount"));
                Map<String, Integer> progressData = new HashMap<>();
                progressData.put("TotalQuantity", count.intValue());
                progressData.put("RemainingQuantity", count.intValue() - translatedCount.intValue());
                progress.setProgressData(progressData);

                // 判断是手动中断，还是limit中断
                if (redisStoppedRepository.isStoppedByTokenLimit(shopName)) {
                    progress.setStatus(3); // limit中断
                } else {
                    progress.setStatus(7);// 中断的状态
                }

                list.add(progress);
            }
        }

        return new BaseResponse<ProgressResponse>().CreateSuccessResponse(response);
    }

    public void createInitialTask(String shopName, String source, String[] targets,
                                  List<String> moduleList, Boolean isCover, String taskType) {
        if ("auto".equals(taskType)) {
            // 自动翻译的新建任务逻辑
            initialTaskV2Repo.deleteByShopNameSourceTarget(shopName, source, targets[0]);
        } else if ("manual".equals(taskType)) {
            // 手动翻译，目前把同一个source的任务都删掉
            initialTaskV2Repo.deleteByShopNameSource(shopName, source);
        }
        redisStoppedRepository.removeStoppedFlag(shopName);

        for (String target : targets) {
            InitialTaskV2DO initialTask = new InitialTaskV2DO();
            initialTask.setShopName(shopName);
            initialTask.setSource(source);
            initialTask.setTarget(target);
            initialTask.setCover(isCover);
            initialTask.setModuleList(JsonUtils.objectToJson(moduleList));
            initialTask.setStatus(InitialTaskStatus.INIT_READING_SHOPIFY.getStatus());
            initialTask.setTaskType(taskType);
            initialTaskV2Repo.insert(initialTask);

            translateTaskMonitorV2RedisService.createRecord(initialTask.getId(), shopName, source, target);

            if ("auto".equals(taskType)) {
                return;
            }
            iTranslatesService.updateTranslateStatus(shopName, 2, target, source);
        }
    }

    // 翻译 step 2, initial -> 查询shopify，翻译任务创建
    public void initialToTranslateTask(InitialTaskV2DO initialTaskV2DO) {
        String shopName = initialTaskV2DO.getShopName();
        String source = initialTaskV2DO.getSource();
        String target = initialTaskV2DO.getTarget();

        List<String> moduleList = JsonUtils.jsonToObject(initialTaskV2DO.getModuleList(), new TypeReference<>() {
        });
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

//                        List<TranslateTaskV2DO> existingTasks = translateTaskV2Repo.selectByResourceId(node.getResourceId());
                            // 每个node有几个translatableContent
                            node.getTranslatableContent().forEach(translatableContent -> {
                                if (needTranslate(translatableContent, node.getTranslations(), module, initialTaskV2DO.isCover())) {
                                    translateTaskV2DO.setSourceValue(translatableContent.getValue());
                                    translateTaskV2DO.setNodeKey(translatableContent.getKey());
                                    translateTaskV2DO.setType(translatableContent.getType());
                                    translateTaskV2DO.setDigest(translatableContent.getDigest());
                                    translateTaskV2DO.setId(null);
                                    translateTaskV2Repo.insert(translateTaskV2DO);
                                    translateTaskMonitorV2RedisService.incrementTotalCount(initialTaskV2DO.getId());
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
        translateTaskMonitorV2RedisService.setInitEndTime(initialTaskV2DO.getId());
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
            appInsights.trackTrace("TranslateTaskV2 translating shop: " + shopName + " randomDo: " + randomDo.getId());
            if (usedToken >= maxToken) {
                // 记录是因为token limit中断的
                redisStoppedRepository.tokenLimitStopped(shopName);
            }
            // 还可能是手动中断
            if (redisStoppedRepository.isTaskStopped(shopName)) {
                break;
            }

            // 随机找一个text type出来
            String textType = randomDo.getType();
            if (PLAIN_TEXT.equals(textType) || TITLE.equals(textType)
                    || META_TITLE.equals(textType) || LOWERCASE_HANDLE.equals(textType)
                    || SINGLE_LINE_TEXT_FIELD.equals(textType)) {
                // 批量翻译
                List<TranslateTaskV2DO> taskList = translateTaskV2Repo.selectByInitialTaskIdAndTypeAndEmptyValueWithLimit(
                        initialTaskId, textType, 50);
//                int tokens = calculateBaiLianToken(text);

                Map<Integer, String> idToSourceValueMap = taskList.stream()
                        .collect(Collectors.toMap(TranslateTaskV2DO::getId, TranslateTaskV2DO::getSourceValue));

                TranslateContext context = TranslateContext.startBatchTranslate(idToSourceValueMap, target);
                ITranslateStrategyService service =
                        translateStrategyFactory.getServiceByContext(context);
                context.setGlossaryMap(glossaryMap);

                service.translate(context);

                Map<Integer, String> translatedValueMap = context.getTranslatedTextMap();
                for (TranslateTaskV2DO updatedDo : taskList) {
                    String targetValue = translatedValueMap.get(updatedDo.getId());
                    updatedDo.setTargetValue(targetValue);
                    updatedDo.setHasTargetValue(true);

                    // 3.3 回写数据库 todo 批量
                    translateTaskV2Repo.update(updatedDo);
                }
                usedToken = userTokenService.addUsedToken(shopName, initialTaskId, context.getUsedToken());
                translateTaskMonitorV2RedisService.trackTranslateDetail(initialTaskId, taskList.size(),
                        context.getUsedToken(), context.getTranslatedChars());
            } else {
                // 其他单条翻译
                TranslateContext context = singleTranslate(shopName, randomDo.getSourceValue(), target,
                        textType, randomDo.getNodeKey(), glossaryMap);

                // 翻译后更新db
                randomDo.setTargetValue(context.getTranslatedContent());
                randomDo.setHasTargetValue(true);
                translateTaskV2Repo.update(randomDo);

                // 更新redis和sql的used token
                usedToken = userTokenService.addUsedToken(shopName, initialTaskId, context.getUsedToken());
                translateTaskMonitorV2RedisService.trackTranslateDetail(initialTaskId, 1,
                        context.getUsedToken(), context.getTranslatedChars());
            }

            maxToken = userTokenService.getMaxToken(shopName); // max token也重新获取，防止期间用户购买
            randomDo = translateTaskV2Repo.selectOneByInitialTaskIdAndEmptyValue(initialTaskId);
        }
        appInsights.trackTrace("TranslateTaskV2 translating done: " + shopName);

        // 判断是手动中断 还是limit中断，切换不同的状态
        if (redisStoppedRepository.isTaskStopped(shopName)) {
            int status = redisStoppedRepository.isStoppedByTokenLimit(shopName) ? 3 : 7;
            iTranslatesService.updateTranslateStatus(shopName, status, target, initialTaskV2DO.getSource());

            // 更新数据库状态为 5，翻译中断
            long translationTimeInMinutes = (System.currentTimeMillis() - initialTaskV2DO.getUpdatedAt().getTime()) / (1000 * 60);
            initialTaskV2DO.setStatus(InitialTaskStatus.STOPPED.status);
            initialTaskV2DO.setUsedToken(userTokenService.getUsedTokenByTaskId(shopName, initialTaskId));
            initialTaskV2DO.setTranslationMinutes((int) translationTimeInMinutes);
            initialTaskV2Repo.updateById(initialTaskV2DO);

            return;
        }

        iTranslatesService.updateTranslateStatus(shopName, 1, target, initialTaskV2DO.getSource());

        // 这个计算方式有问题， 暂定这样
        long translationTimeInMinutes = (System.currentTimeMillis() - initialTaskV2DO.getUpdatedAt().getTime()) / (1000 * 60);
        initialTaskV2DO.setStatus(InitialTaskStatus.TRANSLATE_DONE_SAVING_SHOPIFY.status);
        initialTaskV2DO.setUsedToken(userTokenService.getUsedTokenByTaskId(shopName, initialTaskId));
        initialTaskV2DO.setTranslationMinutes((int) translationTimeInMinutes);
        initialTaskV2Repo.updateById(initialTaskV2DO);
        translateTaskMonitorV2RedisService.setTranslateEndTime(initialTaskId);
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
            appInsights.trackTrace("TranslateTaskV2 saving shopify shop: " + shopName + " randomDo: " + randomDo.getId());
            String resourceId = randomDo.getResourceId();
            List<TranslateTaskV2DO> taskList = translateTaskV2Repo.selectByInitialTaskIdAndResourceIdWithLimit(initialTaskId, resourceId);

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
                appInsights.trackTrace("TranslateTaskV2 saving success: " + shopName +
                        " randomDo: " + randomDo.getId() + " response: " + strResponse);
                // 回写数据库，标记已写入 TODO 批量
                // 需要data.translationsRegister.translations[]不为空，并且有key，才是最严格的
                for (TranslateTaskV2DO taskDO : taskList) {
                    taskDO.setSavedToShopify(true);
                    translateTaskV2Repo.update(taskDO);
                }
                translateTaskMonitorV2RedisService.addSavedCount(initialTaskId, taskList.size());
            } else {
                // 写入失败 fatalException
                appInsights.trackTrace("FatalException TranslateTaskV2 saving failed: " + shopName +
                        " randomDo: " + randomDo.getId() + " response: " + strResponse);
            }
            randomDo = translateTaskV2Repo.selectOneByInitialTaskIdAndNotSaved(initialTaskId);
            appInsights.trackTrace("TranslateTaskV2 saving SHOPIFY: " + shopName + " size: " + taskList.size());
        }

        long savingShopifyTimeInMinutes = (System.currentTimeMillis() - initialTaskV2DO.getUpdatedAt().getTime()) / (1000 * 60);

        if (initialTaskV2DO.getStatus().equals(InitialTaskStatus.TRANSLATE_DONE_SAVING_SHOPIFY.status)) {
            initialTaskV2DO.setStatus(InitialTaskStatus.SAVE_DONE_SENDING_EMAIL.status);
        } // 否则是中断，不改变状态
        initialTaskV2DO.setSavingShopifyMinutes((int) savingShopifyTimeInMinutes);
        translateTaskMonitorV2RedisService.setSavingShopifyEndTime(initialTaskId);
        initialTaskV2Repo.updateById(initialTaskV2DO);
    }

    @Autowired
    private TencentEmailService tencentEmailService;

    // 翻译 step 5, 翻译写入都完成 -> 发送邮件，is_delete部分数据
    public void sendEmail(InitialTaskV2DO initialTaskV2DO) {
        String shopName = initialTaskV2DO.getShopName();

        Integer usingTimeMinutes = (int) ((System.currentTimeMillis() - initialTaskV2DO.getCreatedAt().getTime()) / (1000 * 60));
        Integer usedTokenByTask = userTokenService.getUsedTokenByTaskId(shopName, initialTaskV2DO.getId());

        // 正常结束，发送邮件
        if (InitialTaskStatus.SAVE_DONE_SENDING_EMAIL.status == initialTaskV2DO.getStatus()) {
            appInsights.trackTrace("TranslateTaskV2 Completed Email sent to user: " + shopName +
                    " Total time (minutes): " + usingTimeMinutes +
                    " Total tokens used: " + usedTokenByTask);

            Integer usedToken = userTokenService.getUsedToken(shopName);
            Integer totalToken = userTokenService.getMaxToken(shopName);
            tencentEmailService.sendSuccessEmail(shopName, initialTaskV2DO.getTarget(), usingTimeMinutes, usedTokenByTask,
                    usedToken, totalToken, initialTaskV2DO.getTaskType());

            initialTaskV2DO.setSendEmail(true);
            initialTaskV2Repo.updateToStatus(initialTaskV2DO, InitialTaskStatus.ALL_DONE.status);
            return;
        }

        // 中断，部分翻译发送邮件
        if (InitialTaskStatus.STOPPED.status == initialTaskV2DO.getStatus()) {
            // 中断翻译的话 token limit才发邮件
            if (redisStoppedRepository.isStoppedByTokenLimit(shopName)) {
                List<String> moduleList = JsonUtils.jsonToObject(initialTaskV2DO.getModuleList(), new TypeReference<>() {});
                assert moduleList != null;
                List<TranslateResourceDTO> resourceList = convertALL(moduleList);
                TranslateTaskV2DO translateTaskV2DO = translateTaskV2Repo.selectLastTranslateOne(initialTaskV2DO.getId());
                TypeSplitResponse typeSplitResponse = splitByType(translateTaskV2DO != null ? translateTaskV2DO.getModule() : null, resourceList);

                tencentEmailService.sendFailedEmail(shopName, initialTaskV2DO.getTarget(), usingTimeMinutes, usedTokenByTask,
                        typeSplitResponse.getBefore().toString(), typeSplitResponse.getAfter().toString(), initialTaskV2DO.getTaskType());

                initialTaskV2DO.setSendEmail(true);
                initialTaskV2Repo.updateToStatus(initialTaskV2DO, InitialTaskStatus.STOPPED.status);
            }
        }
    }

    // 根据翻译规则，不翻译的直接不用存
    private boolean needTranslate(ShopifyGraphResponse.TranslatableResources.Node.TranslatableContent translatableContent,
                                  List<ShopifyGraphResponse.TranslatableResources.Node.Translation> translations,
                                  String module, boolean isCover) {
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
                    if (!translation.getOutdated()) {
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
//        if (!CollectionUtils.isEmpty(existingTasks)) {
//            for (TranslateTaskV2DO task : existingTasks) {
//                if (translatableContent.getKey().equals(task.getNodeKey())) {
//                    return false;
//                }
//            }
//        }
        return true;
    }

    // 获取一条最新的没翻译数据，为空，改为searching； 有值则返回值


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
