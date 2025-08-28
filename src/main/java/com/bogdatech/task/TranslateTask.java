package com.bogdatech.task;

import com.bogdatech.logic.TaskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableAsync
@EnableScheduling
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

    /**
     * 每分钟做次打印--正在翻译中和等待翻译的用户数据
     * */
    @Scheduled(cron = "0 * * * * ?")
    public void printTranslatingAndWaitTranslatingData() {
        taskService.printTranslatingAndWaitTranslatingData();
    }
}
