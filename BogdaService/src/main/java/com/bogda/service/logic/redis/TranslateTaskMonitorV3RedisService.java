package com.bogda.service.logic.redis;

import com.bogda.common.utils.JsonUtils;
import com.bogda.common.utils.RedisKeyUtils;
import com.bogda.repository.RedisIntegration;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class TranslateTaskMonitorV3RedisService {
    @Autowired
    private RedisIntegration redisIntegration;

    private static final String MONITOR_KEY_PREFIX = "translate_monitor_v3:";
    private static final String SESSION_KEY_PREFIX = "translate_session_v3:";

    public void createRecord(String taskId, String shopName, String source, String target, String aiModel) {
        String key = MONITOR_KEY_PREFIX + taskId;
        redisIntegration.setHash(key, "shopName", shopName);
        redisIntegration.setHash(key, "source", source);
        redisIntegration.setHash(key, "target", target);
        redisIntegration.setHash(key, "aiModel", aiModel);
        redisIntegration.setHash(key, "phase", "INIT_CREATED");
        redisIntegration.setHash(key, "totalCount", 0);
        redisIntegration.setHash(key, "translatedCount", 0);
        redisIntegration.setHash(key, "savedCount", 0);
        redisIntegration.setHash(key, "usedToken", 0);
        redisIntegration.setHash(key, "updatedAt", String.valueOf(System.currentTimeMillis()));
        redisIntegration.expire(key, RedisKeyUtils.DAY_14);
    }

    public void setPhase(String taskId, String phase) {
        String key = MONITOR_KEY_PREFIX + taskId;
        redisIntegration.setHash(key, "phase", phase);
        redisIntegration.setHash(key, "updatedAt", String.valueOf(System.currentTimeMillis()));
    }

    public void incrementBy(String taskId, int translatedDelta, int savedDelta, int tokenDelta) {
        String key = MONITOR_KEY_PREFIX + taskId;
        if (translatedDelta != 0) {
            redisIntegration.incrementHash(key, "translatedCount", translatedDelta);
        }
        if (savedDelta != 0) {
            redisIntegration.incrementHash(key, "savedCount", savedDelta);
        }
        if (tokenDelta != 0) {
            redisIntegration.incrementHash(key, "usedToken", tokenDelta);
        }
        redisIntegration.setHash(key, "updatedAt", String.valueOf(System.currentTimeMillis()));
    }

    public void setTotalCount(String taskId, long totalCount) {
        String key = MONITOR_KEY_PREFIX + taskId;
        redisIntegration.setHash(key, "totalCount", totalCount);
        redisIntegration.setHash(key, "updatedAt", String.valueOf(System.currentTimeMillis()));
    }

    public Map<String, String> getAll(String taskId) {
        return redisIntegration.hGetAll(MONITOR_KEY_PREFIX + taskId);
    }

    public void setSessionSnapshot(String taskId, Map<String, Object> snapshot) {
        String key = SESSION_KEY_PREFIX + taskId;
        redisIntegration.set(key, JsonUtils.objectToJson(snapshot), RedisKeyUtils.DAY_1 * 7);
    }

    public Map<String, Object> getSessionSnapshot(String taskId) {
        String raw = redisIntegration.get(SESSION_KEY_PREFIX + taskId);
        if (raw == null || "null".equals(raw) || raw.isEmpty()) {
            return new HashMap<>();
        }
        Map<String, Object> map = JsonUtils.jsonToObject(raw, new TypeReference<Map<String, Object>>() {
        });
        return map == null ? new HashMap<>() : map;
    }
}
