package com.bogda.api.task;

import org.springframework.scheduling.annotation.Scheduled;

public class TranslationQualityEvaluationTask {

    @Scheduled(cron = "0 15 1 ? * *")
    public void ss() {

    }
}
