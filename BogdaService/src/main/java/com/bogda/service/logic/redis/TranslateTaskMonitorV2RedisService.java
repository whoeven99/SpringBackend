package com.bogda.service.logic.redis;

import com.bogda.repository.RedisIntegration;
import com.bogda.common.utils.AESUtils;
import com.bogda.common.utils.RedisKeyUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class TranslateTaskMonitorV2RedisService {
    @Autowired
    private RedisIntegration redisIntegration;

    private static final String MONITOR_KEY_PREFIX = "translate_monitor_v2:";

    public Map<String, String> getAllByTaskId(Integer initialTaskId) {
        String key = MONITOR_KEY_PREFIX + initialTaskId;
        return redisIntegration.hGetAll(key);
    }

    public void createRecord(Integer initialTaskId, String shopName, String source, String target, String aiModel) {
        String key = MONITOR_KEY_PREFIX + initialTaskId;
        redisIntegration.setHash(key, "shopName", shopName);
        redisIntegration.setHash(key, "source", source);
        redisIntegration.setHash(key, "target", target);
        redisIntegration.setHash(key, "totalCount", 0);
        redisIntegration.setHash(key, "translatedCount", 0);
        redisIntegration.setHash(key, "savedCount", 0);
        redisIntegration.setHash(key, "usedToken", 0);
        redisIntegration.setHash(key, "translatedChars", 0);
        redisIntegration.setHash(key, "initStartTime", String.valueOf(System.currentTimeMillis()));
        redisIntegration.setHash(key, "aiModel", aiModel);
    }

    public void incrementTotalCount(Integer initialTaskId) {
        String key = MONITOR_KEY_PREFIX + initialTaskId;
        redisIntegration.incrementHash(key, "totalCount", 1L);
        redisIntegration.setHash(key, "lastUpdatedTime", String.valueOf(System.currentTimeMillis()));
    }

    public void addFinishedModule(Integer initialTaskId, String moduleName) {
        String key = MONITOR_KEY_PREFIX + initialTaskId;
        String existingModules = redisIntegration.getHash(key, "finishedModules");
        if (existingModules == null || existingModules.isEmpty()) {
            redisIntegration.setHash(key, "finishedModules", moduleName);
        } else {
            redisIntegration.setHash(key, "finishedModules", existingModules + "," + moduleName);
        }
    }

    public String getFinishedModules(Integer initialTaskId) {
        String key = MONITOR_KEY_PREFIX + initialTaskId;
        return redisIntegration.getHash(key, "finishedModules");
    }

    public void setAfterEndCursor(Integer initialTaskId, String afterEndCursor) {
        String key = MONITOR_KEY_PREFIX + initialTaskId;
        redisIntegration.setHash(key, "afterEndCursor", afterEndCursor);
    }

    public String getAfterEndCursor(Integer initialTaskId) {
        String key = MONITOR_KEY_PREFIX + initialTaskId;
        return redisIntegration.getHash(key, "afterEndCursor");
    }

    public void setInitEndTime(Integer initialTaskId) {
        String key = MONITOR_KEY_PREFIX + initialTaskId;
        redisIntegration.setHash(key, "initEndTime", String.valueOf(System.currentTimeMillis()));
    }

    /** init 增加预计字符，供 getProcess 返回前端 */
    public void incrementEstimatedCredits(Integer initialTaskId, long estimatedCredits) {
        String key = MONITOR_KEY_PREFIX + initialTaskId;
        redisIntegration.incrementHash(key, "estimatedCredits", estimatedCredits);
    }

    /** init 增加预计字符，供 getProcess 返回前端 */
    public void setEstimatedCredits(Integer initialTaskId, long estimatedCredits) {
        String key = MONITOR_KEY_PREFIX + initialTaskId;
        redisIntegration.setHash(key, "estimatedCredits", estimatedCredits);
    }

    /** init 完成后写入预估耗时（分钟），供 getProcess 返回前端 */
    public void setEstimatedMinutes(Integer initialTaskId, int estimatedMinutes) {
        String key = MONITOR_KEY_PREFIX + initialTaskId;
        redisIntegration.setHash(key, "estimatedMinutes", estimatedMinutes);
    }

    public void trackTranslateDetail(Integer initialTaskId, int count, Integer usedToken, Integer translatedChars) {
        String key = MONITOR_KEY_PREFIX + initialTaskId;
        redisIntegration.incrementHash(key, "translatedCount", count);
        redisIntegration.incrementHash(key, "usedToken", usedToken);
        redisIntegration.incrementHash(key, "translatedChars", translatedChars);
        redisIntegration.setHash(key, "lastUpdatedTime", String.valueOf(System.currentTimeMillis()));
    }

    public void setTranslateEndTime(Integer initialTaskId) {
        String key = MONITOR_KEY_PREFIX + initialTaskId;
        redisIntegration.setHash(key, "translateEndTime", String.valueOf(System.currentTimeMillis()));
    }

    public String getTranslateEndTime(Integer initialTaskId) {
        String key = MONITOR_KEY_PREFIX + initialTaskId;
        return redisIntegration.getHash(key, "translateEndTime");
    }

    public void addSavedCount(Integer initialTaskId, int count) {
        String key = MONITOR_KEY_PREFIX + initialTaskId;
        redisIntegration.incrementHash(key, "savedCount", count);
        redisIntegration.setHash(key, "lastUpdatedTime", String.valueOf(System.currentTimeMillis()));
    }

    public void setSavingShopifyEndTime(Integer initialTaskId) {
        String key = MONITOR_KEY_PREFIX + initialTaskId;
        redisIntegration.setHash(key, "savingShopifyEndTime", String.valueOf(System.currentTimeMillis()));
    }

    private final String MONITOR_CACHE_KEY = "translate_monitor_v2:cache:";

    public void addCacheCount(String content){
        String encryptData = AESUtils.encryptMD5(content);
        String key = MONITOR_CACHE_KEY + encryptData;
        redisIntegration.setHashValueIfAbsent(key, "text", content);
        redisIntegration.incrementHash(key, "count", 1L);
        redisIntegration.expire(key, RedisKeyUtils.DAY_14);
    }

}
