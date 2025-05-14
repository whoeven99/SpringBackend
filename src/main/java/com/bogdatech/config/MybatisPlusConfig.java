package com.bogdatech.config;

import com.alibaba.druid.pool.DruidDataSource;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.bogdatech.Application;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.io.IOException;
import java.util.Properties;

@Configuration
@MapperScan("com.bogdatech.mapper")  // 替换为你的Mapper接口所在的包路径
public class MybatisPlusConfig {
        @Bean
        public SqlSessionFactory sqlSessionFactory(DataSource dataSource) throws Exception {
            MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
            factory.setDataSource(dataSource);
            // You can also configure other properties of MyBatis or MyBatis-Plus here
            return factory.getObject();
        }
        // You can add more beans for configuring plugins, interceptors, etc.

    @Bean
    public DataSource dataSource() {
        DruidDataSource dataSource = new DruidDataSource();
        try {
            String env = System.getenv("ApplicationEnv");
            Properties properties = new Properties();
            properties.load(Application.class.getClassLoader().getResourceAsStream("application.properties"));
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
