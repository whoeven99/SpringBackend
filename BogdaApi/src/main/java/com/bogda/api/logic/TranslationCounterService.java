package com.bogda.api.logic;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.bogda.api.Service.*;
import com.bogda.api.entity.DO.*;
import com.bogda.api.entity.VO.TranslationCharsVO;
import com.bogda.api.logic.redis.OrdersRedisService;
import com.bogda.common.contants.TranslateConstants;
import com.bogda.common.utils.AppInsightsUtils;
import com.bogda.common.utils.ShopifyRequestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

import static com.bogda.api.utils.ShopifyUtils.isQueryValid;

@Component
public class TranslationCounterService {
    @Autowired
    private ICharsOrdersService iCharsOrdersService;
    @Autowired
    private ISubscriptionPlansService iSubscriptionPlansService;
    @Autowired
    private ITranslationCounterService iTranslationCounterService;
    @Autowired
    private IUserTrialsService iUserTrialsService;
    @Autowired
    private IUsersService iUsersService;
    @Autowired
    private IUserSubscriptionsService iUserSubscriptionsService;
    @Autowired
    private OrdersRedisService ordersRedisService;
    @Autowired
    private ShopifyService shopifyService;
    @Autowired
    private IUserIpService iUserIpService;

    public final String ACTIVE = "ACTIVE";

    public Boolean updateOnceCharsByShopName(String shopName, String accessToken, String gid, Integer chars) {
        // 添加订单标识
        ordersRedisService.setOrderId(shopName, gid);

        //根据gid，判断是否符合添加额度的条件
        AppInsightsUtils.trackTrace("updateCharsByShopName 用户： " + shopName + " gid: " + gid + " chars: " + chars + " accessToken: " + accessToken);

        return iTranslationCounterService.update(new LambdaUpdateWrapper<TranslationCounterDO>().eq(TranslationCounterDO::getShopName, shopName).setSql("chars = chars + " + chars));
    }

    public Boolean addCharsByShopNameAfterSubscribe(String shopName, TranslationCharsVO translationCharsVO) {
        //获取该用户的accessToken
        UsersDO userByName = iUsersService.getOne(new LambdaQueryWrapper<UsersDO>().eq(UsersDO::getShopName, shopName));
        translationCharsVO.setAccessToken(userByName.getAccessToken());
        //根据传来的gid获取，相关订阅信息
        String subscriptionQuery = ShopifyRequestUtils.getSubscriptionQuery(translationCharsVO.getSubGid());
        String shopifyByQuery = shopifyService.getShopifyData(shopName, userByName.getAccessToken(), TranslateConstants.API_VERSION_LAST, subscriptionQuery);
        AppInsightsUtils.trackTrace("addCharsByShopNameAfterSubscribe " + shopName + " 用户 订阅信息 ：" + shopifyByQuery);
        //判断和解析相关数据
        JSONObject queryValid = isQueryValid(shopifyByQuery);
        if (queryValid == null) {
            return null;
        }

        //获取用户订阅计划表的相关数据，与下面数据进行判断
        CharsOrdersDO charsOrdersDO = iCharsOrdersService.getOne(new LambdaQueryWrapper<CharsOrdersDO>().eq(CharsOrdersDO::getId, translationCharsVO.getSubGid()));
        AppInsightsUtils.trackTrace("addCharsByShopNameAfterSubscribe " + shopName + " 用户 订阅计划表 ：" + charsOrdersDO.toString());
        String name = queryValid.getString("name");
        String status = queryValid.getString("status");
        Integer trialDays = queryValid.getInteger("trialDays");
        AppInsightsUtils.trackTrace("addCharsByShopNameAfterSubscribe " + shopName + " 用户 免费试用天数 ：" + trialDays + " name: " + name + " status: " + status);
        Integer charsByPlanName = iSubscriptionPlansService.getCharsByPlanName(name);
        if (name.equals(charsOrdersDO.getName()) && status.equals(charsOrdersDO.getStatus()) && trialDays > 0) {
            AppInsightsUtils.trackTrace("addCharsByShopNameAfterSubscribe " + shopName + " 用户 第一次免费试用 ：" + translationCharsVO.getSubGid());
            String currentPeriodEnd = queryValid.getString("currentPeriodEnd");
            //修改用户过期时间  和  费用类型
            //订阅结束时间
            if (currentPeriodEnd != null) {
                Instant end = Instant.parse(currentPeriodEnd);
                LocalDateTime subEnd = end.atZone(ZoneOffset.UTC).toLocalDateTime();
                iUserSubscriptionsService.update(new LambdaUpdateWrapper<UserSubscriptionsDO>().eq(UserSubscriptionsDO::getShopName, shopName).set(UserSubscriptionsDO::getFeeType, translationCharsVO.getFeeType()).set(UserSubscriptionsDO::getEndDate, subEnd));
            }

            // 不添加额度, 但需要修改免费试用订阅表
            String createdAt = queryValid.getString("createdAt");

            // 用户购买订阅时间
            Instant begin = Instant.parse(createdAt);
            Timestamp beginTimestamp = Timestamp.from(begin);

            // 试用结束时间
            Instant afterTrialDaysDays = begin.plus(trialDays, ChronoUnit.DAYS);
            Timestamp afterTrialDaysTimestamp = Timestamp.from(afterTrialDaysDays);

            // 获取用户是否已经是免费试用，是的话，将false改为true
            UserTrialsDO userTrialsDO = iUserTrialsService.getOne(new LambdaQueryWrapper<UserTrialsDO>().eq(UserTrialsDO::getShopName, shopName));
            if (userTrialsDO == null) {
                iUserTrialsService.save(new UserTrialsDO(null, shopName, beginTimestamp, afterTrialDaysTimestamp, false, null));

                // 修改额度表里面数据，用于该用户卸载，和扣额度. 暂定openaiChar为1是免费试用
                // 同时修改额度表里面100w字符（暂定），在计划表里
                Integer charsByPlan = iSubscriptionPlansService.getCharsByPlanName("Gift Amount");
                boolean update = iTranslationCounterService.update(new LambdaUpdateWrapper<TranslationCounterDO>().eq(TranslationCounterDO::getShopName, shopName).set(TranslationCounterDO::getGoogleChars, charsByPlan + 200000).set(TranslationCounterDO::getOpenAiChars, 1).setSql("chars = chars + " + charsByPlan));

                // 初始化ip，或将ip数清零
                iUserIpService.addOrUpdateUserIp(shopName);

                // 将ip额度清零
                iUserIpService.clearIP(shopName);
                AppInsightsUtils.trackTrace("addCharsByShopNameAfterSubscribe " + shopName + " 用户 免费试用额度添加 ：" + charsByPlan + " 是否成功： " + update);
                return update;
            }
        } else {
            iUserTrialsService.update(new LambdaUpdateWrapper<UserTrialsDO>().eq(UserTrialsDO::getShopName, shopName).set(UserTrialsDO::getIsTrialExpired, true));
        }

        //添加额度
        AppInsightsUtils.trackTrace("addCharsByShopNameAfterSubscribe " + shopName + " 用户 计划名 ：" + charsOrdersDO.getName() + " name: " + name + " status: " + status);
        if (name.equals(charsOrdersDO.getName()) && status.equals(ACTIVE)) {
            //根据用户的计划添加对应的额度
            return iTranslationCounterService.updateCharsByShopName(shopName, translationCharsVO.getAccessToken(), translationCharsVO.getSubGid(), charsByPlanName);
        }

        return null;
    }
}
