package com.bogdatech.entity.DTO;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ScheduledTranslateTaskDTO<T> {
    private String taskId;         // 任务ID
    private T messageData;         // 消息数据
    private LocalDateTime createdAt; // 创建时间

    public ScheduledTranslateTaskDTO<T> CreateMessageData(T messageData) {
        this.messageData = messageData;
        return this;
    }
}
