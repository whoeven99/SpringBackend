package com.bogda.agenttask.task;

import org.springframework.stereotype.Component;

/**
 * V3 翻译由 {@link com.bogda.agenttask.queue.TranslateTaskV3QueueConsumer} Redis BRPOP 驱动。
 * 下方 Cosmos 30s 轮询已停用；需要兜底时可恢复 {@code @Scheduled} 方法。
 */
@Component
public class TranslateTaskV3AgentScheduled {

    /*
    @EnableScheduledTask
    @Scheduled(fixedDelay = 30 * 1000)
    public void initialToTranslateTaskV3() {
        translateV3Service.processInitialTasksV3();
    }

    @EnableScheduledTask
    @Scheduled(fixedDelay = 30 * 1000)
    public void translateEachTaskV3() {
        translateV3Service.processTranslateTasksV3();
    }
    */
}
