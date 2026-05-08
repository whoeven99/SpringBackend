package com.bogda.agenttask;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Agent 进程：内嵌 Web（Render 健康检查 / 探针）+ 定时任务。
 * 扫描范围与 {@code BogdaTask.TaskApplication} 对齐，以加载 TranslateV3Service、仓储与集成层。
 */
@SpringBootApplication(
        exclude = {DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class},
        scanBasePackages = {"com.bogda.agenttask", "com.bogda.repository", "com.bogda.common",
                "com.bogda.integration", "com.bogda.service"}
)
@EnableScheduling
public class AgentTaskApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgentTaskApplication.class, args);
    }
}
