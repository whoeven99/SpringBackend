package com.bogda.task.task;

import com.bogda.service.logic.TaskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import static com.bogda.service.logic.TranslateService.executorService;

@Component
public class SubscriptionTask {
    @Autowired
    private TaskService taskService;

    // 主 App 额度/试用 cron 已停用：计费迁至 TSF 后不再在 Spring 侧发放额度。
    // subscriptionTask / freeTrialTask 不再调度；PC 图片 App 任务保留。

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
