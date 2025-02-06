package com.bogdatech.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Bean
    public CorsFilter corsFilter() {
        // 初始化cors配置对象
        CorsConfiguration configuration = new CorsConfiguration();
//        configuration.setAllowCredentials(true); // 允许使用cookie，但是使用cookie是addAllowedOrigin必须是具体的地址，不能是*
//        configuration.addAllowedOrigin("https://quickstart-0f992326.myshopify.com");
        configuration.addAllowedOrigin("*"); //允许所有域名
        configuration.addAllowedMethod("GET");  //允许的请求方式,get,put,post,delete
        configuration.addAllowedMethod("POST");
        configuration.addAllowedHeader("*");//允许的头信息

        //初始化cors的源对象配置
        UrlBasedCorsConfigurationSource corsConfigurationSource = new UrlBasedCorsConfigurationSource();
        corsConfigurationSource.registerCorsConfiguration("/**", configuration);

        //3.返回新的CorsFilter.
        return new CorsFilter(corsConfigurationSource);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 禁用静态资源路径
    }
}
