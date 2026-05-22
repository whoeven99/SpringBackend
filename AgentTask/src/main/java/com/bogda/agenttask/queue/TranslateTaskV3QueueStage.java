package com.bogda.agenttask.queue;

/**
 * 与 Spark {@code translateTaskV3Queue.server.ts}、Redis List key 对齐。
 */
public enum TranslateTaskV3QueueStage {
    INIT,
    TRANSLATE
}
