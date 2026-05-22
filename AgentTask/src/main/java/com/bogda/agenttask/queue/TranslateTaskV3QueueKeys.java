package com.bogda.agenttask.queue;

/**
 * Redis List 队列 key；须与 Spark {@code TRANSLATE_V3_QUEUE_KEYS} 完全一致。
 */
public final class TranslateTaskV3QueueKeys {

    public static final String INIT = "translate:v3:q:init";
    public static final String TRANSLATE = "translate:v3:q:translate";

    private TranslateTaskV3QueueKeys() {
    }

    public static String forStage(TranslateTaskV3QueueStage stage) {
        if (stage == TranslateTaskV3QueueStage.TRANSLATE) {
            return TRANSLATE;
        }
        return INIT;
    }
}
