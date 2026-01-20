package com.bogda.repository.config;

import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.CosmosContainer;
import com.bogda.common.utils.ConfigUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CosmosConfig {
    @Bean
    public CosmosClient cosmosClient() {
        return new CosmosClientBuilder()
                .endpoint(ConfigUtils.getConfig("COSMOS_ENDPOINT"))
                .key(ConfigUtils.getConfig("COSMOS_KEY"))
                .buildClient();
    }

    @Bean
    public CosmosContainer discountContainer(CosmosClient client) {
        // 根据环境配置选择不同的数据库
        String appEnv = System.getenv("ApplicationEnv");
        if ("prod".equals(appEnv)) {
            return client.getDatabase("bogdaprodcosmos").getContainer("discount");
        }
        return client.getDatabase("bogdatechtest").getContainer("discount");
    }
}
