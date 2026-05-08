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
    private Integer batchSize;
    private Integer maxCharsPerBatch;
    private Integer concurrency;
    private Integer maxRetries;
    private Integer baseBackoffMs;
}
