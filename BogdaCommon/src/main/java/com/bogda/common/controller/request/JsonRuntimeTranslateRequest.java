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
    /**
     * 可选。写入 Chat Completions 请求体的 {@code max_tokens}（输出上限）。大块 JSON 字符串翻译时若不设置，
     * 默认 completion 可能被网关截断，导致返回无法解析为合法 JSON（{@code MODEL_RESPONSE_JSON_INVALID}）。
     */
    private Integer maxCompletionTokens;
    private Integer concurrency;
    private Integer maxRetries;
    private Integer baseBackoffMs;
}
