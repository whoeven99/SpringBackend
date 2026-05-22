package com.bogda.repository.container;

import com.azure.spring.data.cosmos.core.mapping.Container;
import com.azure.spring.data.cosmos.core.mapping.PartitionKey;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;

/**
 * 任务调度日志：记录每次任务队列转换、入出队、处理事件。
 * 支持按任务或商店查询调度历史。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Container(containerName = "translate_tasks_v3_schedule_logs", autoCreateContainer = false)
public class TranslateTaskV3ScheduleLogDO {

    @Id
    private String id;  // UUID，每条日志唯一

    @PartitionKey
    private String shopName;  // 分区键：店铺名

    // 核心字段
    private String taskId;  // 关联的任务 ID
    private String taskName;  // 任务名称（冗余，便于查询展示）

    // 调度事件类型
    private String eventType;  // ENQUEUED_INIT, DEQUEUED_INIT, PROCESS_INIT_START, PROCESS_INIT_END
                               // ENQUEUED_TRANSLATE, DEQUEUED_TRANSLATE, PROCESS_TRANSLATE_START, PROCESS_TRANSLATE_END

    // 状态转换
    private Integer statusBefore;  // 事件发生前的状态
    private Integer statusAfter;   // 事件发生后的状态

    // 队列信息
    private String queueStage;  // INIT 或 TRANSLATE
    private Long enqueuedAt;    // 入队时间（毫秒）
    private Long dequeuedAt;    // 出队时间（毫秒）
    private Long processedAt;   // 处理完成时间（毫秒）

    // 细节和错误
    private String message;     // 事件描述
    private String errorMsg;    // 错误信息（如有）
    private Boolean success;    // 是否成功

    // 审计字段
    private Long createdAt;  // 日志创建时间（毫秒）
    private String source;   // 数据来源标记（如 QueueRepo, QueueHandler）
}
