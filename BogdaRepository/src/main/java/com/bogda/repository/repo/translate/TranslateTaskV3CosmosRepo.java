package com.bogda.repository.repo.translate;

import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosException;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import com.azure.cosmos.models.CosmosPatchOperations;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.PartitionKey;
import com.azure.cosmos.models.SqlParameter;
import com.azure.cosmos.models.SqlQuerySpec;
import com.bogda.common.reporter.TraceReporterHolder;
import com.bogda.repository.container.TranslateTaskV3DO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class TranslateTaskV3CosmosRepo {
    private static final Logger LOG = LoggerFactory.getLogger(TranslateTaskV3CosmosRepo.class);
    @Autowired
    @Qualifier("translateTaskV3Container")
    private CosmosContainer translateTaskV3Container;

    public boolean upsert(TranslateTaskV3DO task) {
        try {
            if (task.getCreatedAt() == null || task.getCreatedAt().isEmpty()) {
                task.setCreatedAt(Instant.now().toString());
            }
            if (task.getStatusText() == null || task.getStatusText().isEmpty()) {
                task.setStatusText(toStatusText(task.getStatus()));
            }
            task.setUpdatedAt(Instant.now().toString());
            CosmosItemRequestOptions options = new CosmosItemRequestOptions()
                    .setContentResponseOnWriteEnabled(false);
            translateTaskV3Container.upsertItem(task, options);
            return true;
        } catch (Exception e) {
            TraceReporterHolder.report("TranslateTaskV3CosmosRepo.upsert",
                    "FatalException upsert v3 task failed: " + e);
            return false;
        }
    }

    public TranslateTaskV3DO getById(String id, String shopName) {
        try {
            return translateTaskV3Container.readItem(id, new PartitionKey(shopName), TranslateTaskV3DO.class).getItem();
        } catch (Exception e) {
            TraceReporterHolder.report("TranslateTaskV3CosmosRepo.getById",
                    "FatalException read v3 task failed: id=" + id + " shop=" + shopName + " error=" + e);
            return null;
        }
    }

    /**
     * 列出某店铺分区下的全部 v3 任务（不筛 source/target）。
     */
    public List<TranslateTaskV3DO> listByShopName(String shopName) {
        if (shopName == null || shopName.isEmpty()) {
            return Collections.emptyList();
        }
        try {
            String sql = "SELECT * FROM c WHERE c.shopName = @shopName";
            SqlQuerySpec spec = new SqlQuerySpec(sql, List.of(
                    new SqlParameter("@shopName", shopName)
            ));
            CosmosQueryRequestOptions options = new CosmosQueryRequestOptions()
                    .setPartitionKey(new PartitionKey(shopName))
                    .setMaxDegreeOfParallelism(1);

            List<TranslateTaskV3DO> result = new ArrayList<>();
            translateTaskV3Container.queryItems(spec, options, TranslateTaskV3DO.class).forEach(item -> {
                if (item != null) {
                    result.add(item);
                }
            });
            return result;
        } catch (Exception e) {
            TraceReporterHolder.report("TranslateTaskV3CosmosRepo.listByShopName",
                    "FatalException query v3 task list failed: shop=" + shopName + " error=" + e);
            return Collections.emptyList();
        }
    }

    public List<TranslateTaskV3DO> listByShopSource(String shopName, String source) {
        try {
            String sql = "SELECT * FROM c WHERE c.shopName = @shopName";
            SqlQuerySpec spec = new SqlQuerySpec(sql, List.of(
                    new SqlParameter("@shopName", shopName)
            ));
            CosmosQueryRequestOptions options = new CosmosQueryRequestOptions()
                    .setPartitionKey(new PartitionKey(shopName))
                    .setMaxDegreeOfParallelism(1);

            List<TranslateTaskV3DO> result = new ArrayList<>();
            String normalizedSource = normalizeLocaleCode(source);
            translateTaskV3Container.queryItems(spec, options, TranslateTaskV3DO.class).forEach(item -> {
                if (item == null) {
                    return;
                }
                if (normalizedSource.equals(normalizeLocaleCode(item.getSource()))) {
                    result.add(item);
                }
            });
            return result;
        } catch (Exception e) {
            TraceReporterHolder.report("TranslateTaskV3CosmosRepo.listByShopSource",
                    "FatalException query v3 task list failed: shop=" + shopName + " source=" + source + " error=" + e);
            return Collections.emptyList();
        }
    }

    public List<TranslateTaskV3DO> listByStatus(Integer status) {
        try {
            String sql = "SELECT * FROM c WHERE c.status = @status";
            SqlQuerySpec spec = new SqlQuerySpec(sql, List.of(new SqlParameter("@status", status)));
            CosmosQueryRequestOptions options = new CosmosQueryRequestOptions()
                    .setMaxDegreeOfParallelism(1);
            List<TranslateTaskV3DO> result = new ArrayList<>();
            translateTaskV3Container.queryItems(spec, options, TranslateTaskV3DO.class).forEach(result::add);
            LOG.info("v3 cosmos listByStatus status={} fetched={} taskIds={}",
                    status, result.size(), result.stream().map(TranslateTaskV3DO::getId).toList());
            return result;
        } catch (Exception e) {
            TraceReporterHolder.report("TranslateTaskV3CosmosRepo.listByStatus",
                    "FatalException query v3 task list by status failed: status=" + status + " error=" + e);
            return Collections.emptyList();
        }
    }

    /**
     * 按文档 {@code id} 跨分区查询，不限制 status。同一 id 在不同 shopName 分区下可共存，此时返回多条，由调用方处理。
     */
    public List<TranslateTaskV3DO> listByTaskId(String id) {
        if (id == null || id.isEmpty()) {
            return Collections.emptyList();
        }
        try {
            String sql = "SELECT * FROM c WHERE c.id = @id";
            SqlQuerySpec spec = new SqlQuerySpec(sql, List.of(new SqlParameter("@id", id)));
            CosmosQueryRequestOptions options = new CosmosQueryRequestOptions()
                    .setMaxDegreeOfParallelism(1);
            List<TranslateTaskV3DO> result = new ArrayList<>();
            translateTaskV3Container.queryItems(spec, options, TranslateTaskV3DO.class).forEach(result::add);
            LOG.info("v3 cosmos listByTaskId id={} fetched={}", id, result.size());
            return result;
        } catch (Exception e) {
            TraceReporterHolder.report("TranslateTaskV3CosmosRepo.listByTaskId",
                    "FatalException query v3 task by id failed: id=" + id + " error=" + e);
            return Collections.emptyList();
        }
    }

    public boolean existsActiveTask(String shopName, String source, String target) {
        try {
            String sql = "SELECT * FROM c WHERE c.shopName = @shopName AND c.status IN (0,1,2,3)";
            SqlQuerySpec spec = new SqlQuerySpec(sql, Arrays.asList(
                    new SqlParameter("@shopName", shopName)
            ));
            CosmosQueryRequestOptions options = new CosmosQueryRequestOptions()
                    .setPartitionKey(new PartitionKey(shopName))
                    .setMaxDegreeOfParallelism(1);
            String normalizedSource = normalizeLocaleCode(source);
            String normalizedTarget = normalizeLocaleCode(target);
            for (TranslateTaskV3DO item : translateTaskV3Container.queryItems(spec, options, TranslateTaskV3DO.class)) {
                if (item == null) {
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
            TraceReporterHolder.report("TranslateTaskV3CosmosRepo.existsActiveTask",
                    "FatalException check active v3 task failed: shop=" + shopName + " source=" + source + " target=" + target + " error=" + e);
            return false;
        }
    }

    /**
     * 解析 Cosmos 分区键（shopName）。跨分区 list 返回的 shop 提示值可能与实际分区不一致，导致 patch 404。
     */
    public String resolveShopPartitionKey(String id, String shopHint) {
        if (id == null || id.isEmpty()) {
            return normalizeShopHint(shopHint);
        }
        String hint = normalizeShopHint(shopHint);
        if (!hint.isEmpty()) {
            TranslateTaskV3DO direct = getById(id, hint);
            if (direct != null) {
                return firstNonEmpty(direct.getShopName(), hint);
            }
        }
        List<TranslateTaskV3DO> found = listByTaskId(id);
        if (!found.isEmpty()) {
            String resolved = firstNonEmpty(found.get(0).getShopName(), hint);
            if (!resolved.isEmpty() && !resolved.equals(hint)) {
                LOG.warn("v3 cosmos partition resolved id={} hint={} actual={}", id, hint, resolved);
            }
            return resolved;
        }
        return hint;
    }

    public TranslateTaskV3DO findByIdResolved(String id, String shopHint) {
        String pk = resolveShopPartitionKey(id, shopHint);
        if (!pk.isEmpty()) {
            TranslateTaskV3DO doc = getById(id, pk);
            if (doc != null) {
                return doc;
            }
        }
        List<TranslateTaskV3DO> found = listByTaskId(id);
        return found.isEmpty() ? null : found.get(0);
    }

    public boolean patchStatus(String id, String shopName, Integer status) {
        return patchStatusInternal(id, shopName, status, true);
    }

    public boolean patchCheckpointAndMetrics(String id, String shopName, Object checkpoint, Object metrics) {
        return patchCheckpointInternal(id, shopName, checkpoint, metrics, true);
    }

    /**
     * patch 失败时 upsert 全量文档（翻译结束写回 Cosmos 等场景）。
     */
    public boolean mergeAndPersistTaskState(String id,
                                            TranslateTaskV3DO seed,
                                            Integer status,
                                            Map<String, Object> checkpoint,
                                            Map<String, Object> metrics) {
        TranslateTaskV3DO task = seed;
        if (task == null) {
            task = findByIdResolved(id, "");
        }
        if (task == null) {
            List<TranslateTaskV3DO> found = listByTaskId(id);
            if (found.isEmpty()) {
                LOG.warn("v3 cosmos mergeAndPersistTaskState no document id={}", id);
                return false;
            }
            task = found.get(0);
        }
        if (id != null && !id.isEmpty()) {
            task.setId(id);
        }

        boolean checkpointOk = checkpoint == null || patchCheckpointInternal(task.getId(), task.getShopName(), checkpoint, metrics, true);
        boolean statusOk = status == null || patchStatusInternal(task.getId(), task.getShopName(), status, true);
        if (checkpointOk && statusOk) {
            return true;
        }
        LOG.warn("v3 cosmos mergeAndPersistTaskState patch failed, upsert fallback id={} shop={}",
                task.getId(), task.getShopName());
        return upsertTaskState(task, status, checkpoint, metrics);
    }

    private boolean patchStatusInternal(String id, String shopName, Integer status, boolean tryAllPartitions) {
        String itemId = normalizeItemId(id);
        if (itemId.isEmpty()) {
            return false;
        }
        CosmosPatchOperations patch = CosmosPatchOperations.create()
                .set("/status", status)
                .set("/statusText", toStatusText(status))
                .set("/updatedAt", Instant.now().toString());
        for (String partitionKey : partitionKeyCandidates(itemId, shopName, tryAllPartitions)) {
            try {
                translateTaskV3Container.patchItem(itemId, new PartitionKey(partitionKey), patch, Object.class);
                LOG.info("v3 cosmos patchStatus ok id={} shop={} status={} statusText={}",
                        itemId, partitionKey, status, toStatusText(status));
                return true;
            } catch (Exception e) {
                LOG.warn("v3 cosmos patchStatus failed id={} shopHint={} partitionKey={} status={} notFound={} error={}",
                        itemId, shopName, partitionKey, status, isNotFound(e), shortenError(e));
                if (!isNotFound(e)) {
                    TraceReporterHolder.report("TranslateTaskV3CosmosRepo.patchStatus",
                            "FatalException patch status failed: id=" + itemId + " shop=" + partitionKey + " error=" + e);
                    return false;
                }
            }
        }
        return false;
    }

    private boolean patchCheckpointInternal(String id,
                                            String shopName,
                                            Object checkpoint,
                                            Object metrics,
                                            boolean tryAllPartitions) {
        String itemId = normalizeItemId(id);
        if (itemId.isEmpty()) {
            return false;
        }
        CosmosPatchOperations patch = CosmosPatchOperations.create()
                .set("/checkpoint", checkpoint)
                .set("/metrics", metrics)
                .set("/updatedAt", Instant.now().toString());
        for (String partitionKey : partitionKeyCandidates(itemId, shopName, tryAllPartitions)) {
            try {
                translateTaskV3Container.patchItem(itemId, new PartitionKey(partitionKey), patch, Object.class);
                LOG.info("v3 cosmos patchCheckpointAndMetrics ok id={} shop={}", itemId, partitionKey);
                return true;
            } catch (Exception e) {
                LOG.warn("v3 cosmos patchCheckpointAndMetrics failed id={} shopHint={} partitionKey={} notFound={} error={}",
                        itemId, shopName, partitionKey, isNotFound(e), shortenError(e));
                if (!isNotFound(e)) {
                    TraceReporterHolder.report("TranslateTaskV3CosmosRepo.patchCheckpointAndMetrics",
                            "FatalException patch checkpoint/metrics failed: id=" + itemId + " shop=" + partitionKey + " error=" + e);
                    return false;
                }
            }
        }
        return false;
    }

    private Set<String> partitionKeyCandidates(String id, String shopHint, boolean tryAllPartitions) {
        Set<String> keys = new LinkedHashSet<>();
        String hint = normalizeShopHint(shopHint);
        if (!hint.isEmpty()) {
            keys.add(hint);
        }
        if (!tryAllPartitions || id == null || id.isEmpty()) {
            return keys;
        }
        for (TranslateTaskV3DO doc : listByTaskId(id)) {
            if (doc == null) {
                continue;
            }
            String pk = firstNonEmpty(doc.getShopName(), "");
            if (!pk.isEmpty()) {
                keys.add(pk);
            }
            String docId = normalizeItemId(doc.getId());
            if (!docId.isEmpty() && !docId.equals(id)) {
                LOG.warn("v3 cosmos partition candidate id mismatch queryId={} docId={} shop={}", id, docId, pk);
            }
        }
        for (String pk : keys) {
            if (getById(id, pk) != null) {
                return Set.of(pk);
            }
        }
        return keys;
    }

    private static String normalizeItemId(String id) {
        return id == null ? "" : id.trim();
    }

    /**
     * patch 404 或文档不存在时，用完整文档 upsert（Spark 写入的 cover/handle 字段与 Java DO 已对齐）。
     */
    public boolean upsertTaskState(TranslateTaskV3DO task, Integer status, Map<String, Object> checkpoint, Map<String, Object> metrics) {
        if (task == null || task.getId() == null || task.getId().isEmpty()) {
            return false;
        }
        String pk = resolveShopPartitionKey(task.getId(), task.getShopName());
        if (!pk.isEmpty()) {
            task.setShopName(pk);
        }
        if (task.getShopName() == null || task.getShopName().isBlank()) {
            LOG.warn("v3 cosmos upsertTaskState aborted missing shopName id={}", task.getId());
            return false;
        }
        if (task.getSessionId() == null || task.getSessionId().isBlank()) {
            task.setSessionId(task.getShopName() + ":" + task.getId());
        }
        if (task.getTaskType() == null || task.getTaskType().isBlank()) {
            task.setTaskType("spark");
        }
        if (status != null) {
            task.setStatus(status);
            if (task.getStatusText() == null || task.getStatusText().isBlank()) {
                task.setStatusText(toStatusText(status));
            }
        }
        if (checkpoint != null) {
            task.setCheckpoint(checkpoint);
        }
        if (metrics != null) {
            task.setMetrics(metrics);
        }
        task.setUpdatedAt(Instant.now().toString());
        boolean ok = upsert(task);
        if (ok) {
            LOG.info("v3 cosmos upsertTaskState ok id={} shop={} status={}", task.getId(), task.getShopName(), task.getStatus());
        } else {
            LOG.warn("v3 cosmos upsertTaskState failed id={} shop={}", task.getId(), task.getShopName());
        }
        return ok;
    }

    private static String normalizeShopHint(String shopHint) {
        return shopHint == null ? "" : shopHint.trim();
    }

    private static String firstNonEmpty(String primary, String fallback) {
        if (primary != null && !primary.trim().isEmpty()) {
            return primary.trim();
        }
        return normalizeShopHint(fallback);
    }

    private static boolean isNotFound(Exception e) {
        if (e instanceof CosmosException cosmosException) {
            return cosmosException.getStatusCode() == 404;
        }
        String text = e.toString();
        return text.contains("NotFoundException") || text.contains("statusCode=404");
    }

    private static String shortenError(Exception e) {
        String text = e.getMessage();
        if (text == null || text.isEmpty()) {
            text = e.getClass().getSimpleName();
        }
        return text.length() > 500 ? text.substring(0, 500) + "..." : text;
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
