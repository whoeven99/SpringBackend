package com.bogda.service.logic.translate;

import com.bogda.common.controller.request.ClickTranslateRequest;
import com.bogda.common.controller.response.BaseResponse;
import com.bogda.common.controller.response.ProgressResponse;
import com.bogda.common.contants.TranslateConstants;
import com.bogda.common.entity.DO.GlossaryDO;
import com.bogda.common.entity.DO.TranslateResourceDTO;
import com.bogda.common.entity.DO.UsersDO;
import com.bogda.common.entity.VO.SingleReturnVO;
import com.bogda.common.entity.VO.SingleTranslateVO;
import com.bogda.common.reporter.TraceReporterHolder;
import com.bogda.common.utils.JsonUtils;
import com.bogda.common.utils.JsoupUtils;
import com.bogda.common.utils.ModuleCodeUtils;
import com.bogda.common.utils.ShopifyRequestUtils;
import com.bogda.common.utils.StringUtils;
import com.bogda.integration.model.ShopifyTranslationsResponse;
import com.bogda.repository.container.TranslateTaskV3DO;
import com.bogda.repository.entity.InitialTaskV2DO;
import com.bogda.repository.repo.translate.TranslateTaskV3BlobRepo;
import com.bogda.repository.repo.translate.TranslateTaskV3CosmosRepo;
import com.bogda.service.Service.ITranslatesService;
import com.bogda.service.Service.IUsersService;
import com.bogda.service.integration.ALiYunTranslateIntegration;
import com.bogda.service.logic.GlossaryService;
import com.bogda.service.logic.ShopifyRateLimitService;
import com.bogda.service.logic.ShopifyService;
import com.bogda.service.logic.redis.ConfigRedisRepo;
import com.bogda.service.logic.redis.TranslateTaskMonitorV3RedisService;
import com.bogda.service.logic.token.UserTokenService;
import com.fasterxml.jackson.core.type.TypeReference;
import kotlin.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import javax.annotation.PreDestroy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Component
public class TranslateV3Service {
    private static final int PROGRESS_FLUSH_INTERVAL_CHUNKS = 10;
    private static final String SAVE_PROGRESS_SUFFIX = ".save-progress";
    private static final double AI_EVAL_SAMPLE_RATIO = 0.30;
    private static final int MIN_EVAL_SAMPLES_PER_MODULE = 30;
    private static final int MAX_EVAL_SAMPLES_PER_MODULE = 1000;

    @Autowired
    private TranslateV2Service translateV2Service;
    @Autowired
    private TranslateTaskV3CosmosRepo translateTaskV3CosmosRepo;
    @Autowired
    private TranslateTaskV3BlobRepo translateTaskV3BlobRepo;
    @Autowired
    private TranslateTaskMonitorV3RedisService translateTaskMonitorV3RedisService;
    @Autowired
    private ShopifyService shopifyService;
    @Autowired
    private ShopifyRateLimitService shopifyRateLimitService;
    @Autowired
    private IUsersService usersService;
    @Autowired
    private UserTokenService userTokenService;
    @Autowired
    private ConfigRedisRepo configRedisRepo;
    @Autowired
    private ITranslatesService translatesService;
    @Autowired
    private GlossaryService glossaryService;
    @Autowired
    private UniversalTranslateService universalTranslateService;
    @Autowired
    private ModelTranslateService modelTranslateService;
    private final Set<String> processingInitialTaskIds = ConcurrentHashMap.newKeySet();
    private final Set<String> processingTranslateTaskIds = ConcurrentHashMap.newKeySet();
    private final Set<String> processingSaveTaskIds = ConcurrentHashMap.newKeySet();
    private final Set<String> processingVerifyTaskIds = ConcurrentHashMap.newKeySet();
    private final Map<String, PendingProgress> pendingProgressMap = new ConcurrentHashMap<>();

    public BaseResponse<Object> createInitialTask(ClickTranslateRequest request) {
        String shopName = request.getShopName();
        if (StringUtils.isEmpty(shopName) || StringUtils.isEmpty(request.getSource())
                || request.getTarget() == null || request.getTarget().length == 0
                || CollectionUtils.isEmpty(request.getTranslateSettings3())
                || StringUtils.isEmpty(request.getTranslateSettings1())) {
            return BaseResponse.FailedResponse("Missing parameters");
        }

        Integer maxToken = userTokenService.getMaxToken(shopName);
        Integer usedToken = userTokenService.getUsedToken(shopName);
        if (usedToken >= maxToken) {
            return BaseResponse.FailedResponse("Token limit reached");
        }

        String aiModel = ModuleCodeUtils.getModuleCode(request.getTranslateSettings1());
        boolean isCover = Boolean.TRUE.equals(request.getIsCover());
        boolean hasHandle = request.getTranslateSettings3().contains("handle");
        List<String> moduleList = resolveModuleList(request.getTranslateSettings3());
        if (moduleList.isEmpty()) {
            return BaseResponse.FailedResponse("No valid module can translate");
        }

        List<String> createdTaskIds = new ArrayList<>();
        for (String target : request.getTarget()) {
            if (configRedisRepo.isWhiteList(target, "forbiddenTarget")) {
                continue;
            }
            if (translateTaskV3CosmosRepo.existsActiveTask(shopName, request.getSource(), target)) {
                continue;
            }

            String taskId = UUID.randomUUID().toString();
            TranslateTaskV3DO task = new TranslateTaskV3DO();
            task.setId(taskId);
            task.setShopName(shopName);
            task.setSource(request.getSource());
            task.setTarget(target);
            task.setStatus(0);
            task.setTaskType("manual");
            task.setAiModel(aiModel);
            task.setCover(isCover);
            task.setHandle(hasHandle);
            task.setModuleList(JsonUtils.objectToJson(moduleList));
            task.setSessionId(shopName + ":" + taskId);

            Map<String, Object> checkpoint = new HashMap<>();
            checkpoint.put("phase", "INIT_CREATED");
            checkpoint.put("updatedAt", Instant.now().toString());
            task.setCheckpoint(checkpoint);

            Map<String, Object> metrics = new HashMap<>();
            metrics.put("totalCount", 0);
            metrics.put("translatedCount", 0);
            metrics.put("savedCount", 0);
            metrics.put("usedToken", 0);
            task.setMetrics(metrics);

            if (!translateTaskV3CosmosRepo.upsert(task)) {
                continue;
            }

            translateTaskMonitorV3RedisService.createRecord(taskId, shopName, request.getSource(), target, aiModel);
            translatesService.updateTranslateStatus(shopName, 2, target, request.getSource());
            createdTaskIds.add(taskId);
        }

        if (createdTaskIds.isEmpty()) {
            return BaseResponse.FailedResponse("No Target Language Can Create");
        }
        return BaseResponse.SuccessResponse(createdTaskIds);
    }

    public BaseResponse<ProgressResponse> getProcess(String shopName, String source) {
        return translateV2Service.getProcess(shopName, source);
    }

    public BaseResponse<SingleReturnVO> singleTextTranslate(SingleTranslateVO request) {
        return translateV2Service.singleTextTranslate(request);
    }

    public BaseResponse<Object> continueTranslatingV3(String shopName, Integer taskId) {
        return translateV2Service.continueTranslatingV2(shopName, taskId);
    }

    public void continueAutoStoppedTranslatingByShopName(String shopName) {
        translateV2Service.continueAutoStoppedTranslatingByShopName(shopName);
    }

    public BaseResponse<Object> triggerAiScoreReport(String taskId, String shopName, String module) {
        if (StringUtils.isEmpty(taskId) || StringUtils.isEmpty(shopName)) {
            return BaseResponse.FailedResponse("Missing parameters: taskId or shopName");
        }

        TranslateTaskV3DO task = translateTaskV3CosmosRepo.getById(taskId, shopName);
        String effectiveShopName = shopName;
        if (task == null) {
            task = findTaskByIdAcrossStatuses(taskId);
            if (task != null) {
                effectiveShopName = task.getShopName();
            }
        }
        if (task == null) {
            return BaseResponse.FailedResponse("Task not found. Please verify taskId/shopName or environment");
        }

        List<String> modules = JsonUtils.jsonToObject(task.getModuleList(), new TypeReference<List<String>>() {
        });
        if (CollectionUtils.isEmpty(modules)) {
            return BaseResponse.FailedResponse("Task has empty modules");
        }

        List<String> scoreModules = new ArrayList<>();
        if (StringUtils.isEmpty(module)) {
            scoreModules.addAll(modules);
        } else {
            for (String each : modules) {
                if (module.equals(each)) {
                    scoreModules.add(each);
                    break;
                }
            }
            if (scoreModules.isEmpty()) {
                return BaseResponse.FailedResponse("Module not found in task: " + module);
            }
        }

        try {
            generateAiScoreReport(task, scoreModules);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("taskId", taskId);
            result.put("shopName", effectiveShopName);
            result.put("modules", scoreModules);
            result.put("moduleBlobPathPattern", "tasks/" + taskId + "/chunks/{module}/ai-score.json");
            result.put("summaryBlobPath", "tasks/" + taskId + "/qa/ai-score.json");
            return BaseResponse.SuccessResponse(result);
        } catch (Exception e) {
            TraceReporterHolder.report("TranslateV3Service.triggerAiScoreReport",
                    "FatalException trigger ai score failed, taskId=" + taskId + " shopName=" + shopName + " error=" + e);
            return BaseResponse.FailedResponse("Trigger ai score failed");
        }
    }

    private TranslateTaskV3DO findTaskByIdAcrossStatuses(String taskId) {
        if (StringUtils.isEmpty(taskId)) {
            return null;
        }
        List<Integer> statuses = List.of(0, 1, 2, 3, 4);
        for (Integer status : statuses) {
            List<TranslateTaskV3DO> tasks = translateTaskV3CosmosRepo.listByStatus(status);
            if (tasks == null || tasks.isEmpty()) {
                continue;
            }
            for (TranslateTaskV3DO task : tasks) {
                if (task != null && taskId.equals(task.getId())) {
                    return task;
                }
            }
        }
        return null;
    }

    public void processInitialTasksV3() {
        List<TranslateTaskV3DO> tasks = translateTaskV3CosmosRepo.listByStatus(0);
        if (tasks.isEmpty()) {
            return;
        }
        tasks.sort(Comparator.comparing(t -> "manual".equals(t.getTaskType()) ? 0 : 1));
        for (TranslateTaskV3DO task : tasks) {
            if (!processingInitialTaskIds.add(task.getId())) {
                continue;
            }
            try {
                initialToTranslateTaskV3(task);
            } catch (Exception e) {
                TraceReporterHolder.report("TranslateV3Service.processInitialTasksV3",
                        "FatalException process initial task failed, taskId=" + task.getId() + " error=" + e);
            } finally {
                processingInitialTaskIds.remove(task.getId());
            }
        }
    }

    public void processTranslateTasksV3() {
        List<TranslateTaskV3DO> tasks = translateTaskV3CosmosRepo.listByStatus(1);
        if (tasks.isEmpty()) {
            return;
        }
        tasks.sort(Comparator.comparing(t -> "manual".equals(t.getTaskType()) ? 0 : 1));
        for (TranslateTaskV3DO task : tasks) {
            if (!processingTranslateTaskIds.add(task.getId())) {
                continue;
            }
            try {
                translateEachTaskV3(task);
            } catch (Exception e) {
                TraceReporterHolder.report("TranslateV3Service.processTranslateTasksV3",
                        "FatalException process translate task failed, taskId=" + task.getId() + " error=" + e);
            } finally {
                processingTranslateTaskIds.remove(task.getId());
            }
        }
    }

    public void processSaveTasksV3() {
        List<TranslateTaskV3DO> tasks = translateTaskV3CosmosRepo.listByStatus(2);
        if (tasks.isEmpty()) {
            return;
        }
        tasks.sort(Comparator.comparing(t -> "manual".equals(t.getTaskType()) ? 0 : 1));
        for (TranslateTaskV3DO task : tasks) {
            if (!processingSaveTaskIds.add(task.getId())) {
                continue;
            }
            try {
                saveToShopifyV3(task);
            } catch (Exception e) {
                TraceReporterHolder.report("TranslateV3Service.processSaveTasksV3",
                        "FatalException process save task failed, taskId=" + task.getId() + " error=" + e);
            } finally {
                processingSaveTaskIds.remove(task.getId());
            }
        }
    }

    public void processVerifyTasksV3() {
        List<TranslateTaskV3DO> tasks = translateTaskV3CosmosRepo.listByStatus(6);
        if (tasks.isEmpty()) {
            return;
        }
        tasks.sort(Comparator.comparing(t -> "manual".equals(t.getTaskType()) ? 0 : 1));
        for (TranslateTaskV3DO task : tasks) {
            if (isVerifyFinal(task)) {
                continue;
            }
            if (!processingVerifyTaskIds.add(task.getId())) {
                continue;
            }
            try {
                verifySavedDataV3(task);
            } catch (Exception e) {
                TraceReporterHolder.report("TranslateV3Service.processVerifyTasksV3",
                        "FatalException process verify task failed, taskId=" + task.getId() + " error=" + e);
            } finally {
                processingVerifyTaskIds.remove(task.getId());
            }
        }
    }

    @PreDestroy
    public void flushPendingProgressOnShutdown() {
        for (Map.Entry<String, PendingProgress> entry : pendingProgressMap.entrySet()) {
            flushPendingProgress(entry.getKey(), entry.getValue());
        }
    }

    // 兼容旧调用
    public void initialToTranslateTask(InitialTaskV2DO initialTaskV2DO) {
        TranslateTaskV3DO task = new TranslateTaskV3DO();
        task.setId(String.valueOf(initialTaskV2DO.getId()));
        task.setShopName(initialTaskV2DO.getShopName());
        task.setSource(initialTaskV2DO.getSource());
        task.setTarget(initialTaskV2DO.getTarget());
        task.setStatus(initialTaskV2DO.getStatus());
        task.setTaskType(initialTaskV2DO.getTaskType());
        task.setAiModel(initialTaskV2DO.getAiModel());
        task.setCover(initialTaskV2DO.isCover());
        task.setHandle(initialTaskV2DO.isHandle());
        task.setModuleList(initialTaskV2DO.getModuleList());
        task.setSessionId(initialTaskV2DO.getShopName() + ":" + initialTaskV2DO.getId());
        initialToTranslateTaskV3(task);
    }

    public void initialToTranslateTaskV3(TranslateTaskV3DO task) {
        String taskId = task.getId();
        translateTaskMonitorV3RedisService.setPhase(taskId, "INIT_READING_SHOPIFY");

        UsersDO userDO = usersService.getUserByName(task.getShopName());
        if (userDO == null || StringUtils.isEmpty(userDO.getAccessToken())) {
            translateTaskMonitorV3RedisService.setPhase(taskId, "INIT_FAILED_NO_USER");
            return;
        }

        String primaryLocaleData = shopifyService.getShopifyData(task.getShopName(), userDO.getAccessToken(),
                com.bogda.common.contants.TranslateConstants.API_VERSION_LAST, ShopifyRequestUtils.getShopLanguageQuery());
        String primaryLocale = getPrimaryLocaleFromShopifyData(primaryLocaleData);
        if (primaryLocale != null && !primaryLocale.equals(task.getSource())) {
            translateTaskMonitorV3RedisService.setPhase(taskId, "INIT_STOPPED_PRIMARY_LOCALE_MISMATCH");
            translateTaskV3CosmosRepo.patchStatus(taskId, task.getShopName(), 4);
            return;
        }

        List<String> moduleList = JsonUtils.jsonToObject(task.getModuleList(), new com.fasterxml.jackson.core.type.TypeReference<>() {
        });
        if (CollectionUtils.isEmpty(moduleList)) {
            translateTaskMonitorV3RedisService.setPhase(taskId, "INIT_DONE_EMPTY_MODULES");
            translateTaskV3CosmosRepo.patchStatus(taskId, task.getShopName(), 1);
            return;
        }

        int totalCount = 0;
        int totalChars = 0;
        Map<String, Object> moduleSummary = new HashMap<>();
        for (String module : moduleList) {
            ModuleInitSummary summary = dumpModuleToBlob(task, userDO, module);
            totalCount += summary.totalCount;
            totalChars += summary.totalChars;
            moduleSummary.put(module, summary.toMap());
        }

        translateTaskMonitorV3RedisService.setTotalCount(taskId, totalCount);
        translateTaskMonitorV3RedisService.setPhase(taskId, "INIT_DONE");
        translateTaskV3BlobRepo.writeJson(blobPath(taskId, "chunks/manifest.json"), moduleSummary);

        Map<String, Object> checkpoint = new HashMap<>();
        checkpoint.put("phase", "INIT_DONE");
        checkpoint.put("modules", moduleList);
        checkpoint.put("updatedAt", Instant.now().toString());

        Map<String, Object> metrics = new HashMap<>();
        metrics.put("totalCount", totalCount);
        metrics.put("estimatedCreditsRaw", totalChars);
        metrics.put("translatedCount", 0);
        metrics.put("savedCount", 0);
        metrics.put("usedToken", 0);

        translateTaskV3CosmosRepo.patchCheckpointAndMetrics(taskId, task.getShopName(), checkpoint, metrics);
        translateTaskV3CosmosRepo.patchStatus(taskId, task.getShopName(), 1);
    }

    public void translateEachTask(InitialTaskV2DO initialTaskV2DO) {
        TranslateTaskV3DO task = new TranslateTaskV3DO();
        task.setId(String.valueOf(initialTaskV2DO.getId()));
        task.setShopName(initialTaskV2DO.getShopName());
        task.setSource(initialTaskV2DO.getSource());
        task.setTarget(initialTaskV2DO.getTarget());
        task.setStatus(initialTaskV2DO.getStatus());
        task.setTaskType(initialTaskV2DO.getTaskType());
        task.setAiModel(initialTaskV2DO.getAiModel());
        task.setCover(initialTaskV2DO.isCover());
        task.setHandle(initialTaskV2DO.isHandle());
        task.setModuleList(initialTaskV2DO.getModuleList());
        task.setSessionId(initialTaskV2DO.getShopName() + ":" + initialTaskV2DO.getId());
        translateEachTaskV3(task);
    }

    public void translateEachTaskV3(TranslateTaskV3DO task) {
        String taskId = task.getId();
        String shopName = task.getShopName();
        String target = task.getTarget();
        String source = task.getSource();

        translateTaskMonitorV3RedisService.setPhase(taskId, "TRANSLATE_RUNNING");

        UsersDO userDO = usersService.getUserByName(shopName);
        if (userDO == null || StringUtils.isEmpty(userDO.getAccessToken())) {
            translateTaskMonitorV3RedisService.setPhase(taskId, "TRANSLATE_FAILED_NO_USER");
            return;
        }

        String primaryLocaleData = shopifyService.getShopifyData(shopName, userDO.getAccessToken(),
                TranslateConstants.API_VERSION_LAST, ShopifyRequestUtils.getShopLanguageQuery());
        String primaryLocale = getPrimaryLocaleFromShopifyData(primaryLocaleData);
        if (primaryLocale != null && !primaryLocale.equals(source)) {
            translateTaskMonitorV3RedisService.setPhase(taskId, "TRANSLATE_STOPPED_PRIMARY_LOCALE_MISMATCH");
            translateTaskV3CosmosRepo.patchStatus(taskId, shopName, 4);
            return;
        }

        List<String> modules = JsonUtils.jsonToObject(task.getModuleList(), new TypeReference<List<String>>() {
        });
        if (CollectionUtils.isEmpty(modules)) {
            translateTaskMonitorV3RedisService.setPhase(taskId, "TRANSLATE_DONE_EMPTY_MODULES");
            translateTaskV3CosmosRepo.patchStatus(taskId, shopName, 2);
            return;
        }

        Map<String, GlossaryDO> glossaryMap = glossaryService.getGlossaryDoByShopName(shopName, target);
        int maxToken = userTokenService.getMaxToken(shopName);
        int currentUsedToken = userTokenService.getUsedToken(shopName);
        Map<String, Object> baseMetrics = task.getMetrics() == null ? new HashMap<>() : new HashMap<>(task.getMetrics());
        int translatedTotal = getIntMetric(baseMetrics, "translatedCount");
        int usedTokenTotal = getIntMetric(baseMetrics, "usedToken");
        int processedChunkCount = 0;

        boolean stoppedByTokenLimit = false;
        String currentModule = null;
        String currentChunkPath = null;

        for (String module : modules) {
            currentModule = module;
            List<String> chunkPaths = translateTaskV3BlobRepo.listBlobPaths(blobPath(taskId, "chunks/" + module + "/"));
            if (chunkPaths.isEmpty()) {
                continue;
            }
            for (String chunkPath : chunkPaths) {
                currentChunkPath = chunkPath;
                if (currentUsedToken >= maxToken) {
                    stoppedByTokenLimit = true;
                    break;
                }
                List<Map<String, Object>> rows = translateTaskV3BlobRepo.readJsonRows(chunkPath);
                if (rows.isEmpty()) {
                    continue;
                }
                TranslateChunkResult result = translateChunkRows(rows, module, task, glossaryMap, maxToken, currentUsedToken);
                currentUsedToken = result.currentUsedToken;
                translateTaskMonitorV3RedisService.incrementBy(taskId, result.translatedDelta, 0, result.usedTokenDelta);

                // v3 使用同一份 chunk 文件存储翻译前后数据：翻译完成后直接覆盖当前 chunk。
                translateTaskV3BlobRepo.writeJson(chunkPath, rows);

                translatedTotal += result.translatedDelta;
                usedTokenTotal += result.usedTokenDelta;
                processedChunkCount++;
                cachePendingProgress(taskId, shopName, currentModule, currentChunkPath, translatedTotal, usedTokenTotal);
                if (processedChunkCount % PROGRESS_FLUSH_INTERVAL_CHUNKS == 0) {
                    flushPendingProgress(taskId);
                }

                if (result.hitTokenLimit) {
                    stoppedByTokenLimit = true;
                    break;
                }
            }
            if (stoppedByTokenLimit) {
                break;
            }
        }

        baseMetrics.put("translatedCount", translatedTotal);
        baseMetrics.put("usedToken", usedTokenTotal);
        baseMetrics.put("updatedAt", Instant.now().toString());

        Map<String, Object> checkpoint = new HashMap<>();
        checkpoint.put("updatedAt", Instant.now().toString());
        checkpoint.put("module", currentModule);
        checkpoint.put("chunkPath", currentChunkPath);

        if (stoppedByTokenLimit) {
            checkpoint.put("phase", "TRANSLATE_STOPPED_TOKEN_LIMIT");
            translateTaskMonitorV3RedisService.setPhase(taskId, "TRANSLATE_STOPPED_TOKEN_LIMIT");
            translateTaskV3CosmosRepo.patchCheckpointAndMetrics(taskId, shopName, checkpoint, baseMetrics);
            translateTaskV3CosmosRepo.patchStatus(taskId, shopName, 3);
            translatesService.updateTranslateStatus(shopName, 3, target, source);
            pendingProgressMap.remove(taskId);
            return;
        }

        checkpoint.put("phase", "TRANSLATE_DONE");
        translateTaskMonitorV3RedisService.setPhase(taskId, "TRANSLATE_DONE");
        translateTaskV3CosmosRepo.patchCheckpointAndMetrics(taskId, shopName, checkpoint, baseMetrics);
        translateTaskV3CosmosRepo.patchStatus(taskId, shopName, 2);
        translatesService.updateTranslateStatus(shopName, 1, target, source);
        pendingProgressMap.remove(taskId);

        try {
            generateQaReport(taskId, modules);
        } catch (Exception e) {
            TraceReporterHolder.report("TranslateV3Service.generateQaReport",
                    "FatalException generate qa report failed, taskId=" + taskId + " error=" + e);
        }
        try {
            generateAiScoreReport(task, modules);
        } catch (Exception e) {
            TraceReporterHolder.report("TranslateV3Service.generateAiScoreReport",
                    "FatalException generate ai score report failed, taskId=" + taskId + " error=" + e);
        }
    }

    public void saveToShopify(InitialTaskV2DO initialTaskV2DO) {
        if (initialTaskV2DO == null || StringUtils.isEmpty(initialTaskV2DO.getShopName())) {
            return;
        }
        TranslateTaskV3DO v3Task = findSavePendingTask(initialTaskV2DO);
        if (v3Task != null) {
            saveToShopifyV3(v3Task);
            return;
        }
        // 兼容未迁移的 v2 老任务。
        translateV2Service.saveToShopify(initialTaskV2DO);
    }

    public void retrySaveAllFailedTasks() {
        translateV2Service.retrySaveAllFailedTasks();
    }

    public void deleteToShopify() {
        translateV2Service.deleteToShopify();
    }

    public void sendManualEmail(InitialTaskV2DO initialTaskV2DO) {
        translateV2Service.sendManualEmail(initialTaskV2DO);
    }

    public boolean autoTranslateV3(String shopName, String source, String target) {
        return translateV2Service.autoTranslateV2(shopName, source, target);
    }

    public void cleanTask(InitialTaskV2DO initialTaskV2DO) {
        translateV2Service.cleanTask(initialTaskV2DO);
    }

    public void cleanDeleteTask(InitialTaskV2DO initialTaskV2DO) {
        translateV2Service.cleanDeleteTask(initialTaskV2DO);
    }

    private ModuleInitSummary dumpModuleToBlob(TranslateTaskV3DO task, UsersDO userDO, String module) {
        final int chunkSize = 200;
        List<Map<String, Object>> currentChunk = new ArrayList<>();
        final int[] chunkIndex = {0};
        final int[] totalCount = {0};
        final int[] totalChars = {0};

        shopifyService.rotateAllShopifyGraph(task.getShopName(), module, userDO.getAccessToken(), 250, task.getTarget(), "",
                null,
                node -> {
                    if (node == null || CollectionUtils.isEmpty(node.getTranslatableContent())) {
                        return;
                    }
                    for (ShopifyTranslationsResponse.Node.TranslatableContent content : node.getTranslatableContent()) {
                        if (!shouldStoreContent(content, node.getTranslations(), task.isCover(), task.isHandle())) {
                            continue;
                        }
                        Map<String, Object> row = new HashMap<>();
                        row.put("resourceId", node.getResourceId());
                        row.put("module", module);
                        row.put("nodeKey", content.getKey());
                        row.put("type", content.getType());
                        row.put("digest", content.getDigest());
                        row.put("sourceValue", content.getValue());
                        row.put("isHtml", JsoupUtils.isHtml(content.getValue()));
                        row.put("isJson", JsonUtils.isJson(content.getValue()));
                        currentChunk.add(row);

                        totalCount[0]++;
                        totalChars[0] += content.getValue() == null ? 0 : content.getValue().length();
                        if (currentChunk.size() >= chunkSize) {
                            translateTaskV3BlobRepo.writeJson(
                                    blobPath(task.getId(), "chunks/" + module + "/chunk-" + chunkIndex[0] + ".json"),
                                    new ArrayList<>(currentChunk));
                            currentChunk.clear();
                            chunkIndex[0]++;
                        }
                    }
                },
                after -> {
                });

        if (!currentChunk.isEmpty()) {
            translateTaskV3BlobRepo.writeJson(
                    blobPath(task.getId(), "chunks/" + module + "/chunk-" + chunkIndex[0] + ".json"),
                    currentChunk);
            chunkIndex[0]++;
        }
        return new ModuleInitSummary(totalCount[0], totalChars[0], chunkIndex[0]);
    }

    private TranslateChunkResult translateChunkRows(List<Map<String, Object>> rows,
                                                    String module,
                                                    TranslateTaskV3DO task,
                                                    Map<String, GlossaryDO> glossaryMap,
                                                    int maxToken,
                                                    int currentUsedToken) {
        int translatedDelta = 0;
        int usedTokenDelta = 0;

        List<Integer> batchIndexes = new ArrayList<>();
        Map<Integer, String> batchSourceMap = new HashMap<>();
        int batchTokenEstimate = 0;

        for (int i = 0; i < rows.size(); i++) {
            Map<String, Object> row = rows.get(i);
            String sourceValue = asString(row.get("sourceValue"));
            if (StringUtils.isEmpty(sourceValue)) {
                continue;
            }
            if (asBoolean(row.get("translated")) && !StringUtils.isEmpty(asString(row.get("targetValue")))) {
                // 断点续跑场景：已翻译行直接跳过，避免重启后重复翻译。
                continue;
            }
            if (currentUsedToken >= maxToken) {
                return new TranslateChunkResult(translatedDelta, usedTokenDelta, currentUsedToken, true);
            }

            boolean isHtml = asBoolean(row.get("isHtml"));
            boolean isJson = asBoolean(row.get("isJson"));
            String type = asString(row.get("type"));
            String nodeKey = asString(row.get("nodeKey"));

            if (universalTranslateService.shouldUseStructuredTranslation(sourceValue, isJson, isHtml, type)) {
                UniversalTranslateService.TranslateResult result = universalTranslateService.translateStructuredContent(
                        sourceValue,
                        task.getTarget(),
                        glossaryMap,
                        task.getAiModel(),
                        module,
                        task.getShopName(),
                        resolveSessionId(task),
                        isJson,
                        isHtml
                );
                String targetValue = result.getTranslatedContent();
                int tokenUsed = result.getUsedToken();

                if (isJson && !StringUtils.isEmpty(targetValue) && !JsonUtils.isJson(targetValue)) {
                    row.put("targetValue", sourceValue);
                    row.put("translated", false);
                    row.put("translateError", "INVALID_JSON_TARGET");
                    row.remove("translatedAt");
                } else if (isHtml && !StringUtils.isEmpty(targetValue) && !JsoupUtils.isHtml(targetValue)) {
                    row.put("targetValue", sourceValue);
                    row.put("translated", false);
                    row.put("translateError", "INVALID_HTML_TARGET");
                    row.remove("translatedAt");
                } else {
                    row.put("targetValue", targetValue);
                    row.put("translated", true);
                    row.put("translatedAt", Instant.now().toString());
                    row.remove("translateError");
                    translatedDelta++;
                }

                usedTokenDelta += tokenUsed;
                currentUsedToken = userTokenService.addUsedToken(task.getShopName(), tokenUsed);
                continue;
            }

            String batchValue = sourceValue;
            if (TranslateConstants.LOWERCASE_HANDLE.equals(nodeKey) && batchValue != null) {
                batchValue = batchValue.replace('-', ' ');
            }

            int estimatedToken = ALiYunTranslateIntegration.calculateBaiLianToken(batchValue);
            if (batchTokenEstimate > 0 && batchTokenEstimate + estimatedToken > 600) {
                TranslateChunkResult result = flushBatchRows(rows, batchIndexes, batchSourceMap, module, task, glossaryMap, currentUsedToken);
                translatedDelta += result.translatedDelta;
                usedTokenDelta += result.usedTokenDelta;
                currentUsedToken = result.currentUsedToken;
                if (result.hitTokenLimit || currentUsedToken >= maxToken) {
                    return new TranslateChunkResult(translatedDelta, usedTokenDelta, currentUsedToken, true);
                }
                batchIndexes.clear();
                batchSourceMap.clear();
                batchTokenEstimate = 0;
            }

            batchIndexes.add(i);
            batchSourceMap.put(i, batchValue);
            batchTokenEstimate += estimatedToken;
        }

        if (!batchIndexes.isEmpty() && currentUsedToken < maxToken) {
            TranslateChunkResult result = flushBatchRows(rows, batchIndexes, batchSourceMap, module, task, glossaryMap, currentUsedToken);
            translatedDelta += result.translatedDelta;
            usedTokenDelta += result.usedTokenDelta;
            currentUsedToken = result.currentUsedToken;
            if (result.hitTokenLimit) {
                return new TranslateChunkResult(translatedDelta, usedTokenDelta, currentUsedToken, true);
            }
        }
        return new TranslateChunkResult(translatedDelta, usedTokenDelta, currentUsedToken, false);
    }

    private TranslateChunkResult flushBatchRows(List<Map<String, Object>> rows,
                                                List<Integer> batchIndexes,
                                                Map<Integer, String> batchSourceMap,
                                                String module,
                                                TranslateTaskV3DO task,
                                                Map<String, GlossaryDO> glossaryMap,
                                                int currentUsedToken) {
        if (batchIndexes.isEmpty()) {
            return new TranslateChunkResult(0, 0, currentUsedToken, false);
        }
        UniversalTranslateService.BatchTranslateResult batchResult = universalTranslateService.translateBatchContent(
                batchSourceMap,
                task.getTarget(),
                glossaryMap,
                task.getAiModel(),
                module,
                task.getShopName(),
                resolveSessionId(task)
        );
        Map<Integer, String> translatedMap = batchResult.getTranslatedMap();
        int translatedCount = 0;
        for (Integer rowIndex : batchIndexes) {
            Map<String, Object> row = rows.get(rowIndex);
            String sourceValue = asString(row.get("sourceValue"));
            String translatedValue = translatedMap.get(rowIndex);
            if (StringUtils.isEmpty(translatedValue)) {
                translatedValue = sourceValue;
            }
            row.put("targetValue", translatedValue);
            row.put("translated", true);
            row.put("translatedAt", Instant.now().toString());
            translatedCount++;
        }
        int tokenUsed = batchResult.getUsedToken();
        currentUsedToken = userTokenService.addUsedToken(task.getShopName(), tokenUsed);
        return new TranslateChunkResult(translatedCount, tokenUsed, currentUsedToken, false);
    }

    private int getIntMetric(Map<String, Object> metrics, String key) {
        if (metrics == null || key == null) {
            return 0;
        }
        Object value = metrics.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String) {
            String str = (String) value;
            try {
                return Integer.parseInt(str);
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static String resolveSessionId(TranslateTaskV3DO task) {
        if (task == null) {
            return null;
        }
        if (task.getSessionId() != null && !task.getSessionId().isEmpty()) {
            return task.getSessionId();
        }
        if (task.getShopName() == null || task.getId() == null) {
            return null;
        }
        return task.getShopName() + ":" + task.getId();
    }

    private static boolean asBoolean(Object value) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof String) {
            String str = (String) value;
            return "true".equalsIgnoreCase(str);
        }
        return false;
    }

    private TranslateTaskV3DO findSavePendingTask(InitialTaskV2DO initialTaskV2DO) {
        List<TranslateTaskV3DO> taskList = translateTaskV3CosmosRepo.listByShopSource(
                initialTaskV2DO.getShopName(), initialTaskV2DO.getSource());
        if (CollectionUtils.isEmpty(taskList)) {
            return null;
        }
        String target = initialTaskV2DO.getTarget();
        for (TranslateTaskV3DO task : taskList) {
            if (task == null || task.getStatus() == null) {
                continue;
            }
            if (task.getStatus() != 2) {
                continue;
            }
            if (target != null && !target.equals(task.getTarget())) {
                continue;
            }
            return task;
        }
        return null;
    }

    private void saveToShopifyV3(TranslateTaskV3DO task) {
        if (task == null || StringUtils.isEmpty(task.getId()) || StringUtils.isEmpty(task.getShopName())) {
            return;
        }
        String taskId = task.getId();
        String shopName = task.getShopName();
        translateTaskMonitorV3RedisService.setPhase(taskId, "SAVE_RUNNING");

        UsersDO userDO = usersService.getUserByName(shopName);
        if (userDO == null || StringUtils.isEmpty(userDO.getAccessToken())) {
            translateTaskMonitorV3RedisService.setPhase(taskId, "SAVE_FAILED_NO_USER");
            return;
        }
        String token = userDO.getAccessToken();

        List<String> modules = JsonUtils.jsonToObject(task.getModuleList(), new TypeReference<List<String>>() {
        });
        if (CollectionUtils.isEmpty(modules)) {
            markSaveDone(task, 0);
            return;
        }

        Map<String, Object> metrics = task.getMetrics() == null ? new HashMap<>() : new HashMap<>(task.getMetrics());
        int savedTotal = recoverSavedCountFromProgress(taskId, modules);
        int persistedSaved = getIntMetric(metrics, "savedCount");
        if (persistedSaved > savedTotal) {
            savedTotal = persistedSaved;
        }
        metrics.put("savedCount", savedTotal);
        metrics.put("updatedAt", Instant.now().toString());
        boolean hasSaveFailure = false;
        String currentModule = null;
        String currentChunkPath = null;

        for (String module : modules) {
            currentModule = module;
            List<String> chunkPaths = translateTaskV3BlobRepo.listBlobPaths(blobPath(taskId, "chunks/" + module + "/"));
            if (chunkPaths.isEmpty()) {
                continue;
            }
            for (String chunkPath : chunkPaths) {
                if (!isChunkDataPath(chunkPath)) {
                    continue;
                }
                currentChunkPath = chunkPath;
                List<Map<String, Object>> rows = translateTaskV3BlobRepo.readJsonRows(chunkPath);
                if (rows.isEmpty()) {
                    continue;
                }
                Set<Integer> savedRowIndexes = loadSavedRowIndexes(chunkPath);
                Map<String, List<Integer>> resourceToIndexes = groupUnsavedRowsByResource(rows, savedRowIndexes);
                if (resourceToIndexes.isEmpty()) {
                    continue;
                }

                for (Map.Entry<String, List<Integer>> entry : resourceToIndexes.entrySet()) {
                    String resourceId = entry.getKey();
                    List<Integer> rowIndexes = entry.getValue();
                    if (StringUtils.isEmpty(resourceId) || CollectionUtils.isEmpty(rowIndexes)) {
                        continue;
                    }
                    List<Integer> savedIndexes = saveOneResourceRows(shopName, token, task.getTarget(), rows, rowIndexes, resourceId);
                    if (savedIndexes == null) {
                        hasSaveFailure = true;
                        continue;
                    }
                    int savedCount = savedIndexes.size();
                    savedRowIndexes.addAll(savedIndexes);
                    writeSaveProgress(chunkPath, savedRowIndexes, rows.size());
                    savedTotal += savedCount;
                    translateTaskMonitorV3RedisService.incrementBy(taskId, 0, savedCount, 0);
                    metrics.put("savedCount", savedTotal);
                    metrics.put("updatedAt", Instant.now().toString());
                    Map<String, Object> checkpoint = buildSaveCheckpoint("SAVE_RUNNING", currentModule, currentChunkPath, resourceId);
                    translateTaskV3CosmosRepo.patchCheckpointAndMetrics(taskId, shopName, checkpoint, metrics);
                }
            }
        }

        if (hasSaveFailure) {
            translateTaskMonitorV3RedisService.setPhase(taskId, "SAVE_PARTIAL_FAILED");
            Map<String, Object> checkpoint = buildSaveCheckpoint("SAVE_PARTIAL_FAILED", currentModule, currentChunkPath, null);
            metrics.put("savedCount", savedTotal);
            metrics.put("updatedAt", Instant.now().toString());
            translateTaskV3CosmosRepo.patchCheckpointAndMetrics(taskId, shopName, checkpoint, metrics);
            return;
        }
        markSaveDone(task, savedTotal);
    }

    private Map<String, List<Integer>> groupUnsavedRowsByResource(List<Map<String, Object>> rows, Set<Integer> savedRowIndexes) {
        Map<String, List<Integer>> grouped = new LinkedHashMap<>();
        for (int i = 0; i < rows.size(); i++) {
            if (savedRowIndexes != null && savedRowIndexes.contains(i)) {
                continue;
            }
            Map<String, Object> row = rows.get(i);
            if (!asBoolean(row.get("translated"))) {
                continue;
            }
            if (asBoolean(row.get("savedToShopify"))) {
                continue;
            }
            String targetValue = asString(row.get("targetValue"));
            if (StringUtils.isEmpty(targetValue)) {
                continue;
            }
            String resourceId = asString(row.get("resourceId"));
            if (StringUtils.isEmpty(resourceId)) {
                continue;
            }
            grouped.computeIfAbsent(resourceId, k -> new ArrayList<>()).add(i);
        }
        return grouped;
    }

    private List<Integer> saveOneResourceRows(String shopName,
                                              String token,
                                              String target,
                                              List<Map<String, Object>> rows,
                                              List<Integer> rowIndexes,
                                              String resourceId) {
        List<Integer> savedIndexes = new ArrayList<>();
        for (int from = 0; from < rowIndexes.size(); ) {
            int batchSize = shopifyRateLimitService.getRecommendedSaveBatchSize(shopName);
            int to = Math.min(from + batchSize, rowIndexes.size());
            List<Integer> batchIndexes = rowIndexes.subList(from, to);

            ShopifyTranslationsResponse.Node node = new ShopifyTranslationsResponse.Node();
            List<ShopifyTranslationsResponse.Node.Translation> translations = new ArrayList<>();
            for (Integer rowIndex : batchIndexes) {
                Map<String, Object> row = rows.get(rowIndex);
                ShopifyTranslationsResponse.Node.Translation translation = new ShopifyTranslationsResponse.Node.Translation();
                translation.setLocale(target);
                translation.setKey(asString(row.get("nodeKey")));
                translation.setTranslatableContentDigest(asString(row.get("digest")));
                translation.setValue(asString(row.get("targetValue")));
                translations.add(translation);
            }
            node.setTranslations(translations);
            node.setResourceId(resourceId);

            String response = shopifyService.saveDataWithRateLimit(shopName, token, node);
            if (response == null || !response.contains("\"userErrors\":[]")) {
                shopifyRateLimitService.onSaveOutcome(shopName, false, isThrottledResponse(response));
                TraceReporterHolder.report("TranslateV3Service.saveToShopifyV3",
                        "Save resource failed, task for shop=" + shopName + " resourceId=" + resourceId
                                + " batchSize=" + batchIndexes.size() + " response=" + response);
                return null;
            }

            savedIndexes.addAll(batchIndexes);
            shopifyRateLimitService.onSaveOutcome(shopName, true, false);
            from = to;
        }
        return savedIndexes;
    }

    private Set<Integer> loadSavedRowIndexes(String chunkPath) {
        String saveProgressPath = saveProgressPath(chunkPath);
        String raw = translateTaskV3BlobRepo.readText(saveProgressPath);
        if (StringUtils.isEmpty(raw)) {
            return new java.util.HashSet<>();
        }
        Map<String, Object> progress = JsonUtils.jsonToObjectWithNull(raw, new TypeReference<Map<String, Object>>() {
        });
        if (progress == null || progress.isEmpty()) {
            return new java.util.HashSet<>();
        }
        Object savedRowsObj = progress.get("savedRows");
        if (!(savedRowsObj instanceof List<?>)) {
            return new java.util.HashSet<>();
        }
        List<?> savedRows = (List<?>) savedRowsObj;
        Set<Integer> indexes = new java.util.HashSet<>();
        for (Object one : savedRows) {
            if (one instanceof Number) {
                indexes.add(((Number) one).intValue());
            } else if (one instanceof String) {
                try {
                    indexes.add(Integer.parseInt((String) one));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return indexes;
    }

    private int recoverSavedCountFromProgress(String taskId, List<String> modules) {
        if (StringUtils.isEmpty(taskId) || CollectionUtils.isEmpty(modules)) {
            return 0;
        }
        int total = 0;
        for (String module : modules) {
            List<String> chunkPaths = translateTaskV3BlobRepo.listBlobPaths(blobPath(taskId, "chunks/" + module + "/"));
            if (chunkPaths.isEmpty()) {
                continue;
            }
            for (String chunkPath : chunkPaths) {
                if (!isChunkDataPath(chunkPath)) {
                    continue;
                }
                total += loadSavedRowIndexes(chunkPath).size();
            }
        }
        return total;
    }

    private void writeSaveProgress(String chunkPath, Set<Integer> savedRowIndexes, int rowCount) {
        Map<String, Object> progress = new LinkedHashMap<>();
        progress.put("version", 1);
        progress.put("chunkPath", chunkPath);
        progress.put("rowCount", rowCount);
        progress.put("savedCount", savedRowIndexes == null ? 0 : savedRowIndexes.size());
        progress.put("savedRows", savedRowIndexes == null ? new ArrayList<>() : new ArrayList<>(savedRowIndexes));
        progress.put("updatedAt", Instant.now().toString());
        translateTaskV3BlobRepo.writeJson(saveProgressPath(chunkPath), progress);
    }

    private String saveProgressPath(String chunkPath) {
        return chunkPath + SAVE_PROGRESS_SUFFIX;
    }

    private boolean isThrottledResponse(String response) {
        if (response == null || response.isEmpty()) {
            return false;
        }
        return response.contains("THROTTLED")
                || response.contains("throttled")
                || response.contains("Too many requests");
    }

    private Map<String, Object> buildSaveCheckpoint(String phase, String module, String chunkPath, String resourceId) {
        Map<String, Object> checkpoint = new HashMap<>();
        checkpoint.put("phase", phase);
        checkpoint.put("module", module);
        checkpoint.put("chunkPath", chunkPath);
        if (resourceId != null) {
            checkpoint.put("resourceId", resourceId);
        }
        checkpoint.put("updatedAt", Instant.now().toString());
        return checkpoint;
    }

    private void markSaveDone(TranslateTaskV3DO task, int savedTotal) {
        String taskId = task.getId();
        String shopName = task.getShopName();
        Map<String, Object> metrics = task.getMetrics() == null ? new HashMap<>() : new HashMap<>(task.getMetrics());
        metrics.put("savedCount", savedTotal);
        metrics.put("updatedAt", Instant.now().toString());
        Map<String, Object> checkpoint = buildSaveCheckpoint("SAVE_DONE", null, null, null);
        translateTaskMonitorV3RedisService.setPhase(taskId, "SAVE_DONE");
        translateTaskV3CosmosRepo.patchCheckpointAndMetrics(taskId, shopName, checkpoint, metrics);
        translateTaskV3CosmosRepo.patchStatus(taskId, shopName, 6);
        translatesService.updateTranslateStatus(shopName, 2, task.getTarget(), task.getSource());
    }

    private boolean isVerifyFinal(TranslateTaskV3DO task) {
        if (task == null || task.getCheckpoint() == null) {
            return false;
        }
        Object phase = task.getCheckpoint().get("phase");
        return "VERIFY_DONE".equals(phase) || "VERIFY_MISMATCH_DONE".equals(phase);
    }

    private void verifySavedDataV3(TranslateTaskV3DO task) {
        String taskId = task.getId();
        String shopName = task.getShopName();
        translateTaskMonitorV3RedisService.setPhase(taskId, "VERIFY_RUNNING");

        UsersDO userDO = usersService.getUserByName(shopName);
        if (userDO == null || StringUtils.isEmpty(userDO.getAccessToken())) {
            translateTaskMonitorV3RedisService.setPhase(taskId, "VERIFY_FAILED_NO_USER");
            return;
        }
        String token = userDO.getAccessToken();

        List<String> modules = JsonUtils.jsonToObject(task.getModuleList(), new TypeReference<List<String>>() {
        });
        if (CollectionUtils.isEmpty(modules)) {
            return;
        }

        int verifyTotal = 0;
        int verifyMatched = 0;
        int verifyMismatch = 0;
        int verifyMissing = 0;
        List<Map<String, Object>> mismatchSamples = new ArrayList<>();

        for (String module : modules) {
            List<String> chunkPaths = translateTaskV3BlobRepo.listBlobPaths(blobPath(taskId, "chunks/" + module + "/"));
            if (chunkPaths.isEmpty()) {
                continue;
            }
            for (String chunkPath : chunkPaths) {
                if (!isChunkDataPath(chunkPath)) {
                    continue;
                }
                List<Map<String, Object>> rows = translateTaskV3BlobRepo.readJsonRows(chunkPath);
                if (rows.isEmpty()) {
                    continue;
                }
                Set<Integer> savedRowIndexes = loadSavedRowIndexes(chunkPath);
                if (savedRowIndexes.isEmpty()) {
                    continue;
                }
                Set<String> resourceIds = collectSavedResourceIds(rows, savedRowIndexes);
                if (resourceIds.isEmpty()) {
                    continue;
                }

                Map<String, Map<String, String>> actualMap = fetchShopifyTranslationsByResourceIds(
                        shopName, token, module, task.getTarget(), new ArrayList<>(resourceIds));

                for (Integer rowIndex : savedRowIndexes) {
                    if (rowIndex == null || rowIndex < 0 || rowIndex >= rows.size()) {
                        continue;
                    }
                    Map<String, Object> row = rows.get(rowIndex);
                    String resourceId = asString(row.get("resourceId"));
                    String nodeKey = asString(row.get("nodeKey"));
                    String expectedValue = asString(row.get("targetValue"));
                    if (StringUtils.isEmpty(resourceId) || StringUtils.isEmpty(nodeKey) || StringUtils.isEmpty(expectedValue)) {
                        continue;
                    }
                    verifyTotal++;
                    Map<String, String> keyMap = actualMap.get(resourceId);
                    if (keyMap == null) {
                        verifyMissing++;
                        addVerifyMismatchSample(mismatchSamples, module, chunkPath, rowIndex, resourceId, nodeKey,
                                expectedValue, null, "RESOURCE_NOT_FOUND");
                        continue;
                    }
                    String actualValue = keyMap.get(nodeKey);
                    if (actualValue == null) {
                        verifyMissing++;
                        addVerifyMismatchSample(mismatchSamples, module, chunkPath, rowIndex, resourceId, nodeKey,
                                expectedValue, null, "KEY_NOT_FOUND");
                        continue;
                    }
                    if (expectedValue.equals(actualValue)) {
                        verifyMatched++;
                    } else {
                        verifyMismatch++;
                        addVerifyMismatchSample(mismatchSamples, module, chunkPath, rowIndex, resourceId, nodeKey,
                                expectedValue, actualValue, "VALUE_MISMATCH");
                    }
                }
            }
        }

        Map<String, Object> verifyReport = new LinkedHashMap<>();
        verifyReport.put("taskId", taskId);
        verifyReport.put("shopName", shopName);
        verifyReport.put("target", task.getTarget());
        verifyReport.put("verifyTotal", verifyTotal);
        verifyReport.put("verifyMatched", verifyMatched);
        verifyReport.put("verifyMismatch", verifyMismatch);
        verifyReport.put("verifyMissing", verifyMissing);
        verifyReport.put("mismatchSamples", mismatchSamples);
        verifyReport.put("updatedAt", Instant.now().toString());
        translateTaskV3BlobRepo.writeJson(blobPath(taskId, "qa/save-verify.json"), verifyReport);

        Map<String, Object> metrics = task.getMetrics() == null ? new HashMap<>() : new HashMap<>(task.getMetrics());
        metrics.put("verifyTotal", verifyTotal);
        metrics.put("verifyMatched", verifyMatched);
        metrics.put("verifyMismatch", verifyMismatch);
        metrics.put("verifyMissing", verifyMissing);
        metrics.put("verifiedAt", Instant.now().toString());
        metrics.put("updatedAt", Instant.now().toString());

        String phase = verifyMismatch > 0 || verifyMissing > 0 ? "VERIFY_MISMATCH_DONE" : "VERIFY_DONE";
        translateTaskMonitorV3RedisService.setPhase(taskId, phase);
        Map<String, Object> checkpoint = new HashMap<>();
        checkpoint.put("phase", phase);
        checkpoint.put("updatedAt", Instant.now().toString());
        checkpoint.put("verifyReportPath", blobPath(taskId, "qa/save-verify.json"));
        translateTaskV3CosmosRepo.patchCheckpointAndMetrics(taskId, shopName, checkpoint, metrics);
    }

    private boolean isChunkDataPath(String path) {
        if (StringUtils.isEmpty(path)) {
            return false;
        }
        return path.contains("/chunk-")
                && path.endsWith(".json")
                && !path.endsWith(SAVE_PROGRESS_SUFFIX);
    }

    private Set<String> collectSavedResourceIds(List<Map<String, Object>> rows, Set<Integer> savedRowIndexes) {
        Set<String> resourceIds = new java.util.HashSet<>();
        for (Integer rowIndex : savedRowIndexes) {
            if (rowIndex == null || rowIndex < 0 || rowIndex >= rows.size()) {
                continue;
            }
            Map<String, Object> row = rows.get(rowIndex);
            String resourceId = asString(row.get("resourceId"));
            if (!StringUtils.isEmpty(resourceId)) {
                resourceIds.add(resourceId);
            }
        }
        return resourceIds;
    }

    private Map<String, Map<String, String>> fetchShopifyTranslationsByResourceIds(String shopName,
                                                                                    String token,
                                                                                    String module,
                                                                                    String target,
                                                                                    List<String> resourceIds) {
        Map<String, Map<String, String>> result = new HashMap<>();
        if (CollectionUtils.isEmpty(resourceIds)) {
            return result;
        }
        shopifyService.rotateAllShopifyGraph(shopName, module, token, 250, target, "",
                resourceIds,
                node -> {
                    if (node == null || StringUtils.isEmpty(node.getResourceId())) {
                        return;
                    }
                    Map<String, String> keyMap = result.computeIfAbsent(node.getResourceId(), k -> new HashMap<>());
                    if (CollectionUtils.isEmpty(node.getTranslations())) {
                        return;
                    }
                    for (ShopifyTranslationsResponse.Node.Translation translation : node.getTranslations()) {
                        if (translation == null || StringUtils.isEmpty(translation.getKey())) {
                            continue;
                        }
                        keyMap.put(translation.getKey(), translation.getValue());
                    }
                },
                after -> {
                });
        return result;
    }

    private void addVerifyMismatchSample(List<Map<String, Object>> mismatchSamples,
                                         String module,
                                         String chunkPath,
                                         int rowIndex,
                                         String resourceId,
                                         String nodeKey,
                                         String expectedValue,
                                         String actualValue,
                                         String issueType) {
        if (mismatchSamples.size() >= 200) {
            return;
        }
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("module", module);
        item.put("chunkPath", chunkPath);
        item.put("rowIndex", rowIndex);
        item.put("resourceId", resourceId);
        item.put("nodeKey", nodeKey);
        item.put("issueType", issueType);
        item.put("expectedValue", clipForReport(expectedValue));
        item.put("actualValue", clipForReport(actualValue));
        mismatchSamples.add(item);
    }

    private void cachePendingProgress(String taskId,
                                      String shopName,
                                      String module,
                                      String chunkPath,
                                      int translatedTotal,
                                      int usedTokenTotal) {
        PendingProgress progress = new PendingProgress();
        progress.shopName = shopName;
        progress.module = module;
        progress.chunkPath = chunkPath;
        progress.translatedTotal = translatedTotal;
        progress.usedTokenTotal = usedTokenTotal;
        progress.updatedAt = Instant.now().toString();
        pendingProgressMap.put(taskId, progress);
    }

    private void flushPendingProgress(String taskId) {
        PendingProgress progress = pendingProgressMap.get(taskId);
        flushPendingProgress(taskId, progress);
    }

    private void flushPendingProgress(String taskId, PendingProgress progress) {
        if (taskId == null || progress == null || progress.shopName == null) {
            return;
        }
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("translatedCount", progress.translatedTotal);
        metrics.put("usedToken", progress.usedTokenTotal);
        metrics.put("updatedAt", progress.updatedAt);

        Map<String, Object> checkpoint = new HashMap<>();
        checkpoint.put("phase", "TRANSLATE_RUNNING");
        checkpoint.put("module", progress.module);
        checkpoint.put("chunkPath", progress.chunkPath);
        checkpoint.put("updatedAt", progress.updatedAt);
        translateTaskV3CosmosRepo.patchCheckpointAndMetrics(taskId, progress.shopName, checkpoint, metrics);
    }

    private void generateQaReport(String taskId, List<String> modules) {
        if (taskId == null || taskId.isEmpty() || CollectionUtils.isEmpty(modules)) {
            return;
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("generatedAt", Instant.now().toString());
        summary.put("taskId", taskId);
        summary.put("modules", modules);

        int totalRows = 0;
        int translatedRows = 0;
        int missingTargetCount = 0;
        int sameAsSourceCount = 0;
        int invalidJsonCount = 0;
        int htmlRiskCount = 0;
        int placeholderRiskCount = 0;

        List<Map<String, Object>> issueSamples = new ArrayList<>();
        Map<String, Object> moduleStats = new LinkedHashMap<>();

        for (String module : modules) {
            List<String> chunkPaths = translateTaskV3BlobRepo.listBlobPaths(blobPath(taskId, "chunks/" + module + "/"));
            if (chunkPaths.isEmpty()) {
                continue;
            }

            int moduleTotal = 0;
            int moduleTranslated = 0;
            int moduleMissingTarget = 0;
            int moduleSameAsSource = 0;

            for (String chunkPath : chunkPaths) {
                List<Map<String, Object>> rows = translateTaskV3BlobRepo.readJsonRows(chunkPath);
                for (int i = 0; i < rows.size(); i++) {
                    Map<String, Object> row = rows.get(i);
                    String sourceValue = asString(row.get("sourceValue"));
                    String targetValue = asString(row.get("targetValue"));
                    boolean translated = asBoolean(row.get("translated"));
                    boolean isJson = asBoolean(row.get("isJson"));
                    boolean isHtml = asBoolean(row.get("isHtml"));

                    totalRows++;
                    moduleTotal++;
                    if (translated) {
                        translatedRows++;
                        moduleTranslated++;
                    }

                    if (translated && StringUtils.isEmpty(targetValue)) {
                        missingTargetCount++;
                        moduleMissingTarget++;
                        addIssueSample(issueSamples, module, chunkPath, i, "MISSING_TARGET", sourceValue, targetValue);
                    }

                    if (translated && !StringUtils.isEmpty(sourceValue) && sourceValue.equals(targetValue)) {
                        sameAsSourceCount++;
                        moduleSameAsSource++;
                        addIssueSample(issueSamples, module, chunkPath, i, "UNCHANGED_AFTER_TRANSLATE", sourceValue, targetValue);
                    }

                    if (translated && isJson && !StringUtils.isEmpty(targetValue) && !JsonUtils.isJson(targetValue)) {
                        invalidJsonCount++;
                        addIssueSample(issueSamples, module, chunkPath, i, "INVALID_JSON_TARGET", sourceValue, targetValue);
                    }

                    if (translated && isHtml && !StringUtils.isEmpty(targetValue) && !JsoupUtils.isHtml(targetValue)) {
                        htmlRiskCount++;
                        addIssueSample(issueSamples, module, chunkPath, i, "HTML_STRUCTURE_RISK", sourceValue, targetValue);
                    }

                    if (translated && !StringUtils.isEmpty(sourceValue) && !StringUtils.isEmpty(targetValue)
                            && isPlaceholderChanged(sourceValue, targetValue)) {
                        placeholderRiskCount++;
                        addIssueSample(issueSamples, module, chunkPath, i, "PLACEHOLDER_CHANGED", sourceValue, targetValue);
                    }
                }
            }

            Map<String, Object> moduleMetric = new LinkedHashMap<>();
            moduleMetric.put("totalRows", moduleTotal);
            moduleMetric.put("translatedRows", moduleTranslated);
            moduleMetric.put("missingTargetCount", moduleMissingTarget);
            moduleMetric.put("sameAsSourceCount", moduleSameAsSource);
            moduleStats.put(module, moduleMetric);
        }

        summary.put("totalRows", totalRows);
        summary.put("translatedRows", translatedRows);
        summary.put("missingTargetCount", missingTargetCount);
        summary.put("sameAsSourceCount", sameAsSourceCount);
        summary.put("invalidJsonCount", invalidJsonCount);
        summary.put("htmlStructureRiskCount", htmlRiskCount);
        summary.put("placeholderRiskCount", placeholderRiskCount);
        summary.put("issueSampleCount", issueSamples.size());
        summary.put("moduleStats", moduleStats);
        summary.put("issueSamples", issueSamples);

        translateTaskV3BlobRepo.writeJson(blobPath(taskId, "chunks/qa-report.json"), summary);
    }

    private void addIssueSample(List<Map<String, Object>> issueSamples,
                                String module,
                                String chunkPath,
                                int rowIndex,
                                String issueType,
                                String sourceValue,
                                String targetValue) {
        if (issueSamples.size() >= 200) {
            return;
        }
        Map<String, Object> issue = new LinkedHashMap<>();
        issue.put("module", module);
        issue.put("chunkPath", chunkPath);
        issue.put("rowIndex", rowIndex);
        issue.put("issueType", issueType);
        issue.put("sourceValue", clipForReport(sourceValue));
        issue.put("targetValue", clipForReport(targetValue));
        issueSamples.add(issue);
    }

    private boolean isPlaceholderChanged(String source, String target) {
        return countOccurrences(source, "{{") != countOccurrences(target, "{{")
                || countOccurrences(source, "}}") != countOccurrences(target, "}}")
                || countOccurrences(source, "{%") != countOccurrences(target, "{%")
                || countOccurrences(source, "%}") != countOccurrences(target, "%}");
    }

    private static int countOccurrences(String text, String token) {
        if (text == null || token == null || token.isEmpty()) {
            return 0;
        }
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(token, index)) >= 0) {
            count++;
            index += token.length();
        }
        return count;
    }

    private String clipForReport(String text) {
        if (text == null) {
            return null;
        }
        int max = 300;
        if (text.length() <= max) {
            return text;
        }
        return text.substring(0, max) + "...";
    }

    private void generateAiScoreReport(TranslateTaskV3DO task, List<String> modules) {
        if (task == null || task.getId() == null || CollectionUtils.isEmpty(modules)) {
            return;
        }
        String generatedAt = Instant.now().toString();
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("generatedAt", generatedAt);
        report.put("taskId", task.getId());
        report.put("targetLanguage", task.getTarget());
        report.put("model", task.getAiModel());

        List<Map<String, Object>> moduleScores = new ArrayList<>();
        for (String module : modules) {
            Map<String, Object> scoreResult = scoreOneModuleByAi(task, module);
            moduleScores.add(scoreResult);

            // 按模块落盘：评分文件直接放在模块目录下，便于就近查看。
            Map<String, Object> moduleReport = new LinkedHashMap<>();
            moduleReport.put("generatedAt", generatedAt);
            moduleReport.put("taskId", task.getId());
            moduleReport.put("targetLanguage", task.getTarget());
            moduleReport.put("model", task.getAiModel());
            moduleReport.put("module", module);
            moduleReport.put("result", scoreResult);
            translateTaskV3BlobRepo.writeJson(blobPath(task.getId(), "chunks/" + module + "/ai-score.json"), moduleReport);
        }
        report.put("moduleScores", moduleScores);
        translateTaskV3BlobRepo.writeJson(blobPath(task.getId(), "qa/ai-score.json"), report);
    }

    private Map<String, Object> scoreOneModuleByAi(TranslateTaskV3DO task, String module) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("module", module);

        List<Map<String, Object>> samples = collectTranslatedSamples(task.getId(), module);
        result.put("sampleCount", samples.size());
        if (samples.isEmpty()) {
            result.put("status", "NO_SAMPLES");
            result.put("score", null);
            return result;
        }

        String prompt = buildAiScorePrompt(module, task.getTarget(), samples);
        Pair<String, Integer> aiResult = modelTranslateService.aiTranslate(task.getAiModel(), prompt, task.getTarget());
        if (aiResult == null || StringUtils.isEmpty(aiResult.getFirst())) {
            result.put("status", "AI_CALL_EMPTY");
            result.put("score", null);
            return result;
        }

        Map<String, Object> parsed = parseAiScoreResult(aiResult.getFirst());
        if (parsed == null || parsed.isEmpty()) {
            result.put("status", "AI_PARSE_FAILED");
            result.put("raw", clipForReport(aiResult.getFirst()));
            result.put("score", null);
            return result;
        }

        result.put("status", "OK");
        result.put("score", parsed.get("score"));
        result.put("dimensions", parsed.get("dimensions"));
        result.put("summary", parsed.get("summary"));
        result.put("issues", parsed.get("issues"));
        result.put("tokenUsed", aiResult.getSecond());
        return result;
    }

    private List<Map<String, Object>> collectTranslatedSamples(String taskId, String module) {
        List<Map<String, Object>> allSamples = new ArrayList<>();
        List<String> chunkPaths = translateTaskV3BlobRepo.listBlobPaths(blobPath(taskId, "chunks/" + module + "/"));
        for (String chunkPath : chunkPaths) {
            List<Map<String, Object>> rows = translateTaskV3BlobRepo.readJsonRows(chunkPath);
            for (Map<String, Object> row : rows) {
                if (!asBoolean(row.get("translated"))) {
                    continue;
                }
                String sourceValue = asString(row.get("sourceValue"));
                String targetValue = asString(row.get("targetValue"));
                if (StringUtils.isEmpty(sourceValue) || StringUtils.isEmpty(targetValue)) {
                    continue;
                }
                Map<String, Object> sample = new LinkedHashMap<>();
                sample.put("type", asString(row.get("type")));
                sample.put("nodeKey", asString(row.get("nodeKey")));
                sample.put("source", clipForAiEval(sourceValue));
                sample.put("target", clipForAiEval(targetValue));
                allSamples.add(sample);
            }
        }
        if (allSamples.isEmpty()) {
            return allSamples;
        }

        int total = allSamples.size();
        int targetSampleSize = (int) Math.ceil(total * AI_EVAL_SAMPLE_RATIO);
        targetSampleSize = Math.max(targetSampleSize, MIN_EVAL_SAMPLES_PER_MODULE);
        targetSampleSize = Math.min(targetSampleSize, MAX_EVAL_SAMPLES_PER_MODULE);
        targetSampleSize = Math.min(targetSampleSize, total);

        List<Map<String, Object>> shuffled = new ArrayList<>(allSamples);
        long seed = (long) (taskId + ":" + module).hashCode();
        Collections.shuffle(shuffled, new Random(seed));
        return new ArrayList<>(shuffled.subList(0, targetSampleSize));
    }

    private String buildAiScorePrompt(String module, String targetLanguage, List<Map<String, Object>> samples) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("module", module);
        payload.put("targetLanguage", targetLanguage);
        payload.put("samples", samples);

        return "You are a professional translation QA reviewer.\n"
                + "Evaluate translation quality for one Shopify module based on sample pairs.\n"
                + "Output STRICT JSON only, without markdown fences.\n"
                + "Required format:\n"
                + "{\n"
                + "  \"score\": 0-100,\n"
                + "  \"dimensions\": {\n"
                + "    \"accuracy\": 0-100,\n"
                + "    \"fluency\": 0-100,\n"
                + "    \"formatPreservation\": 0-100,\n"
                + "    \"terminologyConsistency\": 0-100\n"
                + "  },\n"
                + "  \"summary\": \"short summary\",\n"
                + "  \"issues\": [\"issue1\", \"issue2\", \"...\"]\n"
                + "}\n"
                + "Scoring rules:\n"
                + "- Penalize meaning errors and mistranslations heavily.\n"
                + "- Penalize placeholder/format breakage heavily.\n"
                + "- Keep summary concise.\n"
                + "Input JSON:\n"
                + JsonUtils.objectToJson(payload);
    }

    private Map<String, Object> parseAiScoreResult(String raw) {
        String jsonPart = StringUtils.extractJsonBlock(raw);
        if (jsonPart == null || jsonPart.isEmpty()) {
            return null;
        }
        Map<String, Object> result = JsonUtils.jsonToObjectWithNull(jsonPart, new TypeReference<Map<String, Object>>() {
        });
        if (result != null) {
            return result;
        }
        String repaired = JsonUtils.highlyRobustRepair(jsonPart);
        repaired = JsonUtils.fixMissingQuote(repaired);
        return JsonUtils.jsonToObjectWithNull(repaired, new TypeReference<Map<String, Object>>() {
        });
    }

    private String clipForAiEval(String text) {
        if (text == null) {
            return null;
        }
        int max = 500;
        if (text.length() <= max) {
            return text;
        }
        return text.substring(0, max) + "...";
    }

    private boolean shouldStoreContent(ShopifyTranslationsResponse.Node.TranslatableContent content,
                                       List<ShopifyTranslationsResponse.Node.Translation> translations,
                                       boolean isCover,
                                       boolean isHandle) {
        if (content == null || StringUtils.isEmpty(content.getValue())) {
            return false;
        }
        String type = content.getType();
        String key = content.getKey();

        if ("FILE_REFERENCE".equals(type) || "LINK".equals(type) || "URL".equals(type)
                || "LIST_FILE_REFERENCE".equals(type) || "LIST_LINK".equals(type)
                || "LIST_URL".equals(type) || "JSON_STRING".equals(type)) {
            return false;
        }
        if ("URI".equals(type) && "handle".equals(key) && !isHandle) {
            return false;
        }
        if (!isCover && translations != null) {
            ShopifyTranslationsResponse.Node.Translation keyTranslation = translations.stream()
                    .filter(t -> key != null && key.equals(t.getKey()))
                    .findFirst()
                    .orElse(null);
            if (keyTranslation != null && Boolean.FALSE.equals(keyTranslation.getOutdated())) {
                return false;
            }
        }
        return true;
    }

    private String getPrimaryLocaleFromShopifyData(String shopifyData) {
        if (shopifyData == null) {
            return null;
        }
        com.fasterxml.jackson.databind.JsonNode root = JsonUtils.readTree(shopifyData);
        if (root == null) {
            return null;
        }
        com.fasterxml.jackson.databind.JsonNode shopLocales = root.path("shopLocales");
        if (!shopLocales.isArray()) {
            return null;
        }
        for (com.fasterxml.jackson.databind.JsonNode node : shopLocales) {
            if (node.path("primary").asBoolean(false)) {
                return node.path("locale").asText(null);
            }
        }
        return null;
    }

    private List<String> resolveModuleList(List<String> settings) {
        List<String> modules = settings.stream()
                .filter(s -> s != null && !"handle".equals(s))
                .flatMap(module -> {
                    List<TranslateResourceDTO> mapped = TranslateResourceDTO.TOKEN_MAP.get(module);
                    if (mapped == null) {
                        return java.util.stream.Stream.empty();
                    }
                    return mapped.stream();
                })
                .map(TranslateResourceDTO::getResourceType)
                .filter(s -> s != null && !s.isEmpty())
                .distinct()
                .collect(Collectors.toCollection(ArrayList::new));

        List<String> order = TranslateResourceDTO.ALL_RESOURCES.stream()
                .map(TranslateResourceDTO::getResourceType)
                .toList();
        Map<String, Integer> rank = new HashMap<>();
        for (int i = 0; i < order.size(); i++) {
            rank.put(order.get(i), i);
        }
        modules.sort(Comparator.comparingInt(m -> rank.getOrDefault(m, Integer.MAX_VALUE)));
        return modules;
    }

    private static String blobPath(String taskId, String tail) {
        return "tasks/" + taskId + "/" + tail;
    }

    private static class ModuleInitSummary {
        private final int totalCount;
        private final int totalChars;
        private final int chunkCount;

        private ModuleInitSummary(int totalCount, int totalChars, int chunkCount) {
            this.totalCount = totalCount;
            this.totalChars = totalChars;
            this.chunkCount = chunkCount;
        }

        private Map<String, Object> toMap() {
            Map<String, Object> map = new HashMap<>();
            map.put("totalCount", totalCount);
            map.put("totalChars", totalChars);
            map.put("chunkCount", chunkCount);
            return map;
        }
    }

    private static class TranslateChunkResult {
        private final int translatedDelta;
        private final int usedTokenDelta;
        private final int currentUsedToken;
        private final boolean hitTokenLimit;

        private TranslateChunkResult(int translatedDelta, int usedTokenDelta, int currentUsedToken, boolean hitTokenLimit) {
            this.translatedDelta = translatedDelta;
            this.usedTokenDelta = usedTokenDelta;
            this.currentUsedToken = currentUsedToken;
            this.hitTokenLimit = hitTokenLimit;
        }
    }

    private static class PendingProgress {
        private String shopName;
        private String module;
        private String chunkPath;
        private int translatedTotal;
        private int usedTokenTotal;
        private String updatedAt;
    }
}
