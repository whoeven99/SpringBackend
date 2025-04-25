package com.bogdatech.logic;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.bogdatech.Service.*;
import com.bogdatech.entity.*;
import com.bogdatech.integration.ShopifyHttpIntegration;
import com.bogdatech.model.controller.request.*;
import com.bogdatech.utils.CharacterCountUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import static com.bogdatech.requestBody.ShopifyRequestBody.getSubscriptionQuery;

@Component
public class TaskService {
    private final ICharsOrdersService charsOrdersService;
    private final IUsersService usersService;
    private final ShopifyHttpIntegration shopifyApiIntegration;
    private final ShopifyService shopifyService;
    private final ITranslationCounterService translationCounterService;
    private final ISubscriptionPlansService subscriptionPlansService;

    private final ISubscriptionQuotaRecordService subscriptionQuotaRecordService;
    private final ITranslatesService translatesService;
    private final TranslateService translateService;


    @Autowired
    public TaskService(ICharsOrdersService charsOrdersService, IUsersService usersService, ShopifyHttpIntegration shopifyApiIntegration, ShopifyService shopifyService, ITranslationCounterService translationCounterService, ISubscriptionPlansService subscriptionPlansService, ISubscriptionQuotaRecordService subscriptionQuotaRecordService, ITranslatesService translatesService, TranslateService translateService) {
        this.charsOrdersService = charsOrdersService;
        this.usersService = usersService;
        this.shopifyApiIntegration = shopifyApiIntegration;
        this.shopifyService = shopifyService;
        this.translationCounterService = translationCounterService;
        this.subscriptionPlansService = subscriptionPlansService;
        this.subscriptionQuotaRecordService = subscriptionQuotaRecordService;
        this.translatesService = translatesService;
        this.translateService = translateService;
    }

    //异步调用根据订阅信息，判断是否添加额度的方法
    @Async
    public void judgeAddChars() {
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
            addCharsByUserData(userPriceRequest, env);
        }

    }

    //获取用户订阅选项并判断是否添加额度
    public void addCharsByUserData(UserPriceRequest userPriceRequest, String env) {

        String query = getSubscriptionQuery(userPriceRequest.getSubscriptionId());
        String infoByShopify;
        //根据新的集合获取这个订阅计划的信息
        if ("prod".equals(env) || "dev".equals(env)) {
            infoByShopify = String.valueOf(shopifyApiIntegration.getInfoByShopify(new ShopifyRequest(userPriceRequest.getShopName(), userPriceRequest.getAccessToken(), "2024-10", null), query));
        } else {
            infoByShopify = shopifyService.getShopifyData(new CloudServiceRequest(userPriceRequest.getShopName(), userPriceRequest.getAccessToken(), "2024-10", "en", query));
        }

        //根据订阅计划信息，判断是否是第一个月的开始，是否要添加额度
        JSONObject root = JSON.parseObject(infoByShopify);
        JSONObject node = root.getJSONObject("node");
        if (node == null) {
            return;
        }
        String name = node.getString("name");
        String status = node.getString("status");
        String createdAt = node.getString("createdAt");
        String currentPeriodEnd = node.getString("currentPeriodEnd");
        //用户购买订阅时间
        LocalDateTime buyCreate = userPriceRequest.getCreateAt();
        Instant buyCreateInstant = buyCreate.atZone(ZoneId.of("UTC")).toInstant();
        //订阅开始时间
        Instant created = Instant.parse(createdAt);
        //订阅结束时间
        Instant end = Instant.parse(currentPeriodEnd);
        //当前时间
        Instant now = Instant.now();

        // 只处理活跃、非试用、且还在本期内的订阅
        if (!"ACTIVE".equals(status) || now.isAfter(end)){
//            System.out.println("不满足条件");
            return;
        }

        //计算当前是第几个月
        int billingCycle = (int) ChronoUnit.DAYS.between(buyCreateInstant, now) / 30 + 1;
//        System.out.println("billingCycle = " + billingCycle);

        // 如果这一周期还没发放过额度，则发放并记录
        SubscriptionQuotaRecordDO quotaRecordDO = subscriptionQuotaRecordService.getOne(new QueryWrapper<SubscriptionQuotaRecordDO>().eq("subscription_id", userPriceRequest.getSubscriptionId()).eq("billing_cycle", billingCycle));
        if (quotaRecordDO == null) {
            // 满足条件，执行添加字符的逻辑
//            System.out.println("满足条件，执行添加字符的逻辑");
            // 根据计划获取对应的字符
            Integer chars = subscriptionPlansService.getCharsByPlanName(name);
            subscriptionQuotaRecordService.insertOne(userPriceRequest.getSubscriptionId(), billingCycle);
            translationCounterService.updateCharsByShopName(new TranslationCounterRequest(0, userPriceRequest.getShopName(), chars, 0, 0, 0, 0));
        }
    }

    //当自动重启后，重启翻译状态为2的任务
    @Async
    public void translateStatus2WhenSystemRestart() {
        //查找翻译状态为2的任务
        List<TranslatesDO> listData =translatesService.getStatus2Data();
        //循环处理获取到的任务，先将状态改为3，然后调用翻译API
        for (TranslatesDO translatesDO: listData
        ) {
//            System.out.println("translatesDO: " + translatesDO);
            translateStatus2WhenSystemRestartComplete(translatesDO);
        }
    }

    //判断是否符合翻译条件
    public Boolean isTranslateCondition(TranslatesDO translatesDO, TranslationCounterDO request1, Integer remainingChars) {
//        一个用户当前只能翻译一条语言，根据用户的status判断
        List<Integer> integers = translatesService.readStatusInTranslatesByShopName(translatesDO.getShopName());
        for (Integer integer : integers) {
            if (integer == 2) {
                return false;
            }
        }

        int usedChars = request1.getUsedChars();
        // 如果字符超限，则直接返回字符超限
        if (usedChars >= remainingChars) {
            return false;
        }
        //通过判断status和字符判断后 就将状态改为2，则开始翻译流程
        translatesService.updateTranslateStatus(translatesDO.getShopName(), 2, translatesDO.getTarget(), translatesDO.getSource(), translatesDO.getAccessToken());
        return true;
    }

    //服务器重启的完整翻译方法
    public void translateStatus2WhenSystemRestartComplete(TranslatesDO translatesDO) {
        int i = translatesService.updateTranslateStatus(translatesDO.getShopName(), 3, translatesDO.getTarget(), translatesDO.getSource(), translatesDO.getAccessToken());
        if (i > 0) {
            //准备开始翻译，判断是否符合翻译条件
            //判断字符是否超限
            String shopName = translatesDO.getShopName();
            TranslationCounterDO request1 = translationCounterService.readCharsByShopName(shopName);
            Integer remainingChars = translationCounterService.getMaxCharsByShopName(shopName);
            Boolean translateCondition = isTranslateCondition(translatesDO, request1, remainingChars);
            if (translateCondition) {
                Integer usedChars = request1.getUsedChars();
                //初始化计数器
                CharacterCountUtils counter = new CharacterCountUtils();
                counter.addChars(usedChars);
                //开始翻译product模块
                translateService.startTranslation(new TranslateRequest(0,translatesDO.getShopName(),translatesDO.getAccessToken(),translatesDO.getSource(),translatesDO.getTarget(), null), remainingChars, counter, usedChars);
            }
        }
    }
}
