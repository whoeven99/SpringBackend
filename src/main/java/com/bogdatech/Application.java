package com.bogdatech;

import com.alibaba.druid.pool.DruidDataSource;
import com.microsoft.applicationinsights.TelemetryClient;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;

import javax.sql.DataSource;
import java.io.IOException;
import java.util.Properties;

@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class})
@EnableAsync
public class Application {

    private TelemetryClient appInsights = new TelemetryClient();
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    @Bean
    public DataSource dataSource() {
        DruidDataSource dataSource = new DruidDataSource();
        try {
            String env = System.getenv("ApplicationEnv");
            Properties properties = new Properties();
            properties.load(Application.class.getClassLoader().getResourceAsStream("application.properties"));
            appInsights.trackTrace("env: " + env);
            if ("prod".equals(env)) {
                dataSource.setUrl(properties.getProperty("spring.datasource.master.url"));
                dataSource.setUsername(properties.getProperty("spring.datasource.master.username"));
                dataSource.setPassword(properties.getProperty("spring.datasource.master.password"));
                dataSource.setDriverClassName(properties.getProperty("spring.datasource.master.driver-class-name"));
//                System.out.println("prod: " + properties.getProperty("spring.datasource.master.username"));
            } else {
                dataSource.setUrl(properties.getProperty("spring.datasource.test.url"));
                dataSource.setUsername(properties.getProperty("spring.datasource.test.username"));
                dataSource.setPassword(properties.getProperty("spring.datasource.test.password"));
                dataSource.setDriverClassName(properties.getProperty("spring.datasource.test.driver-class-name"));
//                System.out.println("test: " + properties.getProperty("spring.datasource.test.username"));
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return dataSource;
    }


}
