package com.bogda.repository.repo.translate;

/**
 * 与 Spark {@code cosmosJobStore.server.ts STATUS_META} 对齐的 Cosmos 任务状态码。
 * translation_jobs 容器分区键为 {@code /shopName}，AgentTask 与 Spark 共用同一套 status / statusText。
 */
public final class TranslateTaskV3CosmosStatus {
    public static final int INIT_PENDING = 0;
    public static final int INIT_READING_SHOPIFY = 1;
    public static final int INIT_DONE = 2;
    public static final int TRANSLATE_RUNNING = 3;
    public static final int TRANSLATE_STOPPED_MANUAL = 4;
    public static final int TRANSLATE_DONE = 5;
    public static final int SAVE_RUNNING = 6;
    public static final int SAVE_DONE = 7;
    public static final int FAILED = 8;

    private TranslateTaskV3CosmosStatus() {
    }

    public static String toStatusText(int status) {
        return switch (status) {
            case INIT_PENDING -> "INIT_PENDING";
            case INIT_READING_SHOPIFY -> "INIT_READING_SHOPIFY";
            case INIT_DONE -> "INIT_DONE";
            case TRANSLATE_RUNNING -> "TRANSLATE_RUNNING";
            case TRANSLATE_STOPPED_MANUAL -> "TRANSLATE_STOPPED_MANUAL";
            case TRANSLATE_DONE -> "TRANSLATE_DONE";
            case SAVE_RUNNING -> "SAVE_RUNNING";
            case SAVE_DONE -> "SAVE_DONE";
            case FAILED -> "FAILED";
            default -> "UNKNOWN";
        };
    }
}
