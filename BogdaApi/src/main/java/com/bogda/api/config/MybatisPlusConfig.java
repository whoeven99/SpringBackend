package com.bogda.api.config;

import com.alibaba.druid.pool.DruidDataSource;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import javax.sql.DataSource;

@Configuration
@MapperScan({"com.bogda.api.mapper", "com.bogda.repository.mapper"})  // 替换为你的Mapper接口所在的包路径
public class MybatisPlusConfig {

    @Autowired
    private Environment env;

    @Bean
    public SqlSessionFactory sqlSessionFactory(DataSource dataSource) throws Exception {
        MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        return factory.getObject();
    }

    @Bean
    public DataSource dataSource() {
        DruidDataSource dataSource = new DruidDataSource();
        String appEnv = System.getenv("ApplicationEnv");
        if ("prod".equals(appEnv)) {
            dataSource.setUrl(env.getProperty("spring.datasource.master.url"));
            dataSource.setUsername(env.getProperty("spring.datasource.master.username"));
            dataSource.setPassword(env.getProperty("spring.datasource.master.password"));
            dataSource.setDriverClassName(env.getProperty("spring.datasource.master.driver-class-name"));
        } else {
            dataSource.setUrl(env.getProperty("spring.datasource.url"));
            dataSource.setUsername(env.getProperty("spring.datasource.username"));
            dataSource.setPassword(env.getProperty("spring.datasource.password"));
            dataSource.setDriverClassName(env.getProperty("spring.datasource.driver-class-name"));
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
