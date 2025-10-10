package com.bogdatech.task;

import com.bogdatech.logic.TaskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import static com.bogdatech.logic.TranslateService.executorService;

@Component
@EnableScheduling
@EnableAsync
public class AutoTranslateTask {

    @Autowired
    private TaskService taskService;

    @Scheduled(cron = "0 0 1 * * ?")
    public void autoTranslate() {
        executorService.execute(() -> {
            taskService.autoTranslate();
        });
    }
}
