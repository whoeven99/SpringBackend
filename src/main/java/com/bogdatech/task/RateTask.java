package com.bogdatech.task;

import com.bogdatech.exception.ClientException;
import com.bogdatech.integration.RateHttpIntegration;
import com.bogdatech.logic.RateDataService;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;

@Component
@EnableScheduling
@EnableAsync
public class RateTask {
    @Autowired
    private RateHttpIntegration rateHttpIntegration;

    private static String scur = "USD";
    private static String tcur = "CNY";

    // 使用线程安全的ConcurrentHashMap
    @Autowired
    private RateDataService rateDataService;

    // TODO 印象里直接加到这里就可以启动时运行一次，记得check一下
    @PostConstruct
    @Scheduled(cron = "0 0 0/1 * * ?")
    @Async
    public void getRateEveryHour()  {
        System.out.println(LocalDateTime.now() + " getRateEveryHour " + Thread.currentThread().getName());
        try {
            LinkedHashMap<String, Object> rates = rateHttpIntegration.getBasicRate(scur, tcur);
            System.out.println("Get rate success: " + rates);
            rateDataService.updateValue("data", rates);
        } catch (Exception e) { // TODO 这里的exception要去掉的，在integration里面做exception管理就好
            throw new ClientException("获取汇率失败");
        }
    }
}
