package com.bogda.agenttask;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Agent 进程：内嵌 Web（Render 健康检查 / 探针）+ 定时任务。
 * 当前仅扫描本模块；接入 Cosmos/Redis/Blob 时再打开 com.bogda.repository、com.bogda.common。
 */
@SpringBootApplication(
        exclude = {DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class},
        scanBasePackages = {"com.bogda.agenttask"}
)
@EnableScheduling
public class AgentTaskApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgentTaskApplication.class, args);
    }
}
