package com.bogda.repository.repo.translate;

import com.bogda.common.reporter.TraceReporterHolder;
import com.bogda.common.utils.JsonUtils;
import com.bogda.repository.container.TranslateTaskV3DO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.HashMap;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Translate V3 任务元数据仓储，使用 Blob {@code tasks/{shop}/{taskId}/task.json} 存储。
 */
@Service
public class TranslateTaskV3TaskRepo {
    private static final Logger LOG = LoggerFactory.getLogger(TranslateTaskV3TaskRepo.class);
    private static final String TASK_FILE = "task.json";

    @Autowired
    private TranslateTaskV3BlobRepo translateTaskV3BlobRepo;

    public boolean upsert(TranslateTaskV3DO task) {
        try {
            if (task.getCreatedAt() == null || task.getCreatedAt().isEmpty()) {
                task.setCreatedAt(Instant.now().toString());
            }
            if (task.getStatusText() == null || task.getStatusText().isEmpty()) {
                task.setStatusText(toStatusText(task.getStatus()));
            }
            task.setUpdatedAt(Instant.now().toString());
            return translateTaskV3BlobRepo.writeJson(taskBlobPath(task.getShopName(), task.getId()), task);
        } catch (Exception e) {
            TraceReporterHolder.report("TranslateTaskV3TaskRepo.upsert",
                    "FatalException upsert v3 task failed: " + e);
            return false;
        }
    }

    public TranslateTaskV3DO getById(String id, String shopName) {
        try {
            String raw = translateTaskV3BlobRepo.readText(taskBlobPath(shopName, id));
            if (raw == null || raw.isEmpty()) {
                return null;
            }
            return JsonUtils.jsonToObject(raw, TranslateTaskV3DO.class);
        } catch (Exception e) {
            TraceReporterHolder.report("TranslateTaskV3TaskRepo.getById",
                    "FatalException read v3 task failed: id=" + id + " shop=" + shopName + " error=" + e);
            return null;
        }
    }

    public List<TranslateTaskV3DO> listByShopName(String shopName) {
        if (shopName == null || shopName.isEmpty()) {
            return Collections.emptyList();
        }
        try {
            List<TranslateTaskV3DO> result = new ArrayList<>();
            for (String path : listTaskBlobPaths("tasks/" + shopName + "/")) {
                TranslateTaskV3DO task = readTaskAtPath(path);
                if (task != null) {
                    result.add(task);
                }
            }
            return result;
        } catch (Exception e) {
            TraceReporterHolder.report("TranslateTaskV3TaskRepo.listByShopName",
                    "FatalException query v3 task list failed: shop=" + shopName + " error=" + e);
            return Collections.emptyList();
        }
    }

    public List<TranslateTaskV3DO> listByShopSource(String shopName, String source) {
        String normalizedSource = normalizeLocaleCode(source);
        List<TranslateTaskV3DO> result = new ArrayList<>();
        for (TranslateTaskV3DO item : listByShopName(shopName)) {
            if (item != null && normalizedSource.equals(normalizeLocaleCode(item.getSource()))) {
                result.add(item);
            }
        }
        return result;
    }

    public List<TranslateTaskV3DO> listByStatus(Integer status) {
        try {
            List<TranslateTaskV3DO> result = new ArrayList<>();
            for (String path : listTaskBlobPaths("tasks/")) {
                TranslateTaskV3DO item = readTaskAtPath(path);
                if (item != null && status != null && status.equals(item.getStatus())) {
                    result.add(item);
                }
            }
            LOG.info("v3 blob listByStatus status={} fetched={} taskIds={}",
                    status, result.size(), result.stream().map(TranslateTaskV3DO::getId).toList());
            return result;
        } catch (Exception e) {
            TraceReporterHolder.report("TranslateTaskV3TaskRepo.listByStatus",
                    "FatalException query v3 task list by status failed: status=" + status + " error=" + e);
            return Collections.emptyList();
        }
    }

    public List<TranslateTaskV3DO> listByTaskId(String id) {
        if (id == null || id.isEmpty()) {
            return Collections.emptyList();
        }
        try {
            List<TranslateTaskV3DO> result = new ArrayList<>();
            for (String path : listTaskBlobPaths("tasks/")) {
                TranslateTaskV3DO item = readTaskAtPath(path);
                if (item != null && id.equals(item.getId())) {
                    result.add(item);
                }
            }
            LOG.info("v3 blob listByTaskId id={} fetched={}", id, result.size());
            return result;
        } catch (Exception e) {
            TraceReporterHolder.report("TranslateTaskV3TaskRepo.listByTaskId",
                    "FatalException query v3 task by id failed: id=" + id + " error=" + e);
            return Collections.emptyList();
        }
    }

    public boolean existsActiveTask(String shopName, String source, String target) {
        try {
            String normalizedSource = normalizeLocaleCode(source);
            String normalizedTarget = normalizeLocaleCode(target);
            for (TranslateTaskV3DO item : listByShopName(shopName)) {
                if (item == null || item.getStatus() == null) {
                    continue;
                }
                if (item.getStatus() < 0 || item.getStatus() > 3) {
                    continue;
                }
                if (!normalizedSource.equals(normalizeLocaleCode(item.getSource()))) {
                    continue;
                }
                if (!normalizedTarget.equals(normalizeLocaleCode(item.getTarget()))) {
                    continue;
                }
                return true;
            }
            return false;
        } catch (Exception e) {
            TraceReporterHolder.report("TranslateTaskV3TaskRepo.existsActiveTask",
                    "FatalException check active v3 task failed: shop=" + shopName + " source=" + source + " target=" + target + " error=" + e);
            return false;
        }
    }

    public boolean patchStatus(String id, String shopName, Integer status) {
        TranslateTaskV3DO task = getById(id, shopName);
        if (task == null) {
            return false;
        }
        task.setStatus(status);
        task.setStatusText(toStatusText(status));
        task.setUpdatedAt(Instant.now().toString());
        return upsert(task);
    }

    public boolean patchCheckpointAndMetrics(String id, String shopName, Object checkpoint, Object metrics) {
        TranslateTaskV3DO task = getById(id, shopName);
        if (task == null) {
            return false;
        }
        if (checkpoint instanceof java.util.Map<?, ?> checkpointMap) {
            task.setCheckpoint(new HashMap<>());
            for (java.util.Map.Entry<?, ?> entry : checkpointMap.entrySet()) {
                if (entry.getKey() != null) {
                    task.getCheckpoint().put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
        } else if (checkpoint != null) {
            task.setCheckpoint(JsonUtils.jsonToObject(JsonUtils.objectToJson(checkpoint),
                    new TypeReference<HashMap<String, Object>>() {}));
        }
        if (metrics instanceof java.util.Map<?, ?> metricsMap) {
            task.setMetrics(new HashMap<>());
            for (java.util.Map.Entry<?, ?> entry : metricsMap.entrySet()) {
                if (entry.getKey() != null) {
                    task.getMetrics().put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
        } else if (metrics != null) {
            task.setMetrics(JsonUtils.jsonToObject(JsonUtils.objectToJson(metrics),
                    new TypeReference<HashMap<String, Object>>() {}));
        }
        task.setUpdatedAt(Instant.now().toString());
        return upsert(task);
    }

    private List<String> listTaskBlobPaths(String prefix) {
        List<String> paths = new ArrayList<>();
        for (String path : translateTaskV3BlobRepo.listBlobPaths(prefix)) {
            if (path != null && path.endsWith("/" + TASK_FILE)) {
                paths.add(path);
            }
        }
        return paths;
    }

    private TranslateTaskV3DO readTaskAtPath(String path) {
        String raw = translateTaskV3BlobRepo.readText(path);
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        return JsonUtils.jsonToObject(raw, TranslateTaskV3DO.class);
    }

    private static String taskBlobPath(String shopName, String taskId) {
        return "tasks/" + shopName + "/" + taskId + "/" + TASK_FILE;
    }

    private String toStatusText(Integer status) {
        if (status == null) {
            return "UNKNOWN";
        }
        return switch (status) {
            case 0 -> "INIT_PENDING";
            case 1 -> "TRANSLATE_PENDING";
            case 2 -> "SAVE_PENDING";
            case 3 -> "STOPPED_TOKEN_LIMIT";
            case 4 -> "STOPPED";
            case 6 -> "VERIFY_PENDING";
            default -> "UNKNOWN";
        };
    }

    private static String normalizeLocaleCode(String locale) {
        if (locale == null) {
            return "";
        }
        String cleaned = locale.trim().replace('_', '-');
        if (cleaned.isEmpty()) {
            return "";
        }
        String[] parts = cleaned.split("-");
        if (parts.length == 0) {
            return cleaned.toLowerCase(Locale.ROOT);
        }
        StringBuilder normalized = new StringBuilder(parts[0].toLowerCase(Locale.ROOT));
        for (int i = 1; i < parts.length; i++) {
            if (parts[i] == null || parts[i].isEmpty()) {
                continue;
            }
            normalized.append("-");
            if (parts[i].length() <= 3) {
                normalized.append(parts[i].toUpperCase(Locale.ROOT));
            } else {
                normalized.append(parts[i].substring(0, 1).toUpperCase(Locale.ROOT));
                normalized.append(parts[i].substring(1).toLowerCase(Locale.ROOT));
            }
        }
        return normalized.toString();
    }
}
