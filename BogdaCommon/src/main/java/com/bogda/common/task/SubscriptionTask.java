package com.bogda.common.task;

import com.bogda.common.logic.TaskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import static com.bogda.common.logic.TranslateService.executorService;

@Component
@EnableScheduling
public class SubscriptionTask {
    @Autowired
    private TaskService taskService;

    /**
     * 每天UTC凌晨0点执行一次 判断翻译App是否符合添加额度的条件，如果符合，添加，反之不添加
     */
    @Scheduled(cron = "0 0 0 * * ?")
    public void subscriptionTask() {
        executorService.execute(() -> {
            taskService.judgeAddChars();
        });
    }

    /**
     * 每天UTC凌晨0点执行一次 判断免费订阅是否过期， 是的话，修改用户计划表改为2，修改用户定时翻译任务，修改用户IP开关方法
     */
    @Scheduled(cron = "0 0 0 * * ?")
    public void freeTrialTask() {
        taskService.freeTrialTask();
    }

    /**
     * 每天UTC凌晨0点执行一次 判断图片翻译App是否符合添加额度的条件，如果符合，添加，反之不添加
     */
    @Scheduled(cron = "0 0 0 * * ?")
    public void subscriptionTaskForImage() {
        executorService.execute(() -> {
            taskService.judgePCAppAddChars();
        });
    }

    /**
     * 每天UTC凌晨0点执行一次 判断免费订阅是否过期， 是的话，修改用户计划表改为1 免费计划
     */
    @Scheduled(cron = "0 0 0 * * ?")
    public void freeTrialTaskForImage() {
        taskService.freeTrialTaskForImage();
    }
}
