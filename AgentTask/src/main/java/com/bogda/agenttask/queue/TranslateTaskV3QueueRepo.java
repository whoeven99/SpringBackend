package com.bogda.agenttask.queue;

import com.bogda.agenttask.config.TranslateTaskV3QueueRedisConfig;
import com.bogda.agenttask.queue.dto.TranslateTaskV3QueueMessage;
import com.bogda.common.utils.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.concurrent.TimeUnit;

/**
 * Translate V3 任务 Redis List 队列（LPUSH 生产 / BRPOP 消费）。
 */
@Repository
public class TranslateTaskV3QueueRepo {

    private static final Logger LOG = LoggerFactory.getLogger(TranslateTaskV3QueueRepo.class);

    private final StringRedisTemplate queueRedis;

    public TranslateTaskV3QueueRepo(
            @Qualifier(TranslateTaskV3QueueRedisConfig.QUEUE_STRING_REDIS_TEMPLATE) StringRedisTemplate queueRedis) {
        this.queueRedis = queueRedis;
    }

    public void enqueue(TranslateTaskV3QueueMessage message) {
        if (message == null || message.getTaskId() == null || message.getTaskId().isBlank()) {
            return;
        }
        TranslateTaskV3QueueStage stage = message.getStage() == null
                ? TranslateTaskV3QueueStage.INIT
                : message.getStage();
        String key = TranslateTaskV3QueueKeys.forStage(stage);
        if (message.getEnqueuedAt() == null) {
            message.setEnqueuedAt(System.currentTimeMillis());
        }
        String json = JsonUtils.objectToJson(message);
        if (json == null || json.isBlank()) {
            LOG.warn("v3 queue skip enqueue, json serialize failed, taskId={}", message.getTaskId());
            return;
        }
        queueRedis.opsForList().leftPush(key, json);
        LOG.info("v3 queue LPUSH stage={} taskId={} shop={}", stage, message.getTaskId(), message.getShopName());
    }

    public void enqueueInit(String taskId, String shopName) {
        enqueue(new TranslateTaskV3QueueMessage(
                taskId, shopName, TranslateTaskV3QueueStage.INIT, System.currentTimeMillis()));
    }

    public void enqueueTranslate(String taskId, String shopName) {
        enqueue(new TranslateTaskV3QueueMessage(
                taskId, shopName, TranslateTaskV3QueueStage.TRANSLATE, System.currentTimeMillis()));
    }

    /**
     * 阻塞弹出一条消息。{@code timeoutSeconds} 为 0 时在 Redis 侧无限等待（由调用方循环控制退出）。
     */
    public TranslateTaskV3QueueMessage blockingPop(TranslateTaskV3QueueStage stage, long timeoutSeconds) {
        String key = TranslateTaskV3QueueKeys.forStage(stage);
        long timeout = timeoutSeconds <= 0 ? 30L : timeoutSeconds;
        String result = queueRedis.opsForList().rightPop(key, timeout, TimeUnit.SECONDS);
        if (result == null || result.isBlank()) {
            return null;
        }
        TranslateTaskV3QueueMessage message = JsonUtils.jsonToObject(result, TranslateTaskV3QueueMessage.class);
        if (message == null || message.getTaskId() == null || message.getTaskId().isBlank()) {
            LOG.warn("v3 queue BRPOP invalid payload stage={} raw={}", stage, truncate(result));
            return null;
        }
        if (message.getStage() == null) {
            message.setStage(stage);
        }
        LOG.info("v3 queue BRPOP stage={} taskId={} shop={}", stage, message.getTaskId(), message.getShopName());
        return message;
    }

    private static String truncate(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.length() <= 200 ? raw : raw.substring(0, 200) + "...";
    }
}
