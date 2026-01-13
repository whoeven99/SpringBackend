package com.bogda.api.logic.translate;

import com.bogda.api.Service.ITranslatesService;
import com.bogda.api.Service.IUsersService;
import com.bogda.api.context.TranslateContext;
import com.bogda.api.entity.DO.GlossaryDO;
import com.bogda.api.entity.DO.TranslateResourceDTO;
import com.bogda.api.entity.DO.TranslatesDO;
import com.bogda.api.entity.DO.UsersDO;
import com.bogda.api.entity.VO.SingleReturnVO;
import com.bogda.api.entity.VO.SingleTranslateVO;
import com.bogda.api.integration.model.ShopifyTranslationsRemove;
import com.bogda.api.utils.ModelTranslateUtils;
import com.bogda.api.utils.ModuleCodeUtils;
import com.bogda.common.contants.TranslateConstants;
import com.bogda.common.enums.ErrorEnum;
import com.bogda.api.integration.ALiYunTranslateIntegration;
import com.bogda.api.integration.GeminiIntegration;
import com.bogda.api.integration.model.ShopifyCheckMetafieldResponse;
import com.bogda.api.integration.model.ShopifyGraphResponse;
import com.bogda.api.logic.GlossaryService;
import com.bogda.api.logic.ShopifyService;
import com.bogda.api.logic.TaskService;
import com.bogda.api.logic.TencentEmailService;
import com.bogda.api.logic.redis.ConfigRedisRepo;
import com.bogda.api.logic.redis.RedisStoppedRepository;
import com.bogda.api.logic.redis.TranslateTaskMonitorV2RedisService;
import com.bogda.api.logic.token.UserTokenService;
import com.bogda.api.logic.translate.stragety.ITranslateStrategyService;
import com.bogda.api.logic.translate.stragety.TranslateStrategyFactory;
import com.bogda.api.model.controller.request.ClickTranslateRequest;
import com.bogda.api.model.controller.response.BaseResponse;
import com.bogda.api.model.controller.response.ProgressResponse;
import com.bogda.api.model.controller.response.TypeSplitResponse;
import com.bogda.repository.entity.InitialTaskV2DO;
import com.bogda.repository.entity.TranslateTaskV2DO;
import com.bogda.repository.repo.InitialTaskV2Repo;
import com.bogda.repository.repo.TranslateTaskV2Repo;
import com.bogda.common.utils.JsoupUtils;
import com.bogda.common.utils.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import kotlin.Pair;
import lombok.Getter;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.io.InputStream;
import java.net.URL;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.bogda.api.entity.DO.TranslateResourceDTO.EMAIL_MAP;
import static com.bogda.api.logic.TaskService.AUTO_TRANSLATE_MAP;

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
    @Autowired
    private TencentEmailService tencentEmailService;
    @Autowired
    private ConfigRedisRepo configRedisRepo;
    @Autowired
    private IUsersService usersService;
    @Autowired
    private ITranslatesService translatesService;
    @Autowired
    private ALiYunTranslateIntegration aLiYunTranslateIntegration;
    @Autowired
    private GeminiIntegration geminiIntegration;

    private static final String JSON_JUDGE = "\"type\":\"text\""; // 用于json数据的筛选

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
        TranslateContext context = new TranslateContext(request.getContext(), request.getTarget(), request.getType(),
                request.getKey(), glossaryService.getGlossaryDoByShopName(shopName, request.getTarget()), GeminiIntegration.GEMINI_3_FLASH);
        ITranslateStrategyService service = translateStrategyFactory.getServiceByContext(context);
        service.translate(context);
        service.finishAndGetJsonRecord(context);
        userTokenService.addUsedToken(shopName, context.getUsedToken());

        SingleReturnVO returnVO = new SingleReturnVO();
        returnVO.setTargetText(context.getTranslatedContent());
        returnVO.setTranslateVariables(context.getTranslateVariables());
        return BaseResponse.SuccessResponse(returnVO);
    }

    // For monitor
    public Map<String, Object> testTranslate(Map<String, Object> map) {
        // 1. 提取并校验参数
        String model = String.valueOf(map.getOrDefault("model", ""));
        String prompt = String.valueOf(map.getOrDefault("prompt", ""));
        String target = String.valueOf(map.getOrDefault("target", ""));
        String picUrl = (map.get("picUrl") != null) ? map.get("picUrl").toString() : null;

        // 解析 JSON 列表并替换占位符
        try {
            String jsonStr = String.valueOf(map.getOrDefault("json", "{}"));
            Map<Integer, String> languageMap = JsonUtils.jsonToObject(jsonStr, new TypeReference<Map<Integer, String>>() {
            });
            if (CollectionUtils.isEmpty(languageMap)) {
                return defaultNullMap();
            }
            prompt = prompt.replace("{{SOURCE_LANGUAGE_LIST}}", languageMap.toString())
                    .replace("{{TARGET_LANGUAGE}}", target);
        } catch (Exception e) {
            AppInsightsUtils.trackTrace("FatalException testTranslate JSON parsing failed: " + e.getMessage());
            return defaultNullMap();
        }

        // 2. 模型路由调度
        try {
            if (model.contains("qwen")) {
                return handleAliYun(prompt, target);
            } else if (model.contains("gemini")) {
                return handleGemini(model, prompt, picUrl);
            }
        } catch (Exception e) {
            AppInsightsUtils.trackException(e);
            AppInsightsUtils.trackTrace("FatalException in testTranslate: " + e.getMessage());
        }

        return defaultNullMap();
    }

    /**
     * 处理通义千问逻辑
     */
    private Map<String, Object> handleAliYun(String prompt, String target) {
        Pair<String, Integer> pair = aLiYunTranslateIntegration.userTranslate(prompt, target);
        if (pair == null) {
            return defaultNullMap();
        }
        return buildResponse(pair.getFirst(), pair.getSecond(), "text");
    }

    /**
     * 处理 Gemini 逻辑 (包含多模态判断)
     */
    private Map<String, Object> handleGemini(String model, String prompt, String picUrl) throws Exception {
        if (picUrl == null) {
            Pair<String, Integer> pair = geminiIntegration.generateText(model, prompt);
            if (pair == null) {
                return defaultNullMap();
            }
            return buildResponse(pair.getFirst(), pair.getSecond(), "text");
        }

        // 图片处理逻辑
        String picType = PictureUtils.getExtensionFromUrl(picUrl);
        String mimeType = (picType != null) ? PictureUtils.IMAGE_MIME_MAP.get(picType.toLowerCase()) : null;

        if (mimeType == null) {
            return defaultNullMap();
        }

        try (InputStream in = new URL(picUrl).openStream()) {
            byte[] imageBytes = in.readAllBytes();
            Pair<String, Integer> pair = geminiIntegration.generateImage(model, prompt, imageBytes, mimeType);
            if (pair == null) {
                return defaultNullMap();
            }

            // 拼接成前端可以直接识别的 Data URL 格式
            // 最终格式示例：data:image/png;base64,iVBORw0KGgoAAA...
            String dataUrl = "data:" + mimeType + ";base64," + pair.getFirst();

            return buildResponse(dataUrl, pair.getSecond(), "pic");
        }
    }

    private Map<String, Object> buildResponse(Object content, Integer tokens, String translateModel) {
        Map<String, Object> ans = new HashMap<>();
        ans.put("content", content);
        ans.put("allToken", tokens);
        ans.put("translateModel", translateModel);
        return ans;
    }

    private Map<String, Object> defaultNullMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("content", "error");
        map.put("allToken", 0);
        return map;
    }

    // 手动开启翻译任务入口
    // 翻译 step 1, 用户 -> initial任务创建
    public BaseResponse<Object> createInitialTask(ClickTranslateRequest request) {
        String shopName = request.getShopName();
        AppInsightsUtils.trackTrace("createInitialTask : " + " shopName : " + shopName + " " + request);
        String[] targets = request.getTarget();
        List<String> moduleList = request.getTranslateSettings3();
        String translateSettings1 = request.getTranslateSettings1();
        if (StringUtils.isEmpty(shopName) || targets == null || targets.length == 0 ||
                CollectionUtils.isEmpty(moduleList) || translateSettings1 == null) {
            return BaseResponse.FailedResponse("Missing parameters");
        }

        // 判断字符是否超限
        Integer maxToken = userTokenService.getMaxToken(shopName);
        Integer usedToken = userTokenService.getUsedToken(shopName);

        // 如果字符超限，则直接返回字符超限
        if (usedToken >= maxToken) {
            return new BaseResponse<>().CreateErrorResponse(ErrorEnum.TOKEN_LIMIT);
        }

        translateSettings1 = ModuleCodeUtils.getModuleCode(translateSettings1);

        // 判断用户语言是否正在翻译，翻译的不管；没翻译的翻译。
        List<InitialTaskV2DO> initialTaskV2DOS =
                initialTaskV2Repo.selectByShopNameSourceManual(shopName, request.getSource());
        Set<String> targetSet = new HashSet<>(Arrays.asList(targets));

        // 找出已经 INIT，READ_DONE，TRANSLATE_DONE，SAVE_DONE 的 target
        Set<String> filteredTargets = initialTaskV2DOS.stream()
                .filter(it -> it.getStatus() == InitialTaskStatus.INIT_READING_SHOPIFY.getStatus()
                        || it.getStatus() == InitialTaskStatus.READ_DONE_TRANSLATING.getStatus()
                        || it.getStatus() == InitialTaskStatus.TRANSLATE_DONE_SAVING_SHOPIFY.getStatus()
                        || it.getStatus() == InitialTaskStatus.SAVE_DONE_SENDING_EMAIL.getStatus())
                .map(InitialTaskV2DO::getTarget)
                .collect(Collectors.toSet());

        // 将filteredTargets和targets对比，去除targets里和filteredTargets相同的值
        Set<String> finalTargets = targetSet.stream()
                .filter(t -> !filteredTargets.contains(t) && !configRedisRepo.isWhiteList(t, "forbiddenTarget"))
                .collect(Collectors.toSet());

        if (finalTargets.isEmpty()) {
            return new BaseResponse<>().CreateErrorResponse("No Target Language Can Create");
        }

        boolean hasHandle = moduleList.contains("handle");

        // 收集所有的resourceType到一个列表中
        List<String> resourceTypeList = moduleList.stream()
                .filter(module -> !"handle".equals(module)) // 去掉 handle
                .flatMap(module -> {
                    List<TranslateResourceDTO> list = TranslateResourceDTO.TOKEN_MAP.get(module);
                    if (list == null) {
                        AppInsightsUtils.trackTrace("FatalException createInitialTask Warning: Unknown module: " + module);
                        return Stream.empty();
                    }
                    return list.stream();
                })
                .filter(Objects::nonNull)
                .map(TranslateResourceDTO::getResourceType)
                .toList();
        resourceTypeList = ModelTranslateUtils.sortTranslateData(resourceTypeList);
        this.isExistInDatabase(shopName, finalTargets.toArray(new String[0]), request.getSource(), request.getAccessToken());
        this.createManualTask(shopName, request.getSource(), finalTargets, resourceTypeList, request.getIsCover(), hasHandle, translateSettings1);


        // 找前端，把这里的返回改了
        request.setTarget(finalTargets.toArray(new String[0]));
        request.setAccessToken("");
        return BaseResponse.SuccessResponse(request);
    }

    public void isExistInDatabase(String shopName, String[] targets, String source, String accessToken) {
        // 1. 查询当前 DB 中已有的 target
        List<TranslatesDO> doList = translatesService.selectTargetByShopNameSource(shopName, source);
        boolean needSync;
        List<String> dbTargetList;
        if (CollectionUtils.isEmpty(doList)) {
            dbTargetList = new ArrayList<>();
            needSync = true;
        } else {
            dbTargetList = doList.stream().map(TranslatesDO::getTarget).toList();

            // 2. 检查是否缺少任意一个 target
            needSync = Arrays.stream(targets).anyMatch(t -> !dbTargetList.contains(t));
        }

        if (!needSync) {
            return;
        }

        // 3. 获取 Shopify 语言数据
        String shopifyData = shopifyService.getShopifyData(shopName, accessToken, TranslateConstants.API_VERSION_LAST, ShopifyRequestUtils.getLanguagesQuery());
        JsonNode root = JsonUtils.readTree(shopifyData);

        if (root == null) {
            return;
        }

        JsonNode shopLocales = root.path("shopLocales");
        if (!shopLocales.isArray()) {
            AppInsightsUtils.trackTrace("syncShopifyAndDatabase: shopLocales is not an array.");
            return;
        }

        // 4. 写入缺失的 locale
        for (JsonNode node : shopLocales) {
            String locale = node.path("locale").asText(null);
            if (locale != null && !dbTargetList.contains(locale)) {
                translatesService.insertShopTranslateInfoByShopify(shopName, accessToken, locale, source);
            }
        }
    }

    private void createManualTask(String shopName, String source, Set<String> targets,
                                  List<String> moduleList, Boolean isCover, Boolean hasHandle, String aiModel) {
        initialTaskV2Repo.deleteByShopNameSourceAndType(shopName, source, "manual");
        redisStoppedRepository.removeStoppedFlag(shopName);

        for (String target : targets) {
            InitialTaskV2DO initialTask = new InitialTaskV2DO();
            initialTask.setShopName(shopName);
            initialTask.setSource(source);
            initialTask.setTarget(target);
            initialTask.setCover(isCover);
            initialTask.setModuleList(JsonUtils.objectToJson(moduleList));
            initialTask.setStatus(InitialTaskStatus.INIT_READING_SHOPIFY.getStatus());
            initialTask.setTaskType("manual");
            initialTask.setHandle(hasHandle);
            initialTask.setAiModel(aiModel);
            initialTaskV2Repo.insert(initialTask);

            translateTaskMonitorV2RedisService.createRecord(initialTask.getId(), shopName, source, target, aiModel);
            iTranslatesService.updateTranslateStatus(shopName, 2, target, source);
        }
    }

    public void createAutoTask(String shopName, String source, String target) {
        if (configRedisRepo.isWhiteList(target, "forbiddenTarget")) {
            return;
        }
        initialTaskV2Repo.deleteByShopNameSourceTarget(shopName, source, target);

        InitialTaskV2DO initialTask = new InitialTaskV2DO();
        initialTask.setShopName(shopName);
        initialTask.setSource(source);
        initialTask.setTarget(target);
        initialTask.setCover(false);
        initialTask.setModuleList(JsonUtils.objectToJson(AUTO_TRANSLATE_MAP));
        initialTask.setStatus(InitialTaskStatus.INIT_READING_SHOPIFY.getStatus());
        initialTask.setTaskType("auto");
        initialTask.setAiModel(GeminiIntegration.GEMINI_3_FLASH);
        initialTaskV2Repo.insert(initialTask);

        translateTaskMonitorV2RedisService.createRecord(initialTask.getId(), shopName, source, target, GeminiIntegration.GEMINI_3_FLASH);
    }

    /**
     * test 自动翻译，仅主题模块
     */
    public void testAutoTranslate(String shopName, String source, String target) {
        initialTaskV2Repo.deleteByShopNameSourceTarget(shopName, source, target);

        InitialTaskV2DO initialTask = new InitialTaskV2DO();
        initialTask.setShopName(shopName);
        initialTask.setSource(source);
        initialTask.setTarget(target);
        initialTask.setCover(false);
        initialTask.setModuleList(JsonUtils.objectToJson(TaskService.TEST_AUTO_TRANSLATE_MAP));
        initialTask.setStatus(InitialTaskStatus.INIT_READING_SHOPIFY.getStatus());
        initialTask.setTaskType("auto");
        initialTaskV2Repo.insert(initialTask);

        translateTaskMonitorV2RedisService.createRecord(initialTask.getId(), shopName, source, target, ALiYunTranslateIntegration.QWEN_MAX);
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
            ProgressResponse.Progress progress = new ProgressResponse.Progress();
            progress.setTaskId(task.getId());
            if (task.getStatus().equals(InitialTaskStatus.INIT_READING_SHOPIFY.getStatus())) {
                progress.setTarget(task.getTarget());
                progress.setStatus(2);
                progress.setTranslateStatus("translation_process_init");
                defaultProgressTranslateData.put("TotalQuantity", 1);
                defaultProgressTranslateData.put("RemainingQuantity", 1);
                progress.setProgressData(defaultProgressTranslateData);
                progress.setInitialCount(taskContext.get("totalCount"));
                list.add(progress);
            } else if (task.getStatus().equals(InitialTaskStatus.READ_DONE_TRANSLATING.getStatus())) {
                progress.setTarget(task.getTarget());
                progress.setStatus(2);
                progress.setTranslateStatus("translation_process_translating");

                Long count = Long.valueOf(taskContext.get("totalCount"));
                Long translatedCount = Long.valueOf(taskContext.get("translatedCount"));

                Map<String, Integer> progressData = new HashMap<>();
                progressData.put("TotalQuantity", count.intValue());
                progressData.put("RemainingQuantity", count.intValue() - translatedCount.intValue());

                progress.setProgressData(progressData);
                list.add(progress);
            } else if (task.getStatus().equals(InitialTaskStatus.TRANSLATE_DONE_SAVING_SHOPIFY.getStatus()) ||
                    task.getStatus().equals(InitialTaskStatus.SAVE_DONE_SENDING_EMAIL.getStatus())) {
                progress.setTarget(task.getTarget());
                progress.setStatus(1);
                progress.setTranslateStatus("translation_process_saving_shopify");

                Long count = Long.valueOf(taskContext.get("totalCount"));
                Long savedCount = Long.valueOf(taskContext.get("savedCount"));

                Map<String, Integer> progressWriteData = new HashMap<>();
                progressWriteData.put("write_total", count.intValue());
                progressWriteData.put("write_done", savedCount.intValue());

                progress.setWritingData(progressWriteData);
                progress.setProgressData(defaultProgressTranslateData);
                list.add(progress);
            } else if (task.getStatus().equals(InitialTaskStatus.ALL_DONE.getStatus())) {
                progress.setTarget(task.getTarget());
                progress.setStatus(1);
                progress.setTranslateStatus("translation_process_saved");
                progress.setProgressData(defaultProgressTranslateData);
                list.add(progress);
            } else if (task.getStatus().equals(InitialTaskStatus.STOPPED.getStatus())) {
                progress.setTarget(task.getTarget());

                Long count = Long.valueOf(taskContext.get("totalCount"));
                Long translatedCount = Long.valueOf(taskContext.get("translatedCount"));
                Map<String, Integer> progressData = new HashMap<>();
                progressData.put("TotalQuantity", count.intValue());
                progressData.put("RemainingQuantity", count.intValue() - translatedCount.intValue());
                progress.setProgressData(progressData);

                // 判断是手动中断，还是limit中断
                if (redisStoppedRepository.isStoppedByTokenLimit(shopName) ||
                        redisStoppedRepository.isStoppedByTokenLimit(shopName, task.getId())) {
                    progress.setStatus(3); // limit中断
                } else {
                    progress.setStatus(7);// 中断的状态
                }

                list.add(progress);
            }
        }

        return new BaseResponse<ProgressResponse>().CreateSuccessResponse(response);
    }

    private boolean containsModule(String modules, String module) {
        if (StringUtils.isEmpty(modules)) {
            return false;
        }
        String[] moduleArray = modules.split(",");
        for (String mod : moduleArray) {
            if (mod.equals(module)) {
                return true;
            }
        }
        return false;
    }

    // 翻译 step 2, initial -> 查询shopify，翻译任务创建
    public void initialToTranslateTask(InitialTaskV2DO initialTaskV2DO) {
        String shopName = initialTaskV2DO.getShopName();
        String target = initialTaskV2DO.getTarget();

        List<String> moduleList = JsonUtils.jsonToObject(initialTaskV2DO.getModuleList(), new TypeReference<>() {
        });
        assert moduleList != null;

        UsersDO userDO = iUsersService.getUserByName(initialTaskV2DO.getShopName());

        String finishedModules = translateTaskMonitorV2RedisService.getFinishedModules(initialTaskV2DO.getId());
        String afterEndCursor = translateTaskMonitorV2RedisService.getAfterEndCursor(initialTaskV2DO.getId());
        List<ShopifyTranslationsRemove> shopifyTranslationsRemoveList = new ArrayList<>();
        for (String module : moduleList) {
            if (containsModule(finishedModules, module)) {
                continue;
            }
            TranslateTaskV2DO translateTaskV2DO = new TranslateTaskV2DO();
            translateTaskV2DO.setModule(module);
            translateTaskV2DO.setInitialTaskId(initialTaskV2DO.getId());

            shopifyService.rotateAllShopifyGraph(shopName, module, userDO.getAccessToken(), 250, target, afterEndCursor,
                    (node -> {
                        if (node != null && !CollectionUtils.isEmpty(node.getTranslatableContent())) {
                            translateTaskV2DO.setResourceId(node.getResourceId());
                            AppInsightsUtils.trackTrace("TranslateTaskV2 rotating Shopify: " + shopName + " module: " + module +
                                    " resourceId: " + node.getResourceId());

                            // 每个node有几个translatableContent
                            node.getTranslatableContent().forEach(translatableContent -> {
                                if (needTranslate(translatableContent, node.getTranslations(), module, initialTaskV2DO.isCover()
                                        , initialTaskV2DO.isHandle(), shopName, userDO.getAccessToken(), node.getResourceId()
                                        , shopifyTranslationsRemoveList)) {
                                    translateTaskV2DO.setSourceValue(translatableContent.getValue());
                                    translateTaskV2DO.setNodeKey(translatableContent.getKey());
                                    translateTaskV2DO.setType(translatableContent.getType());
                                    translateTaskV2DO.setDigest(translatableContent.getDigest());
                                    translateTaskV2DO.setSingleHtml(JsoupUtils.isHtml(translatableContent.getValue()));
                                    translateTaskV2DO.setId(null);
                                    try {
                                        translateTaskV2Repo.insert(translateTaskV2DO);
                                        translateTaskMonitorV2RedisService.incrementTotalCount(initialTaskV2DO.getId());
                                    } catch (Exception e) {
                                        AppInsightsUtils.trackException(e);
                                        AppInsightsUtils.trackTrace("FatalException initialToTranslateTask insert error " + e.getMessage());
                                    }
                                }
                            });

                            // 将shopifyTranslationsRemoveList里面的数据删除掉
                            if (!shopifyTranslationsRemoveList.isEmpty()) {
                                // 生成查询语句
                                shopifyService.deleteShopifyDataWithRateLimit(shopName, userDO.getAccessToken(),
                                        shopifyTranslationsRemoveList);
                                shopifyTranslationsRemoveList.clear();
                            }

                        }
                    }),
                    (after -> translateTaskMonitorV2RedisService.setAfterEndCursor(initialTaskV2DO.getId(), after)));
            // 断电后 跳过这个module
            translateTaskMonitorV2RedisService.addFinishedModule(initialTaskV2DO.getId(), module);
            // 清空afterEndCursor
            translateTaskMonitorV2RedisService.setAfterEndCursor(initialTaskV2DO.getId(), "");
            AppInsightsUtils.trackTrace("TranslateTaskV2 rotate Shopify done: " + shopName + " module: " + module);
        }

        // 更新数据库并记录初始化时间
        AppInsightsUtils.trackTrace("TranslateTaskV2 initialToTranslateTask done: " + shopName);

        long initTimeInMinutes = (System.currentTimeMillis() - initialTaskV2DO.getUpdatedAt().getTime()) / (1000 * 60);
        initialTaskV2DO.setStatus(InitialTaskStatus.READ_DONE_TRANSLATING.status);
        initialTaskV2DO.setInitMinutes((int) initTimeInMinutes);
        translateTaskMonitorV2RedisService.setInitEndTime(initialTaskV2DO.getId());
        initialTaskV2Repo.updateStatusAndInitMinutes(initialTaskV2DO.getStatus(), initialTaskV2DO.getInitMinutes()
                , initialTaskV2DO.getId());
    }

    // 翻译 step 3, 翻译任务 -> 具体翻译行为 直接对数据库操作
    public void translateEachTask(InitialTaskV2DO initialTaskV2DO) {
        // 这里可以从数据库，直接批量获取各种type，一次性翻译不同模块的数据
        Integer initialTaskId = initialTaskV2DO.getId();
        String target = initialTaskV2DO.getTarget();
        String shopName = initialTaskV2DO.getShopName();
        String aiModel = initialTaskV2DO.getAiModel();

        Map<String, GlossaryDO> glossaryMap = glossaryService.getGlossaryDoByShopName(shopName, target);

        Integer maxToken = userTokenService.getMaxToken(shopName);
        Integer usedToken = userTokenService.getUsedToken(shopName);

        TranslateTaskV2DO randomDo = translateTaskV2Repo.selectOneByInitialTaskIdAndEmptyValue(initialTaskId);
        while (randomDo != null) {
            AppInsightsUtils.trackTrace("TranslateTaskV2 translating shop: " + shopName + " randomDo: " + randomDo.getId());

            // 用于中断之后的邮件描述
            initialTaskV2DO.setTransModelType(randomDo.getModule());
            if (usedToken >= maxToken) {
                // 记录是因为token limit中断的
                redisStoppedRepository.tokenLimitStopped(shopName, initialTaskId);
            }

            // 还可能是手动中断
            if (redisStoppedRepository.isTaskStopped(shopName) || redisStoppedRepository.isTaskStopped(shopName, initialTaskId)) {
                break;
            }

            // 随机找一条，如果是html就单条翻译，不是就直接批量
            boolean isHtml = randomDo.isSingleHtml();
            if (JsonUtils.isJson(randomDo.getSourceValue())) {
                TranslateContext context = new TranslateContext(randomDo.getSourceValue(), target, glossaryMap, aiModel);
                ITranslateStrategyService service = translateStrategyFactory.getServiceByStrategy("JSON");
                service.translate(context);

                // 翻译后更新db
                translateTaskV2Repo.updateTargetValueAndHasTargetValue(context.getTranslatedContent(), true, randomDo.getId());

                usedToken = userTokenService.addUsedToken(shopName, initialTaskId, context.getUsedToken());
                translateTaskMonitorV2RedisService.trackTranslateDetail(initialTaskId, 1,
                        context.getUsedToken(), context.getTranslatedChars());

            } else if (isHtml) {
                TranslateContext context = new TranslateContext(randomDo.getSourceValue(), target, glossaryMap, aiModel);
                ITranslateStrategyService service = translateStrategyFactory.getServiceByStrategy("HTML");
                service.translate(context);

                // 翻译后更新db
                translateTaskV2Repo.updateTargetValueAndHasTargetValue(context.getTranslatedContent(), true, randomDo.getId());

                usedToken = userTokenService.addUsedToken(shopName, initialTaskId, context.getUsedToken());
                translateTaskMonitorV2RedisService.trackTranslateDetail(initialTaskId, 1,
                        context.getUsedToken(), context.getTranslatedChars());
            } else {
                // 批量翻译
                List<TranslateTaskV2DO> originTaskList =
                        translateTaskV2Repo.selectByInitialTaskIdAndTypeAndEmptyValueWithLimit(initialTaskId, 30);

                List<TranslateTaskV2DO> taskList = new ArrayList<>();
                int totalChars = 0;
                for (TranslateTaskV2DO task : originTaskList) {
                    taskList.add(task);
                    totalChars += ALiYunTranslateIntegration.calculateBaiLianToken(task.getSourceValue());
                    if (totalChars > 600) {
                        break;
                    }
                }

                Map<Integer, String> idToSourceValueMap = taskList.stream()
                        .collect(Collectors.toMap(TranslateTaskV2DO::getId, TranslateTaskV2DO::getSourceValue));

                TranslateContext context = new TranslateContext(idToSourceValueMap, target, glossaryMap, aiModel);
                ITranslateStrategyService service = translateStrategyFactory.getServiceByContext(context);
                service.translate(context);

                Map<Integer, String> translatedValueMap = context.getTranslatedTextMap();
                for (TranslateTaskV2DO updatedDo : taskList) {
                    String targetValue = translatedValueMap.get(updatedDo.getId());

                    // 3.3 回写数据库 todo 批量
                    if (targetValue == null) {
                        AppInsightsUtils.trackTrace("FatalException targetValue is null: " + shopName + " " + initialTaskId + " " + updatedDo.getId());
                        continue;
                    }
                    translateTaskV2Repo.updateTargetValueAndHasTargetValue(targetValue, true, updatedDo.getId());
                }
                usedToken = userTokenService.addUsedToken(shopName, initialTaskId, context.getUsedToken());
                translateTaskMonitorV2RedisService.trackTranslateDetail(initialTaskId, taskList.size(),
                        context.getUsedToken(), context.getTranslatedChars());
            }

            maxToken = userTokenService.getMaxToken(shopName); // max token也重新获取，防止期间用户购买
            randomDo = translateTaskV2Repo.selectOneByInitialTaskIdAndEmptyValue(initialTaskId);
        }
        AppInsightsUtils.trackTrace("TranslateTaskV2 translating done: " + shopName);

        // 判断是手动中断 还是limit中断，切换不同的状态
        if (redisStoppedRepository.isTaskStopped(shopName, initialTaskId) || redisStoppedRepository.isTaskStopped(shopName)) {
            int status = redisStoppedRepository.isStoppedByTokenLimit(shopName) ||
                    redisStoppedRepository.isStoppedByTokenLimit(shopName, initialTaskId) ? 3 : 7;
            iTranslatesService.updateTranslateStatus(shopName, status, target, initialTaskV2DO.getSource());

            // 更新数据库状态为 5，翻译中断
            long translationTimeInMinutes = (System.currentTimeMillis() - initialTaskV2DO.getUpdatedAt().getTime()) / (1000 * 60);
            initialTaskV2DO.setStatus(InitialTaskStatus.STOPPED.status);
            initialTaskV2DO.setUsedToken(userTokenService.getUsedTokenByTaskId(shopName, initialTaskId));
            initialTaskV2DO.setTranslationMinutes((int) translationTimeInMinutes);

            initialTaskV2Repo.updateStatusUsedTokenTranslationMinutesModuleById(initialTaskV2DO.getStatus(),
                    initialTaskV2DO.getUsedToken(), initialTaskV2DO.getTranslationMinutes()
                    , initialTaskV2DO.getTransModelType(), initialTaskV2DO.getId());
            translateTaskMonitorV2RedisService.setTranslateEndTime(initialTaskId);
            return;
        }

        iTranslatesService.updateTranslateStatus(shopName, 1, target, initialTaskV2DO.getSource());

        // 这个计算方式有问题， 暂定这样
        long translationTimeInMinutes = (System.currentTimeMillis() - initialTaskV2DO.getUpdatedAt().getTime()) / (1000 * 60);
        initialTaskV2DO.setStatus(InitialTaskStatus.TRANSLATE_DONE_SAVING_SHOPIFY.status);
        initialTaskV2DO.setUsedToken(userTokenService.getUsedTokenByTaskId(shopName, initialTaskId));
        initialTaskV2DO.setTranslationMinutes((int) translationTimeInMinutes);
        initialTaskV2Repo.updateStatusUsedTokenTranslationMinutesModuleById(initialTaskV2DO.getStatus(),
                initialTaskV2DO.getUsedToken(), initialTaskV2DO.getTranslationMinutes()
                , initialTaskV2DO.getTransModelType(), initialTaskV2DO.getId());
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
            AppInsightsUtils.trackTrace("TranslateTaskV2 saving shopify shop: " + shopName + " randomDo: " + randomDo.getId());
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
            String strResponse = shopifyService.saveDataWithRateLimit(shopName, token, node);
            if (strResponse != null) {
                AppInsightsUtils.trackTrace("TranslateTaskV2 saving success: " + shopName +
                        " randomDo: " + randomDo.getId() + " response: " + strResponse);
                // 回写数据库，标记已写入 TODO 批量
                // 需要data.translationsRegister.translations[]不为空，并且有key，才是最严格的
                for (TranslateTaskV2DO taskDO : taskList) {
                    translateTaskV2Repo.updateSavedToShopify(taskDO.getId());
                }
                translateTaskMonitorV2RedisService.addSavedCount(initialTaskId, taskList.size());
            } else {
                // 写入失败 fatalException
                AppInsightsUtils.trackTrace("FatalException TranslateTaskV2 saving failed: " + shopName +
                        " randomDo: " + randomDo.getId() + " response: " + strResponse);
            }
            randomDo = translateTaskV2Repo.selectOneByInitialTaskIdAndNotSaved(initialTaskId);
            AppInsightsUtils.trackTrace("TranslateTaskV2 saving SHOPIFY: " + shopName + " size: " + taskList.size());
        }

        long savingShopifyTimeInMinutes = (System.currentTimeMillis() - initialTaskV2DO.getUpdatedAt().getTime()) / (1000 * 60);

        if (initialTaskV2DO.getStatus().equals(InitialTaskStatus.TRANSLATE_DONE_SAVING_SHOPIFY.status)) {
            initialTaskV2DO.setStatus(InitialTaskStatus.SAVE_DONE_SENDING_EMAIL.status);
        }

        // 否则是中断，不改变状态
        initialTaskV2DO.setSavingShopifyMinutes((int) savingShopifyTimeInMinutes);
        translateTaskMonitorV2RedisService.setSavingShopifyEndTime(initialTaskId);
        initialTaskV2Repo.updateStatusSavingShopifyMinutesById(initialTaskV2DO.getStatus(), initialTaskV2DO.getSavingShopifyMinutes()
                , initialTaskV2DO.getId());
    }

    // 翻译 step 5, 翻译写入都完成 -> 发送邮件，is_delete部分数据
    public void sendManualEmail(InitialTaskV2DO initialTaskV2DO) {
        String shopName = initialTaskV2DO.getShopName();

        Integer usingTimeMinutes = (int) ((System.currentTimeMillis() - initialTaskV2DO.getCreatedAt().getTime()) / (1000 * 60));
        Integer usedTokenByTask = userTokenService.getUsedTokenByTaskId(shopName, initialTaskV2DO.getId());

        // 手动翻译 正常结束，发送邮件
        if (InitialTaskStatus.SAVE_DONE_SENDING_EMAIL.status == initialTaskV2DO.getStatus()) {
            AppInsightsUtils.trackTrace("TranslateTaskV2 Completed Email sent to user: " + shopName +
                    " Total time (minutes): " + usingTimeMinutes +
                    " Total tokens used: " + usedTokenByTask);

            Integer usedToken = userTokenService.getUsedToken(shopName);
            Integer totalToken = userTokenService.getMaxToken(shopName);
            tencentEmailService.sendSuccessEmail(shopName, initialTaskV2DO.getTarget(), usingTimeMinutes, usedTokenByTask,
                    usedToken, totalToken);

            initialTaskV2Repo.updateSendEmailAndStatusById(true, InitialTaskStatus.ALL_DONE.status, initialTaskV2DO.getId());
            return;
        }

        // 中断，部分翻译发送邮件
        if (InitialTaskStatus.STOPPED.status == initialTaskV2DO.getStatus()) {
            // 判断是不是手动中断, 是的话 立即中断, 将邮件设置为已发送
            boolean stoppedByLimit = redisStoppedRepository.isStoppedByTokenLimit(initialTaskV2DO.getShopName()) ||
                    redisStoppedRepository.isStoppedByTokenLimit(initialTaskV2DO.getShopName(), initialTaskV2DO.getId());
            if (!stoppedByLimit) {
                initialTaskV2Repo.updateSendEmailById(initialTaskV2DO.getId(), true);
                return;
            }

            // 判断现在的时间和db的更新时间是否相差10分钟 (可调整)  如果不相差10分钟,跳过
            long translateEndTime = Long.parseLong(translateTaskMonitorV2RedisService.getTranslateEndTime(initialTaskV2DO.getId()));
            if ((System.currentTimeMillis() - translateEndTime) < 60 * 10000) {
                return;
            }

            // 相差10分钟, 获取该用户所有状态为5、是部分翻译、未发送邮件、未逻辑删除的手动翻译任务, 发送邮件
            List<InitialTaskV2DO> stoppedTasks = initialTaskV2Repo.selectByShopNameStoppedAndNotEmail(shopName, "manual");
            if (CollectionUtils.isEmpty(stoppedTasks)) {
                return;
            }

            // 判断是否是部分翻译, 然后存到部分翻译的list集合里面; 如果是手动中断,存到手动中断的map集合里面
            List<InitialTaskV2DO> partialTranslation = new ArrayList<>();

            for (InitialTaskV2DO task : stoppedTasks) {
                boolean stoppedByTokenLimit = redisStoppedRepository.isStoppedByTokenLimit(task.getShopName()) ||
                        redisStoppedRepository.isStoppedByTokenLimit(task.getShopName(), task.getId());
                if (stoppedByTokenLimit) {
                    partialTranslation.add(task);
                }
            }

            // 根据部分翻译list的集合,发送批量失败的邮件
            if (!partialTranslation.isEmpty()) {
                AppInsightsUtils.trackTrace("sendManualEmail 手动翻译批量失败邮件 : " + shopName);
                tencentEmailService.sendTranslatePartialEmail(shopName, partialTranslation, "manual translation");
            }

            // 将任务改为已发送
            for (InitialTaskV2DO task : partialTranslation) {
                initialTaskV2Repo.updateSendEmailById(task.getId(), true);
            }
        }
    }

    private static List<TranslateResourceDTO> convertALL(List<String> list) {
        //修改模块的排序
        List<TranslateResourceDTO> translateResourceDTOList = new ArrayList<>();
        for (String s : list) {
            translateResourceDTOList.add(new TranslateResourceDTO(s, TranslateConstants.MAX_LENGTH, "", ""));
        }
        return translateResourceDTOList;
    }

    public void continueTranslating(String shopName) {
        List<InitialTaskV2DO> list = initialTaskV2Repo.selectStoppedByShopName(shopName);
        if (!list.isEmpty()) {
            redisStoppedRepository.removeStoppedFlag(shopName);
            for (InitialTaskV2DO initialTaskV2DO : list) {
                initialTaskV2DO.setStatus(InitialTaskStatus.READ_DONE_TRANSLATING.getStatus());
                boolean updateFlag = initialTaskV2Repo.updateStatusAndSendEmailById(initialTaskV2DO.getStatus(), initialTaskV2DO.getId(), false);
                AppInsightsUtils.trackTrace("continueTranslating updateFlag: " + updateFlag + " shop: " + shopName + " taskId: " + initialTaskV2DO.getId());
            }
        }
    }

    public void continueTranslatingByShopName(String shopName) {
        List<InitialTaskV2DO> list = initialTaskV2Repo.selectStoppedByShopName(shopName);
        if (!list.isEmpty()) {
            for (InitialTaskV2DO initialTaskV2DO : list) {
                redisStoppedRepository.removeStoppedFlag(shopName, initialTaskV2DO.getId());
                initialTaskV2DO.setStatus(InitialTaskStatus.READ_DONE_TRANSLATING.getStatus());
                boolean updateFlag = initialTaskV2Repo.updateStatusAndSendEmailById(initialTaskV2DO.getStatus(), initialTaskV2DO.getId(), false);
                AppInsightsUtils.trackTrace("continueTranslating updateFlag: " + updateFlag + " shop: " + shopName + " taskId: " + initialTaskV2DO.getId());
            }
        }
    }

    // 在TranslateTask里定时调用这里
    public boolean autoTranslateV2(String shopName, String source, String target) {
        UsersDO usersDO = usersService.getUserByName(shopName);
        if (usersDO == null) {
            AppInsightsUtils.trackTrace("autoTranslateV2 已卸载 用户: " + shopName);
            return false;
        }

        if (usersDO.getUninstallTime() != null) {
            if (usersDO.getLoginTime() == null) {
                AppInsightsUtils.trackTrace("autoTranslateV2 卸载了未登陆 用户: " + shopName);
                return false;
            } else if (usersDO.getUninstallTime().after(usersDO.getLoginTime())) {
                AppInsightsUtils.trackTrace("autoTranslateV2 卸载了时间在登陆时间后 用户: " + shopName);
                return false;
            }
        }

        // 判断注册时间
        if (usersDO.getLoginTime() == null) {
            AppInsightsUtils.trackTrace("autoTranslateV2 用户未登录 或 登录时间为空: " + shopName);
            return false;
        }

        // 将登录时间与当前时间都按 UTC 时区比较小时
        int loginHour = usersDO.getCreateAt().toInstant().atZone(ZoneOffset.UTC).getHour();
        int currentHour = Instant.now().atZone(ZoneOffset.UTC).getHour();
        AppInsightsUtils.trackTrace("autoTranslateV2 loginHour: " + loginHour + " currentHour: " + currentHour + " shop: " + shopName);

        // 只有当登录小时与当前小时一致时才继续执行，其他情况按分片逻辑跳过
        if (loginHour != currentHour) {
            AppInsightsUtils.trackTrace("autoTranslateV2 非当前小时，不执行自动翻译 shop: " + shopName);
            return false;
        }

        Integer maxToken = userTokenService.getMaxToken(shopName);
        Integer usedToken = userTokenService.getUsedToken(shopName);
        AppInsightsUtils.trackTrace("autoTranslateV2 maxToken: " + maxToken + " usedToken: " + usedToken + " shop: " + shopName);
        // 如果字符超限，则直接返回字符超限
        if (usedToken >= maxToken) {
            AppInsightsUtils.trackTrace("autoTranslateV2 字符超限 用户: " + shopName);
            return false;
        }

        // 判断这条语言是否在用户本地存在
        String shopifyByQuery = shopifyService.getShopifyData(shopName, usersDO.getAccessToken(),
                TranslateConstants.API_VERSION_LAST, ShopifyRequestUtils.getShopLanguageQuery());
        AppInsightsUtils.trackTrace("autoTranslateV2 获取用户本地语言数据: " + shopName + " 数据为： " + shopifyByQuery);
        if (shopifyByQuery == null) {
            AppInsightsUtils.trackTrace("autoTranslateV2 FatalException 获取用户本地语言数据失败 用户: " + shopName + " ");
            return false;
        }

        String userCode = "\"" + target + "\"";
        if (!shopifyByQuery.contains(userCode)) {
            // 将用户的自动翻译标识改为false
            translatesService.updateAutoTranslateByShopNameAndTargetToFalse(shopName, target);
            AppInsightsUtils.trackTrace("autoTranslateV2 用户本地语言数据不存在 用户: " + shopName + " target: " + target);
            return false;
        }

        AppInsightsUtils.trackTrace("autoTranslateV2 任务准备创建 " + shopName + " target: " + target);
        createAutoTask(shopName, source, target);
        AppInsightsUtils.trackTrace("autoTranslateV2 任务创建成功 " + shopName + " target: " + target);
        return true;
    }

    public void cleanTask(InitialTaskV2DO initialTaskV2DO) {
        AppInsightsUtils.trackTrace("TranslateTaskV2 cleanTask start clean task: " + initialTaskV2DO.getId());
        while (true) {
            int deleted = translateTaskV2Repo.deleteByInitialTaskId(initialTaskV2DO.getId());
            AppInsightsUtils.trackTrace("TranslateTaskV2 cleanTask delete: " + deleted);
            if (deleted <= 0) {
                break;
            }
        }
        initialTaskV2Repo.deleteById(initialTaskV2DO.getId());
    }

    private static TypeSplitResponse splitByType(String targetType, List<TranslateResourceDTO> resourceList) {
        List<TranslateResourceDTO> before;
        List<TranslateResourceDTO> after;

        StringBuilder beforeType = new StringBuilder();
        StringBuilder afterType = new StringBuilder();

        if (targetType == null) {
            // 提前把 EMAIL_MAP 的 values 转成 Set，提高查找效率
            Set<String> emailResources = new HashSet<>(EMAIL_MAP.values());

            for (TranslateResourceDTO dto : resourceList) {
                if (emailResources.contains(dto.getResourceType())) {
                    afterType.append(dto.getResourceType()).append(",");
                }
            }

            // 去掉最后一个逗号
            if (!afterType.isEmpty()) {
                afterType.setLength(afterType.length() - 1);
            }

            return new TypeSplitResponse(beforeType, afterType);
        }
        Set<String> beforeSet = new HashSet<>();
        Set<String> afterSet = new HashSet<>();
        int index = -1;

        // 查找目标 type 的索引
        for (int i = 0; i < resourceList.size(); i++) {
            if (resourceList.get(i).getResourceType().equals(targetType)) {
                index = i;
                break;
            }
        }

        // 如果没找到目标 type，返回空集合并打印错误信息
        if (index == -1) {
            AppInsightsUtils.trackTrace("errors 错误：未找到 type 为 '" + targetType + "' 的资源");
            after = resourceList;
            for (TranslateResourceDTO resource : after) {
                afterSet.add(EMAIL_MAP.get(resource.getResourceType()));
            }
            for (String resource : afterSet) {
                afterType.append(resource).append(",");
            }
            return new TypeSplitResponse(beforeType, afterType);
        }

        // 分割列表
        before = index > 0 ? resourceList.subList(0, index) : new ArrayList<>();
        after = index < resourceList.size() ? resourceList.subList(index, resourceList.size()) : new ArrayList<>();
        //根据TranslateResourceDTO来获取展示的类型名，且不重名
        if (!before.isEmpty()) {
            for (TranslateResourceDTO resource : before) {
                if (EMAIL_MAP.containsKey(resource.getResourceType())) {
                    beforeSet.add(EMAIL_MAP.get(resource.getResourceType()));
                }
            }
        }

        if (!after.isEmpty()) {
            for (TranslateResourceDTO resource : after) {
                if (EMAIL_MAP.containsKey(resource.getResourceType())) {
                    afterSet.add(EMAIL_MAP.get(resource.getResourceType()));
                }
            }
        }

        beforeSet.removeAll(afterSet);
        // 遍历before和after，只获取type字段，转为为String类型
        for (String resource : beforeSet) {
            beforeType.append(resource).append(",");
        }
        for (String resource : afterSet) {
            afterType.append(resource).append(",");
        }
        return new TypeSplitResponse(beforeType, afterType);
    }

    // 根据翻译规则，不翻译的直接不用存
    private boolean needTranslate(ShopifyGraphResponse.TranslatableResources.Node.TranslatableContent translatableContent,
                                  List<ShopifyGraphResponse.TranslatableResources.Node.Translation> translations,
                                  String module, boolean isCover, boolean isHandle, String shopName, String accessToken
            , String resourceId, List<ShopifyTranslationsRemove> shopifyTranslationsRemoveList) {
        String value = translatableContent.getValue();
        String type = translatableContent.getType();
        String key = translatableContent.getKey();

        ShopifyGraphResponse.TranslatableResources.Node.Translation keyTranslation =
                translations.stream()
                        .filter(t -> key.equals(t.getKey()))
                        .findFirst()
                        .orElse(null);

        if (value == null || StringUtils.isBlank(value)) {
            // 判断原文是否为空,译文是否有内容,需要删掉译文
            if (JudgeTranslateUtils.TRANSLATABLE_RESOURCE_TYPES.contains(module) && keyTranslation != null &&
                    !StringUtils.isBlank(keyTranslation.getValue())) {
                // 判断是否有翻译
                // 将数据插入集合中
                ShopifyTranslationsRemove remove;

                if (shopifyTranslationsRemoveList.isEmpty()) {
                    remove = new ShopifyTranslationsRemove();
                    remove.setResourceId(resourceId);
                    shopifyTranslationsRemoveList.add(remove);
                } else {
                    remove = shopifyTranslationsRemoveList.get(0);
                }

                remove.add(keyTranslation.getLocale(), key);
            }

            return false;
        }

        // 先看outdate = false
        if (!isCover) {
            if (keyTranslation != null && !keyTranslation.getOutdated()) {
                return false;
            }
        }

        // From TranslateDataService filterNeedTranslateSet
        // 如果是特定类型，也从集合中移除
        if ("FILE_REFERENCE".equals(type) || "LINK".equals(type) || "URL".equals(type)
                || "LIST_FILE_REFERENCE".equals(type) || "LIST_LINK".equals(type)
                || "LIST_URL".equals(type) || "JSON".equals(type)
                || "JSON_STRING".equals(type)) {
            return false;
        }

        //如果handleFlag为false，则跳过
        if (type.equals(TranslateConstants.URI) && "handle".equals(key)) {
            // 自动翻译的handle默认为false, 手动的记得添加
            return isHandle;
        }

        //通用的不翻译数据
        if (!JudgeTranslateUtils.translationRuleJudgment(key, value)) {
            return false;
        }

        //如果是theme模块的数据
        if (JudgeTranslateUtils.TRANSLATABLE_RESOURCE_TYPES.contains(module)) {
            //如果是html放html文本里面
            if (JsoupUtils.isHtml(value)) {
                return true;
            }

            if (JsonUtils.isJson(value)) {
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
            if (JudgeTranslateUtils.GENERAL_OR_SECTION_PATTERN.matcher(key).find()) {
                //进行白名单的确认
                if (JudgeTranslateUtils.whiteListTranslate(key)) {
                    return true;
                }

                //如果包含对应key和value，则跳过
                if (!JudgeTranslateUtils.shouldTranslate(key, value)) {
                    return false;
                }
            }
        }

        //对METAFIELD字段翻译
        if (TranslateConstants.METAFIELD.equals(module)) {
            //如UXxSP8cSm，UgvyqJcxm。有大写字母和小写字母的组合。有大写字母，小写字母和数字的组合。 10位 字母和数字不翻译
            if (JudgeTranslateUtils.SUSPICIOUS_PATTERN.matcher(value).matches() ||
                    JudgeTranslateUtils.SUSPICIOUS2_PATTERN.matcher(value).matches()) {
                return false;
            }
            if (!JudgeTranslateUtils.metaTranslate(value)) {
                return false;
            }
            //如果是base64编码的数据，不翻译
            if (JudgeTranslateUtils.BASE64_PATTERN.matcher(value).matches()) {
                return false;
            }

            if (JsonUtils.isJson(value) && (!value.contains(JSON_JUDGE) || !"RICH_TEXT_FIELD".equals(type))) {
                return false;
            }

            if (JsonUtils.isJson(value) && value.contains(JSON_JUDGE) && "RICH_TEXT_FIELD".equals(type)) {
                // 判断是否与product相关联
                String shopifyData = shopifyService.getShopifyData(shopName, accessToken, TranslateConstants.API_VERSION_LAST,
                        ShopifyRequestUtils.getQueryForCheckMetafieldId(resourceId));
                ShopifyCheckMetafieldResponse shopifyCheckMetafieldResponse = JsonUtils.jsonToObject(shopifyData,
                        ShopifyCheckMetafieldResponse.class);
                return Optional.ofNullable(shopifyCheckMetafieldResponse)
                        .map(ShopifyCheckMetafieldResponse::getNode)
                        .map(ShopifyCheckMetafieldResponse.Node::getOwner)
                        .map(ShopifyCheckMetafieldResponse.Node.Owner::getId)
                        .isPresent();
            }
        }

        return true;
    }

    public BaseResponse<Object> continueTranslating(String shopName, Integer taskId) {
        InitialTaskV2DO initialTaskV2DO = initialTaskV2Repo.selectById(taskId);
        if (initialTaskV2DO != null) {
            initialTaskV2Repo.updateStatusAndSendEmailById(InitialTaskStatus.READ_DONE_TRANSLATING.getStatus(), initialTaskV2DO.getId(), false);
            redisStoppedRepository.removeStoppedFlag(shopName);
            translatesService.updateTranslateStatus(shopName, 2, initialTaskV2DO.getTarget(), initialTaskV2DO.getSource());
        }

        // 删除用户停止标识
        return new BaseResponse<>().CreateSuccessResponse(true);
    }

    public BaseResponse<Object> continueTranslatingV2(String shopName, Integer taskId) {
        InitialTaskV2DO initialTaskV2DO = initialTaskV2Repo.selectById(taskId);
        if (initialTaskV2DO != null) {
            initialTaskV2Repo.updateStatusAndSendEmailById(InitialTaskStatus.READ_DONE_TRANSLATING.getStatus(), initialTaskV2DO.getId(), false);
            redisStoppedRepository.removeStoppedFlag(shopName, taskId);
            translatesService.updateTranslateStatus(shopName, 2, initialTaskV2DO.getTarget(), initialTaskV2DO.getSource());
        }

        // 删除用户停止标识
        return new BaseResponse<>().CreateSuccessResponse(true);
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
