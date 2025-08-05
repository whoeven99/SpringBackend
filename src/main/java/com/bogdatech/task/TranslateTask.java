package com.bogdatech.task;

import com.bogdatech.logic.TaskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Component;

@Component
@EnableAsync
public class TranslateTask implements ApplicationListener<ApplicationReadyEvent> {

    private final TaskService taskService;

    @Autowired
    public TranslateTask(TaskService taskService) {
        this.taskService = taskService;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        // 执行业务代码
        taskService.translateStatus2WhenSystemRestart();
    }
}
