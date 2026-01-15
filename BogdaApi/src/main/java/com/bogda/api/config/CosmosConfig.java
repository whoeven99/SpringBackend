package com.bogda.api.config;

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
                .endpoint("https://springbackend-cosmos-test.documents.azure.com:443/")
                .key(ConfigUtils.getConfig("COSMOS_KEY"))
                .buildClient();
    }

    @Bean
    public CosmosContainer discountContainer(CosmosClient client) {
        // TODO 根据环境配置选择不同的数据库，目前暂定测试环境
        return client.getDatabase("bogdatechtest").getContainer("discount");
    }
}
