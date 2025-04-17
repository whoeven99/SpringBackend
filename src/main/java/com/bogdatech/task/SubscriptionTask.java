package com.bogdatech.task;

import com.bogdatech.Service.ICharsOrdersService;
import com.bogdatech.entity.CharsOrdersDO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@EnableScheduling
@EnableAsync
public class SubscriptionTask {
    private final ICharsOrdersService charsOrdersService;

    @Autowired
    public SubscriptionTask(ICharsOrdersService charsOrdersService) {
        this.charsOrdersService = charsOrdersService;
    }

//    @PostConstruct
//    @Scheduled(cron = "0 15 1 ? * *")
    @Async
    public void subscriptionTask() {
        //获取数据库中所有order为ACTIVE的id集合
        List<CharsOrdersDO> list = charsOrdersService.getShopNameAndId();
        for (CharsOrdersDO charsOrdersDO : list
        ) {
            System.out.println("value: " + charsOrdersDO);
        }

        //根据shopName获取User表对应的accessToken，重新生成一个数据类型


        //根据新的集合获取这个订阅计划的信息

        //根据订阅计划信息，判断是否是第一个月的开始，是否要添加额度

    }
}
