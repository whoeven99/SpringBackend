package com.bogda.task;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(
    exclude = {
            DataSourceAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class,
    },
    scanBasePackages = {"com.bogda.task", "com.bogda.repository", "com.bogda.common"
            , "com.bogda.integration", "com.bogda.service"}
)
@EnableScheduling
public class TaskApplication {
    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(TaskApplication.class);
        // Task 进程虽以定时任务为主，但部署在 Azure App Service for Containers 上，
        // 平台需周期性 ping 容器的 HTTP 端口判断存活，否则会判定不健康并回收重启。
        // 因此启动最小内嵌 Web 服务器，仅对外暴露健康检查端点。
        app.setWebApplicationType(WebApplicationType.SERVLET);
        app.run(args);
    }
}
