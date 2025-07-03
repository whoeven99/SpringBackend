package com.bogdatech.logic;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.bogdatech.Service.*;
import com.bogdatech.entity.DO.*;
import com.bogdatech.entity.DTO.TaskTranslateDTO;
import com.bogdatech.model.controller.request.*;
import com.bogdatech.model.service.TranslateTaskPublisherService;
import com.bogdatech.utils.CharacterCountUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.bogdatech.entity.DO.TranslateResourceDTO.ALL_RESOURCES;
import static com.bogdatech.integration.ShopifyHttpIntegration.getInfoByShopify;
import static com.bogdatech.logic.ShopifyService.getShopifyDataByCloud;
import static com.bogdatech.logic.TranslateService.userEmailStatus;
import static com.bogdatech.logic.TranslateService.userStopFlags;
import static com.bogdatech.requestBody.ShopifyRequestBody.getSubscriptionQuery;
import static com.bogdatech.utils.CaseSensitiveUtils.appInsights;
import static com.bogdatech.utils.JsonUtils.objectToJson;

@Component
public class TaskService {
    private final ICharsOrdersService charsOrdersService;
    private final IUsersService usersService;
    private final ITranslationCounterService translationCounterService;
    private final ISubscriptionPlansService subscriptionPlansService;

    private final ISubscriptionQuotaRecordService subscriptionQuotaRecordService;
    private final ITranslatesService translatesService;
    private final TranslateService translateService;
    private final TranslateTaskPublisherService translateTaskPublisherService;
    private final IUserTrialsService iUserTrialsService;
    private final IUserSubscriptionsService iUserSubscriptionsService;
    private final IWidgetConfigurationsService iWidgetConfigurationsService;
    private final IGlossaryService iGlossaryService;
    private final IUserIpService iUserIpService;
    private final ITranslateTasksService translateTasksService;

    @Autowired
    public TaskService(ICharsOrdersService charsOrdersService, IUsersService usersService, ITranslationCounterService translationCounterService, ISubscriptionPlansService subscriptionPlansService, ISubscriptionQuotaRecordService subscriptionQuotaRecordService, ITranslatesService translatesService, TranslateService translateService, TranslateTaskPublisherService translateTaskPublisherService, IUserTrialsService iUserTrialsService, IUserSubscriptionsService iUserSubscriptionsService, IWidgetConfigurationsService iWidgetConfigurationsService, IGlossaryService iGlossaryService, IUserIpService iUserIpService, ITranslateTasksService translateTasksService) {
        this.charsOrdersService = charsOrdersService;
        this.usersService = usersService;
        this.translationCounterService = translationCounterService;
        this.subscriptionPlansService = subscriptionPlansService;
        this.subscriptionQuotaRecordService = subscriptionQuotaRecordService;
        this.translatesService = translatesService;
        this.translateService = translateService;
        this.translateTaskPublisherService = translateTaskPublisherService;
        this.iUserTrialsService = iUserTrialsService;
        this.iUserSubscriptionsService = iUserSubscriptionsService;
        this.iWidgetConfigurationsService = iWidgetConfigurationsService;
        this.iGlossaryService = iGlossaryService;
        this.iUserIpService = iUserIpService;
        this.translateTasksService = translateTasksService;
    }

    //异步调用根据订阅信息，判断是否添加额度的方法
    @Async
    public void judgeAddChars() {
        //获取数据库中所有order为ACTIVE的id集合
        List<CharsOrdersDO> list = charsOrdersService.getShopNameAndId();
        List<UserPriceRequest> usedList = new ArrayList<>();
        for (CharsOrdersDO charsOrdersDO : list
        ) {
            //根据shopName获取User表对应的accessToken，重新生成一个数据类型  判断是否是卸载，如果卸载， 不计算
            UsersDO usersDO = usersService.getOne(new QueryWrapper<UsersDO>().eq("shop_name", charsOrdersDO.getShopName()));
            if (usersDO == null) {
                continue;
            }
            if (usersDO.getUninstallTime() != null && usersDO.getLoginTime() != null && usersDO.getUninstallTime().after(usersDO.getLoginTime())){
                continue;
            }
            //根据shopName获取User表对应的accessToken，重新生成一个数据类型
            UserPriceRequest userPriceRequest = new UserPriceRequest();
            userPriceRequest.setShopName(charsOrdersDO.getShopName());
            userPriceRequest.setSubscriptionId(charsOrdersDO.getId());
            userPriceRequest.setCreateAt(charsOrdersDO.getCreatedAt());
            userPriceRequest.setAccessToken(usersDO.getAccessToken());
            usedList.add(userPriceRequest);
        }

        for (UserPriceRequest userPriceRequest : usedList
        ) {
            try {
                addCharsByUserData(userPriceRequest);
            } catch (Exception e) {
                appInsights.trackTrace("用户： " + userPriceRequest.getShopName() + " 获取数据 errors : " + e);
            }
        }

        //判断计划是否过期，如果过期，将状态改为2
        judgeSubscriptionStatus(usedList);

    }

    //判断计划是否过期，如果过期，将状态改为2
    public void judgeSubscriptionStatus(List<UserPriceRequest> usedList) {
        //获取数据库中所有order为ACTIVE的id集合
        //对list里面的数据做处理，只留一个shopName对应最近的status为ACTIVE
        // 2. 使用Map记录每个shopName对应的最新订单
        Map<String, UserPriceRequest> latestOrderMap = new HashMap<>();
        for (UserPriceRequest order : usedList) {
            String shopName = order.getShopName();
            UserPriceRequest existing = latestOrderMap.get(shopName);

            if (existing == null || order.getCreateAt().isAfter(existing.getCreateAt())) {
                latestOrderMap.put(shopName, order); // 更新为时间更近的订单
            }
        }

        // 3. 获取每个shopName对应的最新订单，并更新状态
        for (UserPriceRequest order : latestOrderMap.values()) {
            try {
                //根据订阅计划信息，判断是否过期，如果过期，将用户计划改为2
                JSONObject node = analyzeOrderData(order);
                String status = node.getString("status");
                if (!"ACTIVE".equals(status)){
                    //如果过期，将用户计划改为2
                    boolean i = iUserSubscriptionsService.checkUserPlan(order.getShopName(), 2) > 0;
                    appInsights.trackTrace(order.getShopName() + " 计划过期，将用户计划改为2 " + " 修改状态: " + i);
                }
            } catch (Exception e) {
                appInsights.trackTrace("用户： " + order.getShopName() + " 获取订阅计划数据 errors : " + e);
            }
        }
    }

    //根据用户accessToken和订单id分析数据，获取数据
    public JSONObject analyzeOrderData(UserPriceRequest userPriceRequest) {
        String query = getSubscriptionQuery(userPriceRequest.getSubscriptionId());
        String infoByShopify;
        String env = System.getenv("ApplicationEnv");
        //根据新的集合获取这个订阅计划的信息
        if ("prod".equals(env) || "dev".equals(env)) {
            infoByShopify = String.valueOf(getInfoByShopify(new ShopifyRequest(userPriceRequest.getShopName(), userPriceRequest.getAccessToken(), "2024-10", null), query));
        } else {
            infoByShopify = getShopifyDataByCloud(new CloudServiceRequest(userPriceRequest.getShopName(), userPriceRequest.getAccessToken(), "2024-10", "en", query));
        }
        JSONObject root = JSON.parseObject(infoByShopify);
        if (root == null || root.isEmpty()) {
            appInsights.trackTrace(userPriceRequest.getShopName() + " 定时任务根据订单id获取数据失败" + " token: " + userPriceRequest.getAccessToken());
            return null;
        }
        JSONObject node = root.getJSONObject("node");
        if (node == null || node.isEmpty()) {
            //用户卸载，计划会被取消，但不确定其他情况
            return null;
        }
        return node;
    }


    //获取用户订阅选项并判断是否添加额度
    public void addCharsByUserData(UserPriceRequest userPriceRequest) {

        //根据新的集合获取这个订阅计划的信息
        JSONObject node = analyzeOrderData(userPriceRequest);
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
        if (!"ACTIVE".equals(status) || now.isAfter(end)) {
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
            appInsights.trackTrace("用户： " + userPriceRequest.getShopName() + " 添加字符额度： " + chars);
            translationCounterService.updateCharsByShopName(new TranslationCounterRequest(0, userPriceRequest.getShopName(), chars, 0, 0, 0, 0));
            //将用户免费Ip清零
            iUserIpService.update(new UpdateWrapper<UserIpDO>().eq("shop_name", userPriceRequest.getShopName()).set("times", 0).set("first_email", 0).set("second_email", 0));
        }
    }

    //当自动重启后，重启翻译状态为2的任务
    @Async
    public void translateStatus2WhenSystemRestart() {
        //查找翻译状态为2的任务
        List<TranslatesDO> listData = translatesService.getStatus2Data();

        //循环处理获取到的任务，先将状态改为3，然后调用翻译API
        for (TranslatesDO translatesDO : listData
        ) {
            //给这些用户添加停止标志符的状态
            userEmailStatus.put(translatesDO.getShopName(), new AtomicBoolean(false)); //重置用户发送的邮件
            userStopFlags.put(translatesDO.getShopName(), new AtomicBoolean(false));  // 初始化用户的停止标志

            //将该任务的状态改为0
            translateTasksService.update(new UpdateWrapper<TranslateTasksDO>().eq("status", 2).set("status", 0));
            //
//            //查找测试用户数据表
//            TestTableDO name = testTableMapper.selectOne(new QueryWrapper<TestTableDO>().eq("name", translatesDO.getShopName()));
//            if (name != null) {
//                translateTasksService.update(new UpdateWrapper<TranslateTasksDO>().eq("shop_name", translatesDO.getShopName()).eq("status",2).set("status", 0));
//                continue;
//            }
//            System.out.println("translatesDO: " + translatesDO);
//            translateStatus2WhenSystemRestartComplete(translatesDO);
        }
    }

    //服务器重启的完整翻译方法
    public void translateStatus2WhenSystemRestartComplete(TranslatesDO translatesDO) {
        //准备开始翻译，判断是否符合翻译条件
        //判断字符是否超限
        String shopName = translatesDO.getShopName();
        TranslationCounterDO request1 = translationCounterService.readCharsByShopName(shopName);
        Integer remainingChars = translationCounterService.getMaxCharsByShopName(shopName);
        int usedChars = request1.getUsedChars();
        // 如果字符超限，则直接返回字符超限

        if (usedChars < remainingChars) {
            //初始化计数器
            CharacterCountUtils counter = new CharacterCountUtils();
            counter.addChars(usedChars);
            //开始翻译状态为2的翻译
            //服务器重启后，将所有都翻译
            List<TranslateResourceDTO> list = ALL_RESOURCES.stream().toList();
            List<String> list1 = list.stream().map(TranslateResourceDTO::getResourceType).toList();
            translateService.startTranslation(new TranslateRequest(0, translatesDO.getShopName(), translatesDO.getAccessToken(), translatesDO.getSource(), translatesDO.getTarget(), null), remainingChars, counter, usedChars, false, list1, false);
        }
    }


    /**
     * 用户的自动翻译功能
     * 1，先判断该用户是否在翻译，如果在，放在最后
     * 2，再判断这些用户是否卸载了，卸载了就不管了
     * 3，再判断该用户剩余token数是否足够，不够就不管了
     * 4，再判断该用户是否正在翻译，正在翻译就不翻译了
     * 5，如果一个用户切换了本地语言，前后都设置了定时任务，只翻译最新的那个目标语言
     */
    @Async
    public void autoTranslate() {
        //获取所有使用自动翻译的用户
        List<TranslatesDO> translatesDOList = translatesService.readAllTranslates();
        for (TranslatesDO translatesDO : translatesDOList
        ) {

//            System.out.println("translatesDO: " + translatesDO);
            String shopName = translatesDO.getShopName();

            //判断这些用户是否卸载了，卸载了就不管了
            UsersDO usersDO = usersService.getUserByName(shopName);
            if (usersDO.getUninstallTime() != null) {
                //如果用户卸载了，但有登陆时间，需要判断两者的前后
                if (usersDO.getLoginTime() == null) {
//                    System.out.println("该用户已卸载，不翻译了");
                    continue;
                } else if (usersDO.getUninstallTime().after(usersDO.getLoginTime())) {
//                    System.out.println("该用户已卸载，不翻译了");
                    continue;
                }
            }

            //判断该用户剩余token数是否足够，不够就不管了
            //判断字符是否超限
            TranslationCounterDO request1 = translationCounterService.readCharsByShopName(shopName);
            Integer remainingChars = translationCounterService.getMaxCharsByShopName(shopName);
            int usedChars = request1.getUsedChars();
            // 如果字符超限，则直接返回字符超限
            if (usedChars >= remainingChars) {
//                System.out.println("该用户字符超限，不翻译了");
                continue;
            }

            //修改，发送到定时任务的队列里面
            //UTC每天凌晨1点翻译，且只翻译product模块
            //通过判断status和字符判断后 就将状态改为2，则开始翻译流程
            TaskTranslateDTO translateDTO = new TaskTranslateDTO(translatesDO.getStatus(),shopName, translatesDO.getAccessToken(), translatesDO.getSource(), translatesDO.getTarget());
            String json = objectToJson(translateDTO);
            translateTaskPublisherService.sendScheduledTranslateTask(json);
        }
    }


    public void freeTrialTask() {
        //获取所有免费计划不过期的用户
        List<UserTrialsDO> notTrialExpired = iUserTrialsService.list(new QueryWrapper<UserTrialsDO>().eq("is_trial_expired", false));
        if (notTrialExpired == null || notTrialExpired.isEmpty()) {
            return;
        }
        //循环检测是否过期
        for (UserTrialsDO userTrialsDO: notTrialExpired){
            //判断是否过期
            Timestamp now = new Timestamp(System.currentTimeMillis());
            Timestamp trialEnd = userTrialsDO.getTrialEnd();
            //如果用户购买计划，将状态改为true，然后跳过
            List<CharsOrdersDO> shopNameAndId = charsOrdersService.getCharsOrdersDoByShopName(userTrialsDO.getShopName());
            if (shopNameAndId != null && !shopNameAndId.isEmpty()) {
                userTrialsDO.setIsTrialExpired(true);
                iUserTrialsService.update(userTrialsDO, new UpdateWrapper<UserTrialsDO>().eq("id", userTrialsDO.getId()));
                continue;
            }

            // 如果 trialStart + 5天 小于 trialEnd，不做任何操作
            if (now.after(trialEnd)) {
                //如果过期，则将状态改为true
                userTrialsDO.setIsTrialExpired(true);
                try {
                    iUserTrialsService.update(userTrialsDO, new UpdateWrapper<UserTrialsDO>().eq("id", userTrialsDO.getId()));
                    //将用户计划改为2
                    iUserSubscriptionsService.update(new UpdateWrapper<UserSubscriptionsDO>().eq("shop_name", userTrialsDO.getShopName()).set("plan_id", 2));
                    //修改用户定时翻译任务
                    translatesService.update(new UpdateWrapper<TranslatesDO>().eq("shop_name", userTrialsDO.getShopName()).set("auto_translate", false));
                    //修改用户IP开关方法
                    iWidgetConfigurationsService.update(new UpdateWrapper<WidgetConfigurationsDO>().eq("shop_name", userTrialsDO.getShopName()).set("ip_open", false));
                    //词汇表改为0
                    iGlossaryService.update(new UpdateWrapper<GlossaryDO>().eq("shop_name", userTrialsDO.getShopName()).set("status", 0));
                } catch (Exception e) {
                    appInsights.trackTrace(userTrialsDO.getShopName() + "用户  errors 修改用户计划失败: " + e.getMessage());
                }
            }
        }
    }
}
