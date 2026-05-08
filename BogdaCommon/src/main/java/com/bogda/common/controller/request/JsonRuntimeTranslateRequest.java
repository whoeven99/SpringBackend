package com.bogda.common.controller.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class JsonRuntimeTranslateRequest {
    private String taskId;
    private String inputBlobUri;
    private String outputBlobUri;
    private String reportBlobUri;
    private String redisPrefix;
    private String redisConn;
    private String provider;
    private String model;
    private String apiBase;
    private String apiKey;
    private String sourceLang;
    private String targetLang;
    /**
     * 可选，映射到 OpenAI 兼容接口请求体中的 {@code user} 字段（终用户/会话追踪，非对话记忆）。
     * 不提供则对按任务构建的请求可由服务端填入 {@code sessionId}。
     */
    private String openaiUser;
    private Integer batchSize;
    private Integer maxCharsPerBatch;
    private Integer concurrency;
    private Integer maxRetries;
    private Integer baseBackoffMs;
}
