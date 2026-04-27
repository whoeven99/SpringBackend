package com.bogda.repository.config;

import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.bogda.common.utils.ConfigUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TranslateV3BlobConfig {
    @Value("${blob.translate-v3.connection-string:}")
    private String connectionStringFromConfig;

    @Value("${blob.translate-v3.endpoint:}")
    private String endpoint;

    @Value("${blob.translate-v3.container:translate-v3}")
    private String container;

    @Bean
    public BlobServiceClient translateV3BlobServiceClient() {
        String connectionString = connectionStringFromConfig;
        if (connectionString == null || connectionString.isEmpty()) {
            connectionString = ConfigUtils.getConfig("blob.translate-v3.connection-string");
        }
        if (connectionString == null || connectionString.isEmpty()) {
            connectionString = ConfigUtils.getConfig("BLOB_TRANSLATE_V3_CONNECTION_STRING");
        }

        String endpointValue = endpoint;
        if (endpointValue == null || endpointValue.isEmpty()) {
            endpointValue = ConfigUtils.getConfig("blob.translate-v3.endpoint");
        }
        if (endpointValue == null || endpointValue.isEmpty()) {
            endpointValue = ConfigUtils.getConfig("BLOB_TRANSLATE_V3_ENDPOINT");
        }

        BlobServiceClientBuilder builder = new BlobServiceClientBuilder();
        if (connectionString != null && !connectionString.isEmpty()) {
            builder.connectionString(connectionString);
        } else {
            if (endpointValue == null || endpointValue.isEmpty()) {
                throw new IllegalStateException("Missing blob v3 config: neither connection string nor endpoint is configured.");
            }
            builder.endpoint(endpointValue);
        }
        return builder.buildClient();
    }

    @Bean
    public BlobContainerClient translateV3BlobContainerClient(BlobServiceClient blobServiceClient) {
        String containerValue = container;
        if (containerValue == null || containerValue.isEmpty()) {
            containerValue = ConfigUtils.getConfig("blob.translate-v3.container");
        }
        if (containerValue == null || containerValue.isEmpty()) {
            containerValue = ConfigUtils.getConfig("BLOB_TRANSLATE_V3_CONTAINER");
        }
        if (containerValue == null || containerValue.isEmpty()) {
            containerValue = "translate-v3";
        }

        BlobContainerClient blobContainerClient = blobServiceClient.getBlobContainerClient(containerValue);
        if (!blobContainerClient.exists()) {
            blobContainerClient.create();
        }
        return blobContainerClient;
    }
}
