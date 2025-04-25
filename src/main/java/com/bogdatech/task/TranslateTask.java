package com.bogdatech.task;

import com.bogdatech.logic.TaskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

@Component
@EnableScheduling
@EnableAsync
public class TranslateTask {

    private final TaskService taskService;

    @Autowired
    public TranslateTask(TaskService taskService) {
        this.taskService = taskService;
    }

    @PostConstruct
    public void translateStatus2WhenSystemRestart() {
        taskService.translateStatus2WhenSystemRestart();
    }
}
