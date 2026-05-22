package com.bogda.repository.repo.translate;

import com.azure.cosmos.CosmosContainer;
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
import java.util.List;
import java.util.Locale;

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

    public boolean patchStatus(String id, String shopName, Integer status) {
        try {
            CosmosPatchOperations patch = CosmosPatchOperations.create()
                    .set("/status", status)
                    .set("/statusText", toStatusText(status))
                    .set("/updatedAt", Instant.now().toString());
            translateTaskV3Container.patchItem(id, new PartitionKey(shopName), patch, Object.class);
            LOG.info("v3 cosmos patchStatus ok id={} shop={} status={} statusText={}",
                    id, shopName, status, toStatusText(status));
            return true;
        } catch (Exception e) {
            LOG.warn("v3 cosmos patchStatus failed id={} shop={} status={} error={}",
                    id, shopName, status, e.toString());
            TraceReporterHolder.report("TranslateTaskV3CosmosRepo.patchStatus",
                    "FatalException patch status failed: id=" + id + " shop=" + shopName + " error=" + e);
            return false;
        }
    }

    public boolean patchCheckpointAndMetrics(String id, String shopName, Object checkpoint, Object metrics) {
        try {
            CosmosPatchOperations patch = CosmosPatchOperations.create()
                    .set("/checkpoint", checkpoint)
                    .set("/metrics", metrics)
                    .set("/updatedAt", Instant.now().toString());
            translateTaskV3Container.patchItem(id, new PartitionKey(shopName), patch, Object.class);
            return true;
        } catch (Exception e) {
            TraceReporterHolder.report("TranslateTaskV3CosmosRepo.patchCheckpointAndMetrics",
                    "FatalException patch checkpoint/metrics failed: id=" + id + " shop=" + shopName + " error=" + e);
            return false;
        }
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
