package com.bogda.task.task;

import com.bogda.service.integration.RateHttpIntegration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import javax.annotation.PostConstruct;

@Component
public class RateTask {
    @Autowired
    private RateHttpIntegration rateHttpIntegration;

    private final Logger log = LoggerFactory.getLogger(RateTask.class);

    @PostConstruct
    @Scheduled(cron = "0 15 1 ? * *")
    public void getRate() {
        try {
            //改为存储在缓存中（后面存储到redis中）
            rateHttpIntegration.getFixerRate();
            log.info("rateMap: {}", RateHttpIntegration.rateMap); // 不是app insights 要去log stream里去看
        } catch (Exception e) {
            log.info("Error fetching rates: {}", e.getMessage());
        }
    }
}
