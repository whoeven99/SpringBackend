package com.bogda.task.task;

import com.bogda.common.integration.RateHttpIntegration;
import com.bogda.common.utils.CaseSensitiveUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import javax.annotation.PostConstruct;

@Component
@EnableScheduling
public class RateTask {
    @Autowired
    private RateHttpIntegration rateHttpIntegration;

//    @PostConstruct
    @Scheduled(cron = "0 15 1 ? * *")
    public void getRateEveryHour() {
        //改为存储在缓存中（后面存储到redis中）
        try {
            rateHttpIntegration.getFixerRate();
        } catch (Exception e) {
            CaseSensitiveUtils.appInsights.trackTrace("获取汇率失败: " + e.getMessage());
        }
        CaseSensitiveUtils.appInsights.trackTrace("rateMap: " + RateHttpIntegration.rateMap.toString());
    }
}
