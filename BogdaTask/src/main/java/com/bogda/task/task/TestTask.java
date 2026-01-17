package com.bogda.task.task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class TestTask {

    private final Logger log = LoggerFactory.getLogger(TestTask.class);

    // 每5秒执行一次
    @Scheduled(fixedRate = 5000)
    public void testJob() {
        log.info("TestTask 执行了一次定时任务");
    }
}
