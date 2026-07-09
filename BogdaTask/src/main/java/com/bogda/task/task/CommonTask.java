package com.bogda.task.task;

import com.bogda.task.annotation.EnableScheduledTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CommonTask {

    private final Logger log = LoggerFactory.getLogger(CommonTask.class);

    @EnableScheduledTask
    @Scheduled(fixedRate = 20000)
    public void testJob() {
        log.info("test Job run successfully");
    }
}
