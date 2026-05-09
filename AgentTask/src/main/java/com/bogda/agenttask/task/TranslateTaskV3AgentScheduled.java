package com.bogda.agenttask.task;

import com.bogda.agenttask.annotation.EnableScheduledTask;
import com.bogda.service.logic.translate.TranslateV3Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class TranslateTaskV3AgentScheduled {

    private static final Logger LOG = LoggerFactory.getLogger(TranslateTaskV3AgentScheduled.class);

    private final TranslateV3Service translateV3Service;

    public TranslateTaskV3AgentScheduled(TranslateV3Service translateV3Service) {
        this.translateV3Service = translateV3Service;
    }

    @Scheduled(fixedDelay = 30 * 1000)
    public void initialToTranslateTaskV3() {
        LOG.info("v3 scheduler tick initialToTranslateTaskV3");
        translateV3Service.processInitialTasksV3();
    }

    @Scheduled(fixedDelay = 3000 * 1000)
    @EnableScheduledTask
    public void translateEachTaskV3() {
        LOG.info("v3 scheduler tick translateEachTaskV3");
        translateV3Service.processTranslateTasksV3();
    }
}
