package com.bogda.repository.config;

import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.CosmosContainer;
import com.bogda.common.utils.ConfigUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TranslateV3CosmosConfig {
    private static final Logger LOG = LoggerFactory.getLogger(TranslateV3CosmosConfig.class);

    @Value("${cosmos.translate-v3.database}")
    private String database;

    @Value("${cosmos.translate-v3.container:translate_tasks_v3}")
    private String container;

    @Value("${cosmos.endpoint:}")
    private String cosmosEndpoint;

    @Value("${cosmos.key:}")
    private String cosmosKey;

    @Bean
    public CosmosClient cosmosClient() {
        // Render / Spark：环境变量优先，避免 bootstrap 默认 endpoint 与 COSMOS_KEY 来自不同账户
        String endpoint = firstNonBlank(
                ConfigUtils.getConfig("COSMOS_ENDPOINT"),
                cosmosEndpoint);
        String key = firstNonBlank(
                ConfigUtils.getConfig("COSMOS_KEY"),
                cosmosKey,
                ConfigUtils.getConfig("cosmos.key"));
        if (endpoint == null || endpoint.isBlank()) {
            throw new IllegalStateException("Missing Cosmos endpoint: set COSMOS_ENDPOINT or cosmos.endpoint");
        }
        if (key == null || key.isBlank()) {
            throw new IllegalStateException("Missing Cosmos key: set COSMOS_KEY or cosmos.key");
        }
        LOG.info("Cosmos client config: endpoint={}", endpoint);
        return new CosmosClientBuilder()
                .endpoint(endpoint)
                .key(key)
                .buildClient();
    }

    private static String firstNonBlank(String... candidates) {
        if (candidates == null) {
            return null;
        }
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) {
                return candidate.trim();
            }
        }
        return null;
    }

    @Bean
    public CosmosContainer translateTaskV3Container(CosmosClient cosmosClient) {
        String databaseValue = database;
        if (databaseValue == null || databaseValue.isEmpty()) {
            databaseValue = ConfigUtils.getConfig("cosmos.translate-v3.database");
        }
        if (databaseValue == null || databaseValue.isEmpty()) {
            databaseValue = ConfigUtils.getConfig("COSMOS_TRANSLATION_DATABASE_ID");
        }
        if (databaseValue == null || databaseValue.isEmpty()) {
            databaseValue = ConfigUtils.getConfig("cosmos.database");
        }

        String containerValue = container;
        if (containerValue == null || containerValue.isEmpty()) {
            containerValue = ConfigUtils.getConfig("cosmos.translate-v3.container");
        }
        if (containerValue == null || containerValue.isEmpty()) {
            containerValue = ConfigUtils.getConfig("COSMOS_TRANSLATION_JOBS_CONTAINER");
        }
        if (containerValue == null || containerValue.isEmpty()) {
            containerValue = "translate_tasks_v3";
        }
        LOG.info("TranslateV3 Cosmos resolved config: endpoint={}, database={}, container={}",
                cosmosEndpoint, databaseValue, containerValue);
        return cosmosClient.getDatabase(databaseValue).getContainer(containerValue);
    }
}
