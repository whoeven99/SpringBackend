package com.bogda.service.logic.redis;

import com.bogda.common.utils.JsonUtils;
import com.bogda.common.utils.RedisKeyUtils;
import com.bogda.repository.RedisIntegration;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class TranslateTaskMonitorV3RedisService {
    @Autowired
    private RedisIntegration redisIntegration;

    private static final String MONITOR_KEY_PREFIX = "translate_monitor_v3:";
    private static final String SESSION_KEY_PREFIX = "translate_session_v3:";
    private static final String SHOP_TASK_SET_KEY_PREFIX = "translate_monitor_v3_shop_tasks:";

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
        String shopTaskSetKey = SHOP_TASK_SET_KEY_PREFIX + shopName;
        redisIntegration.setSet(shopTaskSetKey, taskId);
        redisIntegration.expire(shopTaskSetKey, RedisKeyUtils.DAY_14);
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

    public void setInitOverview(String taskId, int moduleTotal) {
        String key = MONITOR_KEY_PREFIX + taskId;
        redisIntegration.setHash(key, "initModuleTotal", moduleTotal);
        redisIntegration.setHash(key, "initModuleDone", 0);
        redisIntegration.setHash(key, "initCurrentModule", "");
        redisIntegration.setHash(key, "initCurrentModuleIndex", 0);
        redisIntegration.setHash(key, "initAccumulatedCount", 0);
        redisIntegration.setHash(key, "initAccumulatedChars", 0);
        redisIntegration.setHash(key, "initAccumulatedChunks", 0);
        redisIntegration.setHash(key, "updatedAt", String.valueOf(System.currentTimeMillis()));
    }

    public void setInitModuleProgress(String taskId,
                                      String module,
                                      int moduleIndex,
                                      int moduleDone,
                                      int moduleCount,
                                      int moduleChars,
                                      int moduleChunks,
                                      int accumulatedCount,
                                      int accumulatedChars,
                                      int accumulatedChunks) {
        String key = MONITOR_KEY_PREFIX + taskId;
        redisIntegration.setHash(key, "initCurrentModule", module);
        redisIntegration.setHash(key, "initCurrentModuleIndex", moduleIndex);
        redisIntegration.setHash(key, "initModuleDone", moduleDone);
        redisIntegration.setHash(key, "initCurrentModuleCount", moduleCount);
        redisIntegration.setHash(key, "initCurrentModuleChars", moduleChars);
        redisIntegration.setHash(key, "initCurrentModuleChunks", moduleChunks);
        redisIntegration.setHash(key, "initAccumulatedCount", accumulatedCount);
        redisIntegration.setHash(key, "initAccumulatedChars", accumulatedChars);
        redisIntegration.setHash(key, "initAccumulatedChunks", accumulatedChunks);
        redisIntegration.setHash(key, "updatedAt", String.valueOf(System.currentTimeMillis()));
    }

    public void setInitManifest(String taskId, Map<String, Object> moduleSummary) {
        String key = MONITOR_KEY_PREFIX + taskId;
        Map<String, Object> safeSummary = moduleSummary == null ? new LinkedHashMap<>() : moduleSummary;
        redisIntegration.setHash(key, "initManifest", JsonUtils.objectToJson(safeSummary));
        redisIntegration.setHash(key, "updatedAt", String.valueOf(System.currentTimeMillis()));
    }

    public void setEstimatedCreditsRaw(String taskId, int estimatedCreditsRaw) {
        String key = MONITOR_KEY_PREFIX + taskId;
        redisIntegration.setHash(key, "estimatedCreditsRaw", estimatedCreditsRaw);
        redisIntegration.setHash(key, "updatedAt", String.valueOf(System.currentTimeMillis()));
    }

    public void setTranslateOverview(String taskId, int moduleTotal, int chunkTotal, int maxToken, int usedTokenStart) {
        String key = MONITOR_KEY_PREFIX + taskId;
        redisIntegration.setHash(key, "translateModuleTotal", moduleTotal);
        redisIntegration.setHash(key, "translateModuleDone", 0);
        redisIntegration.setHash(key, "translateChunkTotal", chunkTotal);
        redisIntegration.setHash(key, "translateChunkDone", 0);
        redisIntegration.setHash(key, "translateCurrentModule", "");
        redisIntegration.setHash(key, "translateCurrentChunkPath", "");
        redisIntegration.setHash(key, "translateMaxToken", maxToken);
        redisIntegration.setHash(key, "translateUsedTokenStart", usedTokenStart);
        redisIntegration.setHash(key, "updatedAt", String.valueOf(System.currentTimeMillis()));
    }

    public void setTranslateProgress(String taskId,
                                     String module,
                                     int moduleDone,
                                     String chunkPath,
                                     int chunkDone,
                                     int translatedTotal,
                                     int usedTokenTotal,
                                     int lastTranslatedDelta,
                                     int lastTokenDelta) {
        String key = MONITOR_KEY_PREFIX + taskId;
        redisIntegration.setHash(key, "translateCurrentModule", module);
        redisIntegration.setHash(key, "translateModuleDone", moduleDone);
        redisIntegration.setHash(key, "translateCurrentChunkPath", chunkPath);
        redisIntegration.setHash(key, "translateChunkDone", chunkDone);
        redisIntegration.setHash(key, "translateTranslatedTotal", translatedTotal);
        redisIntegration.setHash(key, "translateUsedTokenTotal", usedTokenTotal);
        redisIntegration.setHash(key, "translateLastTranslatedDelta", lastTranslatedDelta);
        redisIntegration.setHash(key, "translateLastTokenDelta", lastTokenDelta);
        redisIntegration.setHash(key, "updatedAt", String.valueOf(System.currentTimeMillis()));
    }

    public void setSaveOverview(String taskId, int moduleTotal, int chunkTotal, int savedStart) {
        String key = MONITOR_KEY_PREFIX + taskId;
        redisIntegration.setHash(key, "saveModuleTotal", moduleTotal);
        redisIntegration.setHash(key, "saveModuleDone", 0);
        redisIntegration.setHash(key, "saveChunkTotal", chunkTotal);
        redisIntegration.setHash(key, "saveChunkDone", 0);
        redisIntegration.setHash(key, "saveCurrentModule", "");
        redisIntegration.setHash(key, "saveCurrentChunkPath", "");
        redisIntegration.setHash(key, "saveCurrentResourceId", "");
        redisIntegration.setHash(key, "saveSavedTotal", savedStart);
        redisIntegration.setHash(key, "updatedAt", String.valueOf(System.currentTimeMillis()));
    }

    public void setSaveProgress(String taskId,
                                String module,
                                int moduleDone,
                                String chunkPath,
                                int chunkDone,
                                String resourceId,
                                int savedTotal,
                                int lastSavedDelta) {
        String key = MONITOR_KEY_PREFIX + taskId;
        redisIntegration.setHash(key, "saveCurrentModule", module);
        redisIntegration.setHash(key, "saveModuleDone", moduleDone);
        redisIntegration.setHash(key, "saveCurrentChunkPath", chunkPath);
        redisIntegration.setHash(key, "saveChunkDone", chunkDone);
        redisIntegration.setHash(key, "saveCurrentResourceId", resourceId == null ? "" : resourceId);
        redisIntegration.setHash(key, "saveSavedTotal", savedTotal);
        redisIntegration.setHash(key, "saveLastSavedDelta", lastSavedDelta);
        redisIntegration.setHash(key, "updatedAt", String.valueOf(System.currentTimeMillis()));
    }

    public void setSaveFailure(String taskId, String module, String chunkPath, String resourceId, String reason) {
        String key = MONITOR_KEY_PREFIX + taskId;
        redisIntegration.setHash(key, "saveFailedModule", module == null ? "" : module);
        redisIntegration.setHash(key, "saveFailedChunkPath", chunkPath == null ? "" : chunkPath);
        redisIntegration.setHash(key, "saveFailedResourceId", resourceId == null ? "" : resourceId);
        redisIntegration.setHash(key, "saveFailedReason", reason == null ? "" : reason);
        redisIntegration.setHash(key, "updatedAt", String.valueOf(System.currentTimeMillis()));
    }

    public Map<String, String> getRuntimeMeta(String redisPrefix, String taskId) {
        return redisIntegration.hGetAll(runtimeMetaKey(redisPrefix, taskId));
    }

    public Set<String> getRuntimeDoneSet(String redisPrefix, String taskId) {
        return redisIntegration.getSet(runtimeDoneKey(redisPrefix, taskId));
    }

    public Map<String, String> getRuntimeFailMap(String redisPrefix, String taskId) {
        return redisIntegration.hGetAll(runtimeFailKey(redisPrefix, taskId));
    }

    public Map<String, String> getRuntimeResultMap(String redisPrefix, String taskId) {
        return redisIntegration.hGetAll(runtimeResultKey(redisPrefix, taskId));
    }

    public void setRuntimeMeta(String redisPrefix, String taskId, Map<String, Object> fields) {
        if (fields == null || fields.isEmpty()) {
            return;
        }
        String key = runtimeMetaKey(redisPrefix, taskId);
        for (Map.Entry<String, Object> entry : fields.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            redisIntegration.setHash(key, entry.getKey(), entry.getValue());
        }
    }

    public void setRuntimeFailMap(String redisPrefix, String taskId, Map<String, String> failMap) {
        if (failMap == null || failMap.isEmpty()) {
            return;
        }
        String key = runtimeFailKey(redisPrefix, taskId);
        for (Map.Entry<String, String> entry : failMap.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            redisIntegration.setHash(key, entry.getKey(), entry.getValue() == null ? "UNKNOWN_ERROR" : entry.getValue());
        }
    }

    public void setRuntimeResultMap(String redisPrefix, String taskId, Map<String, String> resultMap) {
        if (resultMap == null || resultMap.isEmpty()) {
            return;
        }
        String key = runtimeResultKey(redisPrefix, taskId);
        for (Map.Entry<String, String> entry : resultMap.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            redisIntegration.setHash(key, entry.getKey(), entry.getValue() == null ? "" : entry.getValue());
        }
    }

    public void addRuntimeDonePaths(String redisPrefix, String taskId, List<String> donePaths) {
        if (donePaths == null || donePaths.isEmpty()) {
            return;
        }
        String key = runtimeDoneKey(redisPrefix, taskId);
        for (String path : donePaths) {
            if (path == null || path.isEmpty()) {
                continue;
            }
            redisIntegration.setSet(key, path);
        }
    }

    public void incrementRuntimeDoneCount(String redisPrefix, String taskId, int delta) {
        if (delta <= 0) {
            return;
        }
        redisIntegration.incrementHash(runtimeMetaKey(redisPrefix, taskId), "doneCount", delta);
    }

    public void incrementRuntimeFailCount(String redisPrefix, String taskId, int delta) {
        if (delta <= 0) {
            return;
        }
        redisIntegration.incrementHash(runtimeMetaKey(redisPrefix, taskId), "failCount", delta);
    }

    public void expireRuntimeKeys(String redisPrefix, String taskId, long ttlSeconds) {
        if (ttlSeconds <= 0) {
            return;
        }
        redisIntegration.expire(runtimeMetaKey(redisPrefix, taskId), ttlSeconds);
        redisIntegration.expire(runtimeDoneKey(redisPrefix, taskId), ttlSeconds);
        redisIntegration.expire(runtimeFailKey(redisPrefix, taskId), ttlSeconds);
        redisIntegration.expire(runtimeResultKey(redisPrefix, taskId), ttlSeconds);
        redisIntegration.expire(runtimeChunkDoneKey(redisPrefix, taskId), ttlSeconds);
    }

    /** 删除旧版存译文的 result hash；分 chunk 管线启动时可与 path-done 一并清理 */
    public void deleteRuntimeTranslationPayloadKeys(String redisPrefix, String taskId) {
        if (safePrefix(redisPrefix).isEmpty() || safeTaskId(taskId).isEmpty()) {
            return;
        }
        redisIntegration.delete(runtimeResultKey(redisPrefix, taskId));
    }

    public void deleteRuntimePathDoneKey(String redisPrefix, String taskId) {
        if (safePrefix(redisPrefix).isEmpty() || safeTaskId(taskId).isEmpty()) {
            return;
        }
        redisIntegration.delete(runtimeDoneKey(redisPrefix, taskId));
    }

    public void addRuntimeChunkDonePath(String redisPrefix, String taskId, String chunkKey) {
        if (safePrefix(redisPrefix).isEmpty() || safeTaskId(taskId).isEmpty() || chunkKey == null || chunkKey.isEmpty()) {
            return;
        }
        redisIntegration.setSet(runtimeChunkDoneKey(redisPrefix, taskId), chunkKey);
    }

    public Set<String> getRuntimeChunkDoneSet(String redisPrefix, String taskId) {
        Set<String> set = redisIntegration.getSet(runtimeChunkDoneKey(redisPrefix, taskId));
        return set == null ? Collections.emptySet() : set;
    }

    private static String safePrefix(String redisPrefix) {
        return redisPrefix == null ? "" : redisPrefix.trim();
    }

    private static String safeTaskId(String taskId) {
        return taskId == null ? "" : taskId.trim();
    }

    public Map<String, String> getAll(String taskId) {
        return redisIntegration.hGetAll(MONITOR_KEY_PREFIX + taskId);
    }

    public List<Map<String, String>> listByShopName(String shopName) {
        if (shopName == null || shopName.isEmpty()) {
            return new ArrayList<>();
        }
        String shopTaskSetKey = SHOP_TASK_SET_KEY_PREFIX + shopName;
        Set<String> taskIds = redisIntegration.getSet(shopTaskSetKey);
        if (taskIds == null || taskIds.isEmpty()) {
            return new ArrayList<>();
        }
        List<Map<String, String>> result = new ArrayList<>();
        for (String taskId : taskIds) {
            if (taskId == null || taskId.isEmpty()) {
                continue;
            }
            Map<String, String> monitor = getAll(taskId);
            if (monitor == null || monitor.isEmpty()) {
                continue;
            }
            monitor.put("taskId", taskId);
            result.add(monitor);
        }
        result.sort(Comparator.comparingLong(this::extractUpdatedAt).reversed());
        return result;
    }

    private long extractUpdatedAt(Map<String, String> monitor) {
        if (monitor == null || monitor.isEmpty()) {
            return 0L;
        }
        String updatedAt = monitor.get("updatedAt");
        if (updatedAt == null || updatedAt.isEmpty()) {
            return 0L;
        }
        try {
            return Long.parseLong(updatedAt);
        } catch (NumberFormatException ignored) {
            return 0L;
        }
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

    private String runtimeMetaKey(String redisPrefix, String taskId) {
        return redisPrefix + ":task:" + taskId + ":meta";
    }

    private String runtimeDoneKey(String redisPrefix, String taskId) {
        return redisPrefix + ":task:" + taskId + ":done";
    }

    private String runtimeFailKey(String redisPrefix, String taskId) {
        return redisPrefix + ":task:" + taskId + ":fail";
    }

    private String runtimeResultKey(String redisPrefix, String taskId) {
        return redisPrefix + ":task:" + taskId + ":result";
    }

    /** 已完成 chunk 的输入路径键（与 inputBlob 相对路径一致），不存译文 */
    private String runtimeChunkDoneKey(String redisPrefix, String taskId) {
        return redisPrefix + ":task:" + taskId + ":chunkDone";
    }
}
