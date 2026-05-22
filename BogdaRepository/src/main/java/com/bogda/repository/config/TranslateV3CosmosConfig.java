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

    @Value("${cosmos.translate-v3.database:${cosmos.database}}")
    private String database;

    @Value("${cosmos.translate-v3.container:translate_tasks_v3}")
    private String container;

    @Value("${cosmos.endpoint:}")
    private String cosmosEndpoint;

    @Bean
    public CosmosClient cosmosClient() {
        String endpoint = cosmosEndpoint;
        if (endpoint == null || endpoint.isEmpty()) {
            endpoint = ConfigUtils.getConfig("COSMOS_ENDPOINT");
        }
        String key = ConfigUtils.getConfig("COSMOS_KEY");
        if (key == null || key.isEmpty()) {
            key = ConfigUtils.getConfig("cosmos.key");
        }
        LOG.info("Cosmos client config: endpoint={}", endpoint);
        return new CosmosClientBuilder()
                .endpoint(endpoint)
                .key(key)
                .buildClient();
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
