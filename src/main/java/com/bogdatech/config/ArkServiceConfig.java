package com.bogdatech.config;

import com.bogdatech.integration.ArkTranslateIntegration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ArkServiceConfig {
    // 使用 @Bean 注解定义一个 Bean，Spring 将管理其生命周期
    // initMethod 指定初始化方法，destroyMethod 指定销毁方法
    @Bean(initMethod = "init", destroyMethod = "shutdown")
    public ArkTranslateIntegration arkServiceSingleton() {
        // 创建并返回 ArkServiceSingleton 实例，交给 Spring 管理
        return new ArkTranslateIntegration();
    }
}
