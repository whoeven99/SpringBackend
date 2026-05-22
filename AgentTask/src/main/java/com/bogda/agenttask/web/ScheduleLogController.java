package com.bogda.agenttask.web;

import com.bogda.common.controller.response.BaseResponse;
import com.bogda.repository.container.TranslateTaskV3ScheduleLogDO;
import com.bogda.repository.repo.translate.TranslateTaskV3ScheduleLogCosmosRepo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 任务调度日志查询 API。
 * 支持按任务 ID 或店铺查询调度历史。
 */
@RestController
@RequestMapping("/translate/v3")
public class ScheduleLogController {

    private static final Logger LOG = LoggerFactory.getLogger(ScheduleLogController.class);

    @Autowired
    private TranslateTaskV3ScheduleLogCosmosRepo scheduleLogRepo;

    /**
     * 按任务 ID 查询调度日志。
     * GET /translate/v3/schedule-logs/task?taskId=xxx&limit=100
     */
    @GetMapping("/schedule-logs/task")
    public BaseResponse<Map<String, Object>> listScheduleLogsByTaskId(
            @RequestParam String taskId,
            @RequestParam(required = false, defaultValue = "100") int limit) {
        try {
            if (taskId == null || taskId.isBlank()) {
                return BaseResponse.FailedResponse("Missing parameter: taskId");
            }

            List<TranslateTaskV3ScheduleLogDO> logs = scheduleLogRepo.listByTaskId(taskId, limit);
            Map<String, Integer> summary = scheduleLogRepo.countEventTypes(taskId);

            Map<String, Object> result = new HashMap<>();
            result.put("logs", logs);
            result.put("summary", summary);
            result.put("total", logs.size());

            LOG.info("Schedule logs queried by taskId: taskId={}, count={}", taskId, logs.size());
            return BaseResponse.SuccessResponse(result);
        } catch (Exception e) {
            LOG.error("Failed to query schedule logs by taskId: taskId={}", taskId, e);
            return BaseResponse.FailedResponse("Failed to query schedule logs: " + e.getMessage());
        }
    }

    /**
     * 按店铺和时间范围查询调度日志。
     * GET /translate/v3/schedule-logs/shop?shopName=xxx&startTime=1234567890&endTime=1234567890&limit=100
     */
    @GetMapping("/schedule-logs/shop")
    public BaseResponse<Map<String, Object>> listScheduleLogsByShop(
            @RequestParam String shopName,
            @RequestParam(required = false) Long startTime,
            @RequestParam(required = false) Long endTime,
            @RequestParam(required = false, defaultValue = "100") int limit) {
        try {
            if (shopName == null || shopName.isBlank()) {
                return BaseResponse.FailedResponse("Missing parameter: shopName");
            }

            long start = startTime != null ? startTime : System.currentTimeMillis() - 24 * 60 * 60 * 1000; // 默认 24 小时前
            long end = endTime != null ? endTime : System.currentTimeMillis();

            List<TranslateTaskV3ScheduleLogDO> logs = scheduleLogRepo.listByShopAndTime(shopName, start, end, limit);

            Map<String, Object> result = new HashMap<>();
            result.put("logs", logs);
            result.put("total", logs.size());
            result.put("shopName", shopName);
            result.put("timeRange", new HashMap<String, Long>() {{
                put("startTime", start);
                put("endTime", end);
            }});

            LOG.info("Schedule logs queried by shop: shopName={}, startTime={}, endTime={}, count={}",
                    shopName, start, end, logs.size());
            return BaseResponse.SuccessResponse(result);
        } catch (Exception e) {
            LOG.error("Failed to query schedule logs by shop: shopName={}", shopName, e);
            return BaseResponse.FailedResponse("Failed to query schedule logs: " + e.getMessage());
        }
    }

    /**
     * 查询任务的调度事件统计。
     * GET /translate/v3/schedule-logs/summary?taskId=xxx
     */
    @GetMapping("/schedule-logs/summary")
    public BaseResponse<Map<String, Object>> getScheduleLogSummary(@RequestParam String taskId) {
        try {
            if (taskId == null || taskId.isBlank()) {
                return BaseResponse.FailedResponse("Missing parameter: taskId");
            }

            Map<String, Integer> summary = scheduleLogRepo.countEventTypes(taskId);
            int total = summary.values().stream().mapToInt(Integer::intValue).sum();

            Map<String, Object> result = new HashMap<>();
            result.put("taskId", taskId);
            result.put("summary", summary);
            result.put("total", total);

            LOG.info("Schedule log summary queried: taskId={}, total={}", taskId, total);
            return BaseResponse.SuccessResponse(result);
        } catch (Exception e) {
            LOG.error("Failed to query schedule log summary: taskId={}", taskId, e);
            return BaseResponse.FailedResponse("Failed to query schedule log summary: " + e.getMessage());
        }
    }
}
