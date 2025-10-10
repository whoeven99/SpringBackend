package com.bogdatech.logic.redis;

import com.bogdatech.integration.RedisIntegration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

@Component
public class TranslationMonitorRedisService {
    @Autowired
    private RedisIntegration redisIntegration;

    // 存储有多少个shop在翻译中
    private static final String TRANSLATION_MONITOR_SET_KEY = "translation_monitor_set_key";

    // 存储有多少个任务在翻译中
    // hkey
    private static final String TRANSLATION_MONITOR_KEY = "translation_monitor_key";

    public void setTranslatingShop(String shopName) {
        redisIntegration.setSet(TRANSLATION_MONITOR_SET_KEY, shopName);
    }

    public Set<String> getTranslatingShops() {
        return redisIntegration.getSet(TRANSLATION_MONITOR_SET_KEY);
    }

    public void hsetCountOfTasks(Integer tasksCount) {
        redisIntegration.setHash(TRANSLATION_MONITOR_KEY, "task_count", tasksCount.toString());
    }

    public void hsetUsedCharsOfShop(String shopName, Integer usedChars) {
        redisIntegration.setHash(TRANSLATION_MONITOR_KEY + '_' + shopName, "usedChars", usedChars.toString());
    }

    public void hsetRemainingCharsOfShop(String shopName, Integer remainingChars) {
        redisIntegration.setHash(TRANSLATION_MONITOR_KEY + '_' + shopName, "remainingChars", remainingChars.toString());
    }

    public void hsetStartTranslationAt(String shopName, String time) {
        redisIntegration.delete(TRANSLATION_MONITOR_KEY + '_' + shopName);
        redisIntegration.setHash(TRANSLATION_MONITOR_KEY + '_' + shopName, "startTranslationAt", time);
    }

    public void hsetModelCharsWithTime(String shopName, String modelType, int chars, String minutes) {
        redisIntegration.setHash(TRANSLATION_MONITOR_KEY + '_' + shopName, modelType, "trans " + chars + " chars in " + minutes + " minutes");
    }

    public void hsetLastTaskFinishAt(String shopName, String time) {
        redisIntegration.setHash(TRANSLATION_MONITOR_KEY + '_' + shopName, "lastTaskFinishAt", time);
    }

    public Map<Object, Object> getShopTranslationStats(String shopName) {
        return redisIntegration.getHashAll(TRANSLATION_MONITOR_KEY + '_' + shopName);
    }
}
