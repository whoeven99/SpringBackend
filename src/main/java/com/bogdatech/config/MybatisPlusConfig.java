package com.bogdatech.config;

import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

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

}
