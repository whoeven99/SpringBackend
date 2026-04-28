package com.bogda.repository.config;

import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosContainer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TranslateV3CosmosConfig {
    @Value("${cosmos.translate-v3.database:${cosmos.database}}")
    private String database;

    @Value("${cosmos.translate-v3.container:translate_tasks_v3}")
    private String container;

    @Bean
    public CosmosContainer translateTaskV3Container(CosmosClient cosmosClient) {
        return cosmosClient.getDatabase(database).getContainer(container);
    }
}
