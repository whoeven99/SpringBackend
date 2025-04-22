package com.bogdatech.task;

import com.bogdatech.logic.TaskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

@Component
@EnableScheduling
@EnableAsync
public class SubscriptionTask {
    private final TaskService taskService;

    @Autowired
    public SubscriptionTask(TaskService taskService) {
        this.taskService = taskService;
    }

    @PostConstruct
    @Scheduled(cron = "0 15 1 ? * *")
    public void subscriptionTask() {
        taskService.judgeAddChars();
    }
}
