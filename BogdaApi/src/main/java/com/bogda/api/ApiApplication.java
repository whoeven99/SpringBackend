package com.bogda.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication(
    exclude = {
            DataSourceAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class,
            com.azure.spring.cloud.autoconfigure.implementation.cosmos.AzureCosmosAutoConfiguration.class,
            com.azure.spring.cloud.autoconfigure.implementation.data.cosmos.CosmosDataAutoConfiguration.class,
            com.azure.spring.cloud.autoconfigure.implementation.data.cosmos.CosmosRepositoriesAutoConfiguration.class,
    },
    scanBasePackages = {"com.bogda.api", "com.bogda.repository", "com.bogda.common",
            "com.bogda.integration", "com.bogda.service"}
)
@EnableAsync
public class ApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(ApiApplication.class, args);
    }
}

