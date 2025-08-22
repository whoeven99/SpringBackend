package com.bogdatech.logic;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.bogdatech.Service.*;
import com.bogdatech.entity.DO.*;
import com.bogdatech.model.controller.request.*;
import com.bogdatech.utils.CharacterCountUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
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

import static com.bogdatech.constants.TranslateConstants.*;
import static com.bogdatech.entity.DO.TranslateResourceDTO.AUTO_TRANSLATE_MAP;
import static com.bogdatech.integration.ShopifyHttpIntegration.getInfoByShopify;
import static com.bogdatech.logic.ShopifyService.getShopifyDataByCloud;
import static com.bogdatech.logic.TranslateService.userEmailStatus;
import static com.bogdatech.logic.TranslateService.userStopFlags;
import static com.bogdatech.requestBody.ShopifyRequestBody.getSubscriptionQuery;
import static com.bogdatech.utils.CaseSensitiveUtils.appInsights;
import static com.bogdatech.utils.ShopifyUtils.getShopifyByQuery;
import static com.bogdatech.utils.ShopifyUtils.isQueryValid;

@Component
public class TaskService {
    private final ICharsOrdersService charsOrdersService;
    private final IUsersService usersService;
    private final ITranslationCounterService translationCounterService;
    private final ISubscriptionPlansService subscriptionPlansService;
    private final OrderService orderService;
    private final ISubscriptionQuotaRecordService subscriptionQuotaRecordService;
    private final ITranslatesService translatesService;
    private final IUserTrialsService iUserTrialsService;
    private final IUserSubscriptionsService iUserSubscriptionsService;
    private final IWidgetConfigurationsService iWidgetConfigurationsService;
    private final IGlossaryService iGlossaryService;
    private final IUserIpService iUserIpService;
    private final ITranslateTasksService translateTasksService;
    private final TencentEmailService tencentEmailService;
    private final RabbitMqTranslateService rabbitMqTranslateService;
    private final ITranslationUsageService iTranslationUsageService;

    @Autowired
    public TaskService(ICharsOrdersService charsOrdersService, IUsersService usersService, ITranslationCounterService translationCounterService, ISubscriptionPlansService subscriptionPlansService, OrderService orderService, ISubscriptionQuotaRecordService subscriptionQuotaRecordService, ITranslatesService translatesService, IUserTrialsService iUserTrialsService, IUserSubscriptionsService iUserSubscriptionsService, IWidgetConfigurationsService iWidgetConfigurationsService, IGlossaryService iGlossaryService, IUserIpService iUserIpService, ITranslateTasksService translateTasksService, TencentEmailService tencentEmailService, RabbitMqTranslateService rabbitMqTranslateService, ITranslationUsageService iTranslationUsageService) {
        this.charsOrdersService = charsOrdersService;
        this.usersService = usersService;
        this.translationCounterService = translationCounterService;
        this.subscriptionPlansService = subscriptionPlansService;
        this.orderService = orderService;
        this.subscriptionQuotaRecordService = subscriptionQuotaRecordService;
        this.translatesService = translatesService;
        this.iUserTrialsService = iUserTrialsService;
        this.iUserSubscriptionsService = iUserSubscriptionsService;
        this.iWidgetConfigurationsService = iWidgetConfigurationsService;
        this.iGlossaryService = iGlossaryService;
        this.iUserIpService = iUserIpService;
        this.translateTasksService = translateTasksService;
        this.tencentEmailService = tencentEmailService;
        this.rabbitMqTranslateService = rabbitMqTranslateService;
        this.iTranslationUsageService = iTranslationUsageService;
    }

    //异步调用根据订阅信息，判断是否添加额度的方法
    @Async
    public void judgeAddChars() {
        //获取数据库中所有order为ACTIVE的id集合
        List<CharsOrdersDO> list = charsOrdersService.getShopNameAndId();
        List<UserPriceRequest> usedList = new ArrayList<>();
        for (CharsOrdersDO charsOrdersDO : list
        ) {
            if ("Starter".equals(charsOrdersDO.getName())) {
                continue;
            }
            //根据shopName获取User表对应的accessToken，重新生成一个数据类型  判断是否是卸载，如果卸载， 不计算
            UsersDO usersDO = usersService.getOne(new QueryWrapper<UsersDO>().eq("shop_name", charsOrdersDO.getShopName()));
            if (usersDO == null) {
                continue;
            }
            if (usersDO.getUninstallTime() != null && usersDO.getLoginTime() != null && usersDO.getUninstallTime().after(usersDO.getLoginTime())) {
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
                if (!"ACTIVE".equals(status)) {
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
            infoByShopify = String.valueOf(getInfoByShopify(new ShopifyRequest(userPriceRequest.getShopName(), userPriceRequest.getAccessToken(), API_VERSION_LAST, null), query));
        } else {
            infoByShopify = getShopifyDataByCloud(new CloudServiceRequest(userPriceRequest.getShopName(), userPriceRequest.getAccessToken(), API_VERSION_LAST, "en", query));
        }

        JSONObject root = JSON.parseObject(infoByShopify);
        if (root == null || root.isEmpty()) {
            appInsights.trackTrace(userPriceRequest.getShopName() + " 定时任务根据订单id: " + userPriceRequest.getSubscriptionId() + "获取数据失败" + " token: " + userPriceRequest.getAccessToken());
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
//            appInsights.trackTrace("不满足条件");
            return;
        }

        //计算当前是第几个月
        int billingCycle = (int) ChronoUnit.DAYS.between(buyCreateInstant, now) / 30 + 1;
//        appInsights.trackTrace("billingCycle = " + billingCycle);

        // 如果这一周期还没发放过额度，则发放并记录
        SubscriptionQuotaRecordDO quotaRecordDO = subscriptionQuotaRecordService.getOne(new QueryWrapper<SubscriptionQuotaRecordDO>().eq("subscription_id", userPriceRequest.getSubscriptionId()).eq("billing_cycle", billingCycle));
        if (quotaRecordDO == null) {
            // 满足条件，执行添加字符的逻辑
//            appInsights.trackTrace("满足条件，执行添加字符的逻辑");
            // 根据计划获取对应的字符
            Integer chars = subscriptionPlansService.getCharsByPlanName(name);
            subscriptionQuotaRecordService.insertOne(userPriceRequest.getSubscriptionId(), billingCycle);
            appInsights.trackTrace("用户： " + userPriceRequest.getShopName() + " 添加字符额度： " + chars);
            translationCounterService.updateCharsByShopName(new TranslationCounterRequest(0, userPriceRequest.getShopName(), chars, 0, 0, 0, 0));
            //将用户免费Ip清零
            iUserIpService.update(new UpdateWrapper<UserIpDO>().eq("shop_name", userPriceRequest.getShopName()).set("times", 0).set("first_email", 0).set("second_email", 0));
            if ("Starter".equals(name)) {
                return;
            }
            tencentEmailService.sendSubscribeEmail(userPriceRequest.getShopName(), chars);
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
        if (translatesDOList.isEmpty()){
            return;
        }
        for (TranslatesDO translatesDO : translatesDOList
        ) {
//            appInsights.trackTrace("translatesDO: " + translatesDO);
            String shopName = translatesDO.getShopName();

            //判断这些用户是否卸载了，卸载了就不管了
            UsersDO usersDO = usersService.getUserByName(shopName);
            if (usersDO == null){
                continue;
            }
            if (usersDO.getUninstallTime() != null) {
                //如果用户卸载了，但有登陆时间，需要判断两者的前后
                if (usersDO.getLoginTime() == null) {
                    continue;
                } else if (usersDO.getUninstallTime().after(usersDO.getLoginTime())) {
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
//                appInsights.trackTrace("该用户字符超限，不翻译了");
                continue;
            }

            //判断这条翻译项在Usage表中是否存在，在的话跳过，不在的话插入
            TranslationUsageDO usageServiceOne = iTranslationUsageService.getOne(new LambdaQueryWrapper<TranslationUsageDO>().eq(TranslationUsageDO::getShopName, shopName).eq(TranslationUsageDO::getLanguageName, translatesDO.getTarget()));
            if (usageServiceOne == null) {
                iTranslationUsageService.save(new TranslationUsageDO(translatesDO.getId(), translatesDO.getShopName(), translatesDO.getTarget(), 0, 0, 0, 0));
            }

            //将任务存到数据库等待翻译
            //初始化用户状态
            userEmailStatus.put(translatesDO.getShopName(), new AtomicBoolean(false)); //重置用户发送的邮件
            userStopFlags.put(translatesDO.getShopName(), new AtomicBoolean(false));  // 初始化用户的停止标志

            //初始化计数器
            CharacterCountUtils counter = new CharacterCountUtils();
            counter.addChars(usedChars);

            //调用DB翻译逻辑
            rabbitMqTranslateService.mqTranslate(new ShopifyRequest(shopName, translatesDO.getAccessToken(), API_VERSION_LAST
                            , translatesDO.getTarget()), counter, AUTO_TRANSLATE_MAP
                    , new TranslateRequest(0, shopName, translatesDO.getAccessToken(), translatesDO.getSource(), translatesDO.getTarget(), null)
                    , remainingChars, usedChars, false, "1", false, EMAIL_TRANSLATE, false);


//            TaskTranslateDTO translateDTO = new TaskTranslateDTO(translatesDO.getStatus(),shopName, translatesDO.getAccessToken(), translatesDO.getSource(), translatesDO.getTarget());
//            String json = objectToJson(translateDTO);
//            translateTaskPublisherService.sendScheduledTranslateTask(json);
        }
    }

    /**
     * 获取所有计划不过期的用户，判断是否过期
     */
    public void freeTrialTask() {
        //获取所有免费计划不过期的用户
        List<UserTrialsDO> notTrialExpired = iUserTrialsService.list(new QueryWrapper<UserTrialsDO>().eq("is_trial_expired", false));
        if (notTrialExpired == null || notTrialExpired.isEmpty()) {
            return;
        }
        //循环检测是否过期
        for (UserTrialsDO userTrialsDO : notTrialExpired) {
            //判断是否过期
            Timestamp now = new Timestamp(System.currentTimeMillis());
            Timestamp trialEnd = userTrialsDO.getTrialEnd();

            //如果这个用户没有过期，跳过
            if (now.before(trialEnd)) {
                continue;
            }
            String shopName = userTrialsDO.getShopName();

            // 如果 trialStart + 5天 小于 trialEnd，不做任何操作
            if (now.after(trialEnd)) {
                //获取最新一条gid订单，判断是否支付成功
                String latestActiveSubscribeId = orderService.getLatestActiveSubscribeId(shopName);
                UsersDO usersDO = usersService.getOne(new LambdaQueryWrapper<UsersDO>().eq(UsersDO::getShopName, shopName));
                //如果订单存在，并且支付成功，添加相关计划额度；如果订单不存在，说明他未支付，修改免费试用计划表
                String subscriptionQuery = getSubscriptionQuery(latestActiveSubscribeId);
                String shopifyByQuery = getShopifyByQuery(subscriptionQuery, shopName, usersDO.getAccessToken());
                //判断和解析相关数据
                JSONObject queryValid = isQueryValid(shopifyByQuery);
                if (queryValid == null) {
                    continue;
                }

                String status = queryValid.getString("status");
                if (!"ACTIVE".equals(status)) {
                    try {
                        //将免费试用计划表里的状态改为true
                        iUserTrialsService.update(new LambdaUpdateWrapper<UserTrialsDO>().eq(UserTrialsDO::getShopName, shopName).set(UserTrialsDO::getIsTrialExpired, true));
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
                    continue;
                }

                //如果订单存在，并且支付成功，添加相关计划额度
                String name = queryValid.getString("name");
                Integer charsByPlanName = subscriptionPlansService.getCharsByPlanName(name);
                Integer maxCharsByShopName = translationCounterService.getMaxCharsByShopName(shopName);
                translationCounterService.updateAddUsedCharsByShopName(shopName, charsByPlanName, maxCharsByShopName);
                appInsights.trackTrace(shopName + " 用户 添加额度成功 ： " + charsByPlanName + " 计划为： " + name);
            }
        }
    }

    /**
     * 获取所有的自动翻译用户，初始化用户状态
     */
    @PostConstruct
    public void initUserStatus() {
        //获取所有使用自动翻译的用户
        List<TranslatesDO> translatesDOList = translatesService.readAllTranslates();
        for (TranslatesDO translatesDO : translatesDOList
        ) {
            String shopName = translatesDO.getShopName();

            //判断这些用户是否卸载了，卸载了就不管了
            UsersDO usersDO = usersService.getOne(new LambdaQueryWrapper<UsersDO>().eq(UsersDO::getShopName, shopName));
            if (usersDO == null) {
                appInsights.trackTrace("shopName: " + shopName);
                continue;
            }
            if (usersDO.getUninstallTime() != null) {
                //如果用户卸载了，但有登陆时间，需要判断两者的前后
                if (usersDO.getLoginTime() == null) {
                    continue;
                } else if (usersDO.getUninstallTime().after(usersDO.getLoginTime())) {
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
                continue;
            }

            //初始化用户状态
            userEmailStatus.put(translatesDO.getShopName(), new AtomicBoolean(false)); //重置用户发送的邮件
            userStopFlags.put(translatesDO.getShopName(), new AtomicBoolean(false));  // 初始化用户的停止标志
        }
    }
}
