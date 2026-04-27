package com.bogda.task.task;

import com.bogda.common.reporter.ExceptionReporterHolder;
import com.bogda.common.reporter.TraceReporterHolder;
import com.bogda.integration.feishu.FeiShuRobotIntegration;
import com.bogda.service.logic.translate.TranslateV3Service;
import com.bogda.task.annotation.EnableScheduledTask;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class TranslateTaskV3Scheduled {
    @Autowired
    private TranslateV3Service translateV3Service;
    @Autowired
    private FeiShuRobotIntegration feiShuRobotIntegration;

    @Scheduled(fixedDelay = 30 * 1000)
    public void initialToTranslateTaskV3() {
        translateV3Service.processInitialTasksV3();
    }

    @Scheduled(fixedDelay = 30 * 1000)
    @EnableScheduledTask
    public void translateEachTaskV3() {
        translateV3Service.processTranslateTasksV3();
    }

    @Scheduled(fixedDelay = 30 * 1000)
    public void saveToShopifyV3() {
        translateV3Service.processSaveTasksV3();
    }

    @Scheduled(fixedDelay = 60 * 1000)
    @EnableScheduledTask
    public void verifySavedToShopifyV3() {
        translateV3Service.processVerifyTasksV3();
    }

    @Scheduled(fixedDelay = 5 * 60 * 1000)
    public void retrySaveFailedTasksV3() {
        try {
            translateV3Service.retrySaveAllFailedTasks();
        } catch (Exception e) {
            TraceReporterHolder.report("TranslateTaskV3Scheduled.retrySaveFailedTasksV3",
                    "FatalException 飞书机器人报错 retrySaveFailedTasksV3: " + e.getMessage());
            ExceptionReporterHolder.report("TranslateTaskV3Scheduled.retrySaveFailedTasksV3", e);
            feiShuRobotIntegration.sendMessage("FatalException retrySaveFailedTasksV3: " + e);
        }
    }

//    @Scheduled(fixedDelay = 300 * 1000)
//    public void deleteToShopifyV3() {
//        translateV3Service.deleteToShopify();
//    }
}
