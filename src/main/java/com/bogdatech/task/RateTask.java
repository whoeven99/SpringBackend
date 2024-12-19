package com.bogdatech.task;

import com.bogdatech.integration.RateHttpIntegration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.stereotype.Component;

import static com.bogdatech.integration.RateHttpIntegration.rateMap;

@Component
@EnableScheduling
@EnableAsync
public class RateTask {
    @Autowired
    private RateHttpIntegration rateHttpIntegration;


    @PostConstruct
//    @Scheduled(cron = "0 0 0/1 * * ?")
    @Async
    public void getRateEveryHour() {
//        System.out.println(LocalDateTime.now() + " getRateEveryHour " + Thread.currentThread().getName());
        //改为存储在缓存中（后面存储到redis中）
        rateHttpIntegration.getFixerRate();
        System.out.println("rateMap: " + rateMap.toString());
    }
}
