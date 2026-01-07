package com.bogda.api.task;

import com.bogda.api.integration.RateHttpIntegration;
import com.bogda.common.utils.AppInsightsUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import javax.annotation.PostConstruct;
import static com.bogda.api.integration.RateHttpIntegration.rateMap;

@Component
@EnableScheduling
public class RateTask {
    @Autowired
    private RateHttpIntegration rateHttpIntegration;

    @PostConstruct
    @Scheduled(cron = "0 15 1 ? * *")
    public void getRateEveryHour() {
        //改为存储在缓存中（后面存储到redis中）
        try {
            rateHttpIntegration.getFixerRate();
            AppInsightsUtils.trackTrace("rateMap: %s", rateMap.toString());
        } catch (Exception e) {
            AppInsightsUtils.trackTrace("FatalException 获取汇率失败: %s", e.getMessage());
        }
    }
}
