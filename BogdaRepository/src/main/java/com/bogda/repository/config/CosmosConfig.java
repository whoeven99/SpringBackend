package com.bogda.repository.config;

import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.CosmosContainer;
import com.bogda.common.utils.ConfigUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CosmosConfig {
    @Value("${cosmos.endpoint}")
    private String cosmosEndpoint;

    @Value("${cosmos.database}")
    private String database;

    @Value("${cosmos.container}")
    private String container;

    @Bean
    public CosmosClient cosmosClient() {
        return new CosmosClientBuilder()
                .endpoint(cosmosEndpoint)
                .key(ConfigUtils.getConfig("COSMOS_KEY"))
                .buildClient();
    }

    @Bean
    public CosmosContainer discountContainer(CosmosClient client) {
        // 根据环境配置选择不同的数据库
        return client.getDatabase(database).getContainer(container);
    }
}
