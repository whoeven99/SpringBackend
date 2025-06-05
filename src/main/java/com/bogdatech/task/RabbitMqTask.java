package com.bogdatech.task;

import com.bogdatech.model.service.RabbitMqTranslateService;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
@EnableAsync
public class RabbitMqTask {
    private final RabbitMqTranslateService dynamicQueueService;

    public RabbitMqTask(RabbitMqTranslateService dynamicQueueService) {
        this.dynamicQueueService = dynamicQueueService;
    }

    // 每 30 分钟检查一次是否有闲置用户队列
    @Scheduled(fixedDelay = 1800000)
    public void cleanup() {
        //TODO: 需要判断是否还有任务
        dynamicQueueService.stopAndCleanupInactiveQueues(60000); // 超过 60 秒未使用就清理
    }
}
