package com.bogdatech.logic;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bogdatech.Service.*;
import com.bogdatech.entity.DO.CharsOrdersDO;
import com.bogdatech.entity.DO.UserTrialsDO;
import com.bogdatech.entity.DO.UsersDO;
import com.bogdatech.entity.VO.TranslationCharsVO;
import com.bogdatech.model.controller.request.TranslationCounterRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
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

    public final String ACTIVE = "ACTIVE";
    @Autowired
    public TranslationCounterService(ICharsOrdersService iCharsOrdersService, ISubscriptionPlansService iSubscriptionPlansService, ITranslationCounterService iTranslationCounterService, IUserTrialsService iUserTrialsService, IUsersService iUsersService) {
        this.iCharsOrdersService = iCharsOrdersService;
        this.iSubscriptionPlansService = iSubscriptionPlansService;
        this.iTranslationCounterService = iTranslationCounterService;
        this.iUserTrialsService = iUserTrialsService;
        this.iUsersService = iUsersService;
    }

    public Boolean addCharsByShopNameAfterSubscribe(String shopName, TranslationCharsVO translationCharsVO) {
        //获取该用户的accessToken
        UsersDO userByName = iUsersService.getOne(new LambdaQueryWrapper<UsersDO>().eq(UsersDO::getShopName, shopName));
        //根据传来的gid获取，相关订阅信息
        String subscriptionQuery = getSubscriptionQuery(translationCharsVO.getSubGid());
        String shopifyByQuery = getShopifyByQuery(subscriptionQuery, shopName, userByName.getAccessToken());
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
        appInsights.trackTrace(shopName + " 用户 免费试用天数 ：" + trialDays);
        if (name.equals(charsOrdersDO.getName()) && status.equals(charsOrdersDO.getStatus()) && trialDays > 0){
            appInsights.trackTrace(shopName + " 用户 第一次免费试用 ：" + translationCharsVO.getSubGid());
            // 不添加额度, 但需要修改免费试用订阅表
            String createdAt = queryValid.getString("createdAt");
//            String currentPeriodEnd = queryValid.getString("currentPeriodEnd");
            //用户购买订阅时间
            Instant begin = Instant.parse(createdAt);
            Timestamp beginTimestamp = Timestamp.from(begin);
//            Instant end = Instant.parse(currentPeriodEnd);
            //试用结束时间
            Instant afterTrialDaysDays = begin.plus(trialDays, ChronoUnit.DAYS);
            Timestamp afterTrialDaysTimestamp = Timestamp.from(afterTrialDaysDays);
            return iUserTrialsService.save(new UserTrialsDO(null, shopName, beginTimestamp, afterTrialDaysTimestamp, false));
        }

        //添加额度
        if (name.equals(charsOrdersDO.getName()) && status.equals(ACTIVE)){
            //根据用户的计划添加对应的额度
            Integer charsByPlanName = iSubscriptionPlansService.getCharsByPlanName(name);
            return iTranslationCounterService.updateCharsByShopName(new TranslationCounterRequest(0, shopName, charsByPlanName, 0 , 0 , 0, 0));
        }
        return null;
    }
}
