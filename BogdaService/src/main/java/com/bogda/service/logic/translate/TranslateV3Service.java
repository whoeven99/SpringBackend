package com.bogda.service.logic.translate;

import com.bogda.common.controller.request.ClickTranslateRequest;
import com.bogda.common.controller.request.JsonRuntimeTranslateRequest;
import com.bogda.common.controller.response.BaseResponse;
import com.bogda.common.controller.response.ProgressResponse;
import com.bogda.common.contants.TranslateConstants;
import com.bogda.common.entity.DO.GlossaryDO;
import com.bogda.common.entity.DO.TranslateResourceDTO;
import com.bogda.common.entity.DO.UsersDO;
import com.bogda.common.entity.VO.SingleReturnVO;
import com.bogda.common.entity.VO.SingleTranslateVO;
import com.bogda.common.reporter.TraceReporterHolder;
import com.bogda.common.utils.ConfigUtils;
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
import com.bogda.common.agent.JsonRuntimeAgentRunner;
import com.bogda.service.logic.token.UserTokenService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import kotlin.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import javax.annotation.PreDestroy;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Random;
import java.util.UUID;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Component
@ConditionalOnProperty(name = "translate.v3.enabled", havingValue = "true")
public class TranslateV3Service {
    private static final Logger LOG = LoggerFactory.getLogger(TranslateV3Service.class);
    private static final int PROGRESS_FLUSH_INTERVAL_CHUNKS = 10;
    private static final String SAVE_PROGRESS_SUFFIX = ".save-progress";
    private static final double AI_EVAL_SAMPLE_RATIO = 0.30;
    private static final int MIN_EVAL_SAMPLES_PER_MODULE = 30;
    private static final int MAX_EVAL_SAMPLES_PER_MODULE = 1000;
    private static final int SHOP_GROUP_WORKER_SIZE = 10;
    private static final long JSON_RUNTIME_MAX_BYTES = 50L * 1024L * 1024L;
    private static final long JSON_RUNTIME_DEFAULT_TTL_SECONDS = 72L * 3600L;
    private static final int JSON_RUNTIME_DEFAULT_BATCH_SIZE = 80;
    private static final int JSON_RUNTIME_DEFAULT_MAX_CHARS_PER_BATCH = 12000;
    private static final int JSON_RUNTIME_DEFAULT_CONCURRENCY = 4;
    private static final int JSON_RUNTIME_DEFAULT_MAX_RETRIES = 5;
    private static final int JSON_RUNTIME_DEFAULT_BASE_BACKOFF_MS = 1000;
    /** 人工可读的总览，置于 tasks/{shop}/{taskId}/chunks/ 下供 Blob 控制台直接打开编辑 */
    private static final String JSON_RUNTIME_TRANSLATION_SUMMARY_TXT = "chunks/translation-summary.txt";
    /** LLM 汇总所有模块/chunk 后生成的 Markdown 翻译报告，便于直接阅读 */
    private static final String JSON_RUNTIME_TRANSLATION_REPORT_MD = "chunks/translation-report.md";
    /** LLM 对照原文/译文抽检后的质量打分报告（Markdown） */
    private static final String JSON_RUNTIME_QUALITY_REPORT_MD = "chunks/translation-quality-report.md";
    /** 所有分片汇总的失败条目（含 sourceValue），便于在 Blob 中直接查看 */
    private static final String JSON_RUNTIME_CHUNKS_FAILED_JSON = "chunks/failed.json";
    private static final int JSON_RUNTIME_HTTP_TIMEOUT_SECONDS = 120;
    /**
     * {@code sourceValue} 内若为字符串化的大 JSON，展开成多条叶子再分批调用模型；
     * 否则单次返回必须与输入同体量，易被 max_tokens 截断导致 {@code MODEL_RESPONSE_JSON_INVALID}。
     */
    private static final int JSON_RUNTIME_EXPAND_STRINGIFIED_JSON_MIN_CHARS = 2000;
    private static final String RUNTIME_INNER_JSON_MARKER = "::INNER::";
    @Value("${translate.v3.init.module-limit:20}")
    private int initModuleFetchLimit;
    @Value("${translate.v3.chunk-translate-timeout-seconds:120}")
    private int chunkTranslateTimeoutSeconds;
    /** 与 JsonRuntimeAgent 共用一套 OpenAI 兼容接口配置，供仅含 init checkpoint 的 json-runtime 任务回填。 */
    @Value("${langchain4j.openai.api-key:}")
    private String jsonRuntimeFallbackApiKey;
    @Value("${langchain4j.openai.base-url:https://api.deepseek.com/v1}")
    private String jsonRuntimeFallbackBaseUrl;
    @Value("${langchain4j.openai.model-name:deepseek-chat}")
    private String jsonRuntimeFallbackModelName;
    /** 更短的 system 指令，略省 token；服务端不保存多轮上下文，此开关不能替代「会话记忆」。 */
    @Value("${translate.v3.runtime.compact-system-prompt:true}")
    private boolean jsonRuntimeCompactSystemPrompt;
    /** 翻译结束后是否调用 LLM 生成 chunks/translation-report.md（关闭时仅写入摘要版 Markdown） */
    @Value("${translate.v3.runtime.translation-report.enabled:true}")
    private boolean jsonRuntimeTranslationReportEnabled;
    @Value("${translate.v3.runtime.translation-report.max-input-chars:48000}")
    private int jsonRuntimeTranslationReportMaxInputChars;
    @Value("${translate.v3.runtime.translation-report.max-completion-tokens:8192}")
    private int jsonRuntimeTranslationReportMaxCompletionTokens;
    /** 翻译结束后是否调用 LLM 生成 chunks/translation-quality-report.md（对照原文/译文抽检打分） */
    @Value("${translate.v3.runtime.quality-report.enabled:true}")
    private boolean jsonRuntimeQualityReportEnabled;
    /** 送入质量模型的最多「路径」条数（超大任务会随机抽样） */
    @Value("${translate.v3.runtime.quality-report.max-pairs:80}")
    private int jsonRuntimeQualityReportMaxPairs;
    /** 每条原文/译文在提示词中的最大字符数（防止超长字段撑爆上下文） */
    @Value("${translate.v3.runtime.quality-report.max-chars-per-field:500}")
    private int jsonRuntimeQualityReportMaxCharsPerField;
    @Value("${translate.v3.runtime.quality-report.max-input-chars:32000}")
    private int jsonRuntimeQualityReportMaxInputChars;
    @Value("${translate.v3.runtime.quality-report.max-completion-tokens:4096}")
    private int jsonRuntimeQualityReportMaxCompletionTokens;

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
    /** 与 JsonRuntimeAgentTools 同依赖本类；Runner 仅在 AgentTask 模块装配，其它进程可为 null */
    @Lazy
    @Autowired(required = false)
    private JsonRuntimeAgentRunner jsonRuntimeAgentRunner;
    private final Set<String> processingInitialTaskIds = ConcurrentHashMap.newKeySet();
    private final Set<String> processingTranslateTaskIds = ConcurrentHashMap.newKeySet();
    private final Set<String> processingSaveTaskIds = ConcurrentHashMap.newKeySet();
    private final Set<String> processingVerifyTaskIds = ConcurrentHashMap.newKeySet();
    private final Set<String> processingShopNames = ConcurrentHashMap.newKeySet();
    private final Map<String, PendingProgress> pendingProgressMap = new ConcurrentHashMap<>();
    private final List<ExecutorService> shopGroupExecutors = createShopGroupExecutors();
    private final ExecutorService chunkTranslateExecutor = Executors.newFixedThreadPool(2);

    public BaseResponse<Object> createInitialTask(ClickTranslateRequest request) {
        String shopName = request.getShopName();
        String normalizedSource = normalizeLocaleCode(request.getSource());
        if (StringUtils.isEmpty(shopName) || StringUtils.isEmpty(normalizedSource)
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
        for (String rawTarget : request.getTarget()) {
            String target = normalizeLocaleCode(rawTarget);
            if (StringUtils.isEmpty(target)) {
                continue;
            }
            if (configRedisRepo.isWhiteList(target, "forbiddenTarget")) {
                continue;
            }
            if (translateTaskV3CosmosRepo.existsActiveTask(shopName, normalizedSource, target)) {
                continue;
            }

            String taskId = UUID.randomUUID().toString();
            TranslateTaskV3DO task = new TranslateTaskV3DO();
            task.setId(taskId);
            task.setShopName(shopName);
            task.setSource(normalizedSource);
            task.setTarget(target);
            task.setStatus(0);
            task.setStatusText("INIT_PENDING");
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

            translateTaskMonitorV3RedisService.createRecord(taskId, shopName, normalizedSource, target, aiModel);
            translatesService.updateTranslateStatus(shopName, 2, target, normalizedSource);
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
            result.put("moduleBlobPathPattern", "tasks/" + effectiveShopName + "/" + taskId + "/chunks/{module}/ai-score.json");
            result.put("summaryBlobPath", "tasks/" + effectiveShopName + "/" + taskId + "/qa/ai-score.json");
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
        List<TranslateTaskV3DO> byId = translateTaskV3CosmosRepo.listByTaskId(taskId);
        if (byId == null || byId.isEmpty()) {
            return null;
        }
        if (byId.size() > 1) {
            LOG.warn("v3 findTaskById: multiple documents share id={}, count={}, using first shopName={}",
                    taskId, byId.size(), byId.get(0).getShopName());
        }
        return byId.get(0);
    }

    public void processInitialTasksV3() {
        processTasksByShopName("INIT", 0, processingInitialTaskIds,
                task -> false, this::initialToTranslateTaskV3,
                () -> LOG.debug("v3 init poll no pending task(status=0)"));
    }

    public void processTranslateTasksV3() {
        processTasksByShopName("TRANSLATE", 1, processingTranslateTaskIds,
                task -> false, this::translateEachTaskV3, null);
    }

    public Map<String, Object> executeJsonRuntimeTaskByTaskId(String taskId) {
        String cleanTaskId = safeText(taskId);
        if (cleanTaskId.isEmpty()) {
            Map<String, Object> failed = baseRuntimeResponse("", "", "", "");
            failed.put("status", "FAILED");
            failed.put("reason", "MISSING_TASK_ID");
            failed.put("hint", "taskId 为空");
            return failed;
        }
        TranslateTaskV3DO task = findTaskByIdAcrossStatuses(cleanTaskId);
        if (task == null) {
            Map<String, Object> failed = baseRuntimeResponse(cleanTaskId, "", "", "");
            failed.put("status", "FAILED");
            failed.put("reason", "TASK_NOT_FOUND");
            failed.put("hint",
                    "Cosmos translate_tasks_v3 中未找到该 id（已按 id 跨分区查询）。请核对环境/容器是否与 API 一致，且 id 字符串完全相等。");
            return failed;
        }
        if (!isRuntimeJsonTask(task)) {
            Map<String, Object> failed = baseRuntimeResponse(cleanTaskId, "", "", "");
            failed.put("status", "FAILED");
            failed.put("reason", "NOT_JSON_RUNTIME_TASK");
            failed.put("taskType", task.getTaskType());
            failed.put("hint", "该文档存在但 taskType 不是 spark（兼容历史 json-runtime），无法用 JSON Runtime 执行器。");
            return failed;
        }
        JsonRuntimeTranslateRequest request = buildRuntimeRequestFromTask(task);
        if (safeText(request.getInputBlobUri()).isEmpty()
                || safeText(request.getOutputBlobUri()).isEmpty()
                || safeText(request.getReportBlobUri()).isEmpty()) {
            Map<String, Object> failed = baseRuntimeResponse(cleanTaskId,
                    safeText(request.getInputBlobUri()),
                    safeText(request.getOutputBlobUri()),
                    safeText(request.getReportBlobUri()));
            failed.put("status", "FAILED");
            failed.put("reason", "CHECKPOINT_MISSING_BLOB_URIS");
            failed.put("hint",
                    "checkpoint 中缺少 inputBlobUri / outputBlobUri / reportBlobUri（且无法根据 init 的 chunks/{module}/chunk-*.json 自动解析）。请确认已执行 init、checkpoint.modules 非空且 Blob 上存在 chunk 文件，或手写三个 URI。");
            return failed;
        }
        return executeJsonRuntimeTask(request);
    }

    public Map<String, Object> getJsonRuntimeTaskProgress(String taskId, String redisPrefix) {
        String cleanTaskId = safeText(taskId);
        String cleanPrefix = safeText(redisPrefix);
        if (cleanPrefix.isEmpty()) {
            cleanPrefix = "tr:v1";
        }
        Map<String, Object> progress = new LinkedHashMap<>();
        progress.put("taskId", cleanTaskId);
        progress.put("redisPrefix", cleanPrefix);
        progress.put("meta", translateTaskMonitorV3RedisService.getRuntimeMeta(cleanPrefix, cleanTaskId));
        progress.put("doneSize", translateTaskMonitorV3RedisService.getRuntimeDoneSet(cleanPrefix, cleanTaskId).size());
        progress.put("failMap", translateTaskMonitorV3RedisService.getRuntimeFailMap(cleanPrefix, cleanTaskId));
        progress.put("chunkDoneSize", translateTaskMonitorV3RedisService.getRuntimeChunkDoneSet(cleanPrefix, cleanTaskId).size());
        progress.put("resultSize",
                translateTaskMonitorV3RedisService.getRuntimeResultMap(cleanPrefix, cleanTaskId).size());
        return progress;
    }

    /**
     * 查看 json-runtime 任务在 Cosmos 中的状态、Redis 进度，以及 checkpoint 里引用的 Blob 是否存在与大小（可选文本预览）。
     * {@code shopName} 若给出则直接点读 Cosmos，否则按各 status 扫描查找（与按 taskId 执行 runtime 一致）。
     */
    public BaseResponse<Object> getJsonRuntimeTaskDetail(String taskId,
                                                         String shopName,
                                                         String redisPrefix,
                                                         boolean includeBlobPreview,
                                                         int maxPreviewBytes) {
        String cleanId = safeText(taskId);
        if (cleanId.isEmpty()) {
            return BaseResponse.FailedResponse("Missing parameters: taskId");
        }
        TranslateTaskV3DO task;
        String cleanShop = safeText(shopName);
        if (!cleanShop.isEmpty()) {
            task = translateTaskV3CosmosRepo.getById(cleanId, cleanShop);
        } else {
            task = findTaskByIdAcrossStatuses(cleanId);
        }
        if (task == null) {
            return BaseResponse.FailedResponse("Task not found: " + cleanId);
        }
        Map<String, Object> checkpoint = task.getCheckpoint() == null ? new HashMap<>() : task.getCheckpoint();
        String effectiveRedis = safeText(redisPrefix);
        if (effectiveRedis.isEmpty()) {
            effectiveRedis = safeText(asString(checkpoint.get("redisPrefix")));
        }
        if (effectiveRedis.isEmpty()) {
            effectiveRedis = "tr:v1";
        }
        int cap = maxPreviewBytes <= 0 ? 8192 : Math.min(Math.max(maxPreviewBytes, 256), 512 * 1024);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("cosmos", cosmosTaskV3ToMap(task));
        body.put("resolvedRedisPrefix", effectiveRedis);
        body.put("redisRuntime", getJsonRuntimeTaskProgress(cleanId, effectiveRedis));

        Map<String, String> translateMonitor = translateTaskMonitorV3RedisService.getAll(cleanId);
        body.put("translateMonitor", translateMonitor == null || translateMonitor.isEmpty() ? null : translateMonitor);

        Map<String, String> redisMeta = translateTaskMonitorV3RedisService.getRuntimeMeta(effectiveRedis, cleanId);
        String inputUri = firstNonEmptyRuntimeBlobUri(
                checkpoint, redisMeta, "inputBlobUri");
        String outputUri = firstNonEmptyRuntimeBlobUri(
                checkpoint, redisMeta, "outputBlobUri");
        String reportUri = firstNonEmptyRuntimeBlobUri(
                checkpoint, redisMeta, "reportBlobUri");

        Map<String, Object> blobs = new LinkedHashMap<>();
        blobs.put("input", buildRuntimeBlobSnapshot(inputUri, includeBlobPreview, cap));
        blobs.put("output", buildRuntimeBlobSnapshot(outputUri, includeBlobPreview, cap));
        blobs.put("report", buildRuntimeBlobSnapshot(reportUri, includeBlobPreview, cap));
        String translationReportRel = blobPath(task.getShopName(), cleanId, JSON_RUNTIME_TRANSLATION_REPORT_MD);
        blobs.put("translationReportMd", buildRuntimeBlobSnapshot(translationReportRel, includeBlobPreview, cap));
        String qualityReportRel = blobPath(task.getShopName(), cleanId, JSON_RUNTIME_QUALITY_REPORT_MD);
        blobs.put("qualityReportMd", buildRuntimeBlobSnapshot(qualityReportRel, includeBlobPreview, cap));
        body.put("blobs", blobs);

        if (includeBlobPreview) {
            @SuppressWarnings("unchecked")
            Map<String, Object> reportSnap = (Map<String, Object>) blobs.get("report");
            if (reportSnap != null) {
                Object preview = reportSnap.get("preview");
                if (preview instanceof String s && !s.isEmpty()) {
                    try {
                        body.put("reportParsed", JsonUtils.jsonToObject(s, new TypeReference<Map<String, Object>>() {
                        }));
                    } catch (Exception ignored) {
                        // 报告非 JSON 或预览被截断时不解析
                    }
                }
            }
        }
        enrichJsonRuntimeDetailWithReportFailures(body, reportUri);
        enrichJsonRuntimeDetailWithChunksFailedJson(body, task.getShopName(), cleanId);

        LOG.info("json-runtime task detail taskId={} shopName={} taskType={}", cleanId, task.getShopName(), task.getTaskType());

        return BaseResponse.SuccessResponse(body);
    }

    /**
     * 列出某店铺在 Cosmos 中已存在的 json-runtime 任务（摘要，不含 checkpoint/metrics 大字段）。
     */
    public BaseResponse<Object> listJsonRuntimeTasksByShop(String shopName) {
        String cleanShop = safeText(shopName);
        if (cleanShop.isEmpty()) {
            return BaseResponse.FailedResponse("Missing parameters: shopName");
        }
        List<TranslateTaskV3DO> all = translateTaskV3CosmosRepo.listByShopName(cleanShop);
        List<TranslateTaskV3DO> runtime = all.stream()
                .filter(this::isRuntimeJsonTask)
                .sorted(Comparator.comparing(TranslateTaskV3DO::getUpdatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());
        List<Map<String, Object>> rows = new ArrayList<>(runtime.size());
        for (TranslateTaskV3DO t : runtime) {
            rows.add(jsonRuntimeTaskSummaryForList(t));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("shopName", cleanShop);
        body.put("total", rows.size());
        body.put("tasks", rows);
        LOG.info("json-runtime task list shopName={} total={}", cleanShop, rows.size());
        return BaseResponse.SuccessResponse(body);
    }

    private Map<String, Object> jsonRuntimeTaskSummaryForList(TranslateTaskV3DO task) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", task.getId());
        m.put("shopName", task.getShopName());
        m.put("source", task.getSource());
        m.put("target", task.getTarget());
        m.put("status", task.getStatus());
        m.put("statusText", task.getStatusText());
        m.put("taskType", task.getTaskType());
        m.put("aiModel", task.getAiModel());
        m.put("createdAt", task.getCreatedAt());
        m.put("updatedAt", task.getUpdatedAt());
        m.put("sessionId", task.getSessionId());
        m.put("moduleList", task.getModuleList());
        return m;
    }

    /**
     * checkpoint 在 SAVE 等阶段可能被覆盖，丢失顶层 input/output/report URI；依次尝试 checkpoint 顶层、runtimeResult、Redis meta。
     */
    private String firstNonEmptyRuntimeBlobUri(Map<String, Object> checkpoint,
                                              Map<String, String> redisMeta,
                                              String field) {
        if (field == null || field.isEmpty()) {
            return "";
        }
        String fromCp = checkpoint == null ? "" : safeText(asString(checkpoint.get(field)));
        if (!fromCp.isEmpty()) {
            return fromCp;
        }
        String fromResult = runtimeResultBlobField(checkpoint, field);
        if (!fromResult.isEmpty()) {
            return fromResult;
        }
        if (redisMeta != null) {
            String fromRedis = safeText(redisMeta.get(field));
            if (!fromRedis.isEmpty()) {
                return fromRedis;
            }
        }
        return "";
    }

    private static String runtimeResultBlobField(Map<String, Object> checkpoint, String field) {
        if (checkpoint == null || field == null) {
            return "";
        }
        Object rr = checkpoint.get("runtimeResult");
        if (!(rr instanceof Map<?, ?> m)) {
            return "";
        }
        return safeText(asString(m.get(field)));
    }

    private Map<String, Object> cosmosTaskV3ToMap(TranslateTaskV3DO task) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", task.getId());
        m.put("shopName", task.getShopName());
        m.put("source", task.getSource());
        m.put("target", task.getTarget());
        m.put("status", task.getStatus());
        m.put("statusText", task.getStatusText());
        m.put("taskType", task.getTaskType());
        m.put("aiModel", task.getAiModel());
        m.put("checkpoint", task.getCheckpoint());
        m.put("metrics", task.getMetrics());
        m.put("createdAt", task.getCreatedAt());
        m.put("updatedAt", task.getUpdatedAt());
        m.put("isCover", task.isCover());
        m.put("isHandle", task.isHandle());
        m.put("moduleList", task.getModuleList());
        m.put("sessionId", task.getSessionId());
        return m;
    }

    private Map<String, Object> buildRuntimeBlobSnapshot(String uriOrPath, boolean includePreview, int maxPreviewBytes) {
        Map<String, Object> snap = new LinkedHashMap<>();
        String uri = safeText(uriOrPath);
        snap.put("uri", uri);
        if (uri.isEmpty()) {
            snap.put("exists", false);
            snap.put("note", "checkpoint 中无此 URI（非 spark/json-runtime 运行时任务或字段未写入）");
            return snap;
        }
        String path = toBlobPath(uri);
        snap.put("blobPath", path);
        boolean exists = translateTaskV3BlobRepo.blobExists(path);
        snap.put("exists", exists);
        if (!exists) {
            return snap;
        }
        Long sizeBytes = translateTaskV3BlobRepo.getBlobSizeBytes(path);
        snap.put("sizeBytes", sizeBytes);
        if (!includePreview) {
            return snap;
        }
        String preview = translateTaskV3BlobRepo.readTextPrefix(path, maxPreviewBytes);
        snap.put("preview", preview);
        if (sizeBytes != null) {
            snap.put("previewTruncated", preview != null && sizeBytes > maxPreviewBytes);
        }
        return snap;
    }

    /**
     * 从 report Blob 解析 {@code failures} 数组供详情页展示（不依赖 includeBlobPreview）。
     * 与 Redis failMap 互补：跨 chunk 相同 JSON path 在 Redis 中会覆盖，完整列表以各 chunk 的 report 为准。
     */
    private void enrichJsonRuntimeDetailWithReportFailures(Map<String, Object> body, String reportUri) {
        String uri = safeText(reportUri);
        if (uri.isEmpty()) {
            return;
        }
        String path = toBlobPath(uri);
        if (!translateTaskV3BlobRepo.blobExists(path)) {
            return;
        }
        Long sz = translateTaskV3BlobRepo.getBlobSizeBytes(path);
        final long maxBytes = 512 * 1024;
        if (sz != null && sz > maxBytes) {
            body.put("runtimeReportFailuresTruncated", true);
            return;
        }
        String raw = translateTaskV3BlobRepo.readText(path);
        if (raw == null || raw.isBlank()) {
            return;
        }
        try {
            Map<String, Object> rep = JsonUtils.jsonToObject(raw, new TypeReference<Map<String, Object>>() {
            });
            Object failures = rep == null ? null : rep.get("failures");
            if (failures instanceof List<?> list && !list.isEmpty()) {
                body.put("runtimeReportFailures", failures);
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * 附加 chunks/failed.json（合并写入，含每条失败的 sourceValue），便于详情页定位原文。
     */
    private void enrichJsonRuntimeDetailWithChunksFailedJson(Map<String, Object> body,
                                                           String shopName,
                                                           String taskId) {
        String shop = safeText(shopName);
        String tid = safeText(taskId);
        if (shop.isEmpty() || tid.isEmpty()) {
            return;
        }
        String blobRel = blobPath(shop, tid, JSON_RUNTIME_CHUNKS_FAILED_JSON);
        if (!translateTaskV3BlobRepo.blobExists(blobRel)) {
            return;
        }
        Long sz = translateTaskV3BlobRepo.getBlobSizeBytes(blobRel);
        final long maxBytes = 512 * 1024;
        if (sz != null && sz > maxBytes) {
            body.put("runtimeFailedJsonTruncated", true);
            return;
        }
        String raw = translateTaskV3BlobRepo.readText(blobRel);
        if (raw == null || raw.isBlank()) {
            return;
        }
        try {
            Map<String, Object> doc = JsonUtils.jsonToObject(raw, new TypeReference<Map<String, Object>>() {
            });
            if (doc != null && !doc.isEmpty()) {
                body.put("runtimeFailedJson", doc);
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * 解析 {@code tasks/{shop}/{taskId}/...} 容器路径前缀。
     */
    private static String[] parseTasksBlobShopAndTaskId(String blobPath) {
        String p = safeText(blobPath);
        if (p.isEmpty() || !p.startsWith("tasks/")) {
            return null;
        }
        String rest = p.substring("tasks/".length());
        int s1 = rest.indexOf('/');
        int s2 = rest.indexOf('/', s1 + 1);
        if (s1 <= 0 || s2 <= s1) {
            return null;
        }
        return new String[]{rest.substring(0, s1), rest.substring(s1 + 1, s2)};
    }

    private static String stripRuntimeFailStorageKeyToPointer(String storageKey) {
        String k = safeText(storageKey);
        int sep = k.indexOf("::");
        if (sep > 0 && sep < k.length() - 2) {
            return k.substring(sep + 2);
        }
        return k;
    }

    private static String failedJsonDedupeKey(Map<String, Object> row) {
        return safeText(asString(row.get("chunkFile"))) + "|"
                + safeText(asString(row.get("storageKey"))) + "|"
                + safeText(asString(row.get("reason")));
    }

    /**
     * 将本分片的失败条目合并写入 {@code tasks/{shop}/{taskId}/chunks/failed.json}（跨 chunk 累积）。
     */
    private void mergeWriteChunksFailedJson(String shopName,
                                            String taskId,
                                            String moduleName,
                                            String chunkFileLabel,
                                            int chunkOrdinal,
                                            Map<String, String> failMap,
                                            Map<String, String> pathToSource,
                                            JsonNode root) {
        if (failMap == null || failMap.isEmpty()) {
            return;
        }
        String shop = safeText(shopName);
        String tid = safeText(taskId);
        if (shop.isEmpty() || tid.isEmpty()) {
            return;
        }
        String blobRel = blobPath(shop, tid, JSON_RUNTIME_CHUNKS_FAILED_JSON);
        List<Map<String, Object>> existingItems = new ArrayList<>();
        String rawExisting = translateTaskV3BlobRepo.readText(blobRel);
        if (rawExisting != null && !rawExisting.isBlank()) {
            try {
                Map<String, Object> doc = JsonUtils.jsonToObject(rawExisting, new TypeReference<Map<String, Object>>() {
                });
                Object arr = doc == null ? null : doc.get("items");
                if (arr instanceof List<?> list) {
                    for (Object o : list) {
                        if (o instanceof Map<?, ?> m) {
                            Map<String, Object> row = new LinkedHashMap<>();
                            for (Map.Entry<?, ?> e : m.entrySet()) {
                                if (e.getKey() != null) {
                                    row.put(String.valueOf(e.getKey()), e.getValue());
                                }
                            }
                            existingItems.add(row);
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }
        Set<String> seen = new LinkedHashSet<>();
        for (Map<String, Object> row : existingItems) {
            seen.add(failedJsonDedupeKey(row));
        }
        Instant now = Instant.now();
        Map<String, String> srcMap = pathToSource == null ? Collections.emptyMap() : pathToSource;
        String modLabel = safeText(moduleName).isEmpty() ? "-" : moduleName;
        for (Map.Entry<String, String> e : failMap.entrySet()) {
            String storageKey = e.getKey();
            String reason = e.getValue();
            String pointer = stripRuntimeFailStorageKeyToPointer(storageKey);
            String sourceVal = safeText(srcMap.get(pointer));
            if (sourceVal.isEmpty() && root != null && pointer.startsWith("/")) {
                sourceVal = safeText(getTextAtPointer(root, pointer));
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("module", modLabel);
            row.put("chunkFile", safeText(chunkFileLabel).isEmpty() ? "-" : chunkFileLabel);
            row.put("chunkOrdinal", chunkOrdinal);
            row.put("storageKey", storageKey);
            row.put("path", pointer);
            row.put("reason", reason == null ? "" : reason);
            row.put("sourceValue", sourceVal);
            row.put("recordedAt", now.toString());
            String dedupe = failedJsonDedupeKey(row);
            if (seen.contains(dedupe)) {
                continue;
            }
            seen.add(dedupe);
            existingItems.add(row);
        }
        Map<String, Object> outDoc = new LinkedHashMap<>();
        outDoc.put("taskId", tid);
        outDoc.put("shopName", shop);
        outDoc.put("updatedAt", now.toString());
        outDoc.put("itemCount", existingItems.size());
        outDoc.put("items", existingItems);
        translateTaskV3BlobRepo.writeJson(blobRel, outDoc);
        LOG.info("json-runtime merged chunks/failed.json taskId={}, path={}, sliceFailures={}, totalItems={}",
                tid, blobRel, failMap.size(), existingItems.size());
    }

    public void processSaveTasksV3() {
        processTasksByShopName("SAVE", 2, processingSaveTaskIds,
                task -> false, this::saveToShopifyV3, null);
    }

    public void processVerifyTasksV3() {
        processTasksByShopName("VERIFY", 6, processingVerifyTaskIds,
                this::isVerifyFinal, this::verifySavedDataV3, null);
    }

    private static final class RuntimeChunkPlan {
        final String progressKey;
        final String inputUri;
        final String outputUri;
        final String reportUri;
        /** checkpoint.modules 中的模块目录名 */
        final String moduleName;
        /** 全部 chunk 文件中的序号，从 1 开始 */
        final int chunkOrdinal;

        RuntimeChunkPlan(String progressKey, String inputUri, String outputUri, String reportUri,
                         String moduleName, int chunkOrdinal) {
            this.progressKey = progressKey;
            this.inputUri = inputUri;
            this.outputUri = outputUri;
            this.reportUri = reportUri;
            this.moduleName = moduleName;
            this.chunkOrdinal = chunkOrdinal;
        }
    }

    private JsonRuntimeTranslateRequest shallowCopy(JsonRuntimeTranslateRequest src) {
        JsonRuntimeTranslateRequest d = new JsonRuntimeTranslateRequest();
        if (src == null) {
            return d;
        }
        d.setTaskId(src.getTaskId());
        d.setInputBlobUri(src.getInputBlobUri());
        d.setOutputBlobUri(src.getOutputBlobUri());
        d.setReportBlobUri(src.getReportBlobUri());
        d.setRedisPrefix(src.getRedisPrefix());
        d.setRedisConn(src.getRedisConn());
        d.setProvider(src.getProvider());
        d.setModel(src.getModel());
        d.setApiBase(src.getApiBase());
        d.setApiKey(src.getApiKey());
        d.setSourceLang(src.getSourceLang());
        d.setTargetLang(src.getTargetLang());
        d.setOpenaiUser(src.getOpenaiUser());
        d.setBatchSize(src.getBatchSize());
        d.setMaxCharsPerBatch(src.getMaxCharsPerBatch());
        d.setMaxCompletionTokens(src.getMaxCompletionTokens());
        d.setConcurrency(src.getConcurrency());
        d.setMaxRetries(src.getMaxRetries());
        d.setBaseBackoffMs(src.getBaseBackoffMs());
        return d;
    }

    private List<RuntimeChunkPlan> buildRuntimeChunkPlan(TranslateTaskV3DO task) {
        List<RuntimeChunkPlan> jobs = new ArrayList<>();
        if (task == null) {
            return jobs;
        }
        String shop = safeText(task.getShopName());
        String tid = safeText(task.getId());
        if (shop.isEmpty() || tid.isEmpty()) {
            return jobs;
        }
        Map<String, Object> cp = task.getCheckpoint() == null ? Collections.emptyMap() : task.getCheckpoint();
        List<String> modules = parseCheckpointModulesList(cp.get("modules"));
        if (modules.isEmpty()) {
            return jobs;
        }
        int chunkOrdinal = 1;
        for (String module : modules) {
            List<String> chunkPaths = listInitChunkBlobPaths(shop, tid, module);
            for (String blobPath : chunkPaths) {
                if (blobPath == null || blobPath.isEmpty()) {
                    continue;
                }
                int slash = blobPath.lastIndexOf('/');
                if (slash < 0) {
                    continue;
                }
                String parent = blobPath.substring(0, slash + 1);
                String file = blobPath.substring(slash + 1);
                String baseStem = file.endsWith(".json") ? file.substring(0, file.length() - 5) : file;
                jobs.add(new RuntimeChunkPlan(blobPath, blobPath, parent + "runtime-output-" + baseStem + ".json",
                        parent + "runtime-report-" + baseStem + ".json", module, chunkOrdinal));
                chunkOrdinal++;
            }
        }
        return jobs;
    }

    private String sanitizeChunkHashFieldSuffix(String progressKey) {
        String raw = safeText(progressKey);
        if (raw.isEmpty()) {
            return "";
        }
        try {
            if (raw.length() > 220) {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                byte[] hashed = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder();
                for (byte b : hashed) {
                    sb.append(String.format("%02x", b));
                }
                return sb.substring(0, 48);
            }
            return raw.replace('/', '_');
        } catch (Exception e) {
            return "_chunk_";
        }
    }

    private void patchCosmosRuntimeChunkCheckpoint(TranslateTaskV3DO task,
                                                   int chunkDoneCount,
                                                   int chunksTotal,
                                                   RuntimeLlmTokenUsage cumulativeTok,
                                                   long cumulativeLlmCalls,
                                                   Map<String, Object> slice) {
        if (task == null || slice == null) {
            return;
        }
        Map<String, Object> checkpoint = task.getCheckpoint() == null ? new HashMap<>() : new HashMap<>(task.getCheckpoint());
        checkpoint.put("phase", "RUNTIME_JSON_CHUNK_PROGRESS");
        checkpoint.put("updatedAt", Instant.now().toString());
        checkpoint.put("runtimeChunksDone", chunkDoneCount);
        checkpoint.put("runtimeChunksTotal", chunksTotal);
        String inSlice = safeText(asString(slice.get("inputBlobUri")));
        String outSlice = safeText(asString(slice.get("outputBlobUri")));
        String repSlice = safeText(asString(slice.get("reportBlobUri")));
        if (!inSlice.isEmpty()) {
            checkpoint.put("inputBlobUri", inSlice);
        }
        if (!outSlice.isEmpty()) {
            checkpoint.put("outputBlobUri", outSlice);
        }
        if (!repSlice.isEmpty()) {
            checkpoint.put("reportBlobUri", repSlice);
        }
        checkpoint.put("runtimeLastSlice", slice);
        Map<String, Object> metrics = task.getMetrics() == null ? new HashMap<>() : new HashMap<>(task.getMetrics());
        metrics.put("runtimeChunksDone", chunkDoneCount);
        metrics.put("runtimeChunksTotal", chunksTotal);
        metrics.put("runtimeLlmPromptTokens", cumulativeTok.promptTokens);
        metrics.put("runtimeLlmCompletionTokens", cumulativeTok.completionTokens);
        metrics.put("runtimeLlmTotalTokens", cumulativeTok.totalTokens);
        metrics.put("runtimeLlmApiCallCount", cumulativeLlmCalls);
        metrics.put("updatedAt", Instant.now().toString());
        translateTaskV3CosmosRepo.patchCheckpointAndMetrics(task.getId(), task.getShopName(), checkpoint, metrics);
    }

    private Map<String, Object> executeJsonRuntimeChunked(JsonRuntimeTranslateRequest template,
                                                          TranslateTaskV3DO cosmosTask,
                                                          List<RuntimeChunkPlan> plans,
                                                          String redisPrefix) {
        long startedOuter = System.currentTimeMillis();
        String taskId = safeText(template.getTaskId());

        List<RuntimeQualityPair> rollupQualityPairs = new ArrayList<>();

        translateTaskMonitorV3RedisService.deleteRuntimeTranslationPayloadKeys(redisPrefix, taskId);
        translateTaskMonitorV3RedisService.deleteRuntimePathDoneKey(redisPrefix, taskId);

        RuntimeLlmTokenUsage grandTok = RuntimeLlmTokenUsage.ZERO;
        long grandCalls = 0;
        double totalAgg = 0;
        double doneAgg = 0;
        double failAgg = 0;
        Map<String, Object> lastSlice = Collections.emptyMap();
        String rollupStatus = "COMPLETED";

        translateTaskMonitorV3RedisService.setRuntimeMeta(redisPrefix, taskId, new LinkedHashMap<>(Map.of(
                "updatedAt", Instant.now().toString(),
                "status", "RUNNING",
                "runtimeMode", "chunked",
                "runtimeChunksTotal", String.valueOf(plans.size())
        )));

        int processedSlices = 0;
        List<String> perChunkLines = new ArrayList<>();
        for (RuntimeChunkPlan plan : plans) {
            if (translateTaskMonitorV3RedisService.getRuntimeChunkDoneSet(redisPrefix, taskId).contains(plan.progressKey)) {
                LOG.info("json-runtime chunked skip chunk (redis chunkDone), taskId={}, key={}", taskId, plan.progressKey);
                continue;
            }
            JsonRuntimeTranslateRequest sliceReq = shallowCopy(template);
            sliceReq.setInputBlobUri(plan.inputUri);
            sliceReq.setOutputBlobUri(runtimeOutputUriBesideInput(plan.inputUri, plan.outputUri));
            sliceReq.setReportBlobUri(runtimeOutputUriBesideInput(plan.inputUri, plan.reportUri));

            String suffix = sanitizeChunkHashFieldSuffix(plan.progressKey);
            Map<String, Object> sliceResp = baseRuntimeResponse(taskId,
                    sliceReq.getInputBlobUri(), sliceReq.getOutputBlobUri(), sliceReq.getReportBlobUri());

            sliceResp = translateJsonRuntimeSingleBlob(sliceReq, sliceResp, redisPrefix, startedOuter, suffix,
                    plan.moduleName, plan.chunkOrdinal, plans.size(), rollupQualityPairs);
            processedSlices++;

            String chunkFileOnly = plan.progressKey;
            int ckSlash = chunkFileOnly.lastIndexOf('/');
            if (ckSlash >= 0 && ckSlash < chunkFileOnly.length() - 1) {
                chunkFileOnly = chunkFileOnly.substring(ckSlash + 1);
            }
            perChunkLines.add(String.format("[%d/%d] module=%s file=%s status=%s total=%s done=%s failed=%s",
                    plan.chunkOrdinal, plans.size(), plan.moduleName, chunkFileOnly,
                    safeText(asString(sliceResp.get("status"))),
                    String.valueOf(sliceResp.get("total")),
                    String.valueOf(sliceResp.get("done")),
                    String.valueOf(sliceResp.get("failed"))));

            grandTok = grandTok.add(RuntimeLlmTokenUsage.ofTotals(
                    parseLongFlexible(sliceResp.get("llmPromptTokens")),
                    parseLongFlexible(sliceResp.get("llmCompletionTokens")),
                    parseLongFlexible(sliceResp.get("llmTotalTokens"))));
            grandCalls += parseIntFlexible(sliceResp.get("llmApiCallCount"));
            totalAgg += parseLongFlexible(sliceResp.get("total"));
            doneAgg += parseLongFlexible(sliceResp.get("done"));
            failAgg += parseLongFlexible(sliceResp.get("failed"));
            lastSlice = sliceResp;

            String st = safeText(asString(sliceResp.get("status")));
            if ("FAILED".equals(st)) {
                rollupStatus = failAgg > 0 && processedSlices > 1 ? "PARTIAL_FAILED" : "FAILED";
            } else if ("PARTIAL_FAILED".equals(st) && !"FAILED".equals(rollupStatus)) {
                rollupStatus = "PARTIAL_FAILED";
            }

            if ("COMPLETED".equals(st) || "PARTIAL_FAILED".equals(st)) {
                translateTaskMonitorV3RedisService.addRuntimeChunkDonePath(redisPrefix, taskId, plan.progressKey);
            }

            int redisChunkDoneNow = translateTaskMonitorV3RedisService.getRuntimeChunkDoneSet(redisPrefix, taskId).size();
            patchCosmosRuntimeChunkCheckpoint(cosmosTask, redisChunkDoneNow, plans.size(), grandTok, grandCalls, sliceResp);

            if ("FAILED".equals(st)) {
                LOG.warn("json-runtime chunked halted on FAILED slice, taskId={}, chunk={}", taskId, plan.progressKey);
                break;
            }
        }

        Map<String, Object> aggregate = baseRuntimeResponse(taskId,
                safeText(asString(lastSlice.get("inputBlobUri"))),
                safeText(asString(lastSlice.get("outputBlobUri"))),
                safeText(asString(lastSlice.get("reportBlobUri"))));

        aggregate.put("status", rollupStatus);
        aggregate.put("total", (long) totalAgg);
        aggregate.put("done", (long) doneAgg);
        aggregate.put("failed", (long) failAgg);
        aggregate.put("runtimeChunksTotal", plans.size());
        aggregate.put("runtimeChunkDoneSize", translateTaskMonitorV3RedisService.getRuntimeChunkDoneSet(redisPrefix, taskId).size());
        aggregate.put("durationMs", System.currentTimeMillis() - startedOuter);
        aggregate.put("llmPromptTokens", grandTok.promptTokens);
        aggregate.put("llmCompletionTokens", grandTok.completionTokens);
        aggregate.put("llmTotalTokens", grandTok.totalTokens);
        aggregate.put("llmApiCallCount", grandCalls);
        aggregate.put("lastSlice", lastSlice);

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("status", rollupStatus);
        meta.put("runtimeMode", "chunked");
        meta.put("updatedAt", Instant.now().toString());
        meta.put("runtimeChunksTotal", String.valueOf(plans.size()));
        meta.put("runtimeChunkDoneSize", String.valueOf(aggregate.get("runtimeChunkDoneSize")));
        meta.put("rollupDone", String.valueOf((long) doneAgg));
        meta.put("rollupFailed", String.valueOf((long) failAgg));
        meta.put("llmPromptTokens", String.valueOf(grandTok.promptTokens));
        meta.put("llmCompletionTokens", String.valueOf(grandTok.completionTokens));
        meta.put("llmTotalTokens", String.valueOf(grandTok.totalTokens));
        meta.put("llmApiCallCount", String.valueOf(grandCalls));
        translateTaskMonitorV3RedisService.setRuntimeMeta(redisPrefix, taskId, meta);
        translateTaskMonitorV3RedisService.expireRuntimeKeys(redisPrefix, taskId, JSON_RUNTIME_DEFAULT_TTL_SECONDS);

        long outerElapsedMs = System.currentTimeMillis() - startedOuter;
        int redisChunksDoneNow = translateTaskMonitorV3RedisService.getRuntimeChunkDoneSet(redisPrefix, taskId).size();
        writeJsonRuntimeTranslationSummaryTxt(cosmosTask, true, aggregate,
                outerElapsedMs,
                plans.size(),
                redisChunksDoneNow,
                perChunkLines);

        maybeWriteJsonRuntimeLlmTranslationReport(cosmosTask, template, true, aggregate, outerElapsedMs, plans.size(),
                redisChunksDoneNow, perChunkLines);

        maybeWriteJsonRuntimeQualityReport(cosmosTask, template, rollupQualityPairs);

        maybeSyncCosmosAfterJsonRuntime(taskId, aggregate);
        return aggregate;
    }

    /**
     * 单 input JSON Blob：译文仅内存回填，不写 Redis translate result；
     * {@code chunkHashSuffixOrEmpty} 非空则用 meta {@code fh__{suffix}} 做本分片文件哈希；空则沿用全局字段 {@code fileHash}。
     */
    private Map<String, Object> translateJsonRuntimeSingleBlob(JsonRuntimeTranslateRequest safeRequest,
                                                               Map<String, Object> finalResponse,
                                                               String redisPrefix,
                                                               long outerStartedMsForEta,
                                                               String chunkHashSuffixOrEmpty,
                                                               String runtimeModuleName,
                                                               int chunkOrdinal,
                                                               int runtimeChunksTotal,
                                                               List<RuntimeQualityPair> qualityPairsSinkOrNull) {
        long startedMs = System.currentTimeMillis();
        String taskId = safeText(safeRequest.getTaskId());
        String inputBlobUri = safeText(safeRequest.getInputBlobUri());
        String chunkFileLabel = "-";
        int uriSlash = inputBlobUri.lastIndexOf('/');
        if (uriSlash >= 0 && uriSlash < inputBlobUri.length() - 1) {
            chunkFileLabel = inputBlobUri.substring(uriSlash + 1);
        }
        String rawOutputUri = safeText(safeRequest.getOutputBlobUri());
        String outputBlobUri = runtimeOutputUriBesideInput(inputBlobUri, rawOutputUri);
        safeRequest.setOutputBlobUri(outputBlobUri);
        String rawReportUri = safeText(safeRequest.getReportBlobUri());
        String reportBlobUri = runtimeOutputUriBesideInput(inputBlobUri, rawReportUri);
        safeRequest.setReportBlobUri(reportBlobUri);

        if (taskId.isEmpty() || inputBlobUri.isEmpty() || outputBlobUri.isEmpty() || reportBlobUri.isEmpty()) {
            finalResponse.put("status", "FAILED");
            finalResponse.put("durationMs", System.currentTimeMillis() - startedMs);
            return finalResponse;
        }

        finalResponse.put("inputBlobUri", inputBlobUri);
        finalResponse.put("outputBlobUri", outputBlobUri);
        finalResponse.put("reportBlobUri", reportBlobUri);

        String inputBlobPath = toBlobPath(inputBlobUri);
        String outputBlobPath = toBlobPath(outputBlobUri);
        String reportBlobPath = toBlobPath(reportBlobUri);
        JsonNode root = null;
        Map<String, String> failMap = new LinkedHashMap<>();

        String hashFieldKey = safeText(chunkHashSuffixOrEmpty).isEmpty()
                ? "fileHash"
                : ("fh__" + safeText(chunkHashSuffixOrEmpty));

        try {
            String inputRaw = translateTaskV3BlobRepo.readText(inputBlobPath);
            if (inputRaw == null) {
                failMap.put("/", "INPUT_BLOB_NOT_FOUND");
                return finishRuntimeTaskWithFailure(
                        redisPrefix, taskId, safeRequest, failMap, root, outputBlobPath, reportBlobPath, finalResponse, startedMs);
            }
            byte[] inputBytes = inputRaw.getBytes(StandardCharsets.UTF_8);
            if (inputBytes.length > JSON_RUNTIME_MAX_BYTES) {
                failMap.put("/", "INPUT_TOO_LARGE");
                return finishRuntimeTaskWithFailure(
                        redisPrefix, taskId, safeRequest, failMap, root, outputBlobPath, reportBlobPath, finalResponse, startedMs);
            }
            root = JsonUtils.readTree(inputRaw);
            if (root == null) {
                failMap.put("/", "INPUT_JSON_INVALID");
                return finishRuntimeTaskWithFailure(
                        redisPrefix, taskId, safeRequest, failMap, root, outputBlobPath, reportBlobPath, finalResponse, startedMs);
            }

            String fileHash = sha256(inputRaw);
            Map<String, String> oldMeta = translateTaskMonitorV3RedisService.getRuntimeMeta(redisPrefix, taskId);
            String oldStored = oldMeta.get(hashFieldKey);
            if (!safeText(oldStored).isEmpty() && !oldStored.equals(fileHash)) {
                failMap.put("/", "FILE_HASH_MISMATCH");
                Map<String, Object> mismatchMeta = new LinkedHashMap<>();
                mismatchMeta.put("status", "FAILED");
                mismatchMeta.put("updatedAt", Instant.now().toString());
                translateTaskMonitorV3RedisService.setRuntimeMeta(redisPrefix, taskId, mismatchMeta);
                return finishRuntimeTaskWithFailure(
                        redisPrefix, taskId, safeRequest, failMap, root, outputBlobPath, reportBlobPath, finalResponse, startedMs);
            }

            int batchSize = positiveOrDefault(safeRequest.getBatchSize(), JSON_RUNTIME_DEFAULT_BATCH_SIZE);
            int maxCharsPerBatch = positiveOrDefault(safeRequest.getMaxCharsPerBatch(), JSON_RUNTIME_DEFAULT_MAX_CHARS_PER_BATCH);
            int concurrency = positiveOrDefault(safeRequest.getConcurrency(), JSON_RUNTIME_DEFAULT_CONCURRENCY);
            int maxRetries = positiveOrDefault(safeRequest.getMaxRetries(), JSON_RUNTIME_DEFAULT_MAX_RETRIES);
            int baseBackoffMs = positiveOrDefault(safeRequest.getBaseBackoffMs(), JSON_RUNTIME_DEFAULT_BASE_BACKOFF_MS);

            Map<String, Object> runningMeta = new LinkedHashMap<>();
            runningMeta.put("status", "RUNNING");
            runningMeta.put("updatedAt", Instant.now().toString());
            runningMeta.put("currentInputBlobUri", inputBlobUri);
            runningMeta.put("currentOutputBlobUri", outputBlobUri);
            runningMeta.put("currentReportBlobUri", reportBlobUri);
            runningMeta.put(hashFieldKey, fileHash);
            translateTaskMonitorV3RedisService.setRuntimeMeta(redisPrefix, taskId, runningMeta);

            List<RuntimeSourceItem> allItems = new ArrayList<>();
            List<RuntimeSkippedItem> skippedItems = new ArrayList<>();
            collectSourceValueItems(root, "", allItems, skippedItems);
            if (allItems.stream().anyMatch(it -> it.path.contains(RUNTIME_INNER_JSON_MARKER))) {
                LOG.info("json-runtime expanded stringified JSON inside sourceValue into {} leaf paths, taskId={}",
                        allItems.size(), taskId);
            }
            if (!skippedItems.isEmpty()) {
                int sampleSize = Math.min(skippedItems.size(), 20);
                LOG.warn("json-runtime skip non-text sourceValue, taskId={}, skipCount={}, samples={}",
                        taskId, skippedItems.size(), skippedItems.subList(0, sampleSize));
            }
            int totalCount = allItems.size();
            translateTaskMonitorV3RedisService.setRuntimeMeta(redisPrefix, taskId, Map.of(
                    "totalCountThisBlob", Integer.toString(totalCount),
                    "updatedAt", Instant.now().toString()
            ));

            List<RuntimeSourceItem> pending = new ArrayList<>(allItems);

            LinkedHashMap<String, String> translationAcc = new LinkedHashMap<>();
            int effectiveDone = 0;
            int failCount = 0;
            RuntimeLlmTokenUsage tokenTotal = RuntimeLlmTokenUsage.ZERO;
            int llmApiCallCount = 0;

            if (!pending.isEmpty()) {
                List<List<RuntimeSourceItem>> batches = splitRuntimeBatches(pending, batchSize, maxCharsPerBatch);
                ExecutorService executor = Executors.newFixedThreadPool(Math.max(1, concurrency));
                CompletionService<RuntimeBatchResult> completionService = new ExecutorCompletionService<>(executor);
                try {
                    for (List<RuntimeSourceItem> batch : batches) {
                        completionService.submit(() -> processRuntimeBatchWithRetry(batch, safeRequest, maxRetries, baseBackoffMs));
                    }
                    for (int i = 0; i < batches.size(); i++) {
                        Future<RuntimeBatchResult> future = completionService.take();
                        RuntimeBatchResult batchResult = future.get();
                        if (!batchResult.successMap.isEmpty()) {
                            translationAcc.putAll(batchResult.successMap);
                            effectiveDone += batchResult.successMap.size();
                        }
                        if (!batchResult.failMap.isEmpty()) {
                            translateTaskMonitorV3RedisService.setRuntimeFailMap(redisPrefix, taskId,
                                    prefixRuntimeFailKeysForChunk(batchResult.failMap, chunkFileLabel,
                                            chunkOrdinal, runtimeChunksTotal));
                            translateTaskMonitorV3RedisService.incrementRuntimeFailCount(redisPrefix, taskId, batchResult.failMap.size());
                            failCount += batchResult.failMap.size();
                        }
                        tokenTotal = tokenTotal.add(batchResult.llmUsage);
                        if (batchResult.llmUsage.hasNonZero()) {
                            llmApiCallCount++;
                        }
                        translateTaskMonitorV3RedisService.setRuntimeMeta(redisPrefix, taskId, Map.of(
                                "updatedAt", Instant.now().toString(),
                                "status", "RUNNING",
                                "currentDoneThisBlob", String.valueOf(effectiveDone),
                                "currentFailThisBlob", String.valueOf(failCount),
                                "llmPromptTokens", String.valueOf(tokenTotal.promptTokens),
                                "llmCompletionTokens", String.valueOf(tokenTotal.completionTokens),
                                "llmTotalTokens", String.valueOf(tokenTotal.totalTokens),
                                "llmApiCallCount", String.valueOf(llmApiCallCount)
                        ));

                        long elapsedOuter = System.currentTimeMillis() - outerStartedMsForEta;
                        int finished = Math.min(totalCount, effectiveDone + failCount);
                        String modLabel = safeText(runtimeModuleName).isEmpty() ? "-" : runtimeModuleName;
                        int chunksTot = runtimeChunksTotal > 0 ? runtimeChunksTotal : 1;
                        int chunkIdx = chunkOrdinal > 0 ? chunkOrdinal : 1;
                        LOG.info("json-runtime blob batch progress taskId={}, module={}, chunk={}/{}, chunkFile={}, done={}/{}, failed={}, eta={}s, llmCalls={}",
                                taskId, modLabel, chunkIdx, chunksTot, chunkFileLabel,
                                effectiveDone, totalCount, failCount,
                                estimateEtaSeconds(totalCount, finished, elapsedOuter), llmApiCallCount);
                    }
                } finally {
                    executor.shutdown();
                }
            }

            for (Map.Entry<String, String> entry : translationAcc.entrySet()) {
                setTextByPointer(root, entry.getKey(), entry.getValue());
            }

            String prettyJson = JsonUtils.OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(root);
            boolean outputSaved = translateTaskV3BlobRepo.writeText(outputBlobPath, prettyJson);
            if (!outputSaved) {
                failMap.put("/", "OUTPUT_BLOB_WRITE_FAILED");
            }

            Map<String, String> redisFailMap = translateTaskMonitorV3RedisService.getRuntimeFailMap(redisPrefix, taskId);
            // 合并 Redis 前 failMap 仅含本分片结构性错误（如写 Blob 失败）；翻译失败只记在 failCount / Redis hash 里
            int structuralFailKeysBeforeRedisMerge = failMap.size();
            failMap.putAll(redisFailMap);
            // 禁止用合并后的 failMap.size()：Redis 含所有 chunk 的累积键，会与本分片 total/done 不一致（例如 total=90 done=89 failed 显示成 2）
            int failedBucket = failCount + structuralFailKeysBeforeRedisMerge;
            Map<String, String> pathToSource = new LinkedHashMap<>();
            for (RuntimeSourceItem it : allItems) {
                pathToSource.put(it.path, it.text == null ? "" : it.text);
            }
            if (qualityPairsSinkOrNull != null && !translationAcc.isEmpty()) {
                for (Map.Entry<String, String> e : translationAcc.entrySet()) {
                    String p = e.getKey();
                    qualityPairsSinkOrNull.add(new RuntimeQualityPair(p,
                            pathToSource.getOrDefault(p, ""),
                            e.getValue() == null ? "" : e.getValue()));
                }
            }
            String[] shopTask = parseTasksBlobShopAndTaskId(inputBlobPath);
            if (shopTask != null && !failMap.isEmpty()) {
                mergeWriteChunksFailedJson(shopTask[0], shopTask[1], runtimeModuleName, chunkFileLabel,
                        chunkOrdinal, failMap, pathToSource, root);
            }

            String status = failedBucket == 0 ? "COMPLETED" : "PARTIAL_FAILED";
            if (!outputSaved) {
                status = "FAILED";
            }

            writeRuntimeReportBlob(taskId, status, totalCount, translationAcc.size(), failedBucket,
                    startedMs, failMap, reportBlobPath, tokenTotal, llmApiCallCount);

            Map<String, Object> finalMeta = new LinkedHashMap<>();
            finalMeta.put("status", status);
            finalMeta.put("inputBlobUri", inputBlobUri);
            finalMeta.put("outputBlobUri", outputBlobUri);
            finalMeta.put("reportBlobUri", reportBlobUri);
            finalMeta.put("totalCountThisBlob", totalCount);
            finalMeta.put("doneCountThisBlob", translationAcc.size());
            finalMeta.put("failCountThisBlob", failedBucket);
            finalMeta.put("currentFailThisBlob", String.valueOf(failedBucket));
            finalMeta.put(hashFieldKey, fileHash);
            finalMeta.put("updatedAt", Instant.now().toString());
            finalMeta.put("llmPromptTokens", String.valueOf(tokenTotal.promptTokens));
            finalMeta.put("llmCompletionTokens", String.valueOf(tokenTotal.completionTokens));
            finalMeta.put("llmTotalTokens", String.valueOf(tokenTotal.totalTokens));
            finalMeta.put("llmApiCallCount", String.valueOf(llmApiCallCount));
            translateTaskMonitorV3RedisService.setRuntimeMeta(redisPrefix, taskId, finalMeta);
            translateTaskMonitorV3RedisService.expireRuntimeKeys(redisPrefix, taskId, JSON_RUNTIME_DEFAULT_TTL_SECONDS);

            finalResponse.put("status", status);
            finalResponse.put("total", totalCount);
            finalResponse.put("done", translationAcc.size());
            finalResponse.put("failed", failedBucket);
            finalResponse.put("durationMs", System.currentTimeMillis() - startedMs);
            finalResponse.put("llmPromptTokens", tokenTotal.promptTokens);
            finalResponse.put("llmCompletionTokens", tokenTotal.completionTokens);
            finalResponse.put("llmTotalTokens", tokenTotal.totalTokens);
            finalResponse.put("llmApiCallCount", llmApiCallCount);
            return finalResponse;
        } catch (Exception e) {
            TraceReporterHolder.report("TranslateV3Service.translateJsonRuntimeSingleBlob",
                    "FatalException json runtime blob taskId=" + taskId + " error=" + e);
            failMap.put("/", "RUNTIME_EXCEPTION:" + e.getClass().getSimpleName());
            return finishRuntimeTaskWithFailure(
                    redisPrefix, taskId, safeRequest, failMap, root, outputBlobPath, reportBlobPath, finalResponse, startedMs);
        }
    }

    private static long parseLongFlexible(Object o) {
        if (o == null) {
            return 0L;
        }
        if (o instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(safeText(o.toString()));
        } catch (Exception e) {
            return 0L;
        }
    }

    private static int parseIntFlexible(Object o) {
        return (int) Math.min(parseLongFlexible(o), Integer.MAX_VALUE);
    }

    public Map<String, Object> executeJsonRuntimeTask(JsonRuntimeTranslateRequest request) {
        JsonRuntimeTranslateRequest safeRequest = request == null ? new JsonRuntimeTranslateRequest() : request;
        applyDefaultRuntimeLlmConfigIfMissing(safeRequest);
        if (StringUtils.isEmpty(safeRequest.getOpenaiUser()) && !safeText(safeRequest.getTaskId()).isEmpty()) {
            String u = safeText(safeRequest.getTaskId());
            safeRequest.setOpenaiUser(u.length() > 128 ? u.substring(0, 128) : u);
        }
        long startedMs = System.currentTimeMillis();
        String taskId = safeText(safeRequest.getTaskId());
        String redisPrefix = safeText(safeRequest.getRedisPrefix());
        if (redisPrefix.isEmpty()) {
            redisPrefix = "tr:v1";
        }
        TranslateTaskV3DO cosmosTask = null;
        if (!taskId.isEmpty()) {
            TranslateTaskV3DO t = findTaskByIdAcrossStatuses(taskId);
            if (t != null && isRuntimeJsonTask(t)) {
                cosmosTask = t;
            }
        }
        List<RuntimeChunkPlan> runtimeChunkPlans = cosmosTask != null ? buildRuntimeChunkPlan(cosmosTask) : Collections.emptyList();
        if (!runtimeChunkPlans.isEmpty()) {
            return executeJsonRuntimeChunked(safeRequest, cosmosTask, runtimeChunkPlans, redisPrefix);
        }

        String inputBlobUri = safeText(safeRequest.getInputBlobUri());
        String rawOutputUri = safeText(safeRequest.getOutputBlobUri());
        String outputBlobUri = runtimeOutputUriBesideInput(inputBlobUri, rawOutputUri);
        if (!outputBlobUri.equals(rawOutputUri)) {
            LOG.info("json-runtime output co-located with input directory: {} -> {}", rawOutputUri, outputBlobUri);
        }
        safeRequest.setOutputBlobUri(outputBlobUri);
        String reportBlobUri = safeText(safeRequest.getReportBlobUri());

        Map<String, Object> finalResponse = baseRuntimeResponse(taskId, inputBlobUri, outputBlobUri, reportBlobUri);
        if (taskId.isEmpty() || inputBlobUri.isEmpty() || outputBlobUri.isEmpty() || reportBlobUri.isEmpty()) {
            finalResponse.put("status", "FAILED");
            finalResponse.put("durationMs", System.currentTimeMillis() - startedMs);
            maybeSyncCosmosAfterJsonRuntime(taskId, finalResponse);
            return finalResponse;
        }

        List<RuntimeQualityPair> singleQualityPairs = new ArrayList<>();
        Map<String, Object> result = translateJsonRuntimeSingleBlob(
                safeRequest, finalResponse, redisPrefix, startedMs, "", "", 1, 1, singleQualityPairs);
        if (cosmosTask != null) {
            List<String> oneChunk = new ArrayList<>();
            String inputUri = safeText(asString(result.get("inputBlobUri")));
            String fn = inputUri;
            int us = fn.lastIndexOf('/');
            if (us >= 0 && us < fn.length() - 1) {
                fn = fn.substring(us + 1);
            }
            oneChunk.add(String.format("[1/1] file=%s status=%s total=%s done=%s failed=%s",
                    fn.isEmpty() ? "-" : fn,
                    safeText(asString(result.get("status"))),
                    String.valueOf(result.get("total")),
                    String.valueOf(result.get("done")),
                    String.valueOf(result.get("failed"))));
            long singleDurMs = parseLongFlexible(result.get("durationMs"));
            writeJsonRuntimeTranslationSummaryTxt(cosmosTask, false, result,
                    singleDurMs,
                    1,
                    1,
                    oneChunk);
            maybeWriteJsonRuntimeLlmTranslationReport(cosmosTask, safeRequest, false, result, singleDurMs, 1, 1, oneChunk);
            maybeWriteJsonRuntimeQualityReport(cosmosTask, safeRequest, singleQualityPairs);
        }
        maybeSyncCosmosAfterJsonRuntime(taskId, result);
        return result;
    }

    /**
     * 将 runtime 最终结果同步到 Cosmos：与 Redis/Blob 一致。无论通过定时任务、HTTP 还是 Agent 触发，只要 Cosmos 中存在同 id 的 json-runtime 任务即会 patch。
     */
    private void maybeSyncCosmosAfterJsonRuntime(String taskId, Map<String, Object> result) {
        if (safeText(taskId).isEmpty() || result == null) {
            return;
        }
        TranslateTaskV3DO task = findTaskByIdAcrossStatuses(taskId);
        if (task == null || !isRuntimeJsonTask(task) || !taskId.equals(task.getId())) {
            return;
        }
        String status = asString(result.get("status"));
        Map<String, Object> checkpoint = task.getCheckpoint() == null ? new HashMap<>() : new HashMap<>(task.getCheckpoint());
        checkpoint.put("phase", "RUNTIME_JSON_" + status);
        checkpoint.put("updatedAt", Instant.now().toString());
        checkpoint.put("runtimeResult", result);
        String in = asString(result.get("inputBlobUri"));
        String out = asString(result.get("outputBlobUri"));
        String rep = asString(result.get("reportBlobUri"));
        if (!in.isEmpty()) {
            checkpoint.put("inputBlobUri", in);
        }
        if (!out.isEmpty()) {
            checkpoint.put("outputBlobUri", out);
        }
        if (!rep.isEmpty()) {
            checkpoint.put("reportBlobUri", rep);
        }
        Map<String, Object> metrics = task.getMetrics() == null ? new HashMap<>() : new HashMap<>(task.getMetrics());
        metrics.put("totalCount", parseInt(asString(result.get("total"))));
        metrics.put("translatedCount", parseInt(asString(result.get("done"))));
        metrics.put("failedCount", parseInt(asString(result.get("failed"))));
        metrics.put("durationMs", parseInt(asString(result.get("durationMs"))));
        metrics.put("updatedAt", Instant.now().toString());
        long totalTok = parseLongMeta(result.get("llmTotalTokens"));
        metrics.put("llmPromptTokens", parseLongMeta(result.get("llmPromptTokens")));
        metrics.put("llmCompletionTokens", parseLongMeta(result.get("llmCompletionTokens")));
        metrics.put("llmTotalTokens", totalTok);
        metrics.put("llmApiCallCount", parseLongMeta(result.get("llmApiCallCount")));
        if (totalTok > 0) {
            metrics.put("usedToken", (int) Math.min(totalTok, Integer.MAX_VALUE));
        }
        translateTaskV3CosmosRepo.patchCheckpointAndMetrics(task.getId(), task.getShopName(), checkpoint, metrics);
        if ("COMPLETED".equals(status) || "PARTIAL_FAILED".equals(status)) {
            translateTaskV3CosmosRepo.patchStatus(task.getId(), task.getShopName(), 2);
        } else {
            translateTaskV3CosmosRepo.patchStatus(task.getId(), task.getShopName(), 4);
        }
    }

    private boolean isRuntimeJsonTask(TranslateTaskV3DO task) {
        if (task == null) {
            return false;
        }
        String taskType = task.getTaskType();
        if (StringUtils.isEmpty(taskType)) {
            return false;
        }
        return "spark".equalsIgnoreCase(taskType) || "json-runtime".equalsIgnoreCase(taskType);
    }

    private JsonRuntimeTranslateRequest buildRuntimeRequestFromTask(TranslateTaskV3DO task) {
        Map<String, Object> checkpoint = task.getCheckpoint() == null ? new HashMap<>() : task.getCheckpoint();
        JsonRuntimeTranslateRequest request = new JsonRuntimeTranslateRequest();
        request.setTaskId(task.getId());
        request.setInputBlobUri(asString(checkpoint.get("inputBlobUri")));
        request.setOutputBlobUri(asString(checkpoint.get("outputBlobUri")));
        request.setReportBlobUri(asString(checkpoint.get("reportBlobUri")));
        request.setRedisPrefix(asString(checkpoint.get("redisPrefix")));
        request.setRedisConn(asString(checkpoint.get("redisConn")));
        request.setProvider(asString(checkpoint.get("provider")));
        request.setModel(asString(checkpoint.get("model")));
        request.setApiBase(asString(checkpoint.get("apiBase")));
        request.setApiKey(asString(checkpoint.get("apiKey")));
        request.setSourceLang(asString(checkpoint.get("sourceLang")));
        request.setTargetLang(asString(checkpoint.get("targetLang")));
        String openaiUser = safeText(asString(checkpoint.get("openaiUser")));
        if (openaiUser.isEmpty()) {
            openaiUser = safeText(task.getSessionId());
        }
        if (!openaiUser.isEmpty() && openaiUser.length() > 128) {
            openaiUser = openaiUser.substring(0, 128);
        }
        request.setOpenaiUser(openaiUser);
        request.setBatchSize(parseIntOrNull(checkpoint.get("batchSize")));
        request.setMaxCharsPerBatch(parseIntOrNull(checkpoint.get("maxCharsPerBatch")));
        request.setMaxCompletionTokens(parseIntOrNull(checkpoint.get("maxCompletionTokens")));
        request.setConcurrency(parseIntOrNull(checkpoint.get("concurrency")));
        request.setMaxRetries(parseIntOrNull(checkpoint.get("maxRetries")));
        request.setBaseBackoffMs(parseIntOrNull(checkpoint.get("baseBackoffMs")));
        if (StringUtils.isEmpty(request.getSourceLang())) {
            request.setSourceLang(safeText(task.getSource()));
        }
        if (StringUtils.isEmpty(request.getTargetLang())) {
            request.setTargetLang(safeText(task.getTarget()));
        }
        ensureRuntimeBlobUrisFromInitArtifacts(task, request);
        applyDefaultRuntimeLlmConfigIfMissing(request);
        return request;
    }

    /**
     * init 仅写入 modules/metrics，通常不带 provider/model/apiBase/apiKey；从 Spring 与 {@link ConfigUtils} 回填（与 {@code com.bogda.agenttask.config.JsonRuntimeAgentConfig} 同源），避免整批 {@code INVALID_PROVIDER_CONFIG}。
     */
    private void applyDefaultRuntimeLlmConfigIfMissing(JsonRuntimeTranslateRequest request) {
        if (request == null) {
            return;
        }
        boolean hadGap = StringUtils.isEmpty(request.getProvider())
                || StringUtils.isEmpty(request.getModel())
                || StringUtils.isEmpty(request.getApiBase())
                || StringUtils.isEmpty(request.getApiKey());
        if (StringUtils.isEmpty(request.getProvider())) {
            request.setProvider("openai-compatible");
        }
        if (StringUtils.isEmpty(request.getModel())) {
            request.setModel(firstNonEmptyCfg(jsonRuntimeFallbackModelName,
                    ConfigUtils.getConfig("langchain4j.openai.model-name"), "deepseek-chat"));
        }
        if (StringUtils.isEmpty(request.getApiBase())) {
            request.setApiBase(firstNonEmptyCfg(jsonRuntimeFallbackBaseUrl,
                    ConfigUtils.getConfig("langchain4j.openai.base-url"),
                    "https://api.deepseek.com/v1"));
        }
        if (StringUtils.isEmpty(request.getApiKey())) {
            request.setApiKey(firstNonEmptyCfg(jsonRuntimeFallbackApiKey,
                    ConfigUtils.getConfig("langchain4j.openai.api-key"),
                    ConfigUtils.getConfig("DEEPSEEK_API_KEY"),
                    ConfigUtils.getConfig("deepseek")));
        }
        if (hadGap) {
            LOG.info("json-runtime LLM defaults applied where checkpoint/request omitted fields, model={}, apiKeyConfigured={}",
                    request.getModel(), !StringUtils.isEmpty(request.getApiKey()));
        }
    }

    private static String firstNonEmptyCfg(String... values) {
        if (values == null) {
            return "";
        }
        for (String v : values) {
            if (v != null && !v.trim().isEmpty()) {
                return v.trim();
            }
        }
        return "";
    }

    /**
     * 当 checkpoint 未写 Blob URI 时，按 {@link #initialToTranslateTaskV3} / {@link #dumpModuleToBlob} 落盘规则解析：
     * {@code tasks/{shop}/{taskId}/chunks/{module}/chunk-{n}.json}。
     * 可选覆盖：checkpoint.runtimeModule、checkpoint.runtimeChunkIndex（从 0 开始）。
     * 输出/报告默认与同目录：runtime-output-{chunkStem}.json、runtime-report-{chunkStem}.json。
     */
    private void ensureRuntimeBlobUrisFromInitArtifacts(TranslateTaskV3DO task, JsonRuntimeTranslateRequest request) {
        if (task == null || request == null) {
            return;
        }
        String shop = safeText(task.getShopName());
        String tid = safeText(task.getId());
        if (shop.isEmpty() || tid.isEmpty()) {
            return;
        }
        Map<String, Object> cp = task.getCheckpoint() == null ? Collections.emptyMap() : task.getCheckpoint();

        if (safeText(request.getInputBlobUri()).isEmpty()) {
            List<String> modules = parseCheckpointModulesList(cp.get("modules"));
            if (modules.isEmpty()) {
                LOG.debug("json-runtime derive blob: checkpoint.modules empty, taskId={}", tid);
                return;
            }
            String runtimeModule = safeText(asString(cp.get("runtimeModule")));
            String module = (!runtimeModule.isEmpty() && modules.contains(runtimeModule)) ? runtimeModule : modules.get(0);
            List<String> chunkPaths = listInitChunkBlobPaths(shop, tid, module);
            if (chunkPaths.isEmpty()) {
                LOG.warn("json-runtime derive blob: no chunk-*.json under module={}, taskId={}", module, tid);
                return;
            }
            Integer idx = parseIntOrNull(cp.get("runtimeChunkIndex"));
            String chosen;
            if (idx != null && idx >= 0) {
                chosen = chunkPaths.stream()
                        .filter(p -> p.endsWith("chunk-" + idx + ".json"))
                        .findFirst()
                        .orElse(chunkPaths.get(0));
            } else {
                chosen = chunkPaths.get(0);
            }
            request.setInputBlobUri(chosen);
            LOG.info("json-runtime derived inputBlobUri from init layout, taskId={}, path={}", tid, chosen);
        }

        String inPath = toBlobPath(safeText(request.getInputBlobUri()));
        if (inPath.isEmpty()) {
            return;
        }
        int slash = inPath.lastIndexOf('/');
        if (slash < 0) {
            return;
        }
        String parent = inPath.substring(0, slash + 1);
        String file = inPath.substring(slash + 1);
        String baseStem = file.endsWith(".json") ? file.substring(0, file.length() - 5) : file;
        if (safeText(request.getOutputBlobUri()).isEmpty()) {
            request.setOutputBlobUri(parent + "runtime-output-" + baseStem + ".json");
            LOG.info("json-runtime derived outputBlobUri, taskId={}, path={}", tid, request.getOutputBlobUri());
        }
        if (safeText(request.getReportBlobUri()).isEmpty()) {
            request.setReportBlobUri(parent + "runtime-report-" + baseStem + ".json");
            LOG.info("json-runtime derived reportBlobUri, taskId={}, path={}", tid, request.getReportBlobUri());
        }
    }

    private List<String> listInitChunkBlobPaths(String shopName, String taskId, String module) {
        String prefix = blobPath(shopName, taskId, "chunks/" + module + "/");
        List<String> paths = translateTaskV3BlobRepo.listBlobPaths(prefix);
        if (paths == null || paths.isEmpty()) {
            return Collections.emptyList();
        }
        return paths.stream()
                .filter(p -> {
                    int s = p.lastIndexOf('/');
                    String name = s >= 0 ? p.substring(s + 1) : p;
                    return name.startsWith("chunk-") && name.endsWith(".json");
                })
                .sorted()
                .collect(Collectors.toList());
    }

    private List<String> parseCheckpointModulesList(Object modulesObj) {
        if (modulesObj == null) {
            return Collections.emptyList();
        }
        if (modulesObj instanceof List<?> rawList) {
            List<String> out = new ArrayList<>();
            for (Object o : rawList) {
                if (o == null) {
                    continue;
                }
                String s = safeText(asString(o.toString()));
                if (!s.isEmpty()) {
                    out.add(s);
                }
            }
            return out;
        }
        if (modulesObj instanceof String str && !str.isEmpty()) {
            List<String> parsed = JsonUtils.jsonToObject(str, new TypeReference<List<String>>() {
            });
            if (parsed == null) {
                return Collections.emptyList();
            }
            return parsed.stream().filter(x -> x != null && !safeText(x).isEmpty()).collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    /**
     * Redis fail hash 以 path 为 field；跨 chunk 若 JSON pointer 相同会互相覆盖。
     * 多分块或能从 Blob 文件名区分时，为 key 加上 chunk 文件名前缀。
     */
    private static Map<String, String> prefixRuntimeFailKeysForChunk(Map<String, String> failMap,
                                                                     String chunkFileLabel,
                                                                     int chunkOrdinal,
                                                                     int chunksTotal) {
        if (failMap == null || failMap.isEmpty()) {
            return failMap;
        }
        String label = safeText(chunkFileLabel);
        boolean multiChunk = chunksTotal > 1 || chunkOrdinal > 1;
        if (!multiChunk && (label.isEmpty() || "-".equals(label))) {
            return failMap;
        }
        String scope = (!label.isEmpty() && !"-".equals(label)) ? label : ("chunk#" + chunkOrdinal);
        String pfx = scope + "::";
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : failMap.entrySet()) {
            if (e.getKey() == null) {
                continue;
            }
            out.put(pfx + e.getKey(), e.getValue() == null ? "UNKNOWN_ERROR" : e.getValue());
        }
        return out;
    }

    private Map<String, Object> finishRuntimeTaskWithFailure(String redisPrefix,
                                                             String taskId,
                                                             JsonRuntimeTranslateRequest request,
                                                             Map<String, String> failMap,
                                                             JsonNode root,
                                                             String outputBlobPath,
                                                             String reportBlobPath,
                                                             Map<String, Object> finalResponse,
                                                             long startedMs) {
        String inputBlobUri = request == null ? "" : safeText(request.getInputBlobUri());
        String outputBlobUri = request == null ? "" : safeText(request.getOutputBlobUri());
        String reportBlobUri = request == null ? "" : safeText(request.getReportBlobUri());

        if (root != null) {
            try {
                String rawOutput = JsonUtils.OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(root);
                translateTaskV3BlobRepo.writeText(outputBlobPath, rawOutput);
            } catch (Exception ignored) {
            }
        }

        if (!failMap.isEmpty()) {
            translateTaskMonitorV3RedisService.setRuntimeFailMap(redisPrefix, taskId, failMap);
        }
            writeRuntimeReportBlob(taskId, "FAILED", 0, 0, failMap.size(), startedMs, failMap, reportBlobPath,
                    RuntimeLlmTokenUsage.ZERO, 0);

        String inPathEarly = toBlobPath(inputBlobUri);
        String[] shopTaskEarly = parseTasksBlobShopAndTaskId(inPathEarly);
        if (shopTaskEarly != null && !failMap.isEmpty()) {
            mergeWriteChunksFailedJson(shopTaskEarly[0], shopTaskEarly[1], "", "-", 0,
                    failMap, Collections.emptyMap(), root);
        }

        Map<String, Object> failedMeta = new LinkedHashMap<>();
        failedMeta.put("status", "FAILED");
        failedMeta.put("inputBlobUri", inputBlobUri);
        failedMeta.put("outputBlobUri", outputBlobUri);
        failedMeta.put("reportBlobUri", reportBlobUri);
        failedMeta.put("updatedAt", Instant.now().toString());
        failedMeta.put("failCount", failMap.size());
        translateTaskMonitorV3RedisService.setRuntimeMeta(redisPrefix, taskId, failedMeta);
        translateTaskMonitorV3RedisService.expireRuntimeKeys(redisPrefix, taskId, JSON_RUNTIME_DEFAULT_TTL_SECONDS);

        finalResponse.put("status", "FAILED");
        finalResponse.put("failed", failMap.size());
        finalResponse.put("durationMs", System.currentTimeMillis() - startedMs);
        maybeSyncCosmosAfterJsonRuntime(taskId, finalResponse);
        return finalResponse;
    }

    private RuntimeBatchResult processRuntimeBatchWithRetry(List<RuntimeSourceItem> batch,
                                                            JsonRuntimeTranslateRequest request,
                                                            int maxRetries,
                                                            int baseBackoffMs) {
        int attempts = 0;
        RuntimeModelException lastError = null;
        while (attempts < maxRetries) {
            attempts++;
            long begin = System.currentTimeMillis();
            try {
                // Step 4: 单批次调用模型翻译；返回 path->translatedText 映射。
                Map<String, String> sourceMap = new LinkedHashMap<>();
                for (RuntimeSourceItem item : batch) {
                    sourceMap.put(item.path, item.text);
                }
                RuntimeModelTranslateOutcome outcome = requestRuntimeModelTranslate(sourceMap, request);
                Map<String, String> translated = outcome.translations;
                RuntimeLlmTokenUsage batchUsage = outcome.usage == null ? RuntimeLlmTokenUsage.ZERO : outcome.usage;
                Map<String, String> successMap = new LinkedHashMap<>();
                Map<String, String> failMap = new LinkedHashMap<>();
                for (RuntimeSourceItem item : batch) {
                    String translatedText = translated.get(item.path);
                    if (translatedText == null) {
                        failMap.put(item.path, "MISSING_PATH_IN_MODEL_RESPONSE");
                    } else {
                        successMap.put(item.path, translatedText);
                    }
                }
                return new RuntimeBatchResult(successMap, failMap, System.currentTimeMillis() - begin, batchUsage);
            } catch (RuntimeModelException e) {
                lastError = e;
                if (!e.retryable || attempts >= maxRetries) {
                    break;
                }
                // 429/5xx/网络异常使用指数退避 + 抖动。
                long sleep = computeBackoffSleepMs(baseBackoffMs, attempts);
                try {
                    Thread.sleep(sleep);
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        Map<String, String> failMap = new LinkedHashMap<>();
        String reason = lastError == null ? "UNKNOWN_BATCH_ERROR" : lastError.message;
        for (RuntimeSourceItem item : batch) {
            failMap.put(item.path, reason);
        }
        return new RuntimeBatchResult(new LinkedHashMap<>(), failMap, 0L, RuntimeLlmTokenUsage.ZERO);
    }

    private String buildJsonRuntimeSystemPrompt(JsonRuntimeTranslateRequest request) {
        String src = safeText(request.getSourceLang());
        String tgt = safeText(request.getTargetLang());
        if (jsonRuntimeCompactSystemPrompt) {
            return "Translate JSON values " + src + "→" + tgt + ". Input: object path→text. Output: JSON, same keys only, no prose.";
        }
        return "You are a translation engine. Translate text values from " + src + " to " + tgt
                + ". Input is a JSON object path->text. Return strict JSON object with the exact same keys only.";
    }

    private RuntimeModelTranslateOutcome requestRuntimeModelTranslate(Map<String, String> sourceMap,
                                                                       JsonRuntimeTranslateRequest request) throws RuntimeModelException {
        if (sourceMap == null || sourceMap.isEmpty()) {
            return new RuntimeModelTranslateOutcome(new LinkedHashMap<>(), RuntimeLlmTokenUsage.ZERO);
        }
        String provider = safeText(request.getProvider()).toLowerCase();
        String model = safeText(request.getModel());
        String apiBase = safeText(request.getApiBase());
        String apiKey = safeText(request.getApiKey());
        if (provider.isEmpty() || model.isEmpty() || apiBase.isEmpty() || apiKey.isEmpty()) {
            throw new RuntimeModelException(false, "INVALID_PROVIDER_CONFIG");
        }

        String endpoint = buildRuntimeEndpoint(provider, model, apiBase);
        // Chat Completions 为无状态：每一批都要带完整 messages；业务 sessionId / openaiUser 不会让网关少计 prompt tokens。
        String systemPrompt = buildJsonRuntimeSystemPrompt(request);
        Map<String, Object> body = new LinkedHashMap<>();
        if (!"azure-openai".equals(provider)) {
            body.put("model", model);
        }
        body.put("temperature", 0);
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt));
        messages.add(Map.of("role", "user", "content", JsonUtils.objectToJson(sourceMap)));
        body.put("messages", messages);
        String traceUser = safeText(request.getOpenaiUser());
        if (!traceUser.isEmpty()) {
            body.put("user", traceUser.length() > 128 ? traceUser.substring(0, 128) : traceUser);
        }
        Integer maxOut = request.getMaxCompletionTokens();
        if (maxOut != null && maxOut > 0) {
            // OpenAI 兼容 Chat Completions；与 maxCharsPerBatch 不同，这是「单次补全」输出 token 上限。
            body.put("max_tokens", maxOut);
        } else {
            String payloadJson = JsonUtils.objectToJson(sourceMap);
            int inChars = payloadJson == null ? 0 : payloadJson.length();
            // 模型返回与输入同 keys 的 JSON，体量相近；默认给足 completion 余量避免截断无法 parse
            int derived = Math.min(32768, Math.max(4096, (int) (inChars * 1.25) + 4096));
            body.put("max_tokens", derived);
        }

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(JSON_RUNTIME_HTTP_TIMEOUT_SECONDS))
                .POST(HttpRequest.BodyPublishers.ofString(JsonUtils.objectToJson(body), StandardCharsets.UTF_8));
        if ("azure-openai".equals(provider)) {
            builder.header("api-key", apiKey);
        } else {
            builder.header("Authorization", "Bearer " + apiKey);
        }

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(JSON_RUNTIME_HTTP_TIMEOUT_SECONDS))
                .build();
        HttpResponse<String> response;
        try {
            response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new RuntimeModelException(true, "MODEL_REQUEST_TIMEOUT_OR_NETWORK");
        }

        int code = response.statusCode();
        String responseBody = response.body();
        if (code >= 400) {
            boolean retryable = code == 429 || code >= 500;
            String truncatedBody = responseBody == null ? "" : responseBody.substring(0, Math.min(responseBody.length(), 400));
            throw new RuntimeModelException(retryable, "MODEL_HTTP_" + code + ":" + truncatedBody);
        }

        JsonNode root = JsonUtils.readTree(responseBody);
        RuntimeLlmTokenUsage usage = parseLlmUsageFromRoot(root);
        String content = parseModelContentFromRoot(root);
        if (content == null || content.isEmpty()) {
            throw new RuntimeModelException(true, "EMPTY_MODEL_CONTENT");
        }
        String jsonText = StringUtils.extractJsonBlock(content);
        if (jsonText == null || jsonText.isEmpty()) {
            jsonText = content;
        }
        Map<String, String> translated = JsonUtils.jsonToObjectWithNull(
                jsonText, new TypeReference<LinkedHashMap<String, String>>() {
                });
        if (translated == null) {
            String repaired = JsonUtils.highlyRobustRepair(jsonText);
            translated = JsonUtils.jsonToObjectWithNull(
                    repaired, new TypeReference<LinkedHashMap<String, String>>() {
                    });
        }
        if (translated == null) {
            throw new RuntimeModelException(false, "MODEL_RESPONSE_JSON_INVALID");
        }
        return new RuntimeModelTranslateOutcome(translated, usage);
    }

    /**
     * 与 {@link #requestRuntimeModelTranslate} 相同端点，但期望模型返回自然语言/Markdown（非 path→译文 JSON）。
     */
    private RuntimePlainTextOutcome requestRuntimeModelPlainText(String systemPrompt,
                                                                  String userContent,
                                                                  JsonRuntimeTranslateRequest request) throws RuntimeModelException {
        return requestRuntimeModelPlainText(systemPrompt, userContent, request,
                jsonRuntimeTranslationReportMaxCompletionTokens);
    }

    /**
     * @param maxCompletionTokens 传入时使用该上限作为 max_tokens（质量报告等可与整包报告区分）
     */
    private RuntimePlainTextOutcome requestRuntimeModelPlainText(String systemPrompt,
                                                                  String userContent,
                                                                  JsonRuntimeTranslateRequest request,
                                                                  int maxCompletionTokens) throws RuntimeModelException {
        if (StringUtils.isEmpty(safeText(systemPrompt)) || StringUtils.isEmpty(safeText(userContent))) {
            return new RuntimePlainTextOutcome("", RuntimeLlmTokenUsage.ZERO);
        }
        String provider = safeText(request.getProvider()).toLowerCase();
        String model = safeText(request.getModel());
        String apiBase = safeText(request.getApiBase());
        String apiKey = safeText(request.getApiKey());
        if (provider.isEmpty() || model.isEmpty() || apiBase.isEmpty() || apiKey.isEmpty()) {
            throw new RuntimeModelException(false, "INVALID_PROVIDER_CONFIG");
        }

        String endpoint = buildRuntimeEndpoint(provider, model, apiBase);
        Map<String, Object> body = new LinkedHashMap<>();
        if (!"azure-openai".equals(provider)) {
            body.put("model", model);
        }
        body.put("temperature", 0);
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt));
        messages.add(Map.of("role", "user", "content", userContent));
        body.put("messages", messages);
        String traceUser = safeText(request.getOpenaiUser());
        if (!traceUser.isEmpty()) {
            body.put("user", traceUser.length() > 128 ? traceUser.substring(0, 128) : traceUser);
        }
        int cap = Math.max(2048, maxCompletionTokens);
        body.put("max_tokens", Math.min(cap, 32768));

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(JSON_RUNTIME_HTTP_TIMEOUT_SECONDS))
                .POST(HttpRequest.BodyPublishers.ofString(JsonUtils.objectToJson(body), StandardCharsets.UTF_8));
        if ("azure-openai".equals(provider)) {
            builder.header("api-key", apiKey);
        } else {
            builder.header("Authorization", "Bearer " + apiKey);
        }

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(JSON_RUNTIME_HTTP_TIMEOUT_SECONDS))
                .build();
        HttpResponse<String> response;
        try {
            response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new RuntimeModelException(true, "MODEL_REQUEST_TIMEOUT_OR_NETWORK");
        }

        int code = response.statusCode();
        String responseBody = response.body();
        if (code >= 400) {
            boolean retryable = code == 429 || code >= 500;
            String truncatedBody = responseBody == null ? "" : responseBody.substring(0, Math.min(responseBody.length(), 400));
            throw new RuntimeModelException(retryable, "MODEL_HTTP_" + code + ":" + truncatedBody);
        }

        JsonNode root = JsonUtils.readTree(responseBody);
        RuntimeLlmTokenUsage usage = parseLlmUsageFromRoot(root);
        String content = parseModelContentFromRoot(root);
        if (content == null) {
            content = "";
        }
        return new RuntimePlainTextOutcome(content.trim(), usage);
    }

    private RuntimeLlmTokenUsage parseLlmUsageFromRoot(JsonNode root) {
        if (root == null || root.path("usage").isMissingNode() || root.get("usage").isNull()) {
            return RuntimeLlmTokenUsage.ZERO;
        }
        JsonNode u = root.get("usage");
        long prompt = u.path("prompt_tokens").asLong(0);
        if (prompt == 0) {
            prompt = u.path("input_tokens").asLong(0);
        }
        long completion = u.path("completion_tokens").asLong(0);
        if (completion == 0) {
            completion = u.path("output_tokens").asLong(0);
        }
        long total = u.path("total_tokens").asLong(0);
        if (total == 0 && (prompt > 0 || completion > 0)) {
            total = prompt + completion;
        }
        return new RuntimeLlmTokenUsage(prompt, completion, total);
    }

    private String parseModelContent(String responseBody) {
        return parseModelContentFromRoot(JsonUtils.readTree(responseBody));
    }

    private String parseModelContentFromRoot(JsonNode root) {
        if (root == null) {
            return "";
        }
        JsonNode choices = root.path("choices");
        if (!choices.isArray() || choices.isEmpty()) {
            return "";
        }
        JsonNode message = choices.get(0).path("message");
        if (message.isMissingNode()) {
            return "";
        }
        return message.path("content").asText("");
    }

    private String buildRuntimeEndpoint(String provider, String model, String apiBase) throws RuntimeModelException {
        String trimmed = safeText(apiBase);
        if (trimmed.isEmpty()) {
            throw new RuntimeModelException(false, "EMPTY_API_BASE");
        }
        if ("azure-openai".equals(provider)) {
            String lower = trimmed.toLowerCase();
            if (lower.contains("/chat/completions")) {
                if (lower.contains("api-version=")) {
                    return trimmed;
                }
                return trimmed + (trimmed.contains("?") ? "&" : "?") + "api-version=2024-02-15-preview";
            }
            String base = trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
            return base + "/openai/deployments/" + model + "/chat/completions?api-version=2024-02-15-preview";
        }

        if (trimmed.toLowerCase().contains("/chat/completions")) {
            return trimmed;
        }
        String base = trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
        return base + "/chat/completions";
    }

    private void collectSourceValueItems(JsonNode node,
                                        String pointer,
                                        List<RuntimeSourceItem> collector,
                                        List<RuntimeSkippedItem> skippedItems) {
        if (node == null) {
            return;
        }
        if (node.isObject()) {
            node.fields().forEachRemaining(entry -> {
                String escaped = escapeJsonPointer(entry.getKey());
                String nextPointer = pointer + "/" + escaped;
                JsonNode child = entry.getValue();
                if ("sourceValue".equals(entry.getKey()) && child != null) {
                    if (child.isTextual()) {
                        String raw = child.asText("");
                        if (shouldExpandStringifiedJsonForRuntime(raw)) {
                            JsonNode inner = JsonUtils.readTree(raw);
                            if (inner != null && (inner.isObject() || inner.isArray())) {
                                collectRuntimeInnerJsonLeaves(inner, nextPointer, "", collector, skippedItems);
                                return;
                            }
                        }
                        collector.add(new RuntimeSourceItem(nextPointer, raw));
                    } else {
                        skippedItems.add(new RuntimeSkippedItem(nextPointer, child.getNodeType().name()));
                    }
                    return;
                }
                collectSourceValueItems(child, nextPointer, collector, skippedItems);
            });
            return;
        }
        if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) {
                collectSourceValueItems(node.get(i), pointer + "/" + i, collector, skippedItems);
            }
        }
    }

    private static boolean shouldExpandStringifiedJsonForRuntime(String raw) {
        if (raw == null || raw.length() < JSON_RUNTIME_EXPAND_STRINGIFIED_JSON_MIN_CHARS) {
            return false;
        }
        return JsonUtils.isJson(raw);
    }

    /**
     * 将字符串化 JSON 内的每一个字符串叶子映射为独立翻译条目；路径形如
     * {@code /0/sourceValue::INNER::/region_language_selector_dict/AE/region_name}。
     */
    private void collectRuntimeInnerJsonLeaves(JsonNode node,
                                               String outerPointer,
                                               String innerPrefix,
                                               List<RuntimeSourceItem> collector,
                                               List<RuntimeSkippedItem> skippedItems) {
        if (node == null) {
            return;
        }
        if (node.isObject()) {
            node.fields().forEachRemaining(entry -> {
                String escaped = escapeJsonPointer(entry.getKey());
                String nextInner = innerPrefix.isEmpty() ? "/" + escaped : innerPrefix + "/" + escaped;
                JsonNode ch = entry.getValue();
                if (ch != null && ch.isTextual()) {
                    String t = ch.asText("");
                    if (!t.trim().isEmpty()) {
                        collector.add(new RuntimeSourceItem(outerPointer + RUNTIME_INNER_JSON_MARKER + nextInner, t));
                    }
                } else {
                    collectRuntimeInnerJsonLeaves(ch, outerPointer, nextInner, collector, skippedItems);
                }
            });
            return;
        }
        if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) {
                String nextInner = innerPrefix.isEmpty() ? "/" + i : innerPrefix + "/" + i;
                JsonNode ch = node.get(i);
                if (ch != null && ch.isTextual()) {
                    String t = ch.asText("");
                    if (!t.trim().isEmpty()) {
                        collector.add(new RuntimeSourceItem(outerPointer + RUNTIME_INNER_JSON_MARKER + nextInner, t));
                    }
                } else {
                    collectRuntimeInnerJsonLeaves(ch, outerPointer, nextInner, collector, skippedItems);
                }
            }
        }
    }

    private List<List<RuntimeSourceItem>> splitRuntimeBatches(List<RuntimeSourceItem> pending, int batchSize, int maxCharsPerBatch) {
        List<List<RuntimeSourceItem>> batches = new ArrayList<>();
        List<RuntimeSourceItem> current = new ArrayList<>();
        int chars = 0;
        for (RuntimeSourceItem item : pending) {
            int length = item.text == null ? 0 : item.text.length();
            boolean hitSize = !current.isEmpty() && current.size() >= batchSize;
            boolean hitChars = !current.isEmpty() && (chars + length > maxCharsPerBatch);
            if (hitSize || hitChars) {
                batches.add(current);
                current = new ArrayList<>();
                chars = 0;
            }
            current.add(item);
            chars += length;
        }
        if (!current.isEmpty()) {
            batches.add(current);
        }
        return batches;
    }

    private long estimateEtaSeconds(int total, int finished, long elapsedMs) {
        if (total <= 0 || finished <= 0 || elapsedMs <= 0) {
            return -1;
        }
        double perItemMs = (double) elapsedMs / (double) finished;
        int left = Math.max(total - finished, 0);
        return (long) ((left * perItemMs) / 1000.0);
    }

    private long computeBackoffSleepMs(int baseBackoffMs, int attempt) {
        long pure = (long) baseBackoffMs * (1L << Math.max(0, attempt - 1));
        long jitter = ThreadLocalRandom.current().nextLong(Math.max(baseBackoffMs, 1));
        return pure + jitter;
    }

    private void setTextByPointer(JsonNode root, String pointer, String text) {
        if (root == null || pointer == null || pointer.isEmpty() || !pointer.startsWith("/")) {
            return;
        }
        int innerMark = pointer.indexOf(RUNTIME_INNER_JSON_MARKER);
        if (innerMark > 0) {
            String outerPointer = pointer.substring(0, innerMark);
            String innerPointer = pointer.substring(innerMark + RUNTIME_INNER_JSON_MARKER.length());
            if (!innerPointer.startsWith("/")) {
                innerPointer = "/" + innerPointer;
            }
            JsonNode outerNode = getNodeAtPointer(root, outerPointer);
            if (outerNode == null || !outerNode.isTextual()) {
                return;
            }
            String embedded = outerNode.asText("");
            JsonNode innerRoot = JsonUtils.readTree(embedded);
            if (innerRoot == null) {
                return;
            }
            setLeafTextAtPointer(innerRoot, innerPointer, text);
            String newJson = JsonUtils.objectToJson(innerRoot);
            if (newJson == null) {
                return;
            }
            setLeafTextAtPointer(root, outerPointer, newJson);
            return;
        }
        setLeafTextAtPointer(root, pointer, text);
    }

    private void setLeafTextAtPointer(JsonNode root, String pointer, String text) {
        if (root == null || pointer == null || pointer.isEmpty() || !pointer.startsWith("/")) {
            return;
        }
        String[] segments = pointer.substring(1).split("/");
        JsonNode current = root;
        for (int i = 0; i < segments.length - 1; i++) {
            String segment = unescapeJsonPointer(segments[i]);
            if (current == null) {
                return;
            }
            if (current.isObject()) {
                current = current.get(segment);
                continue;
            }
            if (current.isArray()) {
                int index = safeIndex(segment);
                if (index < 0 || index >= current.size()) {
                    return;
                }
                current = current.get(index);
                continue;
            }
            return;
        }
        if (current == null) {
            return;
        }
        String lastSegment = unescapeJsonPointer(segments[segments.length - 1]);
        TextNode textNode = JsonUtils.OBJECT_MAPPER.getNodeFactory().textNode(text == null ? "" : text);
        if (current instanceof ObjectNode objectNode) {
            objectNode.set(lastSegment, textNode);
            return;
        }
        if (current instanceof ArrayNode arrayNode) {
            int index = safeIndex(lastSegment);
            if (index >= 0 && index < arrayNode.size()) {
                arrayNode.set(index, textNode);
            }
        }
    }

    private JsonNode getNodeAtPointer(JsonNode root, String pointer) {
        if (root == null || pointer == null || pointer.isEmpty() || !pointer.startsWith("/")) {
            return null;
        }
        String[] segments = pointer.substring(1).split("/");
        JsonNode current = root;
        for (String segment : segments) {
            String seg = unescapeJsonPointer(segment);
            if (current == null) {
                return null;
            }
            if (current.isObject()) {
                current = current.get(seg);
                continue;
            }
            if (current.isArray()) {
                int index = safeIndex(seg);
                if (index < 0 || index >= current.size()) {
                    return null;
                }
                current = current.get(index);
                continue;
            }
            return null;
        }
        return current;
    }

    /** 按 JSON Pointer 读取叶子文本（用于失败条目的 sourceValue 回填） */
    private String getTextAtPointer(JsonNode root, String pointer) {
        if (root == null || pointer == null || pointer.isEmpty() || !pointer.startsWith("/")) {
            return "";
        }
        int innerMark = pointer.indexOf(RUNTIME_INNER_JSON_MARKER);
        if (innerMark > 0) {
            String outerPointer = pointer.substring(0, innerMark);
            String innerPointer = pointer.substring(innerMark + RUNTIME_INNER_JSON_MARKER.length());
            if (!innerPointer.startsWith("/")) {
                innerPointer = "/" + innerPointer;
            }
            JsonNode outerNode = getNodeAtPointer(root, outerPointer);
            if (outerNode == null || !outerNode.isTextual()) {
                return "";
            }
            JsonNode innerRoot = JsonUtils.readTree(outerNode.asText(""));
            if (innerRoot == null) {
                return "";
            }
            return getTextAtPointer(innerRoot, innerPointer);
        }
        String[] segments = pointer.substring(1).split("/");
        JsonNode current = root;
        for (String segment : segments) {
            String seg = unescapeJsonPointer(segment);
            if (current == null) {
                return "";
            }
            if (current.isObject()) {
                current = current.get(seg);
                continue;
            }
            if (current.isArray()) {
                int index = safeIndex(seg);
                if (index < 0 || index >= current.size()) {
                    return "";
                }
                current = current.get(index);
                continue;
            }
            return "";
        }
        if (current == null || current.isNull()) {
            return "";
        }
        if (current.isValueNode()) {
            return current.asText("");
        }
        return "";
    }

    private String escapeJsonPointer(String input) {
        return input == null ? "" : input.replace("~", "~0").replace("/", "~1");
    }

    private String unescapeJsonPointer(String input) {
        return input == null ? "" : input.replace("~1", "/").replace("~0", "~");
    }

    private int safeIndex(String indexText) {
        try {
            return Integer.parseInt(indexText);
        } catch (Exception e) {
            return -1;
        }
    }

    private String toBlobPath(String blobUriOrPath) {
        String raw = safeText(blobUriOrPath);
        if (raw.isEmpty()) {
            return raw;
        }
        if (!raw.contains("://")) {
            return raw;
        }
        try {
            URI uri = new URI(raw);
            String path = safeText(uri.getPath());
            if (path.startsWith("/")) {
                path = path.substring(1);
            }
            int firstSlash = path.indexOf('/');
            if (firstSlash > 0 && firstSlash + 1 < path.length()) {
                return path.substring(firstSlash + 1);
            }
            return path;
        } catch (URISyntaxException e) {
            return raw;
        }
    }

    /**
     * 将 runtime 输出 Blob 路径放到与输入 Blob 相同的目录下，仅保留输出 URI 中的文件名。
     * 这样不会把译稿写到 qa/ 等其它目录；若输入路径无父目录或输出为空则返回原 output。
     */
    private String runtimeOutputUriBesideInput(String inputUriOrPath, String outputUriOrPath) {
        String inputPath = toBlobPath(safeText(inputUriOrPath));
        String outputPath = toBlobPath(safeText(outputUriOrPath));
        if (inputPath.isEmpty() || outputPath.isEmpty()) {
            return safeText(outputUriOrPath);
        }
        int inSlash = inputPath.lastIndexOf('/');
        if (inSlash < 0) {
            return safeText(outputUriOrPath);
        }
        String inputParent = inputPath.substring(0, inSlash + 1);
        int outSlash = outputPath.lastIndexOf('/');
        String fileName = outSlash >= 0 ? outputPath.substring(outSlash + 1) : outputPath;
        if (fileName.isEmpty()) {
            return safeText(outputUriOrPath);
        }
        String newRelative = inputParent + fileName;
        return rewriteBlobUriKeepingScheme(safeText(outputUriOrPath), newRelative);
    }

    private String rewriteBlobUriKeepingScheme(String originalUriOrPath, String newContainerRelativePath) {
        String raw = safeText(originalUriOrPath);
        if (raw.isEmpty()) {
            return newContainerRelativePath;
        }
        if (!raw.contains("://")) {
            return newContainerRelativePath;
        }
        try {
            URI uri = new URI(raw);
            String oldPath = safeText(uri.getPath());
            if (oldPath.startsWith("/")) {
                oldPath = oldPath.substring(1);
            }
            int slash = oldPath.indexOf('/');
            String container = slash > 0 ? oldPath.substring(0, slash) : oldPath;
            if (container.isEmpty()) {
                return newContainerRelativePath;
            }
            String authority = uri.getRawAuthority();
            String scheme = uri.getScheme();
            return scheme + "://" + authority + "/" + container + "/" + newContainerRelativePath;
        } catch (URISyntaxException e) {
            return newContainerRelativePath;
        }
    }

    private String sha256(String value) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
        StringBuilder builder = new StringBuilder(hash.length * 2);
        for (byte b : hash) {
            builder.append(String.format("%02x", b));
        }
        return builder.toString();
    }

    private void writeRuntimeReportBlob(String taskId,
                                        String status,
                                        int total,
                                        int done,
                                        int failed,
                                        long startedMs,
                                        Map<String, String> failMap,
                                        String reportBlobPath,
                                        RuntimeLlmTokenUsage tokenUsage,
                                        int llmApiCallCount) {
        List<Map<String, String>> failures = new ArrayList<>();
        if (failMap != null) {
            for (Map.Entry<String, String> entry : failMap.entrySet()) {
                Map<String, String> item = new LinkedHashMap<>();
                item.put("path", entry.getKey());
                item.put("reason", entry.getValue());
                failures.add(item);
            }
        }
        RuntimeLlmTokenUsage u = tokenUsage == null ? RuntimeLlmTokenUsage.ZERO : tokenUsage;
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("taskId", taskId);
        report.put("status", status);
        report.put("total", total);
        report.put("done", done);
        report.put("failed", failed);
        report.put("durationMs", System.currentTimeMillis() - startedMs);
        report.put("failures", failures);
        report.put("llmApiCallCount", llmApiCallCount);
        Map<String, Object> usageMap = new LinkedHashMap<>();
        usageMap.put("promptTokens", u.promptTokens);
        usageMap.put("completionTokens", u.completionTokens);
        usageMap.put("totalTokens", u.totalTokens);
        report.put("tokenUsage", usageMap);
        translateTaskV3BlobRepo.writeJson(reportBlobPath, report);
    }

    private String buildJsonRuntimeTranslationSummaryBody(TranslateTaskV3DO task,
                                                            boolean chunkedMode,
                                                            Map<String, Object> aggregate,
                                                            long durationMs,
                                                            int plannedChunkFiles,
                                                            int redisChunksDone,
                                                            List<String> perChunkLines) {
        if (task == null || aggregate == null) {
            return "";
        }
        String shop = safeText(task.getShopName());
        String tid = safeText(task.getId());
        if (shop.isEmpty() || tid.isEmpty()) {
            return "";
        }
        String status = safeText(asString(aggregate.get("status")));
        long totalItems = parseLongFlexible(aggregate.get("total"));
        long doneItems = parseLongFlexible(aggregate.get("done"));
        long failedItems = parseLongFlexible(aggregate.get("failed"));
        long prompt = parseLongFlexible(aggregate.get("llmPromptTokens"));
        long completion = parseLongFlexible(aggregate.get("llmCompletionTokens"));
        long totalTok = parseLongFlexible(aggregate.get("llmTotalTokens"));
        long llmCalls = parseLongFlexible(aggregate.get("llmApiCallCount"));

        StringBuilder sb = new StringBuilder(768);
        sb.append("JSON Runtime 翻译总览\n");
        sb.append("====================\n");
        sb.append("任务 ID: ").append(tid).append('\n');
        sb.append("店铺: ").append(shop).append('\n');
        sb.append("生成时间(UTC): ").append(Instant.now().toString()).append('\n');
        sb.append("运行模式: ").append(chunkedMode ? "分块 (chunked)" : "单 Blob").append('\n');
        sb.append('\n');
        sb.append("整体状态: ").append(status.isEmpty() ? "-" : status).append('\n');
        sb.append("总耗时(毫秒): ").append(durationMs).append('\n');
        sb.append("LLM 调用次数: ").append(llmCalls).append('\n');
        sb.append("Token — prompt: ").append(prompt)
                .append("  completion: ").append(completion)
                .append("  total: ").append(totalTok).append('\n');
        sb.append('\n');
        sb.append("条目汇总 — total: ").append(totalItems)
                .append("  done: ").append(doneItems)
                .append("  failed: ").append(failedItems).append('\n');
        long balance = totalItems - doneItems - failedItems;
        if (balance != 0) {
            sb.append("提示: total ≈ done + failed（条目）；若不相差为 0，多为「写输出 Blob 等非翻译条目失败」已计入 failed，或分片汇总舍入；以各 chunk 的 runtime-report JSON 为准。\n");
        }
        if (chunkedMode) {
            sb.append('\n');
            sb.append("分块 — 计划 chunk 文件数: ").append(plannedChunkFiles).append('\n');
            sb.append("分块 — Redis 已完成 chunk 数: ").append(redisChunksDone).append('\n');
            Object rcd = aggregate.get("runtimeChunkDoneSize");
            if (rcd != null) {
                sb.append("结果里的 runtimeChunkDoneSize: ").append(rcd).append('\n');
            }
        }
        sb.append('\n');
        sb.append("--- 各 chunk / 分片明细（一行一条）---\n");
        if (perChunkLines != null && !perChunkLines.isEmpty()) {
            for (String line : perChunkLines) {
                sb.append(line).append('\n');
            }
        } else {
            sb.append("（无分片明细）\n");
        }
        sb.append('\n');
        String blobRel = blobPath(shop, tid, JSON_RUNTIME_TRANSLATION_SUMMARY_TXT);
        sb.append("本文件路径: ").append(blobRel).append('\n');
        return sb.toString();
    }

    private String readTruncatedChunksFailedJson(String shop, String taskId, int maxChars) {
        if (maxChars <= 0 || shop.isEmpty() || taskId.isEmpty()) {
            return "";
        }
        String path = blobPath(shop, taskId, JSON_RUNTIME_CHUNKS_FAILED_JSON);
        String raw = translateTaskV3BlobRepo.readText(path);
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        if (raw.length() <= maxChars) {
            return raw;
        }
        return raw.substring(0, maxChars) + "\n...(truncated)\n";
    }

    private static String buildFallbackTranslationReportMarkdown(String summaryBody) {
        String safe = summaryBody == null ? "" : summaryBody;
        return "# JSON Runtime 翻译报告\n\n"
                + "> 本节为自动生成的兜底版本（模型不可用或未启用 LLM 报告时）。\n\n"
                + safe;
    }

    private boolean runtimeLlmConfigured(JsonRuntimeTranslateRequest request) {
        if (request == null) {
            return false;
        }
        applyDefaultRuntimeLlmConfigIfMissing(request);
        return !safeText(request.getProvider()).isEmpty()
                && !safeText(request.getModel()).isEmpty()
                && !safeText(request.getApiBase()).isEmpty()
                && !safeText(request.getApiKey()).isEmpty();
    }

    /**
     * 翻译结束后生成整体 Markdown 报告：汇总所有模块/chunk 的摘要事实 + 可选 failed.json，调用 LLM 产出可读报告。
     */
    private void maybeWriteJsonRuntimeLlmTranslationReport(TranslateTaskV3DO task,
                                                           JsonRuntimeTranslateRequest llmRequest,
                                                           boolean chunkedMode,
                                                           Map<String, Object> aggregate,
                                                           long durationMs,
                                                           int plannedChunkFiles,
                                                           int redisChunksDone,
                                                           List<String> perChunkLines) {
        if (task == null || aggregate == null) {
            return;
        }
        String shop = safeText(task.getShopName());
        String tid = safeText(task.getId());
        if (shop.isEmpty() || tid.isEmpty()) {
            return;
        }
        String summaryBody = buildJsonRuntimeTranslationSummaryBody(task, chunkedMode, aggregate, durationMs,
                plannedChunkFiles, redisChunksDone, perChunkLines);
        if (summaryBody.isEmpty()) {
            return;
        }
        String reportRel = blobPath(shop, tid, JSON_RUNTIME_TRANSLATION_REPORT_MD);

        JsonRuntimeTranslateRequest req = shallowCopy(llmRequest);
        applyDefaultRuntimeLlmConfigIfMissing(req);
        String src = safeText(req.getSourceLang());
        String tgt = safeText(req.getTargetLang());
        if (src.isEmpty()) {
            src = safeText(task.getSource());
        }
        if (tgt.isEmpty()) {
            tgt = safeText(task.getTarget());
        }

        if (!jsonRuntimeTranslationReportEnabled || !runtimeLlmConfigured(req)) {
            if (!jsonRuntimeTranslationReportEnabled) {
                LOG.info("json-runtime translation report: LLM disabled by config, taskId={}, writing fallback md", tid);
            } else {
                LOG.warn("json-runtime translation report: LLM config incomplete, taskId={}, writing fallback md", tid);
            }
            boolean ok = translateTaskV3BlobRepo.writeText(reportRel, buildFallbackTranslationReportMarkdown(summaryBody));
            if (ok) {
                LOG.info("json-runtime wrote translation report md (fallback), taskId={}, path={}", tid, reportRel);
            } else {
                LOG.warn("json-runtime failed to write translation report md (fallback), taskId={}, path={}", tid, reportRel);
            }
            return;
        }

        int maxFacts = Math.max(8000, jsonRuntimeTranslationReportMaxInputChars);
        int failBudget = Math.min(16000, maxFacts / 3);
        String failedPart = readTruncatedChunksFailedJson(shop, tid, failBudget);
        StringBuilder facts = new StringBuilder(summaryBody.length() + failedPart.length() + 64);
        facts.append("语言方向: ").append(src.isEmpty() ? "未知" : src).append(" → ").append(tgt.isEmpty() ? "未知" : tgt).append('\n');
        facts.append('\n').append(summaryBody);
        if (!failedPart.isEmpty()) {
            facts.append("\n\n--- chunks/failed.json（截断） ---\n").append(failedPart);
        }
        String combined = facts.toString();
        if (combined.length() > maxFacts) {
            combined = combined.substring(0, maxFacts) + "\n\n...(input truncated for LLM)\n";
        }

        String userPrompt = "以下是 JSON Runtime 翻译任务的全部模块与各 chunk 汇总事实（含一行一条的分片统计）。"
                + "另附可能存在的失败条目 JSON 片段（若为空则无）。\n\n"
                + "请基于这些事实撰写一份**简体中文** Markdown「翻译报告」，面向运营/产品/项目经理，结构清晰、可直接转发。\n\n"
                + "必须包含：\n"
                + "1) 标题与任务标识；\n"
                + "2) 执行概况（耗时、条目统计、LLM token 与调用次数）；\n"
                + "3) 按模块/chunk 的结果综述（可用小节或表格）；\n"
                + "4) 若有失败：失败规模、可能原因、建议的补救与复跑策略；\n"
                + "5) 结论：是否建议进入下一步（如保存/发布）及注意事项。\n\n"
                + "禁止编造事实数据中未出现的数字；不确定请写「未知」。不要输出除 Markdown 以外的包裹格式。\n\n"
                + "--- 事实数据 ---\n"
                + combined;

        String systemPrompt = "你是资深本地化项目经理与技术写作者。只输出 Markdown 正文，不要 JSON，不要用代码块包裹整篇文档。";

        try {
            RuntimePlainTextOutcome out = requestRuntimeModelPlainText(systemPrompt, userPrompt, req);
            String md = out.text == null ? "" : out.text.trim();
            if (md.isEmpty()) {
                md = buildFallbackTranslationReportMarkdown(summaryBody);
            }
            boolean ok = translateTaskV3BlobRepo.writeText(reportRel, md);
            if (ok) {
                LOG.info("json-runtime wrote translation report md (LLM), taskId={}, path={}, reportPromptTokApprox={}",
                        tid, reportRel, out.usage.promptTokens);
            } else {
                LOG.warn("json-runtime failed to write translation report md (LLM), taskId={}, path={}", tid, reportRel);
            }
        } catch (Exception e) {
            LOG.warn("json-runtime LLM translation report failed, taskId={}, fallback to summary md", tid, e);
            boolean ok = translateTaskV3BlobRepo.writeText(reportRel, buildFallbackTranslationReportMarkdown(summaryBody));
            if (ok) {
                LOG.info("json-runtime wrote translation report md after LLM error, taskId={}, path={}", tid, reportRel);
            }
        }
    }

    private List<RuntimeQualityPair> sampleQualityPairsForReport(List<RuntimeQualityPair> all, int maxPairs) {
        if (all == null || all.isEmpty()) {
            return Collections.emptyList();
        }
        int cap = Math.max(1, maxPairs);
        if (all.size() <= cap) {
            return new ArrayList<>(all);
        }
        List<RuntimeQualityPair> copy = new ArrayList<>(all);
        Collections.shuffle(copy, ThreadLocalRandom.current());
        return new ArrayList<>(copy.subList(0, cap));
    }

    private static String truncateForQualityPrompt(String s, int maxChars) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        if (s.length() <= maxChars) {
            return s;
        }
        return s.substring(0, maxChars) + "\n...(截断)";
    }

    private static String buildFallbackQualityMarkdown(int totalSuccessPairs, String reasonLine) {
        String why = reasonLine == null || reasonLine.isEmpty() ? "LLM 未返回有效内容或调用失败。" : reasonLine;
        return "# 翻译质量检测报告\n\n> " + why + "\n\n"
                + "本次运行已成功回填译文的条目数（路径数）约为 **" + totalSuccessPairs + "**。\n";
    }

    /**
     * 对照「翻译前原文 / 翻译后译文」抽样调用 LLM，生成总体打分与较差译例说明，写入 {@code chunks/translation-quality-report.md}。
     */
    private void maybeWriteJsonRuntimeQualityReport(TranslateTaskV3DO task,
                                                    JsonRuntimeTranslateRequest llmRequest,
                                                    List<RuntimeQualityPair> allPairs) {
        if (task == null) {
            return;
        }
        String shop = safeText(task.getShopName());
        String tid = safeText(task.getId());
        if (shop.isEmpty() || tid.isEmpty()) {
            return;
        }
        String rel = blobPath(shop, tid, JSON_RUNTIME_QUALITY_REPORT_MD);
        int total = allPairs == null ? 0 : allPairs.size();

        if (!jsonRuntimeQualityReportEnabled) {
            LOG.info("json-runtime quality report skipped (translate.v3.runtime.quality-report.enabled=false), taskId={}", tid);
            return;
        }

        if (total == 0) {
            boolean ok = translateTaskV3BlobRepo.writeText(rel,
                    "# 翻译质量检测报告\n\n暂无已成功翻译并写回的条目，未执行质检。\n");
            if (ok) {
                LOG.info("json-runtime wrote empty quality report stub, taskId={}, path={}", tid, rel);
            }
            return;
        }

        JsonRuntimeTranslateRequest req = shallowCopy(llmRequest);
        applyDefaultRuntimeLlmConfigIfMissing(req);
        String src = safeText(req.getSourceLang());
        String tgt = safeText(req.getTargetLang());
        if (src.isEmpty()) {
            src = safeText(task.getSource());
        }
        if (tgt.isEmpty()) {
            tgt = safeText(task.getTarget());
        }

        if (!runtimeLlmConfigured(req)) {
            LOG.warn("json-runtime quality report: LLM config incomplete, taskId={}, writing fallback md", tid);
            boolean ok = translateTaskV3BlobRepo.writeText(rel,
                    buildFallbackQualityMarkdown(total, "运行时 LLM 配置不完整，未调用质检模型。"));
            if (ok) {
                LOG.info("json-runtime wrote quality report fallback (no LLM), taskId={}, path={}", tid, rel);
            }
            return;
        }

        int maxPairs = Math.max(8, jsonRuntimeQualityReportMaxPairs);
        List<RuntimeQualityPair> sampled = sampleQualityPairsForReport(allPairs, maxPairs);
        int maxField = Math.max(120, jsonRuntimeQualityReportMaxCharsPerField);

        StringBuilder sb = new StringBuilder(Math.min(total * 128, 65536));
        sb.append("翻译方向: ").append(src.isEmpty() ? "未知" : src).append(" → ").append(tgt.isEmpty() ? "未知" : tgt).append('\n');
        sb.append("统计: 本次运行已成功写回译文的 JSON 路径共 ").append(total).append(" 条；以下抽检 ")
                .append(sampled.size()).append(" 条。\n\n");

        int idx = 1;
        for (RuntimeQualityPair p : sampled) {
            sb.append("### 样本 ").append(idx++).append('\n');
            sb.append("- 路径: `").append(p.path.replace("`", "'")).append("`\n");
            sb.append("- 原文:\n```\n").append(truncateForQualityPrompt(p.sourceText, maxField)).append("\n```\n");
            sb.append("- 译文:\n```\n").append(truncateForQualityPrompt(p.targetText, maxField)).append("\n```\n\n");
        }

        String combined = sb.toString();
        int maxIn = Math.max(4000, jsonRuntimeQualityReportMaxInputChars);
        if (combined.length() > maxIn) {
            combined = combined.substring(0, maxIn) + "\n\n...(输入过长已截断，仅保留前段样本)\n";
        }

        String userPrompt = "你是一名资深本地化质检审校（QE）。下面是同一 JSON Runtime 翻译任务中多条「原文 / 译文」对照样本（路径为 JSON Pointer）。\n\n"
                + "请输出**简体中文 Markdown**，必须包含：\n"
                + "1) **总体质量分**：给出一个 **0～100** 的整数分数，并一句话说明评分依据；\n"
                + "2) **总体评价**：约 2～5 句话；\n"
                + "3) **较差译例**：列出最多 **8** 条你认为问题相对最明显的样本（注明样本编号或路径），每条简述问题类型"
                + "（如漏译、术语不一致、语气不当、破坏 HTML/占位符等）；若抽检中未发现明显问题，请明确说明。\n\n"
                + "禁止编造未出现在样本中的路径或句子；不确定之处请写「不确定」。不要输出 JSON；不要用单个代码块包裹整篇文档。\n\n"
                + "--- 抽检样本 ---\n"
                + combined;

        String systemPrompt = "你只输出 Markdown 正文；语气专业、克制。";

        try {
            RuntimePlainTextOutcome out = requestRuntimeModelPlainText(systemPrompt, userPrompt, req,
                    jsonRuntimeQualityReportMaxCompletionTokens);
            String md = out.text == null ? "" : out.text.trim();
            if (md.isEmpty()) {
                md = buildFallbackQualityMarkdown(total, "模型返回空内容。");
            }
            boolean ok = translateTaskV3BlobRepo.writeText(rel, md);
            if (ok) {
                LOG.info("json-runtime wrote quality report md (LLM), taskId={}, path={}, sampled={}, totalPairs={}, promptTok~{}",
                        tid, rel, sampled.size(), total, out.usage.promptTokens);
            } else {
                LOG.warn("json-runtime failed to write quality report md, taskId={}, path={}", tid, rel);
            }
        } catch (Exception e) {
            LOG.warn("json-runtime quality report LLM failed, taskId={}, fallback md", tid, e);
            boolean ok = translateTaskV3BlobRepo.writeText(rel,
                    buildFallbackQualityMarkdown(total, "质检模型调用异常：" + e.getClass().getSimpleName()));
            if (ok) {
                LOG.info("json-runtime wrote quality report md after error, taskId={}, path={}", tid, rel);
            }
        }
    }

    /**
     * 在 {@code tasks/{shop}/{taskId}/chunks/translation-summary.txt} 写入 UTF-8 纯文本总览，便于在 Blob 存储中直接预览/编辑。
     */
    private void writeJsonRuntimeTranslationSummaryTxt(TranslateTaskV3DO task,
                                                       boolean chunkedMode,
                                                       Map<String, Object> aggregate,
                                                       long durationMs,
                                                       int plannedChunkFiles,
                                                       int redisChunksDone,
                                                       List<String> perChunkLines) {
        if (task == null || aggregate == null) {
            return;
        }
        String shop = safeText(task.getShopName());
        String tid = safeText(task.getId());
        if (shop.isEmpty() || tid.isEmpty()) {
            return;
        }
        String body = buildJsonRuntimeTranslationSummaryBody(task, chunkedMode, aggregate, durationMs,
                plannedChunkFiles, redisChunksDone, perChunkLines);
        if (body.isEmpty()) {
            return;
        }
        String blobRel = blobPath(shop, tid, JSON_RUNTIME_TRANSLATION_SUMMARY_TXT);
        boolean ok = translateTaskV3BlobRepo.writeText(blobRel, body);
        if (ok) {
            LOG.info("json-runtime wrote translation summary txt, taskId={}, path={}", tid, blobRel);
        } else {
            LOG.warn("json-runtime failed to write translation summary txt, taskId={}, path={}", tid, blobRel);
        }
    }

    private Map<String, Object> baseRuntimeResponse(String taskId,
                                                    String inputBlobUri,
                                                    String outputBlobUri,
                                                    String reportBlobUri) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("taskId", taskId);
        map.put("status", "FAILED");
        map.put("inputBlobUri", inputBlobUri);
        map.put("outputBlobUri", outputBlobUri);
        map.put("reportBlobUri", reportBlobUri);
        map.put("total", 0);
        map.put("done", 0);
        map.put("failed", 0);
        map.put("durationMs", 0);
        map.put("llmPromptTokens", 0L);
        map.put("llmCompletionTokens", 0L);
        map.put("llmTotalTokens", 0L);
        map.put("llmApiCallCount", 0);
        return map;
    }

    private int positiveOrDefault(Integer input, int defaultValue) {
        if (input == null || input <= 0) {
            return defaultValue;
        }
        return input;
    }

    private static String safeText(String text) {
        return text == null ? "" : text.trim();
    }

    private int parseInt(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        try {
            return Integer.parseInt(text);
        } catch (Exception e) {
            return 0;
        }
    }

    private Integer parseIntOrNull(Object value) {
        if (value == null) {
            return null;
        }
        String text = asString(value);
        if (text.isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(text);
        } catch (Exception e) {
            return null;
        }
    }

    private static long parseLongMeta(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number n) {
            return n.longValue();
        }
        String text = asString(value);
        if (text.isEmpty()) {
            return 0L;
        }
        try {
            return Long.parseLong(text);
        } catch (Exception e) {
            return 0L;
        }
    }

    private void processTasksByShopName(String stageName,
                                        int status,
                                        Set<String> processingTaskIds,
                                        Predicate<TranslateTaskV3DO> skipTaskPredicate,
                                        Consumer<TranslateTaskV3DO> taskHandler,
                                        Runnable emptyTaskCallback) {
        List<TranslateTaskV3DO> tasks = translateTaskV3CosmosRepo.listByStatus(status);
        if (tasks.isEmpty()) {
            if (emptyTaskCallback != null) {
                emptyTaskCallback.run();
            }
            return;
        }

        tasks.sort(Comparator.comparing(t -> "manual".equals(t.getTaskType()) ? 0 : 1));
        Map<String, List<TranslateTaskV3DO>> tasksByShop = tasks.stream()
                .filter(task -> task != null && !StringUtils.isEmpty(task.getShopName()))
                .collect(Collectors.groupingBy(TranslateTaskV3DO::getShopName, LinkedHashMap::new, Collectors.toList()));

        for (Map.Entry<String, List<TranslateTaskV3DO>> entry : tasksByShop.entrySet()) {
            String shopName = entry.getKey();
            if (!processingShopNames.add(shopName)) {
                LOG.info("v3 {} skip shop lock, shop={}", stageName.toLowerCase(), shopName);
                continue;
            }

            resolveShopExecutor(shopName).submit(() -> {
                List<TranslateTaskV3DO> shopTasks = entry.getValue();
                LOG.info("v3 {} group start, shop={}, taskCount={}", stageName.toLowerCase(), shopName, shopTasks.size());
                try {
                    for (TranslateTaskV3DO task : shopTasks) {
                        if (skipTaskPredicate != null && skipTaskPredicate.test(task)) {
                            continue;
                        }
                        if (!processingTaskIds.add(task.getId())) {
                            LOG.info("v3 {} skip duplicated task lock, taskId={}, shop={}",
                                    stageName.toLowerCase(), task.getId(), task.getShopName());
                            continue;
                        }
                        try {
                            taskHandler.accept(task);
                        } catch (Exception e) {
                            LOG.error("v3 {} failed, taskId={}, shop={}",
                                    stageName.toLowerCase(), task.getId(), task.getShopName(), e);
                            TraceReporterHolder.report("TranslateV3Service.processTasksByShopName",
                                    "FatalException process " + stageName + " task failed, taskId=" + task.getId() + " error=" + e);
                        } finally {
                            processingTaskIds.remove(task.getId());
                        }
                    }
                } finally {
                    processingShopNames.remove(shopName);
                    LOG.info("v3 {} group finish, shop={}", stageName.toLowerCase(), shopName);
                }
            });
        }
    }

    private List<ExecutorService> createShopGroupExecutors() {
        List<ExecutorService> executors = new ArrayList<>(SHOP_GROUP_WORKER_SIZE);
        for (int i = 0; i < SHOP_GROUP_WORKER_SIZE; i++) {
            int workerIndex = i;
            executors.add(Executors.newSingleThreadExecutor(r -> {
                Thread thread = new Thread(r);
                thread.setName("translate-v3-shop-group-" + workerIndex);
                return thread;
            }));
        }
        return executors;
    }

    private ExecutorService resolveShopExecutor(String shopName) {
        int index = Math.floorMod(shopName.hashCode(), SHOP_GROUP_WORKER_SIZE);
        return shopGroupExecutors.get(index);
    }

    @PreDestroy
    public void flushPendingProgressOnShutdown() {
        for (Map.Entry<String, PendingProgress> entry : pendingProgressMap.entrySet()) {
            flushPendingProgress(entry.getKey(), entry.getValue());
        }
        for (ExecutorService executorService : shopGroupExecutors) {
            executorService.shutdown();
        }
        chunkTranslateExecutor.shutdown();
    }

    // 兼容旧调用
    public void initialToTranslateTask(InitialTaskV2DO initialTaskV2DO) {
        TranslateTaskV3DO task = new TranslateTaskV3DO();
        task.setId(String.valueOf(initialTaskV2DO.getId()));
        task.setShopName(initialTaskV2DO.getShopName());
        task.setSource(normalizeLocaleCode(initialTaskV2DO.getSource()));
        task.setTarget(normalizeLocaleCode(initialTaskV2DO.getTarget()));
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
        LOG.info("v3 init reading shopify, taskId={}, shop={}", taskId, task.getShopName());
        translateTaskMonitorV3RedisService.setPhase(taskId, "INIT_READING_SHOPIFY");

        UsersDO userDO = usersService.getUserByName(task.getShopName());
        if (userDO == null || StringUtils.isEmpty(userDO.getAccessToken())) {
            LOG.warn("v3 init stop no user/accessToken, taskId={}, shop={}", taskId, task.getShopName());
            translateTaskMonitorV3RedisService.setPhase(taskId, "INIT_FAILED_NO_USER");
            return;
        }

        String primaryLocaleData = shopifyService.getShopifyData(task.getShopName(), userDO.getAccessToken(),
                com.bogda.common.contants.TranslateConstants.API_VERSION_LAST, ShopifyRequestUtils.getShopLanguageQuery());
        String primaryLocale = getPrimaryLocaleFromShopifyData(primaryLocaleData);
        if (!StringUtils.isEmpty(primaryLocale) && !sameLocaleCode(primaryLocale, task.getSource())) {
            LOG.warn("v3 init stop locale mismatch, taskId={}, shop={}, primaryLocale={}, source={}",
                    taskId, task.getShopName(), primaryLocale, task.getSource());
            translateTaskMonitorV3RedisService.setPhase(taskId, "INIT_STOPPED_PRIMARY_LOCALE_MISMATCH");
            translateTaskV3CosmosRepo.patchStatus(taskId, task.getShopName(), 4);
            return;
        }

        List<String> moduleList = JsonUtils.jsonToObject(task.getModuleList(), new com.fasterxml.jackson.core.type.TypeReference<>() {
        });
        if (CollectionUtils.isEmpty(moduleList)) {
            LOG.info("v3 init empty modules directly move to translate, taskId={}, shop={}", taskId, task.getShopName());
            translateTaskMonitorV3RedisService.setPhase(taskId, "INIT_DONE_EMPTY_MODULES");
            translateTaskV3CosmosRepo.patchStatus(taskId, task.getShopName(), 1);
            return;
        }
        LOG.info("v3 init start dump modules, taskId={}, shop={}, moduleCount={}, modules={}",
                taskId, task.getShopName(), moduleList.size(), moduleList);
        translateTaskMonitorV3RedisService.setInitOverview(taskId, moduleList.size());

        int totalCount = 0;
        int totalChars = 0;
        int totalChunks = 0;
        Map<String, Object> moduleSummary = new HashMap<>();
        int moduleIndex = 0;
        for (String module : moduleList) {
            moduleIndex++;
            ModuleInitSummary summary = dumpModuleToBlob(task, userDO, module);
            totalCount += summary.totalCount;
            totalChars += summary.totalChars;
            totalChunks += summary.chunkCount;
            moduleSummary.put(module, summary.toMap());
            translateTaskMonitorV3RedisService.setInitModuleProgress(
                    taskId,
                    module,
                    moduleIndex,
                    moduleIndex,
                    summary.totalCount,
                    summary.totalChars,
                    summary.chunkCount,
                    totalCount,
                    totalChars,
                    totalChunks
            );
        }

        translateTaskMonitorV3RedisService.setTotalCount(taskId, totalCount);
        translateTaskMonitorV3RedisService.setEstimatedCreditsRaw(taskId, totalChars);
        translateTaskMonitorV3RedisService.setInitManifest(taskId, moduleSummary);
        translateTaskMonitorV3RedisService.setPhase(taskId, "INIT_DONE");
        translateTaskV3BlobRepo.writeJson(blobPath(task.getShopName(), taskId, "chunks/manifest.json"), moduleSummary);

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
        LOG.info("v3 init done and status moved to 1, taskId={}, shop={}, totalCount={}, totalChars={}",
                taskId, task.getShopName(), totalCount, totalChars);
    }

    /**
     * 将 JsonRuntimeAgent 最终回复（finalizeTrace 的 JSON）写入 Blob，便于对照 HTTP API 返回排查 Planner/执行轨迹。
     * 路径：{@code tasks/{shop}/{taskId}/qa/agent-runtime-trace-{utcTs}.json} 与 {@code .../agent-runtime-trace-latest.json}。
     */
    private void persistJsonRuntimeAgentTraceBlob(TranslateTaskV3DO task, String userMessage, String agentAnswerRaw) {
        if (task == null || StringUtils.isEmpty(task.getShopName()) || StringUtils.isEmpty(task.getId())) {
            return;
        }
        try {
            String ts = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
                    .withZone(ZoneOffset.UTC)
                    .format(Instant.now());
            String stampedTail = "qa/agent-runtime-trace-" + ts + ".json";
            String latestTail = "qa/agent-runtime-trace-latest.json";
            String shop = task.getShopName();
            String tid = task.getId();

            Map<String, Object> envelope = new LinkedHashMap<>();
            envelope.put("savedAt", Instant.now().toString());
            envelope.put("taskId", tid);
            envelope.put("shopName", shop);
            envelope.put("userMessage", userMessage == null ? "" : userMessage);
            envelope.put("kind", "JsonRuntimeAgent_finalize_trace");
            envelope.put("finalizeTrace", parseAgentFinalizeTracePayload(agentAnswerRaw));

            translateTaskV3BlobRepo.writeJson(blobPath(shop, tid, stampedTail), envelope);
            translateTaskV3BlobRepo.writeJson(blobPath(shop, tid, latestTail), envelope);

            Map<String, Object> checkpoint = task.getCheckpoint() == null ? new HashMap<>() : new HashMap<>(task.getCheckpoint());
            checkpoint.put("agentRuntimeTraceBlobPath", blobPath(shop, tid, latestTail));
            checkpoint.put("agentRuntimeTraceStampedPath", blobPath(shop, tid, stampedTail));
            checkpoint.put("agentRuntimeTraceSavedAt", Instant.now().toString());
            Map<String, Object> metrics = task.getMetrics() == null ? new HashMap<>() : new HashMap<>(task.getMetrics());
            translateTaskV3CosmosRepo.patchCheckpointAndMetrics(tid, shop, checkpoint, metrics);
            task.setCheckpoint(checkpoint);
            LOG.info("json-runtime agent trace blob saved taskId={}, latest={}", tid, latestTail);
        } catch (Exception e) {
            LOG.warn("persistJsonRuntimeAgentTraceBlob failed taskId={}", task.getId(), e);
        }
    }

    private static Object parseAgentFinalizeTracePayload(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return Map.of("note", "empty_agent_reply");
        }
        try {
            JsonNode n = JsonUtils.readTree(raw);
            if (n != null && n.isObject()) {
                Map<String, Object> m = JsonUtils.jsonToObject(raw, new TypeReference<Map<String, Object>>() {
                });
                return m == null ? raw : m;
            }
            return raw;
        } catch (Exception e) {
            return raw;
        }
    }

    public void translateEachTaskV3(TranslateTaskV3DO task) {
        String taskId = task.getId();
        String shopName = task.getShopName();
        if (isRuntimeJsonTask(task)) { // agent 模式的翻译
            String agentMsg = "执行taskId=" + taskId + "的json翻译任务";
            try {
                if (jsonRuntimeAgentRunner == null) {
                    LOG.error("JsonRuntimeAgentRunner 未装配（仅 AgentTask 进程提供），taskId={}, shop={}", taskId, shopName);
                    translateTaskMonitorV3RedisService.setPhase(taskId, "TRANSLATE_FAILED_AGENT_UNAVAILABLE");
                    return;
                }
                LOG.info("v3 translate json-runtime via agent, taskId={}, shop={}", taskId, shopName);
                String agentTrace = jsonRuntimeAgentRunner.run(agentMsg);
                persistJsonRuntimeAgentTraceBlob(task, agentMsg, agentTrace);
            } catch (IllegalArgumentException e) {
                LOG.warn("v3 translate json-runtime agent skipped, taskId={}, reason={}", taskId, e.getMessage());
            } catch (Exception e) {
                LOG.error("v3 translate json-runtime agent failed, taskId={}, shop={}", taskId, shopName, e);
                TraceReporterHolder.report("TranslateV3Service.translateEachTaskV3",
                        "FatalException json-runtime agent taskId=" + taskId + " error=" + e);
            }
            return;
        }
        String target = normalizeLocaleCode(task.getTarget());
        String source = normalizeLocaleCode(task.getSource());

        translateTaskMonitorV3RedisService.setPhase(taskId, "TRANSLATE_RUNNING");

        UsersDO userDO = usersService.getUserByName(shopName);
        if (userDO == null || StringUtils.isEmpty(userDO.getAccessToken())) {
            translateTaskMonitorV3RedisService.setPhase(taskId, "TRANSLATE_FAILED_NO_USER");
            return;
        }

        String primaryLocaleData = shopifyService.getShopifyData(shopName, userDO.getAccessToken(),
                TranslateConstants.API_VERSION_LAST, ShopifyRequestUtils.getShopLanguageQuery());
        String primaryLocale = getPrimaryLocaleFromShopifyData(primaryLocaleData);
        if (!StringUtils.isEmpty(primaryLocale) && !sameLocaleCode(primaryLocale, source)) {
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
        int totalChunkCount = 0;
        for (String module : modules) {
            totalChunkCount += translateTaskV3BlobRepo.listBlobPaths(blobPath(shopName, taskId, "chunks/" + module + "/")).size();
        }
        translateTaskMonitorV3RedisService.setTranslateOverview(taskId, modules.size(), totalChunkCount, maxToken, currentUsedToken);

        boolean stoppedByTokenLimit = false;
        String currentModule = null;
        String currentChunkPath = null;
        int moduleDone = 0;

        for (String module : modules) {
            currentModule = module;
            List<String> chunkPaths = translateTaskV3BlobRepo.listBlobPaths(blobPath(shopName, taskId, "chunks/" + module + "/"));
            if (chunkPaths.isEmpty()) {
                moduleDone++;
                translateTaskMonitorV3RedisService.setTranslateProgress(
                        taskId, module, moduleDone, "", processedChunkCount, translatedTotal, usedTokenTotal, 0, 0);
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
                TranslateChunkResult result = translateChunkRowsWithTimeout(
                        rows, module, task, glossaryMap, maxToken, currentUsedToken);
                currentUsedToken = result.currentUsedToken;
                translateTaskMonitorV3RedisService.incrementBy(taskId, result.translatedDelta, 0, result.usedTokenDelta);
                if (result.timedOut) {
                    LOG.warn("v3 translate chunk timeout, taskId={}, shop={}, module={}, chunkPath={}, timeoutSeconds={}",
                            taskId, shopName, module, chunkPath, chunkTranslateTimeoutSeconds);
                    continue;
                }

                // v3 使用同一份 chunk 文件存储翻译前后数据：翻译完成后直接覆盖当前 chunk。
                translateTaskV3BlobRepo.writeJson(chunkPath, rows);

                translatedTotal += result.translatedDelta;
                usedTokenTotal += result.usedTokenDelta;
                processedChunkCount++;
                cachePendingProgress(taskId, shopName, currentModule, currentChunkPath, translatedTotal, usedTokenTotal);
                translateTaskMonitorV3RedisService.setTranslateProgress(
                        taskId, module, moduleDone, chunkPath, processedChunkCount,
                        translatedTotal, usedTokenTotal, result.translatedDelta, result.usedTokenDelta);
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
            moduleDone++;
            translateTaskMonitorV3RedisService.setTranslateProgress(
                    taskId, module, moduleDone, currentChunkPath, processedChunkCount,
                    translatedTotal, usedTokenTotal, 0, 0);
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
            generateQaReport(taskId, shopName, modules);
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
        final int moduleFetchLimit = Math.max(1, initModuleFetchLimit);
        List<Map<String, Object>> currentChunk = new ArrayList<>();
        final int[] chunkIndex = {0};
        final int[] totalCount = {0};
        final int[] totalChars = {0};
        AtomicBoolean stopFetch = new AtomicBoolean(false);

        shopifyService.rotateAllShopifyGraph(task.getShopName(), module, userDO.getAccessToken(), 250, task.getTarget(), "",
                null,
                node -> {
                    if (stopFetch.get()) {
                        return;
                    }
                    if (node == null || CollectionUtils.isEmpty(node.getTranslatableContent())) {
                        return;
                    }
                    for (ShopifyTranslationsResponse.Node.TranslatableContent content : node.getTranslatableContent()) {
                        if (totalCount[0] >= moduleFetchLimit) {
                            stopFetch.set(true);
                            break;
                        }
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
                                    blobPath(task.getShopName(), task.getId(), "chunks/" + module + "/chunk-" + chunkIndex[0] + ".json"),
                                    new ArrayList<>(currentChunk));
                            currentChunk.clear();
                            chunkIndex[0]++;
                        }
                    }
                },
                after -> {
                },
                stopFetch::get);

        if (!currentChunk.isEmpty()) {
            translateTaskV3BlobRepo.writeJson(
                    blobPath(task.getShopName(), task.getId(), "chunks/" + module + "/chunk-" + chunkIndex[0] + ".json"),
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
                return new TranslateChunkResult(translatedDelta, usedTokenDelta, currentUsedToken, true, false);
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
                    return new TranslateChunkResult(translatedDelta, usedTokenDelta, currentUsedToken, true, false);
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
                return new TranslateChunkResult(translatedDelta, usedTokenDelta, currentUsedToken, true, false);
            }
        }
        return new TranslateChunkResult(translatedDelta, usedTokenDelta, currentUsedToken, false, false);
    }

    private TranslateChunkResult translateChunkRowsWithTimeout(List<Map<String, Object>> rows,
                                                               String module,
                                                               TranslateTaskV3DO task,
                                                               Map<String, GlossaryDO> glossaryMap,
                                                               int maxToken,
                                                               int currentUsedToken) {
        int timeoutSeconds = Math.max(10, chunkTranslateTimeoutSeconds);
        CompletableFuture<TranslateChunkResult> future = CompletableFuture.supplyAsync(
                () -> translateChunkRows(rows, module, task, glossaryMap, maxToken, currentUsedToken),
                chunkTranslateExecutor
        );
        try {
            return future.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            TraceReporterHolder.report("TranslateV3Service.translateChunkRowsWithTimeout",
                    "chunk translate timeout, taskId=" + (task == null ? null : task.getId())
                            + " shop=" + (task == null ? null : task.getShopName())
                            + " module=" + module + " timeoutSeconds=" + timeoutSeconds
                            + " rowCount=" + (rows == null ? 0 : rows.size()));
            return TranslateChunkResult.timeout(currentUsedToken);
        } catch (Exception e) {
            future.cancel(true);
            throw new RuntimeException("translateChunkRowsWithTimeout failed", e);
        }
    }

    private TranslateChunkResult flushBatchRows(List<Map<String, Object>> rows,
                                                List<Integer> batchIndexes,
                                                Map<Integer, String> batchSourceMap,
                                                String module,
                                                TranslateTaskV3DO task,
                                                Map<String, GlossaryDO> glossaryMap,
                                                int currentUsedToken) {
        if (batchIndexes.isEmpty()) {
            return new TranslateChunkResult(0, 0, currentUsedToken, false, false);
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
        return new TranslateChunkResult(translatedCount, tokenUsed, currentUsedToken, false, false);
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
                initialTaskV2DO.getShopName(), normalizeLocaleCode(initialTaskV2DO.getSource()));
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
            if (!StringUtils.isEmpty(target) && !sameLocaleCode(target, task.getTarget())) {
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

        UsersDO userDO = usersService.getUserByName(shopName);
        if (userDO == null || StringUtils.isEmpty(userDO.getAccessToken())) {
            translateTaskMonitorV3RedisService.setPhase(taskId, "SAVE_FAILED_NO_USER");
            return;
        }
        String token = userDO.getAccessToken();

        List<String> modules = JsonUtils.jsonToObject(task.getModuleList(), new TypeReference<List<String>>() {
        });
        Map<String, Object> metrics = task.getMetrics() == null ? new HashMap<>() : new HashMap<>(task.getMetrics());
        if (CollectionUtils.isEmpty(modules)) {
            finalizeSaveWithCheckpointPhase(task, 0, metrics, "SAVE_SKIPPED_NO_MODULES", "SAVE_SKIPPED_NO_MODULES");
            return;
        }

        patchSaveCheckpoint(task, shopName, metrics, "SAVE_READY", null, null, null);
        translateTaskMonitorV3RedisService.setPhase(taskId, "SAVE_READY");
        boolean saveWorkStarted = false;
        int savedTotal = recoverSavedCountFromProgress(shopName, taskId, modules);
        int persistedSaved = getIntMetric(metrics, "savedCount");
        if (persistedSaved > savedTotal) {
            savedTotal = persistedSaved;
        }
        int saveChunkTotal = 0;
        for (String module : modules) {
            List<String> chunkPaths = translateTaskV3BlobRepo.listBlobPaths(blobPath(shopName, taskId, "chunks/" + module + "/"));
            for (String path : chunkPaths) {
                if (isChunkDataPath(path)) {
                    saveChunkTotal++;
                }
            }
        }
        translateTaskMonitorV3RedisService.setSaveOverview(taskId, modules.size(), saveChunkTotal, savedTotal);
        metrics.put("savedCount", savedTotal);
        metrics.put("updatedAt", Instant.now().toString());
        boolean hasSaveFailure = false;
        String currentModule = null;
        String currentChunkPath = null;
        int saveModuleDone = 0;
        int saveChunkDone = 0;

        for (String module : modules) {
            currentModule = module;
            List<String> chunkPaths = translateTaskV3BlobRepo.listBlobPaths(blobPath(shopName, taskId, "chunks/" + module + "/"));
            if (chunkPaths.isEmpty()) {
                saveModuleDone++;
                translateTaskMonitorV3RedisService.setSaveProgress(
                        taskId, module, saveModuleDone, "", saveChunkDone, null, savedTotal, 0);
                continue;
            }
            for (String chunkPath : chunkPaths) {
                if (!isChunkDataPath(chunkPath)) {
                    continue;
                }
                currentChunkPath = chunkPath;
                saveChunkDone++;
                List<Map<String, Object>> rows = translateTaskV3BlobRepo.readJsonRows(chunkPath);
                if (rows.isEmpty()) {
                    translateTaskMonitorV3RedisService.setSaveProgress(
                            taskId, module, saveModuleDone, chunkPath, saveChunkDone, null, savedTotal, 0);
                    continue;
                }
                Set<Integer> savedRowIndexes = loadSavedRowIndexes(chunkPath);
                Map<String, List<Integer>> resourceToIndexes = groupUnsavedRowsByResource(rows, savedRowIndexes);
                if (resourceToIndexes.isEmpty()) {
                    translateTaskMonitorV3RedisService.setSaveProgress(
                            taskId, module, saveModuleDone, chunkPath, saveChunkDone, null, savedTotal, 0);
                    continue;
                }

                for (Map.Entry<String, List<Integer>> entry : resourceToIndexes.entrySet()) {
                    String resourceId = entry.getKey();
                    List<Integer> rowIndexes = entry.getValue();
                    if (StringUtils.isEmpty(resourceId) || CollectionUtils.isEmpty(rowIndexes)) {
                        continue;
                    }
                    if (!saveWorkStarted) {
                        translateTaskMonitorV3RedisService.setPhase(taskId, "SAVE_RUNNING");
                        saveWorkStarted = true;
                    }
                    List<Integer> savedIndexes = saveOneResourceRows(shopName, token, task.getTarget(), rows, rowIndexes, resourceId);
                    if (savedIndexes == null) {
                        hasSaveFailure = true;
                        translateTaskMonitorV3RedisService.setSaveFailure(
                                taskId, module, chunkPath, resourceId, "SAVE_RESOURCE_FAILED");
                        continue;
                    }
                    int savedCount = savedIndexes.size();
                    savedRowIndexes.addAll(savedIndexes);
                    writeSaveProgress(chunkPath, savedRowIndexes, rows.size());
                    savedTotal += savedCount;
                    translateTaskMonitorV3RedisService.incrementBy(taskId, 0, savedCount, 0);
                    translateTaskMonitorV3RedisService.setSaveProgress(
                            taskId, module, saveModuleDone, chunkPath, saveChunkDone, resourceId, savedTotal, savedCount);
                    metrics.put("savedCount", savedTotal);
                    metrics.put("updatedAt", Instant.now().toString());
                    patchSaveCheckpoint(task, shopName, metrics, "SAVE_RUNNING", currentModule, currentChunkPath, resourceId);
                }
            }
            saveModuleDone++;
            translateTaskMonitorV3RedisService.setSaveProgress(
                    taskId, module, saveModuleDone, currentChunkPath, saveChunkDone, null, savedTotal, 0);
        }

        if (hasSaveFailure) {
            translateTaskMonitorV3RedisService.setPhase(taskId, "SAVE_PARTIAL_FAILED");
            metrics.put("savedCount", savedTotal);
            metrics.put("updatedAt", Instant.now().toString());
            patchSaveCheckpoint(task, shopName, metrics, "SAVE_PARTIAL_FAILED", currentModule, currentChunkPath, null);
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

    private int recoverSavedCountFromProgress(String shopName, String taskId, List<String> modules) {
        if (StringUtils.isEmpty(shopName) || StringUtils.isEmpty(taskId) || CollectionUtils.isEmpty(modules)) {
            return 0;
        }
        int total = 0;
        for (String module : modules) {
            List<String> chunkPaths = translateTaskV3BlobRepo.listBlobPaths(blobPath(shopName, taskId, "chunks/" + module + "/"));
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

    private void patchSaveCheckpoint(TranslateTaskV3DO task,
            String shopName,
            Map<String, Object> metrics,
            String phase,
            String module,
            String chunkPath,
            String resourceId) {
        if (task == null) {
            return;
        }
        Map<String, Object> cp = task.getCheckpoint() == null
                ? new HashMap<>()
                : new HashMap<>(task.getCheckpoint());
        cp.put("phase", phase);
        cp.put("updatedAt", Instant.now().toString());
        if (module != null) {
            cp.put("module", module);
        } else {
            cp.remove("module");
        }
        if (chunkPath != null) {
            cp.put("chunkPath", chunkPath);
        } else {
            cp.remove("chunkPath");
        }
        if (resourceId != null && !resourceId.isEmpty()) {
            cp.put("resourceId", resourceId);
        } else {
            cp.remove("resourceId");
        }
        task.setCheckpoint(cp);
        task.setMetrics(metrics);
        translateTaskV3CosmosRepo.patchCheckpointAndMetrics(task.getId(), shopName, cp, metrics);
    }

    private void finalizeSaveWithCheckpointPhase(TranslateTaskV3DO task,
            int savedTotal,
            Map<String, Object> metrics,
            String checkpointPhase,
            String redisPhase) {
        String taskId = task.getId();
        String shopName = task.getShopName();
        metrics.put("savedCount", savedTotal);
        metrics.put("updatedAt", Instant.now().toString());
        patchSaveCheckpoint(task, shopName, metrics, checkpointPhase, null, null, null);
        translateTaskMonitorV3RedisService.setPhase(taskId, redisPhase);
        translateTaskV3CosmosRepo.patchStatus(taskId, shopName, 6);
        translatesService.updateTranslateStatus(shopName, 2, task.getTarget(), task.getSource());
    }

    private void markSaveDone(TranslateTaskV3DO task, int savedTotal) {
        Map<String, Object> metrics = task.getMetrics() == null ? new HashMap<>() : new HashMap<>(task.getMetrics());
        finalizeSaveWithCheckpointPhase(task, savedTotal, metrics, "SAVE_DONE", "SAVE_DONE");
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
            List<String> chunkPaths = translateTaskV3BlobRepo.listBlobPaths(blobPath(shopName, taskId, "chunks/" + module + "/"));
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
        translateTaskV3BlobRepo.writeJson(blobPath(shopName, taskId, "qa/save-verify.json"), verifyReport);

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
        checkpoint.put("verifyReportPath", blobPath(shopName, taskId, "qa/save-verify.json"));
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

    private void generateQaReport(String taskId, String shopName, List<String> modules) {
        if (taskId == null || taskId.isEmpty() || shopName == null || shopName.isEmpty() || CollectionUtils.isEmpty(modules)) {
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
            List<String> chunkPaths = translateTaskV3BlobRepo.listBlobPaths(blobPath(shopName, taskId, "chunks/" + module + "/"));
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

        translateTaskV3BlobRepo.writeJson(blobPath(shopName, taskId, "chunks/qa-report.json"), summary);
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
            translateTaskV3BlobRepo.writeJson(blobPath(task.getShopName(), task.getId(), "chunks/" + module + "/ai-score.json"), moduleReport);
        }
        report.put("moduleScores", moduleScores);
        translateTaskV3BlobRepo.writeJson(blobPath(task.getShopName(), task.getId(), "qa/ai-score.json"), report);
    }

    private Map<String, Object> scoreOneModuleByAi(TranslateTaskV3DO task, String module) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("module", module);

        List<Map<String, Object>> samples = collectTranslatedSamples(task.getShopName(), task.getId(), module);
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

    private List<Map<String, Object>> collectTranslatedSamples(String shopName, String taskId, String module) {
        List<Map<String, Object>> allSamples = new ArrayList<>();
        List<String> chunkPaths = translateTaskV3BlobRepo.listBlobPaths(blobPath(shopName, taskId, "chunks/" + module + "/"));
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

    private static String normalizeLocaleCode(String locale) {
        if (StringUtils.isEmpty(locale)) {
            return locale;
        }
        String cleaned = locale.trim().replace('_', '-');
        if (cleaned.isEmpty()) {
            return cleaned;
        }
        String[] parts = cleaned.split("-");
        if (parts.length == 0) {
            return cleaned;
        }
        StringBuilder normalized = new StringBuilder(parts[0].toLowerCase());
        for (int i = 1; i < parts.length; i++) {
            if (parts[i] == null || parts[i].isEmpty()) {
                continue;
            }
            normalized.append("-");
            if (parts[i].length() <= 3) {
                normalized.append(parts[i].toUpperCase());
            } else {
                normalized.append(Character.toUpperCase(parts[i].charAt(0)));
                normalized.append(parts[i].substring(1).toLowerCase());
            }
        }
        return normalized.toString();
    }

    private static boolean sameLocaleCode(String left, String right) {
        if (StringUtils.isEmpty(left) || StringUtils.isEmpty(right)) {
            return false;
        }
        return normalizeLocaleCode(left).equals(normalizeLocaleCode(right));
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

    private static String blobPath(String shopName, String taskId, String tail) {
        return "tasks/" + shopName + "/" + taskId + "/" + tail;
    }

    /** 用于翻译质量抽检：JSON Pointer 路径 + 原文 + 译文（成功写入 translationAcc 的条目） */
    private static final class RuntimeQualityPair {
        private final String path;
        private final String sourceText;
        private final String targetText;

        private RuntimeQualityPair(String path, String sourceText, String targetText) {
            this.path = path == null ? "" : path;
            this.sourceText = sourceText == null ? "" : sourceText;
            this.targetText = targetText == null ? "" : targetText;
        }
    }

    private static class RuntimeSourceItem {
        private final String path;
        private final String text;

        private RuntimeSourceItem(String path, String text) {
            this.path = path;
            this.text = text;
        }
    }

    private static class RuntimeSkippedItem {
        private final String path;
        private final String nodeType;

        private RuntimeSkippedItem(String path, String nodeType) {
            this.path = path;
            this.nodeType = nodeType;
        }

        @Override
        public String toString() {
            return path + ":" + nodeType;
        }
    }

    private static final class RuntimeLlmTokenUsage {
        private static final RuntimeLlmTokenUsage ZERO = new RuntimeLlmTokenUsage(0, 0, 0);
        private final long promptTokens;
        private final long completionTokens;
        private final long totalTokens;

        private RuntimeLlmTokenUsage(long promptTokens, long completionTokens, long totalTokens) {
            this.promptTokens = promptTokens;
            this.completionTokens = completionTokens;
            this.totalTokens = totalTokens;
        }

        private RuntimeLlmTokenUsage add(RuntimeLlmTokenUsage o) {
            if (o == null) {
                return this;
            }
            return new RuntimeLlmTokenUsage(
                    this.promptTokens + o.promptTokens,
                    this.completionTokens + o.completionTokens,
                    this.totalTokens + o.totalTokens);
        }

        private static RuntimeLlmTokenUsage ofTotals(long promptTokens, long completionTokens, long totalTokens) {
            return new RuntimeLlmTokenUsage(promptTokens, completionTokens, totalTokens);
        }

        private boolean hasNonZero() {
            return promptTokens > 0 || completionTokens > 0 || totalTokens > 0;
        }

        private static RuntimeLlmTokenUsage fromRedisMeta(Map<String, String> meta) {
            if (meta == null) {
                return ZERO;
            }
            return new RuntimeLlmTokenUsage(
                    parseLongString(meta.get("llmPromptTokens")),
                    parseLongString(meta.get("llmCompletionTokens")),
                    parseLongString(meta.get("llmTotalTokens")));
        }

        private static long parseLongString(String s) {
            if (s == null || s.isEmpty()) {
                return 0L;
            }
            try {
                return Long.parseLong(s.trim());
            } catch (Exception e) {
                return 0L;
            }
        }
    }

    private static final class RuntimePlainTextOutcome {
        private final String text;
        private final RuntimeLlmTokenUsage usage;

        private RuntimePlainTextOutcome(String text, RuntimeLlmTokenUsage usage) {
            this.text = text == null ? "" : text;
            this.usage = usage == null ? RuntimeLlmTokenUsage.ZERO : usage;
        }
    }

    private static final class RuntimeModelTranslateOutcome {
        private final Map<String, String> translations;
        private final RuntimeLlmTokenUsage usage;

        private RuntimeModelTranslateOutcome(Map<String, String> translations, RuntimeLlmTokenUsage usage) {
            this.translations = translations == null ? new LinkedHashMap<>() : translations;
            this.usage = usage == null ? RuntimeLlmTokenUsage.ZERO : usage;
        }
    }

    private static class RuntimeBatchResult {
        private final Map<String, String> successMap;
        private final Map<String, String> failMap;
        private final long batchCostMs;
        private final RuntimeLlmTokenUsage llmUsage;

        private RuntimeBatchResult(Map<String, String> successMap,
                                   Map<String, String> failMap,
                                   long batchCostMs,
                                   RuntimeLlmTokenUsage llmUsage) {
            this.successMap = successMap == null ? new LinkedHashMap<>() : successMap;
            this.failMap = failMap == null ? new LinkedHashMap<>() : failMap;
            this.batchCostMs = batchCostMs;
            this.llmUsage = llmUsage == null ? RuntimeLlmTokenUsage.ZERO : llmUsage;
        }
    }

    private static class RuntimeModelException extends Exception {
        private final boolean retryable;
        private final String message;

        private RuntimeModelException(boolean retryable, String message) {
            this.retryable = retryable;
            this.message = message == null ? "UNKNOWN_MODEL_ERROR" : message;
        }
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
        private final boolean timedOut;

        private TranslateChunkResult(int translatedDelta, int usedTokenDelta, int currentUsedToken,
                                     boolean hitTokenLimit, boolean timedOut) {
            this.translatedDelta = translatedDelta;
            this.usedTokenDelta = usedTokenDelta;
            this.currentUsedToken = currentUsedToken;
            this.hitTokenLimit = hitTokenLimit;
            this.timedOut = timedOut;
        }

        private static TranslateChunkResult timeout(int currentUsedToken) {
            return new TranslateChunkResult(0, 0, currentUsedToken, false, true);
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
