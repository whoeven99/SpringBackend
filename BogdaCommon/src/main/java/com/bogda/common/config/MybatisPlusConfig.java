package com.bogda.common.config;

import com.alibaba.druid.pool.DruidDataSource;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.bogda.common.ApiApplication;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import javax.sql.DataSource;
import java.util.Properties;

@Configuration
@MapperScan({"com.bogda.common.mapper", "com.bogda.common.repository.mapper"})  // 替换为你的Mapper接口所在的包路径
public class MybatisPlusConfig {
    @Bean
    public SqlSessionFactory sqlSessionFactory(DataSource dataSource) throws Exception {
        MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        return factory.getObject();
    }

    @Bean
    public DataSource dataSource() throws Exception {
        DruidDataSource dataSource = new DruidDataSource();
        String env = System.getenv("ApplicationEnv");
        Properties properties = new Properties();
        properties.load(ApiApplication.class.getClassLoader().getResourceAsStream("application.properties"));
        if ("prod".equals(env)) {
            dataSource.setUrl(properties.getProperty("spring.datasource.master.url"));
            dataSource.setUsername(properties.getProperty("spring.datasource.master.username"));
            dataSource.setPassword(properties.getProperty("spring.datasource.master.password"));
            dataSource.setDriverClassName(properties.getProperty("spring.datasource.master.driver-class-name"));
        } else {
            dataSource.setUrl(properties.getProperty("spring.datasource.url"));
            dataSource.setUsername(properties.getProperty("spring.datasource.username"));
            dataSource.setPassword(properties.getProperty("spring.datasource.password"));
            dataSource.setDriverClassName(properties.getProperty("spring.datasource.driver-class-name"));
        }
        return dataSource;
    }

    /**
     * 配置事务管理器
     */
    @Bean
    public DataSourceTransactionManager transactionManager(DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }
}
