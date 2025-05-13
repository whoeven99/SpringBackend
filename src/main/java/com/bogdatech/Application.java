package com.bogdatech;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class})
@EnableAsync
public class Application {
    @Value("${spring.application.name}")
    private String name;
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    @Bean
    public String name1(){
        System.out.println("1This is " + name);
        return name;
    }
}
