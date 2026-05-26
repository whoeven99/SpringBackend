package com.bogda.repository.repo.translate;

import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.models.BlobItem;
import com.azure.storage.blob.models.ListBlobsOptions;
import com.azure.storage.blob.specialized.BlockBlobClient;
import com.bogda.common.reporter.TraceReporterHolder;
import com.bogda.common.utils.JsonUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Service
@ConditionalOnProperty(name = "translate.v3.enabled", havingValue = "true")
public class TranslateTaskV3BlobRepo {
    @Autowired
    @Qualifier("translateV3BlobContainerClient")
    private BlobContainerClient translateV3BlobContainerClient;

    public boolean writeJson(String blobPath, Object data) {
        return writeText(blobPath, JsonUtils.objectToJson(data));
    }

    public boolean writeText(String blobPath, String content) {
        try {
            BlockBlobClient client = translateV3BlobContainerClient.getBlobClient(blobPath).getBlockBlobClient();
            byte[] bytes = (content == null ? "" : content).getBytes(StandardCharsets.UTF_8);
            client.upload(new ByteArrayInputStream(bytes), bytes.length, true);
            return true;
        } catch (Exception e) {
            TraceReporterHolder.report("TranslateTaskV3BlobRepo.writeText",
                    "FatalException write blob failed: path=" + blobPath + " error=" + e);
            return false;
        }
    }

    public String readText(String blobPath) {
        try {
            BlockBlobClient client = translateV3BlobContainerClient.getBlobClient(blobPath).getBlockBlobClient();
            if (!client.exists()) {
                return null;
            }
            return client.downloadContent().toString();
        } catch (Exception e) {
            TraceReporterHolder.report("TranslateTaskV3BlobRepo.readText",
                    "FatalException read blob failed: path=" + blobPath + " error=" + e);
            return null;
        }
    }

    public boolean blobExists(String blobPath) {
        if (blobPath == null || blobPath.isEmpty()) {
            return false;
        }
        try {
            return translateV3BlobContainerClient.getBlobClient(blobPath).getBlockBlobClient().exists();
        } catch (Exception e) {
            TraceReporterHolder.report("TranslateTaskV3BlobRepo.blobExists",
                    "FatalException blob exists check failed: path=" + blobPath + " error=" + e);
            return false;
        }
    }

    public Long getBlobSizeBytes(String blobPath) {
        if (blobPath == null || blobPath.isEmpty()) {
            return null;
        }
        try {
            BlockBlobClient client = translateV3BlobContainerClient.getBlobClient(blobPath).getBlockBlobClient();
            if (!client.exists()) {
                return null;
            }
            return client.getProperties().getBlobSize();
        } catch (Exception e) {
            TraceReporterHolder.report("TranslateTaskV3BlobRepo.getBlobSizeBytes",
                    "FatalException blob size failed: path=" + blobPath + " error=" + e);
            return null;
        }
    }

    /**
     * 仅读取前 maxBytes 字节为 UTF-8 文本，用于大文件预览，避免整对象下载进内存。
     */
    public String readTextPrefix(String blobPath, int maxBytes) {
        if (blobPath == null || blobPath.isEmpty() || maxBytes <= 0) {
            return null;
        }
        try {
            BlockBlobClient client = translateV3BlobContainerClient.getBlobClient(blobPath).getBlockBlobClient();
            if (!client.exists()) {
                return null;
            }
            byte[] buf = new byte[maxBytes];
            try (InputStream input = client.openInputStream()) {
                int total = 0;
                while (total < maxBytes) {
                    int n = input.read(buf, total, maxBytes - total);
                    if (n < 0) {
                        break;
                    }
                    total += n;
                }
                return new String(buf, 0, total, StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            TraceReporterHolder.report("TranslateTaskV3BlobRepo.readTextPrefix",
                    "FatalException read blob prefix failed: path=" + blobPath + " error=" + e);
            return null;
        }
    }

    public List<String> listBlobPaths(String prefix) {
        try {
            List<String> paths = new ArrayList<>();
            ListBlobsOptions options = new ListBlobsOptions().setPrefix(prefix);
            for (BlobItem blobItem : translateV3BlobContainerClient.listBlobs(options, null)) {
                if (blobItem == null) {
                    continue;
                }
                if (blobItem.getName() != null) {
                    paths.add(blobItem.getName());
                }
            }
            paths.sort(Comparator.naturalOrder());
            return paths;
        } catch (Exception e) {
            TraceReporterHolder.report("TranslateTaskV3BlobRepo.listBlobPaths",
                    "FatalException list blob paths failed: prefix=" + prefix + " error=" + e);
            return new ArrayList<>();
        }
    }

    public List<Map<String, Object>> readJsonRows(String blobPath) {
        String raw = readText(blobPath);
        if (raw == null || raw.isEmpty()) {
            return new ArrayList<>();
        }
        List<Map<String, Object>> rows = JsonUtils.jsonToObject(raw, new TypeReference<List<Map<String, Object>>>() {
        });
        return rows == null ? new ArrayList<>() : rows;
    }
}
