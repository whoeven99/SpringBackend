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
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Service
public class TranslateTaskV3CosmosRepo {
    private static final Logger LOG = LoggerFactory.getLogger(TranslateTaskV3CosmosRepo.class);
    @Autowired
    private CosmosContainer translateTaskV3Container;

    public boolean upsert(TranslateTaskV3DO task) {
        try {
            if (task.getCreatedAt() == null || task.getCreatedAt().isEmpty()) {
                task.setCreatedAt(Instant.now().toString());
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

    public List<TranslateTaskV3DO> listByShopSource(String shopName, String source) {
        try {
            String sql = "SELECT * FROM c WHERE c.shopName = @shopName AND c.source = @source";
            SqlQuerySpec spec = new SqlQuerySpec(sql, Arrays.asList(
                    new SqlParameter("@shopName", shopName),
                    new SqlParameter("@source", source)
            ));
            CosmosQueryRequestOptions options = new CosmosQueryRequestOptions()
                    .setPartitionKey(new PartitionKey(shopName))
                    .setMaxDegreeOfParallelism(1);

            List<TranslateTaskV3DO> result = new ArrayList<>();
            translateTaskV3Container.queryItems(spec, options, TranslateTaskV3DO.class).forEach(result::add);
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

    public boolean existsActiveTask(String shopName, String source, String target) {
        try {
            String sql = "SELECT VALUE COUNT(1) FROM c WHERE c.shopName = @shopName AND c.source = @source " +
                    "AND c.target = @target AND c.status IN (0,1,2,3)";
            SqlQuerySpec spec = new SqlQuerySpec(sql, Arrays.asList(
                    new SqlParameter("@shopName", shopName),
                    new SqlParameter("@source", source),
                    new SqlParameter("@target", target)
            ));
            CosmosQueryRequestOptions options = new CosmosQueryRequestOptions()
                    .setPartitionKey(new PartitionKey(shopName))
                    .setMaxDegreeOfParallelism(1);
            List<Integer> count = new ArrayList<>();
            translateTaskV3Container.queryItems(spec, options, Integer.class).forEach(count::add);
            return !count.isEmpty() && count.get(0) != null && count.get(0) > 0;
        } catch (Exception e) {
            TraceReporterHolder.report("TranslateTaskV3CosmosRepo.existsActiveTask",
                    "FatalException check active v3 task failed: shop=" + shopName + " source=" + source + " target=" + target + " error=" + e);
            return false;
        }
    }

    public boolean patchStatus(String id, String shopName, Integer status) {
        try {
            CosmosPatchOperations patch = CosmosPatchOperations.create()
                    .replace("/status", status)
                    .replace("/updatedAt", Instant.now().toString());
            translateTaskV3Container.patchItem(id, new PartitionKey(shopName), patch, Object.class);
            return true;
        } catch (Exception e) {
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
}
