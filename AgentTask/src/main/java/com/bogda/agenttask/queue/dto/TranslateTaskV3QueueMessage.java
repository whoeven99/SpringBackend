package com.bogda.agenttask.queue.dto;

import com.bogda.agenttask.queue.TranslateTaskV3QueueStage;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * LPUSH / BRPOP 载荷；Spark 与 AgentTask 共用 JSON 形状。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class TranslateTaskV3QueueMessage {

    private String taskId;
    private String shopName;
    private TranslateTaskV3QueueStage stage;
    private Long enqueuedAt;

    public TranslateTaskV3QueueMessage() {
    }

    public TranslateTaskV3QueueMessage(String taskId, String shopName, TranslateTaskV3QueueStage stage, long enqueuedAt) {
        this.taskId = taskId;
        this.shopName = shopName;
        this.stage = stage;
        this.enqueuedAt = enqueuedAt;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getShopName() {
        return shopName;
    }

    public void setShopName(String shopName) {
        this.shopName = shopName;
    }

    public TranslateTaskV3QueueStage getStage() {
        return stage;
    }

    public void setStage(TranslateTaskV3QueueStage stage) {
        this.stage = stage;
    }

    public Long getEnqueuedAt() {
        return enqueuedAt;
    }

    public void setEnqueuedAt(Long enqueuedAt) {
        this.enqueuedAt = enqueuedAt;
    }
}
