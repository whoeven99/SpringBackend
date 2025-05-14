package com.bogdatech.entity.DTO;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class ScheduledTranslateTaskDTO<T> implements Serializable {
    private String taskId;         // 任务ID
    private T messageData;         // 消息数据
    private LocalDateTime createdAt; // 创建时间

    public ScheduledTranslateTaskDTO<T> CreateMessageData(T messageData) {
        this.taskId = messageData.toString();
        this.messageData = messageData;
        this.createdAt = LocalDateTime.now();
        return this;
    }
}
