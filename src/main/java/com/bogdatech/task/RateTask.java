package com.bogdatech.task;

import com.bogdatech.exception.ClientException;
import com.bogdatech.logic.BasicRateService;
import com.bogdatech.logic.DataService;
import com.bogdatech.model.controller.request.BasicRateRequest;
import com.bogdatech.model.controller.response.BaseResponse;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;

import static com.bogdatech.common.enums.BasicEnum.GET_RATE_ERROR;

@Component
@EnableScheduling
@EnableAsync
public class RateTask {
    @Autowired
    private BasicRateService basicRateService;

//    private BasicRateRequest request = new BasicRateRequest();
    private static String scur = "USD";
    private static String tcur = "CNY";

    // 使用线程安全的ConcurrentHashMap
   @Autowired
   private DataService dataService;
    @PostConstruct
    public void onStartup()  {
        // 项目启动时调用的方法
        getRateEveryHour();
    }
    @Scheduled(cron = "0 0 0/1 * * ?")
    @Async
    public void getRateEveryHour()  {
        System.out.println(LocalDateTime.now() + " getRateEveryHour " + Thread.currentThread().getName());
        BasicRateRequest request = new BasicRateRequest();
        request.setScur(scur);
        request.setTcur(tcur);
        BaseResponse basicRate = null;
        try {
            basicRate = basicRateService.getBasicRate(request);
            if (basicRate.getSuccess().equals("success")) {
                dataService.updateValue("data", (LinkedHashMap<String, Object>) basicRate.getResponse());
                System.out.println("Get rate success: " + basicRate.getResponse());
            }
        } catch (Exception e) {
            throw new ClientException(GET_RATE_ERROR.getSuccess(), GET_RATE_ERROR.getErrorMessage());
        }


    }
}
