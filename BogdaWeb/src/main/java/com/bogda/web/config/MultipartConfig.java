package com.bogda.web.config;

import org.springframework.boot.web.servlet.MultipartConfigFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.unit.DataSize;

import jakarta.servlet.MultipartConfigElement;

@Configuration
public class MultipartConfig {

    @Bean
    public MultipartConfigElement multipartConfigElement() {
        MultipartConfigFactory factory = new MultipartConfigFactory();
        
        // 设置单个文件最大大小为 20MB
        factory.setMaxFileSize(DataSize.ofMegabytes(20));
        
        // 设置整个请求最大大小为 100MB
        factory.setMaxRequestSize(DataSize.ofMegabytes(100));
        
        return factory.createMultipartConfig();
    }
}

