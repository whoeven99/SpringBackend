package com.bogdatech.task;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.bogdatech.Service.ICharsOrdersService;
import com.bogdatech.Service.IUsersService;
import com.bogdatech.entity.CharsOrdersDO;
import com.bogdatech.entity.UsersDO;
import com.bogdatech.integration.ShopifyHttpIntegration;
import com.bogdatech.logic.ShopifyService;
import com.bogdatech.model.controller.request.CloudServiceRequest;
import com.bogdatech.model.controller.request.ShopifyRequest;
import com.bogdatech.model.controller.request.UserPriceRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static com.bogdatech.requestBody.ShopifyRequestBody.getSubscriptionQuery;

@Component
@EnableScheduling
@EnableAsync
public class SubscriptionTask {
    private final ICharsOrdersService charsOrdersService;
    private final IUsersService usersService;
    private final ShopifyHttpIntegration shopifyApiIntegration;
    private final ShopifyService shopifyService;

    @Autowired
    public SubscriptionTask(ICharsOrdersService charsOrdersService, IUsersService usersService, ShopifyHttpIntegration shopifyApiIntegration, ShopifyService shopifyService) {
        this.charsOrdersService = charsOrdersService;
        this.usersService = usersService;
        this.shopifyApiIntegration = shopifyApiIntegration;
        this.shopifyService = shopifyService;
    }

    @PostConstruct
    @Scheduled(cron = "0 15 1 ? * *")
    @Async
    public void subscriptionTask() {
        //获取数据库中所有order为ACTIVE的id集合
        List<CharsOrdersDO> list = charsOrdersService.getShopNameAndId();
        List<UserPriceRequest> usedList = new ArrayList<>();
        for (CharsOrdersDO charsOrdersDO : list
        ) {
            //根据shopName获取User表对应的accessToken，重新生成一个数据类型
            UserPriceRequest userPriceRequest = new UserPriceRequest();
            userPriceRequest.setShopName(charsOrdersDO.getShopName());
            userPriceRequest.setSubscriptionId(charsOrdersDO.getId());
            userPriceRequest.setCreateAt(charsOrdersDO.getCreatedAt());
            userPriceRequest.setAccessToken(usersService.getOne(new QueryWrapper<UsersDO>().eq("shop_name", charsOrdersDO.getShopName())).getAccessToken());
            usedList.add(userPriceRequest);
        }
        String env = System.getenv("ApplicationEnv");
        for (UserPriceRequest userPriceRequest : usedList
             ) {
            String query = getSubscriptionQuery(userPriceRequest.getSubscriptionId());
            String infoByShopify = null;
            //根据新的集合获取这个订阅计划的信息
            if ("prod".equals(env) || "dev".equals(env)) {
                infoByShopify = String.valueOf(shopifyApiIntegration.getInfoByShopify(new ShopifyRequest(userPriceRequest.getShopName(), userPriceRequest.getAccessToken(), "2024-10", null), query));
            } else {
                infoByShopify = shopifyService.getShopifyData(new CloudServiceRequest(userPriceRequest.getShopName(), userPriceRequest.getAccessToken(), "2024-10", "en",query));
            }

//            System.out.println("infoByShopify: " + infoByShopify);
            //根据订阅计划信息，判断是否是第一个月的开始，是否要添加额度
            JSONObject root = JSON.parseObject(infoByShopify);
            JSONObject node = root.getJSONObject("node");

            String name = node.getString("name");
            String status = node.getString("status");
            String createdAt = node.getString("createdAt");
            String currentPeriodEnd = node.getString("currentPeriodEnd");

            System.out.println("Name: " + name);
            System.out.println("Status: " + status);
            System.out.println("Created At: " + createdAt);
            System.out.println("Current Period End: " + currentPeriodEnd);

            Instant created = Instant.parse(createdAt);
            Instant end = Instant.parse(currentPeriodEnd);
            Instant buyCreated = Instant.parse(userPriceRequest.getCreateAt());



        }






    }
}
