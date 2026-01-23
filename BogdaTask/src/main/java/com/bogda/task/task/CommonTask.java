package com.bogda.task.task;

import com.bogda.integration.aimodel.RateHttpIntegration;
import com.bogda.service.logic.redis.RateRedisService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import javax.annotation.PostConstruct;

import java.util.Map;

@Component
public class CommonTask {
    @Autowired
    private RateHttpIntegration rateHttpIntegration;

    @Autowired
    private RateRedisService rateRedisService;

    private final Logger log = LoggerFactory.getLogger(CommonTask.class);

    @Scheduled(fixedRate = 20000)
    public void testJob() {
        log.info("test Job run successfully");
    }

    @Scheduled(cron = "0 15 1 ? * *")
    public void getRate() {
        try {
            // 拉取并写入 Redis（Hash）
            Map<String, Double> rates = rateHttpIntegration.getFixerRate();
            if (rates == null || rates.isEmpty()) {
                log.info("getRate: empty rates, skip redis write");
                return;
            }

            rateRedisService.refreshRates(rates);
            log.info("getRate: wrote {} rates to redis", rates.size());
        } catch (Exception e) {
            log.info("Error fetching rates: {}", e.getMessage());
        }
    }
}
