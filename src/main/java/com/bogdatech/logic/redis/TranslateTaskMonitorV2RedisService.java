package com.bogdatech.logic.redis;

import com.bogdatech.integration.RedisIntegration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class TranslateTaskMonitorV2RedisService {
    @Autowired
    private RedisIntegration redisIntegration;

    private static final String MONITOR_KEY_PREFIX = "translate_monitor_v2:";

    /**
     * 创建监控记录
     */
    public void createRecord(Integer initialTaskId, String shopName, String source, String target) {
        String key = MONITOR_KEY_PREFIX + initialTaskId;
        redisIntegration.setHash(key, "shopName", shopName);
        redisIntegration.setHash(key, "source", source);
        redisIntegration.setHash(key, "target", target);
        redisIntegration.setHash(key, "totalCount", "0");
        redisIntegration.setHash(key, "translatedCount", "0");
        redisIntegration.setHash(key, "savedCount", "0");
        redisIntegration.setHash(key, "usedToken", "0");
        redisIntegration.setHash(key, "translatedChars", "0");
        redisIntegration.setHash(key, "initStartTime", String.valueOf(System.currentTimeMillis()));
    }

    /**
     * 增加总任务数
     */
    public void incrementTotalCount(Integer initialTaskId) {
        String key = MONITOR_KEY_PREFIX + initialTaskId;
        redisIntegration.incrementHash(key, "totalCount", 1);
    }

    /**
     * 设置初始化结束时间
     */
    public void setInitEndTime(Integer initialTaskId) {
        String key = MONITOR_KEY_PREFIX + initialTaskId;
        redisIntegration.setHash(key, "initEndTime", String.valueOf(System.currentTimeMillis()));
    }

    /**
     * 增加已翻译计数
     */
    public void trackTranslateDetail(Integer initialTaskId, int count, Integer usedToken, Integer translatedChars) {
        String key = MONITOR_KEY_PREFIX + initialTaskId;
        redisIntegration.incrementHash(key, "translatedCount", count);
        redisIntegration.incrementHash(key, "usedToken", usedToken);
        redisIntegration.incrementHash(key, "translatedChars", translatedChars);
    }

    /**
     * 设置翻译结束时间
     */
    public void setTranslateEndTime(Integer initialTaskId) {
        String key = MONITOR_KEY_PREFIX + initialTaskId;
        redisIntegration.setHash(key, "translateEndTime", String.valueOf(System.currentTimeMillis()));
    }

    /**
     * 增加已保存计数
     */
    public void addSavedCount(Integer initialTaskId, int count) {
        String key = MONITOR_KEY_PREFIX + initialTaskId;
        redisIntegration.incrementHash(key, "savedCount", count);
    }

    /**
     * 设置保存到Shopify结束时间
     */
    public void setSavingShopifyEndTime(Integer initialTaskId) {
        String key = MONITOR_KEY_PREFIX + initialTaskId;
        redisIntegration.setHash(key, "savingShopifyEndTime", String.valueOf(System.currentTimeMillis()));
    }
}
