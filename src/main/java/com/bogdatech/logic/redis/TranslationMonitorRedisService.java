package com.bogdatech.logic.redis;

import com.bogdatech.integration.RedisIntegration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashSet;
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
        String shops = redisIntegration.get(TRANSLATION_MONITOR_SET_KEY);
        if (shops == null || shops.isEmpty()) {
            return new HashSet<>();
        }
        String[] shopArray = shops.split(",");
        return new HashSet<>(Arrays.asList(shopArray));
    }

    public void hsetCountOfTasks(Integer tasksCount) {
        redisIntegration.setHash(TRANSLATION_MONITOR_KEY, "task_count", tasksCount.toString());
    }

    public Integer getCountOfTasks() {
        String countStr = redisIntegration.getHash(TRANSLATION_MONITOR_KEY, "task_count");
        if (countStr == null || countStr.isEmpty()) {
            return 0;
        }
        return Integer.parseInt(countStr);
    }

    public void hsetUsedCharsOfShop(String shopName, Integer usedChars) {
        redisIntegration.setHash(TRANSLATION_MONITOR_KEY + '_' + shopName, "usedChars", usedChars.toString());
    }

    public void hsetRemainingCharsOfShop(String shopName, Integer remainingChars) {
        redisIntegration.setHash(TRANSLATION_MONITOR_KEY + '_' + shopName, "usedChars", remainingChars.toString());
    }

    public Map<Object, Object> getShopTranslationStats(String shopName) {
        return redisIntegration.getHashAll(TRANSLATION_MONITOR_KEY + '_' + shopName);
    }
}
