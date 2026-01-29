package com.bogda.task.task;

import com.bogda.service.logic.bundle.BundleTaskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import static com.bogda.service.logic.TranslateService.executorService;

@Component
public class BundleTask {
    @Autowired
    private BundleTaskService bundleTaskService;

    // 定时更新用户折扣的累积数据 目前暂定是每天UTC0点更新
    @Scheduled(cron = "0 0 0 * * ?")
    public void updateDiscountData() {
        executorService.execute(() -> {
            bundleTaskService.updateDiscountData();
        });
    }
}
