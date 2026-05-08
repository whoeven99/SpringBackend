package com.bogda.api.agent;

import com.bogda.common.controller.request.JsonRuntimeTranslateRequest;
import com.bogda.common.utils.JsonUtils;
import com.bogda.common.utils.StringUtils;
import com.bogda.service.logic.translate.TranslateV3Service;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class JsonRuntimeAgentTools {
    private static final Logger LOG = LoggerFactory.getLogger(JsonRuntimeAgentTools.class);
    private static final Pattern TASK_ID_KV_PATTERN = Pattern.compile("taskId\\s*[=:：]\\s*([A-Za-z0-9\\-]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern TASK_ID_FREE_PATTERN = Pattern.compile("\\b([0-9a-fA-F]{8}\\-[0-9a-fA-F]{4}\\-[0-9a-fA-F]{4}\\-[0-9a-fA-F]{4}\\-[0-9a-fA-F]{12}|rt-[A-Za-z0-9\\-]+)\\b");
    private static final Pattern REDIS_PREFIX_PATTERN = Pattern.compile("\\b([a-z]{1,8}:[A-Za-z0-9:\\-]+)\\b");

    @Autowired
    private TranslateV3Service translateV3Service;
    private final ThreadLocal<Object> plannerContext = new ThreadLocal<>();
    private final ThreadLocal<Object> executionContext = new ThreadLocal<>();

    @Tool("规划 JSON 翻译任务下一步，输入 userMessage，输出 Thought/Action/Observation 结构化 JSON")
    public String plannerStep(String userMessage) {
        plannerContext.remove();
        executionContext.remove();
        String text = userMessage == null ? "" : userMessage.trim();
        LOG.info("agent-tools plannerStep start, userMessage={}", shorten(text, 300));
        String taskId = extractTaskId(text);
        String redisPrefix = extractRedisPrefix(text);
        String embeddedJson = extractJson(text);
        String action;
        String thought;
        String observation;
        Map<String, Object> actionInput = new LinkedHashMap<>();

        if (!StringUtils.isEmpty(embeddedJson)) {
            action = "runJsonTaskByRequestJson";
            thought = "检测到完整请求 JSON，优先直接执行运行时翻译。";
            observation = "已从消息中提取请求 JSON。";
            actionInput.put("requestJson", embeddedJson);
        } else if (containsProgressIntent(text)) {
            action = "getJsonTaskProgress";
            thought = "用户意图偏向查询进度，应调用进度工具。";
            observation = StringUtils.isEmpty(taskId) ? "未明确提取到 taskId，执行阶段将提示缺失参数。" : "已提取 taskId。";
            actionInput.put("taskId", taskId);
            actionInput.put("redisPrefix", redisPrefix);
        } else if (!StringUtils.isEmpty(taskId)) {
            action = "runJsonTaskByTaskId";
            thought = "已识别 taskId，按任务 ID 直接执行翻译。";
            observation = "已提取 taskId，可执行。";
            actionInput.put("taskId", taskId);
        } else {
            action = "ASK_MISSING_TASK_ID";
            thought = "未识别到 taskId 或完整请求 JSON，无法执行。";
            observation = "需要用户提供 taskId 或完整请求 JSON。";
        }
        LOG.info("agent-tools plannerStep decided, action={}, taskId={}, redisPrefix={}",
                action, taskId, redisPrefix);
        Map<String, Object> planLog = buildStepLog("PLAN", thought, action, observation, actionInput);
        plannerContext.set(planLog);
        return JsonUtils.objectToJson(planLog);
    }

    @Tool("按 taskId 执行 JSON 翻译任务，参数是 taskId 字符串")
    public String runJsonTaskByTaskId(String taskId) {
        LOG.info("agent-tools runJsonTaskByTaskId start, taskId={}", taskId);
        if (taskId == null || taskId.trim().isEmpty()) {
            LOG.warn("agent-tools runJsonTaskByTaskId invalid param: missing taskId");
            Map<String, Object> stepLog = buildStepLog(
                    "EXECUTE",
                    "执行工具前校验参数。",
                    "runJsonTaskByTaskId",
                    "缺少 taskId，无法执行。",
                    Map.of("status", "FAILED", "reason", "MISSING_TASK_ID")
            );
            executionContext.set(stepLog);
            return JsonUtils.objectToJson(stepLog);
        }
        Map<String, Object> result = translateV3Service.executeJsonRuntimeTaskByTaskId(taskId);
        LOG.info("agent-tools runJsonTaskByTaskId finish, taskId={}, status={}, reason={}, done={}, failed={}",
                taskId, result.get("status"), result.get("reason"), result.get("done"), result.get("failed"));
        String observation = "执行完成，返回任务结果。";
        if ("FAILED".equals(String.valueOf(result.get("status")))) {
            Object reason = result.get("reason");
            Object hint = result.get("hint");
            if (reason != null && !String.valueOf(reason).isEmpty()) {
                observation = "执行失败: reason=" + reason
                        + (hint != null && !String.valueOf(hint).isEmpty() ? " — " + hint : "");
            } else {
                observation = "执行失败，详见 payload（多为 Blob/参数校验或运行时错误）。";
            }
        }
        Map<String, Object> stepLog = buildStepLog(
                "EXECUTE",
                "已根据 taskId 调用运行时执行器。",
                "runJsonTaskByTaskId",
                observation,
                result
        );
        executionContext.set(stepLog);
        return JsonUtils.objectToJson(stepLog);
    }

    @Tool("按完整请求 JSON 执行 JSON 翻译任务，参数是 JsonRuntimeTranslateRequest 的 JSON 字符串")
    public String runJsonTaskByRequestJson(String requestJson) {
        LOG.info("agent-tools runJsonTaskByRequestJson start, requestJson={}", shorten(requestJson, 500));
        JsonRuntimeTranslateRequest request = JsonUtils.jsonToObjectWithNull(requestJson, JsonRuntimeTranslateRequest.class);
        if (request == null) {
            LOG.warn("agent-tools runJsonTaskByRequestJson invalid JSON");
            Map<String, Object> stepLog = buildStepLog(
                    "EXECUTE",
                    "执行工具前先解析请求 JSON。",
                    "runJsonTaskByRequestJson",
                    "请求 JSON 非法，无法执行。",
                    Map.of("status", "FAILED", "reason", "INVALID_REQUEST_JSON")
            );
            executionContext.set(stepLog);
            return JsonUtils.objectToJson(stepLog);
        }
        Map<String, Object> result = translateV3Service.executeJsonRuntimeTask(request);
        LOG.info("agent-tools runJsonTaskByRequestJson finish, taskId={}, status={}, done={}, failed={}",
                request.getTaskId(), result.get("status"), result.get("done"), result.get("failed"));
        Map<String, Object> stepLog = buildStepLog(
                "EXECUTE",
                "已按完整请求参数调用运行时执行器。",
                "runJsonTaskByRequestJson",
                "执行完成，返回任务结果。",
                result
        );
        executionContext.set(stepLog);
        return JsonUtils.objectToJson(stepLog);
    }

    @Tool("读取 JSON 翻译任务实时进度，参数格式：taskId,redisPrefix（redisPrefix 可为空）")
    public String getJsonTaskProgress(String taskId, String redisPrefix) {
        LOG.info("agent-tools getJsonTaskProgress start, taskId={}, redisPrefix={}", taskId, redisPrefix);
        if (taskId == null || taskId.trim().isEmpty()) {
            LOG.warn("agent-tools getJsonTaskProgress invalid param: missing taskId");
            Map<String, Object> stepLog = buildStepLog(
                    "EXECUTE",
                    "执行进度查询前校验参数。",
                    "getJsonTaskProgress",
                    "缺少 taskId，无法查询。",
                    Map.of("status", "FAILED", "reason", "MISSING_TASK_ID")
            );
            executionContext.set(stepLog);
            return JsonUtils.objectToJson(stepLog);
        }
        Map<String, Object> result = translateV3Service.getJsonRuntimeTaskProgress(taskId, redisPrefix);
        LOG.info("agent-tools getJsonTaskProgress finish, taskId={}, meta={}",
                taskId, shorten(JsonUtils.objectToJson(result.get("meta")), 400));
        Map<String, Object> stepLog = buildStepLog(
                "EXECUTE",
                "已调用进度查询工具。",
                "getJsonTaskProgress",
                "查询完成，返回实时进度。",
                result
        );
        executionContext.set(stepLog);
        return JsonUtils.objectToJson(stepLog);
    }

    @Tool("汇总轨迹日志（无参数），输出统一 Thought/Action/Observation 轨迹 JSON")
    public String finalizeTrace() {
        LOG.info("agent-tools finalizeTrace start");
        Object plannerObj = plannerContext.get();
        Object executionObj = executionContext.get();
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("planner", plannerObj == null ? Map.of("status", "MISSING_PLAN") : plannerObj);
        summary.put("execution", executionObj == null ? Map.of("status", "MISSING_EXECUTION") : executionObj);
        summary.put("status", resolveTraceStatus(executionObj == null ? null : executionObj));
        LOG.info("agent-tools finalizeTrace finish, status={}", summary.get("status"));
        plannerContext.remove();
        executionContext.remove();

        return JsonUtils.objectToJson(buildStepLog(
                "FINALIZE",
                "已完成计划与执行两个阶段，开始汇总轨迹。",
                "finalizeTrace",
                "已产出结构化 ReAct 轨迹。",
                summary
        ));
    }

    private Map<String, Object> buildStepLog(String step,
                                             String thought,
                                             String action,
                                             String observation,
                                             Object payload) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("step", step);
        map.put("thought", thought);
        map.put("action", action);
        map.put("observation", observation);
        map.put("payload", payload);
        return map;
    }

    private boolean containsProgressIntent(String text) {
        if (text == null) {
            return false;
        }
        String lower = text.toLowerCase();
        return lower.contains("进度") || lower.contains("状态") || lower.contains("progress") || lower.contains("status");
    }

    private String extractTaskId(String text) {
        if (StringUtils.isEmpty(text)) {
            return "";
        }
        Matcher kvMatcher = TASK_ID_KV_PATTERN.matcher(text);
        if (kvMatcher.find()) {
            return kvMatcher.group(1);
        }
        Matcher freeMatcher = TASK_ID_FREE_PATTERN.matcher(text);
        if (freeMatcher.find()) {
            return freeMatcher.group(1);
        }
        return "";
    }

    private String extractRedisPrefix(String text) {
        if (StringUtils.isEmpty(text)) {
            return "tr:v1";
        }
        Matcher matcher = REDIS_PREFIX_PATTERN.matcher(text);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "tr:v1";
    }

    private String extractJson(String text) {
        if (StringUtils.isEmpty(text)) {
            return "";
        }
        String json = StringUtils.extractJsonBlock(text);
        return json == null ? "" : json;
    }

    private String resolveTraceStatus(Object executionObj) {
        if (!(executionObj instanceof Map<?, ?> map)) {
            return "UNKNOWN";
        }
        Object payloadObj = map.get("payload");
        if (!(payloadObj instanceof Map<?, ?> payload)) {
            return "UNKNOWN";
        }
        Object status = payload.get("status");
        return status == null ? "UNKNOWN" : String.valueOf(status);
    }

    private String shorten(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...(truncated)";
    }
}
