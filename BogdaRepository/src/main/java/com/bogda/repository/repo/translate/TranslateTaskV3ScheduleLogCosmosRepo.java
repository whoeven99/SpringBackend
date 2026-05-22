package com.bogda.repository.repo.translate;

import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.PartitionKey;
import com.azure.cosmos.models.SqlParameter;
import com.azure.cosmos.models.SqlQuerySpec;
import com.bogda.common.reporter.TraceReporterHolder;
import com.bogda.repository.container.TranslateTaskV3ScheduleLogDO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * 任务调度日志 Repository。
 * 支持按任务、店铺、时间范围查询调度历史。
 */
@Service
public class TranslateTaskV3ScheduleLogCosmosRepo {

    private static final Logger LOG = LoggerFactory.getLogger(TranslateTaskV3ScheduleLogCosmosRepo.class);

    @Autowired
    @Qualifier("translateTaskV3ScheduleLogContainer")
    private CosmosContainer scheduleLogContainer;

    /**
     * 记录一条调度日志。
     */
    public boolean append(TranslateTaskV3ScheduleLogDO log) {
        try {
            if (log == null || log.getTaskId() == null || log.getTaskId().isEmpty()) {
                LOG.warn("v3 schedule log skip, invalid taskId");
                return false;
            }

            // 生成唯一 ID（UUID）
            if (log.getId() == null || log.getId().isEmpty()) {
                log.setId(UUID.randomUUID().toString());
            }

            // 确保有时间戳
            if (log.getCreatedAt() == null || log.getCreatedAt() <= 0) {
                log.setCreatedAt(System.currentTimeMillis());
            }

            CosmosItemRequestOptions options = new CosmosItemRequestOptions()
                    .setContentResponseOnWriteEnabled(false);
            scheduleLogContainer.upsertItem(log, options);

            LOG.info("v3 schedule log appended, taskId={}, eventType={}, shop={}, source={}",
                    log.getTaskId(), log.getEventType(), log.getShopName(), log.getSource());
            return true;
        } catch (Exception e) {
            TraceReporterHolder.report("TranslateTaskV3ScheduleLogCosmosRepo.append",
                    "FatalException append schedule log failed: taskId=" + (log != null ? log.getTaskId() : "null") +
                            ", error=" + e);
            return false;
        }
    }

    /**
     * 按任务 ID 查询调度日志（最新 limit 条）。
     */
    public List<TranslateTaskV3ScheduleLogDO> listByTaskId(String taskId, int limit) {
        if (taskId == null || taskId.isEmpty()) {
            return Collections.emptyList();
        }
        try {
            String sql = "SELECT * FROM c WHERE c.taskId = @taskId ORDER BY c.createdAt DESC OFFSET 0 LIMIT @limit";
            SqlQuerySpec spec = new SqlQuerySpec(sql, List.of(
                    new SqlParameter("@taskId", taskId),
                    new SqlParameter("@limit", Math.min(Math.max(limit, 1), 1000))
            ));

            List<TranslateTaskV3ScheduleLogDO> results = new ArrayList<>();
            CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
            scheduleLogContainer.queryItems(spec, options, TranslateTaskV3ScheduleLogDO.class)
                    .stream()
                    .forEach(results::add);

            LOG.info("v3 schedule log queried by taskId, taskId={}, count={}", taskId, results.size());
            return results;
        } catch (Exception e) {
            TraceReporterHolder.report("TranslateTaskV3ScheduleLogCosmosRepo.listByTaskId",
                    "FatalException query schedule logs by taskId failed: taskId=" + taskId +
                            ", error=" + e);
            return Collections.emptyList();
        }
    }

    /**
     * 按商店和时间范围查询调度日志。
     */
    public List<TranslateTaskV3ScheduleLogDO> listByShopAndTime(String shopName, long startTime, long endTime, int limit) {
        if (shopName == null || shopName.isEmpty()) {
            return Collections.emptyList();
        }
        try {
            String sql = "SELECT * FROM c WHERE c.shopName = @shopName AND c.createdAt >= @startTime AND c.createdAt <= @endTime " +
                    "ORDER BY c.createdAt DESC OFFSET 0 LIMIT @limit";
            SqlQuerySpec spec = new SqlQuerySpec(sql, List.of(
                    new SqlParameter("@shopName", shopName),
                    new SqlParameter("@startTime", startTime),
                    new SqlParameter("@endTime", endTime),
                    new SqlParameter("@limit", Math.min(Math.max(limit, 1), 1000))
            ));

            List<TranslateTaskV3ScheduleLogDO> results = new ArrayList<>();
            CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
            scheduleLogContainer.queryItems(spec, options, TranslateTaskV3ScheduleLogDO.class)
                    .stream()
                    .forEach(results::add);

            LOG.info("v3 schedule log queried by shop and time, shopName={}, count={}", shopName, results.size());
            return results;
        } catch (Exception e) {
            TraceReporterHolder.report("TranslateTaskV3ScheduleLogCosmosRepo.listByShopAndTime",
                    "FatalException query schedule logs by shop and time failed: shopName=" + shopName +
                            ", error=" + e);
            return Collections.emptyList();
        }
    }

    /**
     * 按任务 ID 和事件类型查询日志。
     */
    public List<TranslateTaskV3ScheduleLogDO> listByTaskIdAndEventType(String taskId, String eventType) {
        if (taskId == null || taskId.isEmpty() || eventType == null || eventType.isEmpty()) {
            return Collections.emptyList();
        }
        try {
            String sql = "SELECT * FROM c WHERE c.taskId = @taskId AND c.eventType = @eventType ORDER BY c.createdAt DESC";
            SqlQuerySpec spec = new SqlQuerySpec(sql, List.of(
                    new SqlParameter("@taskId", taskId),
                    new SqlParameter("@eventType", eventType)
            ));

            List<TranslateTaskV3ScheduleLogDO> results = new ArrayList<>();
            CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
            scheduleLogContainer.queryItems(spec, options, TranslateTaskV3ScheduleLogDO.class)
                    .stream()
                    .forEach(results::add);

            return results;
        } catch (Exception e) {
            TraceReporterHolder.report("TranslateTaskV3ScheduleLogCosmosRepo.listByTaskIdAndEventType",
                    "FatalException query schedule logs by taskId and eventType failed: taskId=" + taskId +
                            ", eventType=" + eventType + ", error=" + e);
            return Collections.emptyList();
        }
    }

    /**
     * 查询事件统计（用于摘要）。
     */
    public java.util.Map<String, Integer> countEventTypes(String taskId) {
        if (taskId == null || taskId.isEmpty()) {
            return Collections.emptyMap();
        }
        try {
            String sql = "SELECT c.eventType, COUNT(1) as count FROM c WHERE c.taskId = @taskId GROUP BY c.eventType";
            SqlQuerySpec spec = new SqlQuerySpec(sql, List.of(
                    new SqlParameter("@taskId", taskId)
            ));

            java.util.Map<String, Integer> result = new java.util.HashMap<>();
            CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
            scheduleLogContainer.queryItems(spec, options, java.util.Map.class)
                    .stream()
                    .forEach(item -> {
                        String eventType = (String) item.get("eventType");
                        Integer count = ((Number) item.get("count")).intValue();
                        result.put(eventType, count);
                    });

            return result;
        } catch (Exception e) {
            TraceReporterHolder.report("TranslateTaskV3ScheduleLogCosmosRepo.countEventTypes",
                    "FatalException count event types failed: taskId=" + taskId + ", error=" + e);
            return Collections.emptyMap();
        }
    }
}
