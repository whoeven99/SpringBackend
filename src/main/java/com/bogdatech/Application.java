package com.bogdatech;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Properties;

@SpringBootApplication
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    // TODO Need to move this to Mybatis
    @Bean
    public Connection getSqlJdbcConnection() {
        try {
            Properties properties = new Properties();
            properties.load(Application.class.getClassLoader().getResourceAsStream("application.properties"));

            String env = System.getenv("ApplicationEnv");
            String connectionUrl = "prod".equals(env) ? properties.getProperty("prodSqlUrl") : properties.getProperty("devSqlUrl");
            //当 encrypt 属性设置为 true 且 trustServerCertificate 属性设置为 true 时，Microsoft JDBC Driver for SQL Server 将不验证SQL Server TLS 证书。 此设置常用于允许在测试环境中建立连接，如 SQL Server 实例只有自签名证书的情况。
            return DriverManager.getConnection(connectionUrl);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
