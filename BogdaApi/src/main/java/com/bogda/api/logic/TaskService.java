package com.bogda.api.logic;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.bogda.api.Service.*;
import com.bogda.api.entity.DO.*;
import com.bogda.api.logic.PCApp.PCEmailService;
import com.bogda.api.model.controller.request.UserPriceRequest;
import com.bogda.api.repository.entity.PCOrdersDO;
import com.bogda.api.repository.entity.PCSubscriptionQuotaRecordDO;
import com.bogda.api.repository.entity.PCUserTrialsDO;
import com.bogda.api.repository.repo.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static com.bogda.api.constants.TranslateConstants.*;
import static com.bogda.api.requestBody.ShopifyRequestBody.getSubscriptionQuery;
import static com.bogda.api.utils.CaseSensitiveUtils.appInsights;
import static com.bogda.api.utils.ShopifyUtils.isQueryValid;

@Component
public class TaskService {
    @Autowired
    private ICharsOrdersService charsOrdersService;
    @Autowired
    private IUsersService usersService;
    @Autowired
    private ITranslationCounterService translationCounterService;
    @Autowired
    private ISubscriptionPlansService subscriptionPlansService;
    @Autowired
    private OrderService orderService;
    @Autowired
    private ISubscriptionQuotaRecordService subscriptionQuotaRecordService;
    @Autowired
    private ITranslatesService translatesService;
    @Autowired
    private IUserTrialsService iUserTrialsService;
    @Autowired
    private IUserSubscriptionsService iUserSubscriptionsService;
    @Autowired
    private IWidgetConfigurationsService iWidgetConfigurationsService;
    @Autowired
    private IGlossaryService iGlossaryService;
    @Autowired
    private IUserIpService iUserIpService;
    @Autowired
    private TencentEmailService tencentEmailService;
    @Autowired
    private ShopifyService shopifyService;
    @Autowired
    private PCOrdersRepo pcOrdersRepo;
    @Autowired
    private PCUsersRepo pcUsersRepo;
    @Autowired
    private PCSubscriptionQuotaRecordRepo pcSubscriptionQuotaRecordRepo;
    @Autowired
    private PCSubscriptionsRepo pcSubscriptionsRepo;
    @Autowired
    private PCUserSubscriptionsRepo pcUserSubscriptionsRepo;
    @Autowired
    private PCUserTrialsRepo pcUserTrialsRepo;
    @Autowired
    private PCEmailService pcEmailService;

    /**
     * 异步调用根据订阅信息，判断是否添加额度的方法
     */
    public void judgeAddChars() {
        // 获取数据库中所有order为ACTIVE的id集合
        List<CharsOrdersDO> list = charsOrdersService.getShopNameAndId();
        List<UserPriceRequest> usedList = new ArrayList<>();
        for (CharsOrdersDO charsOrdersDO : list
        ) {
            if ("Starter".equals(charsOrdersDO.getName())) {
                continue;
            }

            // 根据shopName获取User表对应的accessToken，重新生成一个数据类型  判断是否是卸载，如果卸载， 不计算
            UsersDO usersDO = usersService.getOne(new QueryWrapper<UsersDO>().eq("shop_name", charsOrdersDO.getShopName()));
            if (usersDO == null) {
                continue;
            }
            if (usersDO.getUninstallTime() != null && usersDO.getLoginTime() != null && usersDO.getUninstallTime().after(usersDO.getLoginTime())) {
                continue;
            }

            // 根据shopName获取User表对应的accessToken，重新生成一个数据类型
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
                appInsights.trackException(e);
                appInsights.trackTrace("FatalException judgeAddChars 用户： " + userPriceRequest.getShopName() + " 获取数据 errors : " + e);
            }
        }

        // 判断计划是否过期，如果过期，将状态改为2
        judgeSubscriptionStatus(usedList);

    }

    // 判断计划是否过期，如果过期，将状态改为2
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
                JSONObject node = analyzeOrderData(order.getSubscriptionId(), order.getAccessToken(), order.getShopName());
                if (node == null) {
                    appInsights.trackTrace("judgeAddChars " + order.getShopName() + " 获取不到计划的相关数据，获取为null");
                    continue;
                }
                String status = node.getString("status");
                if (!"ACTIVE".equals(status)) {
                    //如果过期，将用户计划改为2
                    boolean i = iUserSubscriptionsService.checkUserPlan(order.getShopName(), 2) > 0;
                    appInsights.trackTrace("judgeAddChars " + order.getShopName() + " 计划过期，将用户计划改为2 " + " 修改状态: " + i);
                }
            } catch (Exception e) {
                appInsights.trackException(e);
                appInsights.trackTrace("FatalException judgeAddChars 用户： " + order.getShopName() + " 获取订阅计划数据 errors : " + e);
            }
        }
    }

    // 根据用户accessToken和订单id分析数据，获取数据
    public JSONObject analyzeOrderData(String subscriptionId, String accessToken, String shopName) {
        String query = getSubscriptionQuery(subscriptionId);
        String infoByShopify;

        // 根据新的集合获取这个订阅计划的信息
        infoByShopify = shopifyService.getShopifyData(shopName, accessToken, API_VERSION_LAST, query);

        JSONObject root = JSON.parseObject(infoByShopify);
        if (root == null || root.isEmpty()) {
            appInsights.trackTrace("FatalException " + shopName + " 定时任务根据订单id: " + subscriptionId + "获取数据失败" + " token: " + accessToken);
            return null;
        }
        JSONObject node = root.getJSONObject("node");
        if (node == null || node.isEmpty()) {
            // 用户卸载，计划会被取消，但不确定其他情况
            return null;
        }
        return node;
    }


    // 获取用户订阅选项并判断是否添加额度
    public void addCharsByUserData(UserPriceRequest userPriceRequest) {
        // 根据新的集合获取这个订阅计划的信息
        JSONObject node = analyzeOrderData(userPriceRequest.getSubscriptionId(), userPriceRequest.getAccessToken(), userPriceRequest.getShopName());
        if (node == null) {
            appInsights.trackTrace("addCharsByUserData 用户： " + userPriceRequest.getShopName() + " 获取不到计划的相关数据，获取为null " + userPriceRequest);
            return;
        }
        String name = node.getString("name");
        String status = node.getString("status");
        String createdAt = node.getString("createdAt");
        String currentPeriodEnd = node.getString("currentPeriodEnd");
        //用户购买订阅时间
        LocalDateTime buyCreate = userPriceRequest.getCreateAt();
        Instant buyCreateInstant = buyCreate.atZone(ZoneId.of("UTC")).toInstant();

        //订阅结束时间
        if (currentPeriodEnd == null) {
            appInsights.trackTrace("FatalException addCharsByUserData 用户： " + userPriceRequest.getShopName() + " 订阅结束时间为null : " + node);
            return;
        }
        Instant end = Instant.parse(currentPeriodEnd);
        LocalDateTime subEnd = end.atZone(ZoneOffset.UTC).toLocalDateTime();
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
            Boolean flag = translationCounterService.updateCharsByShopName(userPriceRequest.getShopName(), userPriceRequest.getAccessToken(), userPriceRequest.getSubscriptionId(), chars);
            appInsights.trackTrace("addCharsByUserData 用户： " + userPriceRequest.getShopName() + " 添加字符额度： " + chars + " 是否成功： " + flag);

            // 将用户免费Ip清零
            iUserIpService.clearIP(userPriceRequest.getShopName());

            // 修改该用户过期时间
            iUserSubscriptionsService.update(new LambdaUpdateWrapper<UserSubscriptionsDO>().eq(UserSubscriptionsDO::getShopName, userPriceRequest.getShopName()).set(UserSubscriptionsDO::getEndDate, subEnd));

            // 修改Translates表中，状态3 - 》 6
            translatesService.updateStatus3To6(userPriceRequest.getShopName());

            if ("Starter".equals(name)) {
                return;
            }
            tencentEmailService.sendSubscribeEmail(userPriceRequest.getShopName(), chars);
        }
    }

    //自动翻译模块顺序
    public static final List<String> AUTO_TRANSLATE_MAP = new ArrayList<>(Arrays.asList(
            SHOP, MENU, LINK, FILTER, PACKING_SLIP_TEMPLATE, DELIVERY_METHOD_DEFINITION, METAOBJECT, ONLINE_STORE_THEME_JSON_TEMPLATE, ONLINE_STORE_THEME_SECTION_GROUP,
            ONLINE_STORE_THEME_SETTINGS_CATEGORY, ONLINE_STORE_THEME_SETTINGS_DATA_SECTIONS, ONLINE_STORE_THEME_LOCALE_CONTENT,
            COLLECTION, PRODUCT, PRODUCT_OPTION, PRODUCT_OPTION_VALUE, BLOG, ARTICLE, PAGE, METAFIELD, SHOP_POLICY, EMAIL_TEMPLATE, SELLING_PLAN, SELLING_PLAN_GROUP
    ));

    // test自动翻译模块
    public static final List<String> TEST_AUTO_TRANSLATE_MAP = new ArrayList<>(Arrays.asList(
            ONLINE_STORE_THEME_JSON_TEMPLATE, ONLINE_STORE_THEME_SECTION_GROUP,
            ONLINE_STORE_THEME_SETTINGS_CATEGORY, ONLINE_STORE_THEME_SETTINGS_DATA_SECTIONS, ONLINE_STORE_THEME_LOCALE_CONTENT
    ));

    /**
     * 获取所有计划不过期的用户，判断是否过期
     */
    public void freeTrialTask() {
        // 获取所有免费计划不过期的用户
        List<UserTrialsDO> notTrialExpired = iUserTrialsService.list(new QueryWrapper<UserTrialsDO>().eq("is_trial_expired", false));
        if (notTrialExpired == null || notTrialExpired.isEmpty()) {
            return;
        }
        // 循环检测是否过期
        for (UserTrialsDO userTrialsDO : notTrialExpired) {
            // 判断是否过期
            Timestamp now = new Timestamp(System.currentTimeMillis());
            Timestamp trialEnd = userTrialsDO.getTrialEnd();

            // 如果这个用户没有过期，跳过
            if (now.before(trialEnd)) {
                continue;
            }
            String shopName = userTrialsDO.getShopName();

            // 如果 trialStart + 5天 小于 trialEnd，不做任何操作
            if (now.after(trialEnd)) {
                // 获取最新一条gid订单，判断是否支付成功
                String latestActiveSubscribeId = orderService.getLatestActiveSubscribeId(shopName);
                if (latestActiveSubscribeId == null) {
                    appInsights.trackTrace("freeTrialTask  latestActiveSubscribeId的数据为null，用户是：" + shopName);

                    // 将is_trial_expired改为true
                    iUserTrialsService.updateExpiredByShopName(shopName);
                    continue;
                }
                UsersDO usersDO = usersService.getOne(new LambdaQueryWrapper<UsersDO>().eq(UsersDO::getShopName, shopName));

                // 如果订单存在，并且支付成功，添加相关计划额度；如果订单不存在，说明他未支付，修改免费试用计划表
                String subscriptionQuery = getSubscriptionQuery(latestActiveSubscribeId);
                String shopifyByQuery = shopifyService.getShopifyData(shopName, usersDO.getAccessToken(), API_VERSION_LAST, subscriptionQuery);

                // 判断和解析相关数据
                JSONObject queryValid = isQueryValid(shopifyByQuery);
                if (queryValid == null) {
                    continue;
                }
                String name = queryValid.getString("name");
                Integer charsByPlanName = subscriptionPlansService.getCharsByPlanName(name);
                String status = queryValid.getString("status");
                if (!"ACTIVE".equals(status)) {
                    try {
                        // 将免费试用计划表里的状态改为true
                        iUserTrialsService.update(new LambdaUpdateWrapper<UserTrialsDO>().eq(UserTrialsDO::getShopName, shopName).set(UserTrialsDO::getIsTrialExpired, true));

                        // 将用户计划改为2
                        iUserSubscriptionsService.update(new UpdateWrapper<UserSubscriptionsDO>().eq("shop_name", userTrialsDO.getShopName()).set("plan_id", 2));

                        // 修改用户定时翻译任务
                        translatesService.update(new UpdateWrapper<TranslatesDO>().eq("shop_name", userTrialsDO.getShopName()).set("auto_translate", false));

                        // 修改用户IP开关方法
                        iWidgetConfigurationsService.update(new UpdateWrapper<WidgetConfigurationsDO>().eq("shop_name", userTrialsDO.getShopName()).set("ip_open", false));

                        // 词汇表改为0
                        iGlossaryService.update(new UpdateWrapper<GlossaryDO>().eq("shop_name", userTrialsDO.getShopName()).set("status", 0));
                    } catch (Exception e) {
                        appInsights.trackTrace("FatalException " + userTrialsDO.getShopName() + "用户  errors 修改用户计划失败: " + e.getMessage());
                    }
                    continue;
                }

                // 将免费试用计划表里的状态改为true
                iUserTrialsService.update(new LambdaUpdateWrapper<UserTrialsDO>().eq(UserTrialsDO::getShopName, shopName).set(UserTrialsDO::getIsTrialExpired, true));

                // 如果订单存在，并且支付成功，添加相关计划额度
                Boolean flag = translationCounterService.updateCharsByShopName(shopName, usersDO.getAccessToken(), latestActiveSubscribeId, charsByPlanName);
                appInsights.trackTrace(shopName + " 用户 添加额度成功 ： " + charsByPlanName + " 计划为： " + name + " 是否成功： " + flag);
            }
        }
    }

    public void judgePCAppAddChars() {
        // 获取数据库中所有order为ACTIVE的id集合
        List<PCOrdersDO> orderList = pcOrdersRepo.selectActiveOrders();
        List<UserPriceRequest> usedList = new ArrayList<>();
        for (PCOrdersDO pcOrdersDO : orderList) {
            // 根据shopName获取User表对应的accessToken
            PCUsersDO usersDO = pcUsersRepo.getUserByShopName(pcOrdersDO.getShopName());
            if (usersDO == null) {
                continue;
            }
            if (usersDO.getUninstallTime() != null && usersDO.getLoginTime() != null && usersDO.getUninstallTime().after(usersDO.getLoginTime())) {
                continue;
            }

            // 判断是否可以添加额度
            try {
                addPCCharsByUserData(usersDO.getShopName(), usersDO.getAccessToken(), pcOrdersDO.getOrderId(), pcOrdersDO.getCreatedAt());
            } catch (Exception e) {
                appInsights.trackException(e);
                appInsights.trackTrace("FatalException judgeAddChars 用户： " + pcOrdersDO.getShopName() + " 获取数据 errors : " + e);
            }
        }

        // 判断计划是否过期，如果过期，将状态改为2
        judgeSubscriptionStatus(usedList);
    }

    private void addPCCharsByUserData(String shopName, String accessToken, String subscriptionId, Timestamp userCreatedAt) {
        // 根据新的集合获取这个订阅计划的信息
        JSONObject node = analyzeOrderData(subscriptionId, accessToken, shopName);
        if (node == null) {
            appInsights.trackTrace("addCharsByUserData 用户： " + shopName + " 获取不到计划的相关数据，获取为null " + accessToken + " " + subscriptionId);
            return;
        }
        String name = node.getString("name");
        String status = node.getString("status");
        String currentPeriodEnd = node.getString("currentPeriodEnd");

        // 用户购买订阅时间
        Instant buyCreateInstant = userCreatedAt.toInstant();

        // 订阅结束时间
        Instant end = Instant.parse(currentPeriodEnd);
        Timestamp subEnd = Timestamp.from(end);

        // 当前时间
        Instant now = Instant.now();

        // 只处理活跃、非试用、且还在本期内的订阅
        if (!"ACTIVE".equals(status) || now.isAfter(end)) {
            appInsights.trackTrace("FatalException 不满足条件 只处理活跃、非试用、且还在本期内的订阅: " + shopName + " " + status + " " + end + " " + now);

            return;
        }

        // 计算当前是第几个月
        int billingCycle = (int) ChronoUnit.DAYS.between(buyCreateInstant, now) / 30 + 1;
        appInsights.trackTrace("PC billingCycle = " + billingCycle);

        // 如果这一周期还没发放过额度，则发放并记录
        PCSubscriptionQuotaRecordDO quotaRecordDO = pcSubscriptionQuotaRecordRepo.getQuotaRecordDO(subscriptionId, billingCycle);

        if (quotaRecordDO == null) {
            // 满足条件，执行添加字符的逻辑
            // 根据计划获取对应的字符
            Integer chars = pcSubscriptionsRepo.getCharsByPlanName(name);
            if (chars == null) {
                appInsights.trackTrace("FatalException test chars is null : " + shopName);
                return;
            }

            pcSubscriptionQuotaRecordRepo.insertQuotaRecord(subscriptionId, billingCycle);
            boolean flag = pcUsersRepo.updatePurchasePointsByShopName(shopName, accessToken, subscriptionId, chars);
            appInsights.trackTrace("PC addCharsByUserData 用户： " + shopName + " 添加字符额度： " + chars + " 是否成功： " + flag);

            // 修改该用户过期时间
            pcUserSubscriptionsRepo.updateUserEndDate(shopName, subEnd);

            // 发送邮件通知
            PCUsersDO userByShopName = pcUsersRepo.getUserByShopName(shopName);

            // 发送对应的邮件
            // 计算token转化为图片张数 除于2000
            String planPicChars = String.valueOf(chars / 2000);
            String allPicLimit = (userByShopName.getPurchasePoints() - userByShopName.getUsedPoints()) / 2000 + "";

            pcEmailService.sendPcFreeEmail(userByShopName.getEmail(), userByShopName.getFirstName(), name, planPicChars, allPicLimit);
        }
    }

    public void freeTrialTaskForImage() {
        // 获取所有免费计划不过期的用户
        List<PCUserTrialsDO> notTrialExpired = pcUserTrialsRepo.getNotExpiredTrialByShopName();
        if (notTrialExpired == null || notTrialExpired.isEmpty()) {
            return;
        }

        // 循环检测是否过期
        for (PCUserTrialsDO pcUserTrialsDO : notTrialExpired) {
            // 判断是否过期
            Timestamp now = new Timestamp(System.currentTimeMillis());
            Timestamp trialEnd = pcUserTrialsDO.getTrialEnd();

            // 如果这个用户没有过期，跳过
            if (now.before(trialEnd)) {
                continue;
            }
            String shopName = pcUserTrialsDO.getShopName();

            // 如果 trialStart + 5天 小于 trialEnd，不做任何操作
            if (now.after(trialEnd)) {
                // 获取最新一条gid订单，判断是否支付成功
                String latestActiveSubscribeId = pcOrdersRepo.getLatestActiveSubscribeId(shopName);
                if (latestActiveSubscribeId == null) {
                    appInsights.trackTrace("PC freeTrialTask latestActiveSubscribeId的数据为null，用户是：" + shopName);

                    // 将数据改为true
                    pcUserTrialsRepo.updateTrialExpiredByShopName(shopName, true);
                    continue;
                }
                PCUsersDO usersDO = pcUsersRepo.getUserByShopName(shopName);

                // 如果订单存在，并且支付成功，添加相关计划额度；如果订单不存在，说明他未支付，修改免费试用计划表
                String subscriptionQuery = getSubscriptionQuery(latestActiveSubscribeId);
                String shopifyByQuery = shopifyService.getShopifyData(shopName, usersDO.getAccessToken(), API_VERSION_LAST, subscriptionQuery);

                // 判断和解析相关数据
                JSONObject queryValid = isQueryValid(shopifyByQuery);
                if (queryValid == null) {
                    continue;
                }
                System.out.println("queryValid  " + queryValid);
                String name = queryValid.getString("name");
                Integer charsByPlanName = pcSubscriptionsRepo.getCharsByPlanName(name);

                String status = queryValid.getString("status");
                if (!"ACTIVE".equals(status)) {
                    try {
                        // 将免费试用计划表里的状态改为true
                        pcUserTrialsRepo.updateTrialExpiredByShopName(shopName, true);

                        // 将用户计划改为1 免费计划
                        pcUserSubscriptionsRepo.updateUserPlanIdByShopName(shopName, 1);
                    } catch (Exception e) {
                        appInsights.trackTrace("FatalException " + shopName + " 用户  errors PC 修改用户计划失败: " + e.getMessage());
                    }
                    continue;
                }

                // 将免费试用计划表里的状态改为true
                pcUserTrialsRepo.updateTrialExpiredByShopName(shopName, true);

                // 如果订单存在，并且支付成功，添加相关计划额度
                boolean flag = pcUsersRepo.updatePurchasePoints(shopName, charsByPlanName);
                appInsights.trackTrace(shopName + " 用户 PC 添加额度成功 ： " + charsByPlanName + " 计划为： " + name + " 是否成功： " + flag);
            }
        }
    }
}
