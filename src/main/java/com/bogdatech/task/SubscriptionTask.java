package com.bogdatech.task;

import com.bogdatech.logic.TaskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
@EnableAsync
public class SubscriptionTask {
    private final TaskService taskService;

    @Autowired
    public SubscriptionTask(TaskService taskService) {
        this.taskService = taskService;
    }

    @Scheduled(cron = "0 0 0 * * ?")
    public void subscriptionTask() {
        taskService.judgeAddChars();
    }
}
