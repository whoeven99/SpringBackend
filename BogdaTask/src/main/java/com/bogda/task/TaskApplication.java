package com.bogda.task;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(
    exclude = {DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class},
    scanBasePackages = {"com.bogda.task", "com.bogda.repository", "com.bogda.common"
            , "com.bogda.integration", "com.bogda.service"}
)
@EnableScheduling
public class TaskApplication {
    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(TaskApplication.class);
        // Task 进程仅承担定时任务，不需要启动内嵌 Web 服务器，避免 servlet/jackson web 链路冲突。
        app.setWebApplicationType(WebApplicationType.NONE);
        app.run(args);
    }
}
