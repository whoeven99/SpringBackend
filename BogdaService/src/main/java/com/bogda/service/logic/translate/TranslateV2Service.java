package com.bogda.service.logic.translate;

import com.bogda.common.reporter.ExceptionReporterHolder;
import com.bogda.common.reporter.TraceReporterHolder;
import com.bogda.integration.aimodel.ChatGptIntegration;
import com.bogda.integration.aimodel.KimiIntegration;
import com.bogda.integration.feishu.FeiShuRobotIntegration;
import com.bogda.integration.model.*;
import com.bogda.repository.entity.DeleteTasksDO;
import com.bogda.repository.repo.DeleteTasksRepo;
import com.bogda.service.Service.ITranslatesService;
import com.bogda.service.Service.IUsersService;
import com.bogda.common.TranslateContext;
import com.bogda.common.entity.DO.GlossaryDO;
import com.bogda.common.entity.DO.TranslateResourceDTO;
import com.bogda.common.entity.DO.TranslatesDO;
import com.bogda.common.entity.DO.UsersDO;
import com.bogda.common.entity.VO.SingleReturnVO;
import com.bogda.common.entity.VO.SingleTranslateVO;
import com.bogda.common.utils.ModuleCodeUtils;
import com.bogda.common.contants.TranslateConstants;
import com.bogda.common.enums.ErrorEnum;
import com.bogda.service.integration.ALiYunTranslateIntegration;
import com.bogda.integration.aimodel.GeminiIntegration;
import com.bogda.service.logic.GlossaryService;
import com.bogda.service.logic.ShopifyService;
import com.bogda.service.logic.TaskService;
import com.bogda.service.logic.TencentEmailService;
import com.bogda.service.logic.redis.ConfigRedisRepo;
import com.bogda.service.logic.redis.RedisStoppedRepository;
import com.bogda.service.logic.redis.TranslateTaskMonitorV2RedisService;
import com.bogda.service.logic.token.UserTokenService;
import com.bogda.service.logic.translate.stragety.ITranslateStrategyService;
import com.bogda.service.logic.translate.stragety.TranslateStrategyFactory;
import com.bogda.common.controller.request.ClickTranslateRequest;
import com.bogda.common.controller.response.BaseResponse;
import com.bogda.common.controller.response.ProgressResponse;
import com.bogda.repository.entity.InitialTaskV2DO;
import com.bogda.repository.entity.TranslateSaveFailedTaskDO;
import com.bogda.repository.entity.TranslateTaskV2DO;
import com.bogda.repository.repo.InitialTaskV2Repo;
import com.bogda.repository.repo.TranslateSaveFailedTaskRepo;
import com.bogda.repository.repo.TranslateTaskV2Repo;
import com.bogda.common.utils.JsoupUtils;
import com.bogda.common.utils.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import kotlin.Pair;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.io.InputStream;
import java.net.URL;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.bogda.service.logic.TaskService.AUTO_TRANSLATE_MAP;

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
    @Autowired
    private DeleteTasksRepo deleteTasksRepo;
    @Autowired
    private ChatGptIntegration chatGptIntegration;
    @Autowired
    private KimiIntegration kimiIntegration;
    @Autowired
    private AiModelConfigService aiModelConfigService;
    @Autowired
    private FeiShuRobotIntegration feiShuRobotIntegration;
    @Autowired
    private TranslateSaveFailedTaskRepo translateSaveFailedTaskRepo;
    @Autowired
    private PromptConfigService promptConfigService;
    @Autowired
    private ModelTranslateService modelTranslateService;
    private static final String JSON_JUDGE = "\"type\":\"text\""; // 用于json数据的筛选（降级逻辑）
    private static final String METAFIELD_JSON_TRANSLATE_RULE = "METAFIELD_JSON_TRANSLATE_RULE";

    /**
     * 根据模型选择 平均消耗 token 系数
     */
    public static final Map<String, Double> AVG_TOKEN_PER_ITEM = new HashMap<String, Double>() {{
        put(GeminiIntegration.GEMINI_3_FLASH, 1.58);
        put(ModuleCodeUtils.GPT_5, 0.99);
        put(ALiYunTranslateIntegration.QWEN_MAX, 0.85);
    }};

    /**
     * 根据模型选择 每秒钟约消耗 token 系数
     */
    public static Map<String, Double> TOKEN_PER_SECOND = new HashMap<String, Double>() {{
        put(GeminiIntegration.GEMINI_3_FLASH, 0.31);
        put(ModuleCodeUtils.GPT_5, 0.17);
        put(ALiYunTranslateIntegration.QWEN_MAX, 0.31);
    }};

    private static int parseIntSafe(String s, int defaultValue) {
        if (s == null || s.isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private String getPrimaryLocaleFromShopifyData(String shopifyData) {
        if (shopifyData == null) {
            return null;
        }
        JsonNode root = JsonUtils.readTree(shopifyData);
        if (root == null) {
            return null;
        }
        JsonNode shopLocales = root.path("shopLocales");
        if (!shopLocales.isArray()) {
            return null;
        }
        for (JsonNode node : shopLocales) {
            if (node.path("primary").asBoolean(false)) {
                return node.path("locale").asText(null);
            }
        }
        return null;
    }

    private static double getDoubleSafe(Map<String, Double> map, String key, Map<String, Double> fallbackMap, double defaultVal) {
        if (key != null && map != null && map.containsKey(key)) {
            Double v = map.get(key);
            if (v != null) {
                return v;
            }
        }
        if (key != null && fallbackMap != null && fallbackMap.containsKey(key)) {
            Double v = fallbackMap.get(key);
            if (v != null) {
                return v;
            }
        }
        return defaultVal;
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
        String aiModel = aiModelConfigService.getSingleTranslateModel();
        TranslateContext context = new TranslateContext(request.getContext(), request.getTarget(), request.getType(),
                request.getKey(), glossaryService.getGlossaryDoByShopName(shopName, request.getTarget()),
                aiModel, request.getResourceType(), request.getTargetText());
        context.setShopName(shopName);
        ITranslateStrategyService service = translateStrategyFactory.getServiceByContext(context);
        service.translate(context);
        service.finishAndGetJsonRecord(context);
        userTokenService.addUsedToken(shopName, context.getUsedToken());
        TraceReporterHolder.report("TranslateV2Service.singleTextTranslate", "shopName : " + shopName
                + " token 单条翻译消耗 " + context.getUsedToken());
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
            ExceptionReporterHolder.report("TranslateV2Service.testTranslate", e);
            return defaultNullMap();
        }

        // 2. 模型路由调度
        try {
            if (model.contains("qwen")) {
                return handleAliYun(prompt, target);
            } else if (model.contains("gemini")) {
                return handleGemini(model, prompt, picUrl);
            } else if (model.contains("gpt")) {
                return handleGpt(model, prompt, target);
            } else if (model.contains("kimi")) {
                return handleKimi(prompt, target);
            }
        } catch (Exception e) {
            ExceptionReporterHolder.report("TranslateV2Service.testTranslate", e);
        }

        return defaultNullMap();
    }

    /**
     * 处理kimi逻辑
     */
    private Map<String, Object> handleKimi(String prompt, String target) {
        Pair<String, Integer> pair = kimiIntegration.chat(prompt, target);
        if (pair == null) {
            return defaultNullMap();
        }
        return buildResponse(pair.getFirst(), pair.getSecond(), "text");
    }

    /**
     * 处理gpt逻辑，支持指定模型名称
     */
    private Map<String, Object> handleGpt(String modelName, String prompt, String target) {
        Pair<String, Integer> pair = chatGptIntegration.chatWithGpt(modelName, prompt, target);
        if (pair == null) {
            return defaultNullMap();
        }
        return buildResponse(pair.getFirst(), pair.getSecond(), "text");
    }

    /**
     * 处理通义千问逻辑
     */
    private Map<String, Object> handleAliYun(String prompt, String target) {
        Pair<String, Integer> pair = aLiYunTranslateIntegration.userTranslate(prompt, target, aiModelConfigService.getMagnification("qwen"));
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
            Pair<String, Integer> pair = geminiIntegration.generateText(model, prompt, aiModelConfigService.getMagnification("gemini"));
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
        TraceReporterHolder.report("TranslateV2Service.createInitialTask", "createInitialTask : " + " shopName : " + shopName + " " + request);
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
                        TraceReporterHolder.report("TranslateV2Service.createInitialTask", "FatalException createInitialTask Warning: Unknown module: " + module);
                        feiShuRobotIntegration.sendMessage("FatalException " + " shopName: " + shopName + " createInitialTask Warning: Unknown module: " + module);
                        return Stream.empty();
                    }
                    return list.stream();
                })
                .filter(Objects::nonNull)
                .map(TranslateResourceDTO::getResourceType)
                .toList();
        resourceTypeList = sortTranslateData(resourceTypeList);
        this.isExistInDatabase(shopName, finalTargets.toArray(new String[0]), request.getSource(), request.getAccessToken());
        this.createManualTask(shopName, request.getSource(), finalTargets, resourceTypeList, request.getIsCover(), hasHandle, translateSettings1);


        // 找前端，把这里的返回改了
        request.setTarget(finalTargets.toArray(new String[0]));
        request.setAccessToken("");
        return BaseResponse.SuccessResponse(request);
    }

    public static List<String> sortTranslateData(List<String> list) {
        // 1. 提取 ALL_RESOURCES 中的顺序
        List<String> orderList = TranslateResourceDTO.ALL_RESOURCES.stream()
                .map(TranslateResourceDTO::getResourceType)
                .toList();

        // 2. 构造 name -> index 的 Map
        Map<String, Integer> orderMap = new HashMap<>();
        for (int i = 0; i < orderList.size(); i++) {
            orderMap.put(orderList.get(i), i);
        }

        // 3. 对 targetList 排序
        List<String> sortedList = new ArrayList<>(list);
        sortedList.sort(Comparator.comparingInt(name -> orderMap.getOrDefault(name, Integer.MAX_VALUE)));
        return sortedList;
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
            TraceReporterHolder.report("TranslateV2Service.isExistInDatabase", "syncShopifyAndDatabase: shopLocales is not an array.");
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
            translatesService.updateTranslateStatus(shopName, 2, target, source);

            // 获取自动翻译任务，是否有该任务，然后停止
            try {
                InitialTaskV2DO autoTaskByShopNameAndTarget = initialTaskV2Repo.getAutoTaskByShopNameAndTarget(shopName, target);
                if (autoTaskByShopNameAndTarget != null) {
                    // 停止自动翻译任务
                    redisStoppedRepository.manuallyStopped(shopName, autoTaskByShopNameAndTarget.getId());
                }
            } catch (Exception e) {
                TraceReporterHolder.report("TranslateV2Service.createManualTask", "手动翻译后，停止可能正在翻译的自动翻译任务 ： " + shopName + " target: " + target);
                feiShuRobotIntegration.sendMessage("手动翻译后，停止可能正在翻译的自动翻译任务 ： " + shopName + " target: " + target);
            }
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
//        initialTask.setAiModel(GeminiIntegration.GEMINI_3_FLASH);
        initialTask.setAiModel(ChatGptIntegration.GPT_4_1_NANO);
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
                setEstimatedFromContext(progress, taskContext);
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
                setEstimatedFromContext(progress, taskContext);
                list.add(progress);
            } else if (task.getStatus().equals(InitialTaskStatus.ALL_DONE.getStatus())) {
                progress.setTarget(task.getTarget());
                progress.setStatus(1);
                progress.setTranslateStatus("translation_process_saved");
                progress.setProgressData(defaultProgressTranslateData);
                setEstimatedFromContext(progress, taskContext);
                list.add(progress);
            } else if (task.getStatus().equals(InitialTaskStatus.STOPPED.getStatus()) || task.getStatus().equals(InitialTaskStatus.INIT_STOPPED.getStatus())) {
                progress.setTarget(task.getTarget());
                String totalCountStr = taskContext != null ? taskContext.get("totalCount") : null;
                String translatedCountStr = taskContext != null ? taskContext.get("translatedCount") : null;
                long count = (totalCountStr != null && !totalCountStr.isEmpty()) ? Long.parseLong(totalCountStr) : 0L;
                long translatedCount = (translatedCountStr != null && !translatedCountStr.isEmpty()) ? Long.parseLong(translatedCountStr) : 0L;

                Map<String, Integer> progressData = new HashMap<>();
                progressData.put("TotalQuantity", 1);
                progressData.put("RemainingQuantity", 1);
                if (count > 0) {
                    progressData.put("TotalQuantity", (int) count);
                }
                if (count > translatedCount) {
                    progressData.put("RemainingQuantity", (int) (count - translatedCount));
                }

                progress.setProgressData(progressData);

                // 判断是手动中断，还是limit中断（INIT_STOPPED 复用 STOPPED 的展示逻辑，status=7 表示可继续）
                if (task.getStatus().equals(InitialTaskStatus.INIT_STOPPED.getStatus()) ||
                        redisStoppedRepository.isStoppedByTokenLimit(shopName, task.getId())) {
                    progress.setStatus(3);
                } else {
                    progress.setStatus(7);
                }

                setEstimatedFromContext(progress, taskContext);
                list.add(progress);
            }
        }

        return new BaseResponse<ProgressResponse>().CreateSuccessResponse(response);
    }

    /**
     * 从 Redis taskContext 中解析预估积分、预估耗时并设置到 Progress，供前端轮询展示
     */
    private void setEstimatedFromContext(ProgressResponse.Progress progress, Map<String, String> taskContext) {
        if (taskContext == null) {
            return;
        }
        String ec = taskContext.get("estimatedCredits");
        if (ec != null && !ec.isEmpty()) {
            try {
                progress.setEstimatedCredits(Long.parseLong(ec));
            } catch (Exception e) {
                ExceptionReporterHolder.report("TranslateV2Service.setEstimatedFromContext", e);
            }
        }
        String em = taskContext.get("estimatedMinutes");
        if (em != null && !em.isEmpty()) {
            try {
                progress.setEstimatedMinutes(Integer.parseInt(em));
            } catch (Exception e1) {
                ExceptionReporterHolder.report("TranslateV2Service.setEstimatedFromContext", e1);
            }
        }
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

    /**
     * 初始化阶段中止时的收尾：将任务状态更新为「初始化阶段已停止」并持久化，不执行初始化完成后的逻辑。
     */
    private void doInitStoppedCleanup(InitialTaskV2DO initialTaskV2DO) {
        initialTaskV2Repo.updateStatusAndSendEmailById(InitialTaskStatus.INIT_STOPPED.getStatus(), initialTaskV2DO.getId(), false, false);
        translateTaskMonitorV2RedisService.setTranslateEndTime(initialTaskV2DO.getId());
        TraceReporterHolder.report("TranslateV2Service.initialToTranslateTask", "TranslateTaskV2 init stopped cleanup: shop=" + initialTaskV2DO.getShopName() + " taskId=" + initialTaskV2DO.getId());
    }

    // 翻译 step 2, initial -> 查询 Shopify translatableResources，创建翻译任务（支持按模块分页游标断点续传）
    // 参考 Shopify Admin API: translatableResources(resourceType, first, after) 与 pageInfo.endCursor
    public void initialToTranslateTask(InitialTaskV2DO initialTaskV2DO) {
        String shopName = initialTaskV2DO.getShopName();
        String target = initialTaskV2DO.getTarget();
        int initialTaskId = initialTaskV2DO.getId();
        List<String> moduleList = JsonUtils.jsonToObject(initialTaskV2DO.getModuleList(), new TypeReference<>() {
        });
        assert moduleList != null;

        UsersDO userDO = iUsersService.getUserByName(initialTaskV2DO.getShopName());

        // 判断默认语言是否和source一致，不一致则停止任务
        String primaryLocaleData = shopifyService.getShopifyData(shopName, userDO.getAccessToken(),
                TranslateConstants.API_VERSION_LAST, ShopifyRequestUtils.getShopLanguageQuery());
        String primaryLocale = getPrimaryLocaleFromShopifyData(primaryLocaleData);
        if (primaryLocale != null && !primaryLocale.equals(initialTaskV2DO.getSource())) {
            initialTaskV2Repo.updateStatusAndSendEmailById(InitialTaskStatus.ALL_DONE.getStatus(), initialTaskId, true, true);
            translateTaskMonitorV2RedisService.setSavingShopifyEndTime(initialTaskId);
            TraceReporterHolder.report("TranslateV2Service.initialToTranslateTask",
                    "默认语言与source不一致，停止任务 shop: " + shopName + " primaryLocale: " + primaryLocale + " source: " + initialTaskV2DO.getSource());
            return;
        }

        String finishedModules = translateTaskMonitorV2RedisService.getFinishedModules(initialTaskId);
        List<ShopifyTranslationsRemove> shopifyTranslationsRemoveList = new ArrayList<>();

        // 自动翻译：上次翻译任务创建时间，用于筛选 updated_at 大于该时间的数据（仅 PRODUCT/ARTICLE/PAGE/COLLECTION）
        java.time.Instant updatedAtAfter = null;
        if ("auto".equals(initialTaskV2DO.getTaskType()) && initialTaskV2DO.getCreatedAt() != null) {
            InitialTaskV2DO lastAuto = initialTaskV2Repo.selectLatestAutoTaskBeforeCreatedAt(shopName, initialTaskV2DO.getSource(), target, initialTaskV2DO.getCreatedAt());
            if (lastAuto != null && lastAuto.getCreatedAt() != null) {
                updatedAtAfter = lastAuto.getCreatedAt().toInstant();
            }
        }

        // 方法入口：进入 for 之前检查是否已停止，若已停止则执行初始化中止收尾并 return
        if (redisStoppedRepository.isTaskStopped(shopName, initialTaskId)) {
            doInitStoppedCleanup(initialTaskV2DO);
            return;
        }

        for (String module : moduleList) {
            if (containsModule(finishedModules, module)) {
                continue;
            }
            // 每个 module 开始前：在处理当前 module 之前再检查一次，若已停止则执行初始化中止收尾并 return
            if (redisStoppedRepository.isTaskStopped(shopName, initialTaskId)) {
                doInitStoppedCleanup(initialTaskV2DO);
                return;
            }
            String moduleCursor = translateTaskMonitorV2RedisService.getAfterEndCursor(initialTaskId, module);
            final String currentModule = module;

            Consumer<ShopifyTranslationsResponse.Node> nodeConsumer = (node) -> {
                if (node == null || CollectionUtils.isEmpty(node.getTranslatableContent())) {
                    return;
                }
                TranslateTaskV2DO taskDo = new TranslateTaskV2DO();
                taskDo.setModule(currentModule);
                taskDo.setInitialTaskId(initialTaskId);
                taskDo.setResourceId(node.getResourceId());
                TraceReporterHolder.report("TranslateV2Service.initialToTranslateTask", "TranslateTaskV2 rotating Shopify: " + shopName + " module: " + currentModule + " resourceId: " + node.getResourceId());
                node.getTranslatableContent().forEach(translatableContent -> {
                    if (needTranslate(translatableContent, node.getTranslations(), currentModule, initialTaskV2DO.isCover(), initialTaskV2DO.isHandle(), shopName, userDO.getAccessToken(), node.getResourceId(), shopifyTranslationsRemoveList)) {
                        taskDo.setSourceValue(translatableContent.getValue());
                        taskDo.setNodeKey(translatableContent.getKey());
                        taskDo.setType(translatableContent.getType());
                        taskDo.setDigest(translatableContent.getDigest());
                        taskDo.setSingleHtml(JsoupUtils.isHtml(translatableContent.getValue()));
                        taskDo.setSingleJson(JsonUtils.isJson(translatableContent.getValue()));
                        taskDo.setId(null);
                        try {
                            translateTaskV2Repo.insert(taskDo);
                            translateTaskMonitorV2RedisService.incrementTotalCount(initialTaskId);
                            translateTaskMonitorV2RedisService.incrementEstimatedCredits(initialTaskId, translatableContent.getValue().length());
                        } catch (Exception e) {
                            ExceptionReporterHolder.report("TranslateV2Service.initialToTranslateTask", e);
                            feiShuRobotIntegration.sendMessage("FatalException initialToTranslateTask errors " + e);
                        }
                    }
                });
                if (!shopifyTranslationsRemoveList.isEmpty()) {
                    ShopifyTranslationsRemove remove = shopifyTranslationsRemoveList.get(0);
                    for (String key : remove.getTranslationKeys()) {
                        deleteTasksRepo.saveSingleData(initialTaskId, remove.getResourceId(), key);
                    }
                    shopifyTranslationsRemoveList.clear();
                }
            };

            // PRODUCT/ARTICLE/PAGE/COLLECTION：先按 config/updated_at 拉取 ID，传 resourceIds 走 translatableResourcesByIds；其他模块传 null 走 translatableResources(resourceType, first, after)
            List<String> resourceIds = null;
            if (TranslateConstants.PRODUCT.equals(module) || TranslateConstants.ARTICLE.equals(module)
                    || TranslateConstants.PAGE.equals(module) || TranslateConstants.COLLECTION.equals(module)) {
                String queryFilter = configRedisRepo.getConfig(module);
                if (queryFilter == null || queryFilter.isEmpty()) {
                    queryFilter = "";
                }
                resourceIds = shopifyService.fetchResourceIdsByQuery(shopName, userDO.getAccessToken(), module, queryFilter, updatedAtAfter);
            }
            shopifyService.rotateAllShopifyGraph(shopName, module, userDO.getAccessToken(), 250, target, moduleCursor,
                    resourceIds,
                    nodeConsumer,
                    (after -> translateTaskMonitorV2RedisService.setAfterEndCursor(initialTaskId, currentModule, after)));

            translateTaskMonitorV2RedisService.addFinishedModule(initialTaskId, module);
            translateTaskMonitorV2RedisService.clearAfterEndCursor(initialTaskId, module);
            TraceReporterHolder.report("TranslateV2Service.initialToTranslateTask", "TranslateTaskV2 rotate Shopify done: " + shopName + " module: " + module);
        }

        // 更新数据库并记录初始化时间
        TraceReporterHolder.report("TranslateV2Service.initialToTranslateTask", "TranslateTaskV2 initialToTranslateTask done: " + shopName);

        long initTimeInMinutes = (System.currentTimeMillis() - initialTaskV2DO.getUpdatedAt().getTime()) / (1000 * 60);
        initialTaskV2DO.setStatus(InitialTaskStatus.READ_DONE_TRANSLATING.status);
        initialTaskV2DO.setInitMinutes((int) initTimeInMinutes);
        translateTaskMonitorV2RedisService.setInitEndTime(initialTaskId);

        // totalCount 确定后立即计算预估积分与翻译消耗时间，供 getProcess 返回前端
        Map<String, String> monitor = translateTaskMonitorV2RedisService.getAllByTaskId(initialTaskId);
        String avgTokenPerItem = configRedisRepo.getConfig("AVG_TOKEN_PER_ITEM");
        String tokenPerSecond = configRedisRepo.getConfig("TOKEN_PER_SECOND");

        // 将String 转为Map<String, Double>，配置缺失时用静态默认值，避免分支不执行导致 Redis 不更新
        Map<String, Double> avgTokenMap = JsonUtils.jsonToObject(avgTokenPerItem, new TypeReference<Map<String, Double>>() {
        });
        Map<String, Double> avgSecondMap = JsonUtils.jsonToObject(tokenPerSecond, new TypeReference<Map<String, Double>>() {
        });
        if (avgTokenMap == null) {
            avgTokenMap = AVG_TOKEN_PER_ITEM;
        }
        if (avgSecondMap == null) {
            avgSecondMap = TOKEN_PER_SECOND;
        }
        TraceReporterHolder.report("TranslateV2Service.initialToTranslateTask", "TranslateTaskV2 avgTokenMap: "
                + avgTokenMap + " avgSecondMap: " + avgSecondMap + " shopName: " + shopName + " initialTaskId: " + initialTaskId);

        String estimatedCreditsStr = monitor != null ? monitor.get("estimatedCredits") : null;
        String totalCountStr = monitor != null ? monitor.get("totalCount") : null;
        String aiModel = initialTaskV2DO.getAiModel();
        if (estimatedCreditsStr != null && !estimatedCreditsStr.isEmpty()) {
            int totalCredits = parseIntSafe(estimatedCreditsStr, 0);
            // 避免 aiModel 为 null 或不在 Map 中时 get(aiModel) 返回 null 导致 NPE
            double tokenPerItem = getDoubleSafe(avgTokenMap, aiModel, AVG_TOKEN_PER_ITEM, 1.0);
            long finalCredits = (long) (totalCredits * tokenPerItem);
            translateTaskMonitorV2RedisService.setEstimatedCredits(initialTaskId, finalCredits);

            double tokenPerSecondVal = getDoubleSafe(avgSecondMap, aiModel, TOKEN_PER_SECOND, 0.2);
            int estimatedTranslateSeconds = (int) (finalCredits * tokenPerSecondVal);

            // totalCount 确定后立即计算预估存储消耗时间，供 getProcess 返回前端
            if (totalCountStr != null && !totalCountStr.isEmpty()) {
                int finalCount = parseIntSafe(totalCountStr, 0);
                estimatedTranslateSeconds += finalCount;
            }
            translateTaskMonitorV2RedisService.setEstimatedMinutes(initialTaskId, estimatedTranslateSeconds);
        }

        TraceReporterHolder.report("TranslateV2Service.initialToTranslateTask",
                "TranslateTaskV2 初始化数据计算完成，修改任务状态 " + " shopName: " + shopName + " initialTaskId: " + initialTaskId);
        initialTaskV2Repo.updateStatusAndInitMinutes(initialTaskV2DO.getStatus(), initialTaskV2DO.getInitMinutes()
                , initialTaskId);
    }

    // 翻译 step 3, 翻译任务 -> 具体翻译行为 直接对数据库操作
    public void translateEachTask(InitialTaskV2DO initialTaskV2DO) {
        // 这里可以从数据库，直接批量获取各种type，一次性翻译不同模块的数据
        Integer initialTaskId = initialTaskV2DO.getId();
        String target = initialTaskV2DO.getTarget();
        String shopName = initialTaskV2DO.getShopName();
        String aiModel = initialTaskV2DO.getAiModel();

        // 判断默认语言是否和source一致，不一致则停止任务
        UsersDO primaryCheckUser = iUsersService.getUserByName(shopName);
        String primaryLocaleData = shopifyService.getShopifyData(shopName, primaryCheckUser.getAccessToken(),
                TranslateConstants.API_VERSION_LAST, ShopifyRequestUtils.getShopLanguageQuery());
        String primaryLocale = getPrimaryLocaleFromShopifyData(primaryLocaleData);
        if (primaryLocale != null && !primaryLocale.equals(initialTaskV2DO.getSource())) {
            initialTaskV2Repo.updateStatusAndSendEmailById(InitialTaskStatus.ALL_DONE.getStatus(), initialTaskV2DO.getId(), true, true);
            translateTaskMonitorV2RedisService.setSavingShopifyEndTime(initialTaskId);
            TraceReporterHolder.report("TranslateV2Service.translateEachTask",
                    "默认语言与source不一致，停止任务 shop: " + shopName + " primaryLocale: " + primaryLocale + " source: " + initialTaskV2DO.getSource());
            return;
        }

        Map<String, GlossaryDO> glossaryMap = glossaryService.getGlossaryDoByShopName(shopName, target);

        Integer maxToken = userTokenService.getMaxToken(shopName);
        Integer usedToken = userTokenService.getUsedToken(shopName);

        TranslateTaskV2DO randomDo = translateTaskV2Repo.selectOneByInitialTaskIdAndEmptyValue(initialTaskId);
        while (randomDo != null) {
            TraceReporterHolder.report("TranslateV2Service.translateEachTask", "TranslateTaskV2 translating shop: " + shopName + " randomDo: " + randomDo.getId());

            // 用于中断之后的邮件描述
            initialTaskV2DO.setTransModelType(randomDo.getModule());
            if (usedToken >= maxToken) {
                // 记录是因为token limit中断的
                // 获取没有翻译的任务，修改该用户任务的停止标识
                List<InitialTaskV2DO> initialTaskV2DOS = initialTaskV2Repo.selectByShopNameAndNotDeleted(shopName);
                if (!initialTaskV2DOS.isEmpty()) {
                    for (InitialTaskV2DO task : initialTaskV2DOS) {
                        redisStoppedRepository.tokenLimitStopped(shopName, task.getId());
                    }
                }
                redisStoppedRepository.tokenLimitStopped(shopName, initialTaskId);
            }

            // 还可能是手动中断
            if (redisStoppedRepository.isTaskStopped(shopName, initialTaskId)) {
                break;
            }

            // 随机找一条，如果是html就单条翻译，不是就直接批量
            boolean isHtml = randomDo.isSingleHtml();
            if (JsonUtils.isJson(randomDo.getSourceValue())) {
                TranslateContext context = new TranslateContext(randomDo.getSourceValue(), target, glossaryMap, aiModel);
                context.setModule(randomDo.getModule());
                context.setShopName(shopName);
                ITranslateStrategyService service = translateStrategyFactory.getServiceByStrategy("JSON");
                service.translate(context);

                // 翻译后更新db
                translateTaskV2Repo.updateTargetValueAndHasTargetValue(context.getTranslatedContent(), true, randomDo.getId());

                usedToken = userTokenService.addUsedToken(shopName, initialTaskId, context.getUsedToken());
                translateTaskMonitorV2RedisService.trackTranslateDetail(initialTaskId, 1,
                        context.getUsedToken(), context.getTranslatedChars());

            } else if (JsonUtils.isListFormat(randomDo.getSourceValue()) && TranslateConstants.LIST_SINGLE_LINE_TEXT_FIELD
                    .equals(randomDo.getType())) {
                TranslateContext context = new TranslateContext(randomDo.getSourceValue(), target, glossaryMap, aiModel);
                context.setModule(randomDo.getModule());
                context.setShopName(shopName);
                ITranslateStrategyService service = translateStrategyFactory.getServiceByStrategy("LIST");
                service.translate(context);

                // 翻译后更新db
                translateTaskV2Repo.updateTargetValueAndHasTargetValue(context.getTranslatedContent(), true, randomDo.getId());
                usedToken = userTokenService.addUsedToken(shopName, initialTaskId, context.getUsedToken());
                translateTaskMonitorV2RedisService.trackTranslateDetail(initialTaskId, 1, context.getUsedToken()
                        , context.getTranslatedChars());
            } else if (isHtml) {
                TranslateContext context = new TranslateContext(randomDo.getSourceValue(), target, glossaryMap, aiModel);
                context.setModule(randomDo.getModule());
                context.setShopName(shopName);
                ITranslateStrategyService service = translateStrategyFactory.getServiceByStrategy("HTML");
                service.translate(context);

                // 翻译后更新db
                translateTaskV2Repo.updateTargetValueAndHasTargetValue(context.getTranslatedContent(), true, randomDo.getId());

                usedToken = userTokenService.addUsedToken(shopName, initialTaskId, context.getUsedToken());
                translateTaskMonitorV2RedisService.trackTranslateDetail(initialTaskId, 1, context.getUsedToken()
                        , context.getTranslatedChars());
            } else {
                // 批量翻译
                List<TranslateTaskV2DO> originTaskList =
                        translateTaskV2Repo.selectByInitialTaskIdAndTypeAndEmptyValueWithLimit(initialTaskId, 30);
                List<TranslateTaskV2DO> taskList = new ArrayList<>();
                int totalChars = 0;
                for (TranslateTaskV2DO task : originTaskList) {
                    if (task.isSingleHtml() || JsonUtils.isJson(task.getSourceValue()) ||
                            (JsonUtils.isListFormat(task.getSourceValue()) && TranslateConstants.LIST_SINGLE_LINE_TEXT_FIELD
                                    .equals(task.getType()))) {
                        continue;
                    }
                    taskList.add(task);
                    totalChars += ALiYunTranslateIntegration.calculateBaiLianToken(task.getSourceValue());
                    if (totalChars > 600) {
                        break;
                    }
                }

                Map<Integer, String> idToSourceValueMap = taskList.stream()
                        .collect(Collectors.toMap(TranslateTaskV2DO::getId, task -> {
                            String sourceValue = task.getSourceValue();
                            if (TranslateConstants.LOWERCASE_HANDLE.equals(task.getNodeKey()) && sourceValue != null) {
                                return sourceValue.replace('-', ' ');
                            }
                            return sourceValue;
                        }));

                if (idToSourceValueMap.isEmpty()) {
                    maxToken = userTokenService.getMaxToken(shopName); // max token也重新获取，防止期间用户购买
                    randomDo = translateTaskV2Repo.selectOneByInitialTaskIdAndEmptyValue(initialTaskId);
                    continue;
                }
                TranslateContext context = new TranslateContext(idToSourceValueMap, target, glossaryMap, aiModel);
                context.setShopName(shopName);
                context.setModule(randomDo.getModule());
                ITranslateStrategyService service = translateStrategyFactory.getServiceByContext(context);
                service.translate(context);

                Map<Integer, String> translatedValueMap = context.getTranslatedTextMap();
                for (TranslateTaskV2DO updatedDo : taskList) {
                    String targetValue = translatedValueMap.get(updatedDo.getId());

                    // 3.3 回写数据库 todo 批量
                    if (targetValue == null || targetValue.isEmpty()) {
                        TraceReporterHolder.report("TranslateV2Service.translateEachTask", "FatalException targetValue is null: " + shopName + " " + initialTaskId + " " + updatedDo.getId());
                        feiShuRobotIntegration.sendMessage("FatalException targetValue is null: " + shopName + " " + initialTaskId + " " + updatedDo.getId());
                        continue;
                    }
                    translateTaskV2Repo.updateTargetValueAndHasTargetValue(targetValue, true, updatedDo.getId());
                }
                usedToken = userTokenService.addUsedToken(shopName, initialTaskId, context.getUsedToken());
                translateTaskMonitorV2RedisService.trackTranslateDetail(initialTaskId, translatedValueMap.size(),
                        context.getUsedToken(), context.getTranslatedChars());
            }

            maxToken = userTokenService.getMaxToken(shopName); // max token也重新获取，防止期间用户购买
            randomDo = translateTaskV2Repo.selectOneByInitialTaskIdAndEmptyValue(initialTaskId);
        }
        TraceReporterHolder.report("TranslateV2Service.translateEachTask", "TranslateTaskV2 translating done: " + shopName);

        // 判断是手动中断 还是limit中断，切换不同的状态
        if (redisStoppedRepository.isTaskStopped(shopName, initialTaskId)) {
            int status = redisStoppedRepository.isStoppedByTokenLimit(shopName, initialTaskId) ? 3 : 7;
            translatesService.updateTranslateStatus(shopName, status, target, initialTaskV2DO.getSource());

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

        translatesService.updateTranslateStatus(shopName, 1, target, initialTaskV2DO.getSource());

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

        // 判断默认语言是否和source一致，不一致则停止任务
        String primaryLocaleData = shopifyService.getShopifyData(shopName, token,
                TranslateConstants.API_VERSION_LAST, ShopifyRequestUtils.getShopLanguageQuery());
        String primaryLocale = getPrimaryLocaleFromShopifyData(primaryLocaleData);
        if (primaryLocale != null && !primaryLocale.equals(initialTaskV2DO.getSource())) {
            initialTaskV2Repo.updateStatusAndSendEmailById(InitialTaskStatus.ALL_DONE.getStatus(), initialTaskV2DO.getId(), true, true);
            translateTaskMonitorV2RedisService.setSavingShopifyEndTime(initialTaskId);
            TraceReporterHolder.report("TranslateV2Service.saveToShopify",
                    "默认语言与source不一致，停止任务 shop: " + shopName + " primaryLocale: " + primaryLocale + " source: " + initialTaskV2DO.getSource());
            return;
        }

        TranslateTaskV2DO randomDo = translateTaskV2Repo.selectOneByInitialTaskIdAndNotSaved(initialTaskId);
        while (randomDo != null) {
            TraceReporterHolder.report("TranslateV2Service.saveToShopify", "TranslateTaskV2 saving shopify shop: "
                    + shopName + " randomDo: " + randomDo.getId() + " token: " + token + " initialTaskId: " + initialTaskId);
            String resourceId = randomDo.getResourceId();
            List<TranslateTaskV2DO> taskList = translateTaskV2Repo.selectByInitialTaskIdAndResourceIdWithLimit(initialTaskId, resourceId, randomDo.getModule());

            // 填回shopify
            ShopifyTranslationsResponse.Node node = new ShopifyTranslationsResponse.Node();
            node.setTranslations(taskList.stream()
                    .map(taskDO -> {
                        ShopifyTranslationsResponse.Node.Translation translation =
                                new ShopifyTranslationsResponse.Node.Translation();
                        translation.setLocale(target);
                        translation.setKey(taskDO.getNodeKey());
                        translation.setTranslatableContentDigest(taskDO.getDigest());
                        translation.setValue(taskDO.getTargetValue());
                        return translation;
                    })
                    .collect(Collectors.toList()));
            node.setResourceId(resourceId);
            TraceReporterHolder.report("TranslateV2Service.saveToShopify", "shopName: " + shopName + " token: " + token + " node: " + node);
            String strResponse = shopifyService.saveDataWithRateLimit(shopName, token, node);
            if (strResponse == null) {
                // 写入失败 fatalException
                TraceReporterHolder.report("TranslateV2Service.saveToShopify", "FatalException TranslateTaskV2 saving failed null : " + shopName +
                        " randomDo: " + randomDo.getId() + " token: " + token + " module : " + randomDo.getModule());
            }

            if (strResponse != null) {
                if (!strResponse.contains("\"userErrors\":[]")) {
                    feiShuRobotIntegration.sendMessage("FatalException TranslateTaskV2 saving failed: " + shopName + " randomDo: " + randomDo.getId() + " response: " + strResponse + " module : " + randomDo.getModule());

                    ShopifyRegisterResponse registerResponse = JsonUtils.jsonToObject(strResponse, ShopifyRegisterResponse.class);
                    Map<Integer, String> failedIndexToMessage = parseUserErrorIndexToMessage(registerResponse);
                    for (Map.Entry<Integer, String> e : failedIndexToMessage.entrySet()) {
                        int idx = e.getKey();
                        String errorMsg = e.getValue();
                        if (idx >= 0 && idx < taskList.size()) {
                            TranslateTaskV2DO failedTask = taskList.get(idx);
                            translateSaveFailedTaskRepo.insertFailedTask(
                                    failedTask.getId(), initialTaskId, shopName, errorMsg);
                        }
                    }
                }

                TraceReporterHolder.report("TranslateV2Service.saveToShopify", "TranslateTaskV2 saving success: " + shopName +
                        " randomDo: " + randomDo.getId() + " response: " + strResponse);

                // 回写数据库，标记已写入 TODO 批量
                // 需要data.translationsRegister.translations[]不为空，并且有key，才是最严格的
                for (TranslateTaskV2DO taskDO : taskList) {
                    translateTaskV2Repo.updateSavedToShopify(taskDO.getId());
                }
                translateTaskMonitorV2RedisService.addSavedCount(initialTaskId, taskList.size());
            }

            randomDo = translateTaskV2Repo.selectOneByInitialTaskIdAndNotSaved(initialTaskId);
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

    /**
     * 解析 Shopify translationsRegister 响应中 userErrors 的失败索引。
     * userErrors[].field 格式为 ["translations", "3"]，其中第二个元素为翻译请求列表中的索引。
     */
    private static final List<String> RETRYABLE_ERROR_KEYWORDS = Arrays.asList(
            "Meta description is too long",
            "Title is too long",
            "Translatable content hash is invalid"
    );

    private boolean isRetryableError(String message) {
        if (message == null || message.isEmpty()) {
            return false;
        }
        for (String keyword : RETRYABLE_ERROR_KEYWORDS) {
            if (message.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 解析 userErrors：同一 translations 下标对应其 message；同一 index 多条可重试错误时合并为一条字符串。
     */
    private Map<Integer, String> parseUserErrorIndexToMessage(ShopifyRegisterResponse response) {
        Map<Integer, String> indexToMessage = new LinkedHashMap<>();
        if (response == null || response.getData() == null
                || response.getData().getTranslationsRegister() == null) {
            return indexToMessage;
        }

        List<ShopifyGraphRegisterResponse.TranslationsRegister.UserError> userErrors =
                response.getData().getTranslationsRegister().getUserErrors();
        if (CollectionUtils.isEmpty(userErrors)) {
            return indexToMessage;
        }

        for (ShopifyGraphRegisterResponse.TranslationsRegister.UserError error : userErrors) {
            String msg = error.getMessage();
            if (!isRetryableError(msg)) {
                continue;
            }
            List<String> field = error.getField();
            if (field != null && field.size() >= 2) {
                try {
                    int idx = Integer.parseInt(field.get(1));
                    indexToMessage.merge(idx, msg, (existing, incoming) ->
                            existing.equals(incoming) ? existing : existing + "; " + incoming);
                } catch (NumberFormatException ignored) {
                    TraceReporterHolder.report("TranslateV2Service.saveToShopify", "Failed to parse user error field: " + field);
                }
            }
        }
        return indexToMessage;
    }

    /**
     * 定时任务入口：获取一条未重试的保存失败记录，根据 nodeKey 构建带 FIELD_RULE 的提示词重新翻译后保存到 Shopify。
     * 不再做多次重试，无论成功失败均标记为已重试。
     */
    public void retrySaveAllFailedTasks() {
        TranslateSaveFailedTaskDO failedRecord = translateSaveFailedTaskRepo.selectOneUnretried();
        if (failedRecord == null) {
            return;
        }

        TranslateTaskV2DO taskDO = translateTaskV2Repo.getById(failedRecord.getTranslateTaskId());
        if (taskDO == null) {
            translateSaveFailedTaskRepo.markRetried(failedRecord.getId());
            return;
        }

        InitialTaskV2DO initialTaskV2DO = initialTaskV2Repo.getById(failedRecord.getInitialTaskId());
        if (initialTaskV2DO == null) {
            translateSaveFailedTaskRepo.markRetried(failedRecord.getId());
            return;
        }

        String shopName = failedRecord.getShopName();
        UsersDO userDO = iUsersService.getUserByName(shopName);
        if (userDO == null) {
            translateSaveFailedTaskRepo.markRetried(failedRecord.getId());
            return;
        }
        String token = userDO.getAccessToken();
        String target = initialTaskV2DO.getTarget();
        String aiModel = initialTaskV2DO.getAiModel();

        TraceReporterHolder.report("TranslateV2Service.retrySaveAllFailedTasks",
                "Retranslating failed task: shop=" + shopName + " sourceText: " + taskDO.getSourceValue()
                        + " taskId=" + taskDO.getId() + " nodeKey=" + taskDO.getNodeKey());

        String errorMessage = failedRecord.getErrorMessage();
        if (errorMessage != null && errorMessage.contains("Translatable content hash is invalid")) {
            retryForInvalidTranslatableContentHash(failedRecord, taskDO, shopName, token, target, aiModel);
            return;
        }

        // Value fails validation on resource：根据 FIELD_RULE 提示词重新翻译并保存
        if (errorMessage != null && errorMessage.contains("Value fails validation on resource")) {
            retryWithFieldRulePromptAndSaveToShopify(failedRecord, taskDO, shopName, token, target, aiModel);
            return;
        }

        // 其他未知可重试错误：只打印日志，不走翻译重试
        TraceReporterHolder.report("TranslateV2Service.retrySaveAllFailedTasks",
                "Skip retry for unknown errorMessage: shop=" + shopName +
                        " taskId=" + taskDO.getId() + " nodeKey=" + taskDO.getNodeKey() +
                        " errorMessage=" + errorMessage);
        translateSaveFailedTaskRepo.markRetried(failedRecord.getId());
    }

    /**
     * 错误类型 1：Value fails validation on resource
     * 构建带 FIELD_RULE 的提示词（根据 nodeKey 匹配字段规则），重新翻译并保存到 Shopify。
     */
    private void retryWithFieldRulePromptAndSaveToShopify(TranslateSaveFailedTaskDO failedRecord,
                                                          TranslateTaskV2DO taskDO,
                                                          String shopName,
                                                          String token,
                                                          String target,
                                                          String aiModel) {
        // 构建带 FIELD_RULE 的提示词（根据 nodeKey 匹配字段规则）
        Map<String, GlossaryDO> glossaryMap = glossaryService.getGlossaryDoByShopName(shopName, target);
        String glossaryMapping = null;
        Map<String, GlossaryDO> usedGlossaryMap = new HashMap<>();
        if (GlossaryService.hasGlossary(taskDO.getSourceValue(), glossaryMap, usedGlossaryMap)) {
            glossaryMapping = GlossaryService.convertMapToText(usedGlossaryMap, taskDO.getSourceValue());
        }

        String prompt = promptConfigService.buildSinglePromptWithFieldRule(
                taskDO.getModule(), target, taskDO.getSourceValue(),
                glossaryMapping, null, taskDO.getNodeKey());

        Pair<String, Integer> pair = modelTranslateService.modelTranslate(
                aiModel, prompt, target, taskDO.getSourceValue());
        if (pair == null) {
            TraceReporterHolder.report("TranslateV2Service.retrySaveAllFailedTasks",
                    "Retranslation returned null: shop=" + shopName + " taskId=" + taskDO.getId());
            feiShuRobotIntegration.sendMessage("Retranslation returned null: shop=" + shopName + " taskId=" + taskDO.getId());
            translateSaveFailedTaskRepo.markRetried(failedRecord.getId());
            return;
        }

        String translatedValue = pair.getFirst();

        // 检查解析结果是否有效
        if (translatedValue == null || translatedValue.isEmpty()) {
            // 解析失败，可能是AI返回格式错误
            TraceReporterHolder.report("TranslateV2Service.retrySaveAllFailedTasks",
                    "Retry failed (no more retries): shop=" + shopName + " taskId=" + taskDO.getId()
                            + " nodeKey=" + taskDO.getNodeKey() + " response=" + pair.toString());
            feiShuRobotIntegration.sendMessage("Retry failed (no more retries): shop=" + shopName + " taskId=" + taskDO.getId()
                    + " nodeKey=" + taskDO.getNodeKey() + " response=" + pair.toString());
            return;
        }

        userTokenService.addUsedToken(shopName, pair.getSecond());
        translateTaskV2Repo.updateTargetValueAndHasTargetValue(translatedValue, true, taskDO.getId());

        // 保存到 Shopify
        ShopifyTranslationsResponse.Node node = new ShopifyTranslationsResponse.Node();
        ShopifyTranslationsResponse.Node.Translation translation = new ShopifyTranslationsResponse.Node.Translation();
        translation.setLocale(target);
        translation.setKey(taskDO.getNodeKey());
        translation.setTranslatableContentDigest(taskDO.getDigest());
        translation.setValue(translatedValue);
        node.setTranslations(Collections.singletonList(translation));
        node.setResourceId(taskDO.getResourceId());

        String strResponse = shopifyService.saveDataWithRateLimit(shopName, token, node);

        // 无论成功失败，直接标记为已重试，不再做后续重试
        translateSaveFailedTaskRepo.markRetried(failedRecord.getId());

        if (strResponse != null && strResponse.contains("\"userErrors\":[]")) {
            translateTaskV2Repo.updateSavedToShopify(taskDO.getId());
            translateTaskMonitorV2RedisService.addSavedCount(failedRecord.getInitialTaskId(), 1);
            TraceReporterHolder.report("TranslateV2Service.retrySaveAllFailedTasks",
                    "Retry success: shop=" + shopName + " taskId=" + taskDO.getId());
        } else {
            feiShuRobotIntegration.sendMessage("Retry failed (no more retries): shop=" + shopName + " taskId=" + taskDO.getId()
                    + " nodeKey=" + taskDO.getNodeKey() + " response=" + strResponse);
        }
    }

    /**
     * 错误类型 2：Translatable content hash is invalid
     * 先根据 resourceId 拉取 Shopify 最新数据，基于相同 nodeKey 获取最新 digest（并同步最新 source value），
     * 然后重新翻译并保存到 Shopify。
     */
    private void retryForInvalidTranslatableContentHash(TranslateSaveFailedTaskDO failedRecord,
                                                        TranslateTaskV2DO taskDO,
                                                        String shopName,
                                                        String token,
                                                        String target,
                                                        String aiModel) {
        boolean refreshed = refreshDigestAndSourceValueFromShopify(shopName, token, target, taskDO);
        if (!refreshed) {
            translateSaveFailedTaskRepo.markRetried(failedRecord.getId());
            return;
        }

        TraceReporterHolder.report("TranslateV2Service.retryForInvalidTranslatableContentHash",
                "Retry with refreshed digest=" + refreshed +
                        " shop=" + shopName + " taskId=" + taskDO.getId() + " nodeKey=" + taskDO.getNodeKey());

        // digest 重新翻译
        TranslateContext context = new TranslateContext(taskDO.getSourceValue(), target, taskDO.getType(),
                taskDO.getNodeKey(), glossaryService.getGlossaryDoByShopName(shopName, target), aiModel, taskDO.getModule(), taskDO.getTargetValue());
        context.setShopName(shopName);
        ITranslateStrategyService service = translateStrategyFactory.getServiceByContext(context);
        service.translate(context);
        service.finishAndGetJsonRecord(context);
        userTokenService.addUsedToken(shopName, context.getUsedToken());
        String translatedValue = context.getTranslatedContent();
        translateTaskV2Repo.updateTargetValueAndHasTargetValue(translatedValue, true, taskDO.getId());

        // 保存到 Shopify
        ShopifyTranslationsResponse.Node node = new ShopifyTranslationsResponse.Node();
        ShopifyTranslationsResponse.Node.Translation translation = new ShopifyTranslationsResponse.Node.Translation();
        translation.setLocale(target);
        translation.setKey(taskDO.getNodeKey());
        translation.setTranslatableContentDigest(taskDO.getDigest());
        translation.setValue(translatedValue);
        node.setTranslations(Collections.singletonList(translation));
        node.setResourceId(taskDO.getResourceId());

        String strResponse = shopifyService.saveDataWithRateLimit(shopName, token, node);

        // 无论成功失败，直接标记为已重试，不再做后续重试
        translateSaveFailedTaskRepo.markRetried(failedRecord.getId());

        if (strResponse != null && strResponse.contains("\"userErrors\":[]")) {
            translateTaskV2Repo.updateSavedToShopify(taskDO.getId());
            translateTaskMonitorV2RedisService.addSavedCount(failedRecord.getInitialTaskId(), 1);
            TraceReporterHolder.report("TranslateV2Service.retryForInvalidTranslatableContentHash",
                    "Retry success: shop=" + shopName + " taskId=" + taskDO.getId());
        } else {
            feiShuRobotIntegration.sendMessage("Retry Digest failed (no more retries): shop=" + shopName + " taskId=" + taskDO.getId()
                    + " nodeKey=" + taskDO.getNodeKey() + " response=" + strResponse);
        }
    }

    /**
     * 从 Shopify 拉取最新 translatableContent，并按 nodeKey 刷新 TranslateTaskV2DO 的 digest / sourceValue。
     */
    private boolean refreshDigestAndSourceValueFromShopify(String shopName,
                                                           String token,
                                                           String target,
                                                           TranslateTaskV2DO taskDO) {
        if (taskDO == null
                || taskDO.getResourceId() == null || taskDO.getResourceId().isEmpty()
                || taskDO.getNodeKey() == null || taskDO.getNodeKey().isEmpty()) {
            return false;
        }

        String resourceId = taskDO.getResourceId();
        String nodeKey = taskDO.getNodeKey();

        // 直接按你给的 query：translatableResourcesByIds 只取 translatableContent.digest/key/value，
        // 再根据 key 找到最新 digest（以及同步 value 作为 source）。
        String graphQuery = "query MyQuery { " +
                "translatableResourcesByIds(resourceIds: \"" + resourceId + "\", first: 1) { " +
                "nodes { " +
                "resourceId " +
                "translatableContent { digest key value } " +
                "} " +
                "pageInfo { endCursor hasNextPage } " +
                "} " +
                "}";

        String shopifyData = shopifyService.getShopifyData(shopName, token, TranslateConstants.API_VERSION_LAST, graphQuery);
        if (shopifyData == null || shopifyData.isEmpty()) {
            return false;
        }

        JsonNode root = JsonUtils.readTree(shopifyData);
        if (root == null) {
            return false;
        }

        JsonNode nodes = root.path("translatableResourcesByIds").path("nodes");
        if (nodes == null || !nodes.isArray() || nodes.isEmpty()) {
            return false;
        }

        for (JsonNode node : nodes) {
            JsonNode translatableContentList = node.path("translatableContent");
            if (translatableContentList == null || !translatableContentList.isArray()) {
                continue;
            }
            for (JsonNode translatableContent : translatableContentList) {
                if (nodeKey.equals(translatableContent.path("key").asText(null))) {
                    String latestDigest = translatableContent.path("digest").asText(null);
                    String latestSourceValue = translatableContent.path("value").asText(null);
                    if (latestDigest != null && !latestDigest.isEmpty()) {
                        taskDO.setDigest(latestDigest);
                    }
                    if (latestSourceValue != null) {
                        taskDO.setSourceValue(latestSourceValue);
                    }
                    return true;
                }
            }
        }

        TraceReporterHolder.report("TranslateV2Service.refreshDigestAndSourceValueFromShopify",
                "Digest not found for key. shop=" + shopName + " resourceId=" + resourceId + " nodeKey=" + nodeKey);
        return false;
    }

    // 翻译 step 5, 翻译写入都完成 -> 发送邮件，is_delete部分数据
    public void sendManualEmail(InitialTaskV2DO initialTaskV2DO) {
        if (InitialTaskStatus.SAVE_DONE_SENDING_EMAIL.status == initialTaskV2DO.getStatus()) {
            sendManualSuccessEmail(initialTaskV2DO);
            return;
        }
        if (InitialTaskStatus.STOPPED.status == initialTaskV2DO.getStatus() || InitialTaskStatus.INIT_STOPPED.status == initialTaskV2DO.getStatus()) {
            handleManualStoppedEmail(initialTaskV2DO);
        }
    }

    /**
     * 手动翻译正常结束时发送成功邮件并更新状态
     */
    private void sendManualSuccessEmail(InitialTaskV2DO initialTaskV2DO) {
        String shopName = initialTaskV2DO.getShopName();
        Integer usingTimeMinutes = (int) ((System.currentTimeMillis() - initialTaskV2DO.getCreatedAt().getTime()) / (1000 * 60));
        Integer usedTokenByTask = userTokenService.getUsedTokenByTaskId(shopName, initialTaskV2DO.getId());

        TraceReporterHolder.report("TranslateV2Service.sendManualSuccessEmail", "TranslateTaskV2 Completed Email sent to user: " + shopName +
                " Total time (minutes): " + usingTimeMinutes +
                " Total tokens used: " + usedTokenByTask);

        Integer usedToken = userTokenService.getUsedToken(shopName);
        Integer totalToken = userTokenService.getMaxToken(shopName);
        tencentEmailService.sendSuccessEmail(shopName, initialTaskV2DO.getTarget(), usingTimeMinutes, usedTokenByTask,
                usedToken, totalToken);

        initialTaskV2Repo.updateSendEmailAndStatusById(true, InitialTaskStatus.ALL_DONE.status, initialTaskV2DO.getId());
    }

    /**
     * 手动翻译中断时处理：手动中断仅标记已发邮件；因 token 限制的部分翻译则延迟批量发邮件
     */
    private void handleManualStoppedEmail(InitialTaskV2DO initialTaskV2DO) {
        String shopName = initialTaskV2DO.getShopName();
        boolean stoppedByLimit = redisStoppedRepository.isStoppedByTokenLimit(initialTaskV2DO.getShopName(), initialTaskV2DO.getId());

        if (!stoppedByLimit) {
            initialTaskV2Repo.updateSendEmailById(initialTaskV2DO.getId(), true);
            return;
        }

        long translateEndTime = Long.parseLong(translateTaskMonitorV2RedisService.getTranslateEndTime(initialTaskV2DO.getId()));
        if ((System.currentTimeMillis() - translateEndTime) < 60 * 10000) {
            return;
        }

        sendPartialTranslationEmailForManual(shopName);
    }

    /**
     * 获取该用户未发邮件的部分翻译任务，发送批量失败邮件并标记已发邮件
     */
    private void sendPartialTranslationEmailForManual(String shopName) {
        List<InitialTaskV2DO> stoppedTasks = initialTaskV2Repo.selectByShopNameStoppedAndNotEmail(shopName, "manual", 5);

        if (CollectionUtils.isEmpty(stoppedTasks)) {
            return;
        }

        List<InitialTaskV2DO> partialTranslation = new ArrayList<>();
        for (InitialTaskV2DO task : stoppedTasks) {
            boolean stoppedByTokenLimit = redisStoppedRepository.isStoppedByTokenLimit(task.getShopName(), task.getId());
            if (stoppedByTokenLimit) {
                partialTranslation.add(task);
            }
        }

        if (!partialTranslation.isEmpty()) {
            List<InitialTaskV2DO> initialTasks = initialTaskV2Repo.selectByShopNameStoppedAndNotEmail(shopName, "manual", 0);
            partialTranslation.addAll(initialTasks);

            TraceReporterHolder.report("TranslateV2Service.sendPartialTranslationEmailForManual", "sendManualEmail 手动翻译批量失败邮件 : " + shopName);
            tencentEmailService.sendTranslatePartialEmail(shopName, partialTranslation, "manual translation");
            for (InitialTaskV2DO task : partialTranslation) {
                initialTaskV2Repo.updateSendEmailById(task.getId(), true);
            }
        }
    }

    /**
     * 付费后继续翻译：只恢复“自动停止”（额度/字符上限触发暂停）的任务。
     */
    public void continueAutoStoppedTranslatingByShopName(String shopName) {
        List<InitialTaskV2DO> list = initialTaskV2Repo.selectResumableByShopName(shopName);
        if (CollectionUtils.isEmpty(list)) {
            return;
        }

        for (InitialTaskV2DO initialTaskV2DO : list) {
            boolean resumeByTokenLimitFlag = redisStoppedRepository.isStoppedByTokenLimit(shopName, initialTaskV2DO.getId());
            if (!resumeByTokenLimitFlag) {
                // 非“自动停止”（tokenLimit）任务，不在付费后自动恢复范围内
                continue;
            }

            continueTranslatingV2(shopName, initialTaskV2DO.getId());
            TraceReporterHolder.report("TranslateV2Service.continueAutoStoppedTranslatingByShopName",
                    "continueAutoStopped : " + " shop: " + shopName + " taskId: " + initialTaskV2DO.getId()
                            + " resumeByTokenLimit: " + resumeByTokenLimitFlag);
        }
    }

    // 在TranslateTask里定时调用这里
    public boolean autoTranslateV2(String shopName, String source, String target) {
        UsersDO usersDO = usersService.getUserByName(shopName);
        if (usersDO == null) {
            TraceReporterHolder.report("TranslateV2Service.autoTranslateV2", "autoTranslateV2 已卸载 用户: " + shopName);
            return false;
        }

        if (usersDO.getUninstallTime() != null) {
            if (usersDO.getLoginTime() == null) {
                TraceReporterHolder.report("TranslateV2Service.autoTranslateV2", "autoTranslateV2 卸载了未登陆 用户: " + shopName);
                return false;
            } else if (usersDO.getUninstallTime().after(usersDO.getLoginTime())) {
                TraceReporterHolder.report("TranslateV2Service.autoTranslateV2", "autoTranslateV2 卸载了时间在登陆时间后 用户: " + shopName);
                return false;
            }
        }

        // 判断注册时间
        if (usersDO.getLoginTime() == null) {
            TraceReporterHolder.report("TranslateV2Service.autoTranslateV2", "autoTranslateV2 用户未登录 或 登录时间为空: " + shopName);
            return false;
        }

        // 将登录时间与当前时间都按 UTC 时区比较小时
        int loginHour = usersDO.getCreateAt().toInstant().atZone(ZoneOffset.UTC).getHour();
        int currentHour = Instant.now().atZone(ZoneOffset.UTC).getHour();
        TraceReporterHolder.report("TranslateV2Service.autoTranslateV2", "autoTranslateV2 loginHour: " + loginHour + " currentHour: " + currentHour + " shop: " + shopName);

        // 只有当登录小时与当前小时一致时才继续执行，其他情况按分片逻辑跳过
        if (loginHour != currentHour) {
            TraceReporterHolder.report("TranslateV2Service.autoTranslateV2", "autoTranslateV2 非当前小时，不执行自动翻译 shop: " + shopName);
            return false;
        }

        Integer maxToken = userTokenService.getMaxToken(shopName);
        Integer usedToken = userTokenService.getUsedToken(shopName);
        TraceReporterHolder.report("TranslateV2Service.autoTranslateV2", "autoTranslateV2 maxToken: " + maxToken + " usedToken: " + usedToken + " shop: " + shopName);
        // 如果字符超限，则直接返回字符超限
        if (usedToken >= maxToken) {
            TraceReporterHolder.report("TranslateV2Service.autoTranslateV2", "autoTranslateV2 字符超限 用户: " + shopName);
            return false;
        }

        if (initialTaskV2Repo.existsTranslatingTask(shopName, source, target)) {
            TraceReporterHolder.report("TranslateV2Service.autoTranslateV2",
                    "autoTranslateV2 已存在翻译中任务，跳过创建 shop: " + shopName + " source: " + source + " target: " + target);
            return false;
        }

        // 判断这条语言是否在用户本地存在
        String shopifyByQuery = shopifyService.getShopifyData(shopName, usersDO.getAccessToken(),
                TranslateConstants.API_VERSION_LAST, ShopifyRequestUtils.getShopLanguageQuery());
        TraceReporterHolder.report("TranslateV2Service.autoTranslateV2", "autoTranslateV2 获取用户本地语言数据: " + shopName + " 数据为： " + shopifyByQuery);
        if (shopifyByQuery == null) {
            return false;
        }

        String userCode = "\"" + target + "\"";
        if (!shopifyByQuery.contains(userCode)) {
            // 将用户的自动翻译标识改为false
            translatesService.updateAutoTranslateByShopNameAndTargetToFalse(shopName, target);
            TraceReporterHolder.report("TranslateV2Service.autoTranslateV2", "autoTranslateV2 用户本地语言数据不存在 用户: " + shopName + " target: " + target);
            return false;
        }

        // 判断默认语言是否和source一致，不一致则关闭自动翻译
        String primaryLocale = getPrimaryLocaleFromShopifyData(shopifyByQuery);
        if (primaryLocale != null && !primaryLocale.equals(source)) {
            translatesService.updateAutoTranslateByShopNameAndTargetToFalse(shopName, target);
            TraceReporterHolder.report("TranslateV2Service.autoTranslateV2",
                    "autoTranslateV2 默认语言与source不一致，关闭自动翻译 用户: " + shopName + " primaryLocale: " + primaryLocale + " source: " + source);
            return false;
        }

        TraceReporterHolder.report("TranslateV2Service.autoTranslateV2", "autoTranslateV2 任务准备创建 " + shopName + " target: " + target);
        createAutoTask(shopName, source, target);
        TraceReporterHolder.report("TranslateV2Service.autoTranslateV2", "autoTranslateV2 任务创建成功 " + shopName + " target: " + target);
        return true;
    }

    public void cleanTask(InitialTaskV2DO initialTaskV2DO) {
        TraceReporterHolder.report("TranslateV2Service.cleanTask", "TranslateTaskV2 cleanTask start clean task: " + initialTaskV2DO.getId());
        while (true) {
            int deleted = translateTaskV2Repo.deleteByInitialTaskId(initialTaskV2DO.getId());
            TraceReporterHolder.report("TranslateV2Service.cleanTask", "TranslateTaskV2 cleanTask delete: " + deleted);
            if (deleted <= 0) {
                break;
            }
        }
        initialTaskV2Repo.deleteById(initialTaskV2DO.getId());
    }

    // 根据翻译规则，不翻译的直接不用存
    private boolean needTranslate(ShopifyTranslationsResponse.Node.TranslatableContent translatableContent,
                                  List<ShopifyTranslationsResponse.Node.Translation> translations,
                                  String module, boolean isCover, boolean isHandle, String shopName, String accessToken
            , String resourceId, List<ShopifyTranslationsRemove> shopifyTranslationsRemoveList) {
        String value = translatableContent.getValue();
        String type = translatableContent.getType();
        String key = translatableContent.getKey();

        ShopifyTranslationsResponse.Node.Translation keyTranslation =
                translations.stream()
                        .filter(t -> key.equals(t.getKey()))
                        .findFirst()
                        .orElse(null);

        if (StringUtils.isEmpty(value)) {
            // 判断原文是否为空,译文是否有内容,需要删掉译文
            if (JudgeTranslateUtils.TRANSLATABLE_RESOURCE_TYPES.contains(module) && keyTranslation != null &&
                    !StringUtils.isEmpty(keyTranslation.getValue())) {
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
                || "LIST_URL".equals(type) || "JSON_STRING".equals(type)) {
            return false;
        }

        // 如果不是metatield 且是 type为JSON 返回false
        if (!TranslateConstants.METAFIELD.equals(module) && "JSON".equals(type)) {
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

            // TODO: 暂时先硬编码下（解决下问题）， 后面再改为config配置
            for (String blackValue : JudgeTranslateUtils.BLACKLIST_WORDS) {
                if (blackValue.equals(value)) {
                    return false;
                }
            }

            // 对ONLINE_STORE_THEME_LOCALE_CONTENT 模块的key值进行判断，如果包含gempage、pagefly、ecom、beae、error 不翻译，大小写不敏感
            if (TranslateConstants.ONLINE_STORE_THEME_LOCALE_CONTENT.equals(module)) {
                String lowerKey = key.toLowerCase(Locale.ROOT);
                if (lowerKey.contains("gempage") || lowerKey.contains("pagefly") || lowerKey.contains("ecom")
                        || lowerKey.contains("beae") || lowerKey.contains("error")) {
                    JudgeTranslateUtils.printTranslateReason(
                            "key : " + key + " 命中 ONLINE_STORE_THEME_LOCALE_CONTENT 黑名单关键词，value是： " + value);
                    return false;
                }
            }

            // 判断图片 与 mp4
            if (JudgeTranslateUtils.IMAGE_PATTERN.matcher(value).matches()) {
                JudgeTranslateUtils.printTranslateReason("key : " + key + " 包含图片或视频, value是： " + value);
                return false;
            }

            // 以/开头的不翻译
            if (JudgeTranslateUtils.PATH_PATTERN.matcher(value).matches()) {
                JudgeTranslateUtils.printTranslateReason("key : " + key + " value是： " + value + " 以/开头的不翻译");
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

            // 如果是base64编码的数据，不翻译
            if (JudgeTranslateUtils.BASE64_PATTERN.matcher(value).matches()) {
                return false;
            }

            if (value.startsWith("=")) {
                return false;
            }

            // 原字段如果 包含 class='jdgm-all-reviews__header' 不翻译
            if (value.contains("class='jdgm-all-reviews__header'")) {
                return false;
            }

            //对CC_CC-PT的数据不翻译
            if ("CC_CC-PT".equals(value)) {
                return false;
            }

            if (JsonUtils.isJson(value) || "JSON".equals(type)) {
                return canTranslateMetafieldJsonByConfig(value, type, shopName, accessToken, resourceId);
            }
        }

        if (TranslateConstants.METAOBJECT.equals(module)) {
            if (value.contains("grp__")) {
                return false;
            }
        }
        return true;
    }

    /**
     * 基于配置判断 Metafield 的 JSON 值是否需要翻译。
     * <p>
     * 判定顺序（优先走新配置，失败时可降级）：
     * 1. 读取配置 `METAFIELD_JSON_TRANSLATE_RULE.needTranslateJudge`；
     * 2. 校验 `allowedShopifyTypes`（如果配置了该字段）；
     * 3. 校验 `jsonMustContainAny`（如果配置了该字段）；
     * 4. 按 `requireOwnerCheck` 决定是否追加 owner 存在性校验；
     * 5. 任一步配置结构无效时，根据 `fallbackToLegacyWhenInvalid` 决定是否回退旧逻辑。
     * <p>
     * 注意：
     * - 该方法只负责“是否可翻译”的策略判定，不改动原始内容；
     * - 异常场景统一兜底到旧逻辑，避免因配置问题导致线上翻译能力突然失效。
     */
    public boolean canTranslateMetafieldJsonByConfig(String value, String type, String shopName, String accessToken, String resourceId) {
        try {
            JsonNode configNode = JsonUtils.readTree(configRedisRepo.getConfig(METAFIELD_JSON_TRANSLATE_RULE));
            if (configNode == null || !configNode.isObject()) {
                return canTranslateMetafieldJsonLegacy(value, type, shopName, accessToken, resourceId);
            }

            JsonNode judgeNode = configNode.get("needTranslateJudge");
            if (judgeNode == null || !judgeNode.isObject()) {
                return canTranslateMetafieldJsonLegacy(value, type, shopName, accessToken, resourceId);
            }

            // 配置项缺失时默认 true，确保配置异常时优先兼容旧线上行为。
            boolean fallbackToLegacy = !judgeNode.has("fallbackToLegacyWhenInvalid")
                    || judgeNode.path("fallbackToLegacyWhenInvalid").asBoolean(true);

            JsonNode allowedTypesNode = judgeNode.get("allowedShopifyTypes");
            if (!matchAllowedType(type, allowedTypesNode)) {
                return false;
            }

            JsonNode mustContainNode = judgeNode.get("jsonMustContainAny");
            if (mustContainNode != null && !mustContainNode.isArray()) {
                // 配置格式不合法（应为数组），按开关决定是否降级到旧逻辑。
                return fallbackToLegacy && canTranslateMetafieldJsonLegacy(value, type, shopName, accessToken, resourceId);
            }
            if (!matchMustContainCondition(value, mustContainNode)) {
                return false;
            }

            // 仅当显式要求时才调用 Shopify 校验 owner，避免额外远程请求。
            boolean requireOwnerCheck = judgeNode.path("requireOwnerCheck").asBoolean(false);
            return !requireOwnerCheck || hasMetafieldOwner(shopName, accessToken, resourceId);
        } catch (Exception e) {
            ExceptionReporterHolder.report("TranslateV2Service.canTranslateMetafieldJsonByConfig", e);
            return canTranslateMetafieldJsonLegacy(value, type, shopName, accessToken, resourceId);
        }
    }

    /**
     * 类型白名单匹配：
     * - 未配置或配置为空数组：默认放行；
     * - 配置了数组：只要命中一个类型即放行，否则拦截。
     */
    private boolean matchAllowedType(String type, JsonNode allowedTypesNode) {
        if (allowedTypesNode == null) {
            return true;
        }
        if (!allowedTypesNode.isArray()) {
            return true;
        }
        if (allowedTypesNode.size() == 0) {
            return true;
        }

        for (JsonNode allowedTypeNode : allowedTypesNode) {
            if (allowedTypeNode != null && allowedTypeNode.isTextual()
                    && allowedTypeNode.asText().equals(type)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 内容关键字匹配：
     * - 未配置或空数组：默认放行；
     * - 配置了数组：JSON 字符串中命中任一关键字即放行。
     */
    private boolean matchMustContainCondition(String value, JsonNode mustContainNode) {
        if (mustContainNode == null || mustContainNode.size() == 0) {
            return true;
        }
        if (!mustContainNode.isArray()) {
            return false;
        }

        for (JsonNode conditionNode : mustContainNode) {
            if (conditionNode != null && conditionNode.isTextual()) {
                String condition = conditionNode.asText();
                if (!StringUtils.isEmpty(condition) && value.contains(condition)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean canTranslateMetafieldJsonLegacy(String value, String type, String shopName, String accessToken, String resourceId) {
        if (!value.contains(JSON_JUDGE) || !"RICH_TEXT_FIELD".equals(type)) {
            return false;
        }
        return hasMetafieldOwner(shopName, accessToken, resourceId);
    }

    private boolean hasMetafieldOwner(String shopName, String accessToken, String resourceId) {
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

    public void deleteToShopify() {
        // 从数据库中随机获取一个DeleteTasksDO，然后去获取shopName等数据
        DeleteTasksDO deleteTasksDO = deleteTasksRepo.selectOneByNotDeleted();
        if (deleteTasksDO == null) {
            return;
        }

        InitialTaskV2DO initialTaskV2DO = initialTaskV2Repo.getById(deleteTasksDO.getInitialTaskId());
        if (initialTaskV2DO == null) {
            // 将deleted_to_shopify 改为 true
            deleteTasksRepo.updateDeletedToShopify(deleteTasksDO.getId());
            return;
        }

        String shopName = initialTaskV2DO.getShopName();
        UsersDO userDO = iUsersService.getUserByName(shopName);
        String token = userDO.getAccessToken();
        String target = initialTaskV2DO.getTarget();

        DeleteTasksDO randomDo = deleteTasksRepo.selectOneByInitialTaskIdAndNotDeleted(initialTaskV2DO.getId());
        while (randomDo != null) {
            TraceReporterHolder.report("TranslateV2Service.deleteToShopify", "DeleteTasks deleted shopify shop: " + shopName + " randomDo: " + randomDo.getId());
            String resourceId = randomDo.getResourceId();
            List<DeleteTasksDO> taskList = deleteTasksRepo.selectByInitialTaskIdAndResourceIdWithLimit(initialTaskV2DO.getId(), resourceId);

            // 删除shopify
            ShopifyTranslationsRemove remove = new ShopifyTranslationsRemove();
            remove.setResourceId(resourceId);
            remove.setLocales(new String[]{target});

            // 循环存key值
            String[] keys = taskList.stream()
                    .map(DeleteTasksDO::getNodeKey)
                    .toArray(String[]::new);
            remove.setTranslationKeys(keys);

            ShopifyGraphRemoveResponse shopifyGraphRemoveResponse = shopifyService.deleteShopifyDataWithRateLimit(
                    shopName, token, remove);
            if (shopifyGraphRemoveResponse != null) {
                TraceReporterHolder.report("TranslateV2Service.deleteToShopify", "DeleteTasks deleting success: " + shopName +
                        " randomDo: " + randomDo.getId() + " response: " + shopifyGraphRemoveResponse);
                // 回写数据库，标记已删除
                for (DeleteTasksDO taskDO : taskList) {
                    deleteTasksRepo.updateDeletedToShopify(taskDO.getId());
                }
            } else {
                // 删除失败 fatalException
                TraceReporterHolder.report("TranslateV2Service.deleteToShopify", "FatalException DeleteTasks deleting failed: " + shopName +
                        " randomDo: " + randomDo.getId());
            }


            randomDo = deleteTasksRepo.selectOneByInitialTaskIdAndNotDeleted(initialTaskV2DO.getId());
            TraceReporterHolder.report("TranslateV2Service.deleteToShopify", "TranslateTaskV2 delete SHOPIFY: " + shopName + " size: " + taskList.size());
        }
    }

    public void cleanDeleteTask(InitialTaskV2DO initialTaskV2DO) {
        TraceReporterHolder.report("TranslateV2Service.cleanDeleteTask", "TranslateTaskV2 cleanDeleteTask start clean task: " + initialTaskV2DO.getId());
        while (true) {
            int deleted = deleteTasksRepo.deleteByInitialTaskId(initialTaskV2DO.getId());
            TraceReporterHolder.report("TranslateV2Service.cleanDeleteTask", "TranslateTaskV2 cleanDeleteTask delete: " + deleted);
            if (deleted <= 0) {
                break;
            }
        }
    }

    public BaseResponse<Object> continueTranslatingV2(String shopName, Integer taskId) {
        InitialTaskV2DO initialTaskV2DO = initialTaskV2Repo.selectById(taskId);
        if (initialTaskV2DO == null) {
            return new BaseResponse<>().CreateSuccessResponse(true);
        }
        int status = initialTaskV2DO.getStatus();
        redisStoppedRepository.removeStoppedFlag(shopName, taskId);
        translatesService.updateTranslateStatus(shopName, 2, initialTaskV2DO.getTarget(), initialTaskV2DO.getSource());
        if (status == InitialTaskStatus.INIT_STOPPED.getStatus()) {
            // 初始化阶段停止：按「从头重新初始化」处理
            List<String> moduleList = JsonUtils.jsonToObject(initialTaskV2DO.getModuleList(), new TypeReference<>() {
            });
            if (moduleList != null) {
                translateTaskMonitorV2RedisService.clearInitProgress(initialTaskV2DO.getId(), moduleList);
            }
            translateTaskMonitorV2RedisService.resetMonitorForReinit(initialTaskV2DO.getId());
            translateTaskV2Repo.logicalDeletionById(initialTaskV2DO.getId());
            initialTaskV2Repo.updateStatusAndSendEmailById(InitialTaskStatus.INIT_READING_SHOPIFY.getStatus(), initialTaskV2DO.getId(), false, false);
        } else if (status == InitialTaskStatus.STOPPED.getStatus()) {
            // 翻译阶段停止：保持现有逻辑，仅继续翻译，不重新初始化
            initialTaskV2Repo.updateStatusAndSendEmailById(InitialTaskStatus.READ_DONE_TRANSLATING.getStatus(), initialTaskV2DO.getId(), false, false);
        }

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
        INIT_STOPPED(6, "初始化阶段已停止"),
        ;

        private final int status;
        private final String desc;

        InitialTaskStatus(int status, String desc) {
            this.status = status;
            this.desc = desc;
        }
    }

}
