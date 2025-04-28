package com.bogdatech.task;

import com.bogdatech.integration.RateHttpIntegration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

import static com.bogdatech.integration.RateHttpIntegration.rateMap;
import static com.bogdatech.utils.CaseSensitiveUtils.appInsights;

@Component
@EnableScheduling
@EnableAsync
public class RateTask {

    private final RateHttpIntegration rateHttpIntegration;
    @Autowired
    public RateTask(RateHttpIntegration rateHttpIntegration) {
        this.rateHttpIntegration = rateHttpIntegration;
    }

    @PostConstruct
    @Scheduled(cron = "0 15 1 ? * *")
    public void getRateEveryHour() {
//        System.out.println(LocalDateTime.now() + " getRateEveryHour " + Thread.currentThread().getName());
        //改为存储在缓存中（后面存储到redis中）
        try {
            rateHttpIntegration.getFixerRate();
        } catch (Exception e) {
            appInsights.trackTrace("获取汇率失败: " + e.getMessage());
        }
        System.out.println("rateMap: " + rateMap.toString());
    }
}
