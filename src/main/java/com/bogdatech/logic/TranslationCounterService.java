package com.bogdatech.logic;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.bogdatech.Service.*;
import com.bogdatech.entity.DO.*;
import com.bogdatech.entity.VO.AddCharsVO;
import com.bogdatech.entity.VO.TranslationCharsVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import static com.bogdatech.requestBody.ShopifyRequestBody.getSubscriptionQuery;
import static com.bogdatech.utils.CaseSensitiveUtils.appInsights;
import static com.bogdatech.utils.ShopifyUtils.getShopifyByQuery;
import static com.bogdatech.utils.ShopifyUtils.isQueryValid;

@Component
public class TranslationCounterService {

    private final ICharsOrdersService iCharsOrdersService;
    private final ISubscriptionPlansService iSubscriptionPlansService;
    private final ITranslationCounterService iTranslationCounterService;
    private final IUserTrialsService iUserTrialsService;
    private final IUsersService iUsersService;
    private final IUserSubscriptionsService iUserSubscriptionsService;

    public final String ACTIVE = "ACTIVE";

    @Autowired
    public TranslationCounterService(ICharsOrdersService iCharsOrdersService, ISubscriptionPlansService iSubscriptionPlansService, ITranslationCounterService iTranslationCounterService, IUserTrialsService iUserTrialsService, IUsersService iUsersService, IUserSubscriptionsService iUserSubscriptionsService) {
        this.iCharsOrdersService = iCharsOrdersService;
        this.iSubscriptionPlansService = iSubscriptionPlansService;
        this.iTranslationCounterService = iTranslationCounterService;
        this.iUserTrialsService = iUserTrialsService;
        this.iUsersService = iUsersService;
        this.iUserSubscriptionsService = iUserSubscriptionsService;
    }

    public Boolean addCharsByShopNameAfterSubscribe(String shopName, TranslationCharsVO translationCharsVO) {
        //获取该用户的accessToken
        UsersDO userByName = iUsersService.getOne(new LambdaQueryWrapper<UsersDO>().eq(UsersDO::getShopName, shopName));
        //根据传来的gid获取，相关订阅信息
        String subscriptionQuery = getSubscriptionQuery(translationCharsVO.getSubGid());
        String shopifyByQuery = getShopifyByQuery(subscriptionQuery, shopName, userByName.getAccessToken());
        appInsights.trackTrace("addCharsByShopNameAfterSubscribe " + shopName + " 用户 订阅信息 ：" + shopifyByQuery);
        //判断和解析相关数据
        JSONObject queryValid = isQueryValid(shopifyByQuery);
        if (queryValid == null) {
            return null;
        }

        //获取用户订阅计划表的相关数据，与下面数据进行判断
        CharsOrdersDO charsOrdersDO = iCharsOrdersService.getOne(new LambdaQueryWrapper<CharsOrdersDO>().eq(CharsOrdersDO::getId, translationCharsVO.getSubGid()));
        String name = queryValid.getString("name");
        String status = queryValid.getString("status");
        Integer trialDays = queryValid.getInteger("trialDays");
        appInsights.trackTrace("addCharsByShopNameAfterSubscribe " + shopName + " 用户 免费试用天数 ：" + trialDays);
        Integer charsByPlanName = iSubscriptionPlansService.getCharsByPlanName(name);
        if (name.equals(charsOrdersDO.getName()) && status.equals(charsOrdersDO.getStatus()) && trialDays > 0) {
            appInsights.trackTrace("addCharsByShopNameAfterSubscribe " + shopName + " 用户 第一次免费试用 ：" + translationCharsVO.getSubGid());
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
            //用户购买订阅时间
            Instant begin = Instant.parse(createdAt);
            Timestamp beginTimestamp = Timestamp.from(begin);
            //试用结束时间
            Instant afterTrialDaysDays = begin.plus(trialDays, ChronoUnit.DAYS);
            Timestamp afterTrialDaysTimestamp = Timestamp.from(afterTrialDaysDays);
            // 获取用户是否已经是免费试用，是的话，将false改为true
            UserTrialsDO userTrialsDO = iUserTrialsService.getOne(new LambdaQueryWrapper<UserTrialsDO>().eq(UserTrialsDO::getShopName, shopName));
            if (userTrialsDO == null) {
                iUserTrialsService.save(new UserTrialsDO(null, shopName, beginTimestamp, afterTrialDaysTimestamp, false));
                //修改额度表里面数据，用于该用户卸载，和扣额度. 暂定openaiChar为1是免费试用
                //同时修改额度表里面100w字符（暂定），在计划表里
                Integer charsByPlan = iSubscriptionPlansService.getCharsByPlanName("Gift Amount");
                boolean update = iTranslationCounterService.update(new LambdaUpdateWrapper<TranslationCounterDO>().eq(TranslationCounterDO::getShopName, shopName).set(TranslationCounterDO::getGoogleChars, charsByPlan + 200000).set(TranslationCounterDO::getOpenAiChars, 1).setSql("chars = chars + " + charsByPlan));
                appInsights.trackTrace("addCharsByShopNameAfterSubscribe " + shopName + " 用户 免费试用额度添加 ：" + charsByPlan + " 是否成功： " + update);
                return update;
            }
        } else {
            iUserTrialsService.update(new LambdaUpdateWrapper<UserTrialsDO>().eq(UserTrialsDO::getShopName, shopName).set(UserTrialsDO::getIsTrialExpired, true));
        }

        //添加额度
        if (name.equals(charsOrdersDO.getName()) && status.equals(ACTIVE)) {
            //根据用户的计划添加对应的额度
            return iTranslationCounterService.updateCharsByShopName(shopName, translationCharsVO.getAccessToken(), translationCharsVO.getSubGid(), charsByPlanName);
        }

        return null;
    }
}
