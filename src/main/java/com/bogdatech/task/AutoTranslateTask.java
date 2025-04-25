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
public class AutoTranslateTask {

    private final TaskService taskService;

    @Autowired
    public AutoTranslateTask(TaskService taskService) {
        this.taskService = taskService;
    }

    @Scheduled(cron = "0 0 0 * * ?")
    @PostConstruct
    public void autoTranslate() {
        taskService.autoTranslate();
    }
}
