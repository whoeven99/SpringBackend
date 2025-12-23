package com.bogda.api.logic.PCApp;

import com.alibaba.fastjson.JSONObject;
import com.bogda.api.entity.DO.PCUsersDO;
import com.bogda.api.entity.VO.PCUserPointsVO;
import com.bogda.api.entity.VO.TranslationCharsVO;
import com.bogda.api.logic.ShopifyService;
import com.bogda.api.logic.redis.OrdersRedisService;
import com.bogda.api.model.controller.response.BaseResponse;
import com.bogda.api.repository.entity.PCOrdersDO;
import com.bogda.api.repository.entity.PCUserTrialsDO;
import com.bogda.api.repository.repo.*;
import com.bogda.api.requestBody.ShopifyRequestBody;
import com.bogda.api.utils.ShopifyUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static com.bogda.api.constants.TranslateConstants.API_VERSION_LAST;
import static com.bogda.api.utils.CaseSensitiveUtils.appInsights;

@Component
public class PCUsersService {
    @Autowired
    private PCUsersRepo pcUsersRepo;
    @Autowired
    private PCUserSubscriptionsRepo pcUserSubscriptionsRepo;
    @Autowired
    private ShopifyService shopifyService;
    @Autowired
    private PCOrdersRepo pcOrdersRepo;
    @Autowired
    private PCSubscriptionsRepo pcSubscriptionsRepo;
    @Autowired
    private PCUserTrialsRepo pcUserTrialsRepo;
    @Autowired
    private OrdersRedisService ordersRedisService;

    public void initUser(String shopName, PCUsersDO pcUsersDO) {
        // 获取用户是否存在 ，存在，做更新操作； 不存在，存储用户
        PCUsersDO pcUsers = pcUsersRepo.getUserByShopName(shopName);
        if (pcUsers == null) {
            pcUsersDO.setPurchasePoints(0);
            pcUsersDO.setUsedPoints(0);
            Timestamp now = Timestamp.from(Instant.now());
            pcUsersDO.setCreateAt(now);
            pcUsersDO.setLoginTime(now);
            pcUsersDO.setPurchasePoints(20000);
            pcUsersRepo.saveSingleUser(pcUsersDO);
            pcUserSubscriptionsRepo.insertUserSubscriptions(shopName, PCUserSubscriptionsRepo.FREE_PLAN);
        } else {
            Timestamp now = Timestamp.from(Instant.now());
            pcUsers.setEmail(pcUsersDO.getEmail());
            pcUsers.setAccessToken(pcUsersDO.getAccessToken());
            pcUsers.setFirstName(pcUsersDO.getFirstName());
            pcUsers.setUpdateAt(now);
            pcUsers.setLoginTime(now);
            pcUsersRepo.updateSingleUser(pcUsersDO);
        }
    }

    public BaseResponse<Object> addPurchasePoints(String shopName, Integer chars, String orderId) {
        PCUsersDO userByShopName = pcUsersRepo.getUserByShopName(shopName);
        if (userByShopName == null) {
            return new BaseResponse<>().CreateErrorResponse("用户不存在");
        }

        // 判断是否有订单标识 有的话 就直接返回true
        String redisOrderId = ordersRedisService.getOrderId(shopName, orderId);
        if (!"null".equals(redisOrderId)) {
            appInsights.trackTrace("PC addCharsByShopName 用户 " + shopName + " redisOrderId : " + redisOrderId);
            return new BaseResponse<>().CreateSuccessResponse(true);
        }

        boolean flag = pcUsersRepo.updatePurchasePointsByShopName(shopName, userByShopName.getAccessToken(), orderId, chars);
        if (flag) {
            appInsights.trackTrace("PC addPurchasePoints 添加额度成功 " + shopName + " chars: " + chars);
            return new BaseResponse<>().CreateSuccessResponse(true);
        }
        return new BaseResponse<>().CreateErrorResponse(false);
    }

    public BaseResponse<Object> getPurchasePoints(String shopName) {
        PCUsersDO userByShopName = pcUsersRepo.getUserByShopName(shopName);
        if (userByShopName == null) {
            return new BaseResponse<>().CreateErrorResponse("用户不存在");
        }
        PCUserPointsVO purchasePoints = new PCUserPointsVO();
        purchasePoints.setPurchasePoints(userByShopName.getPurchasePoints());
        purchasePoints.setUsedPoints(userByShopName.getUsedPoints());
        purchasePoints.setShopName(shopName);
        return new BaseResponse<>().CreateSuccessResponse(purchasePoints);
    }

    public BaseResponse<Object> uninstall(String shopName) {
        boolean flag = pcUsersRepo.updateUninstallByShopName(shopName);

        // 判断免费试用额度是否扣除，然后扣除
        PCUserTrialsDO pcUserTrialsDO = pcUserTrialsRepo.getUserTrialByShopName(shopName);
        Boolean isDeduct = pcUserTrialsDO.getIsDeduct();
        if (!isDeduct) {
            boolean deduct = pcUsersRepo.updatePurchasePoints(shopName, -80000);
            appInsights.trackTrace("PC uninstall 用户 " + shopName + " 扣除额度 ：" + deduct);
        }

        if (flag) {
            return new BaseResponse<>().CreateSuccessResponse(true);
        }
        return new BaseResponse<>().CreateErrorResponse(false);
    }

    public BaseResponse<Object> addCharsByShopNameAfterSubscribe(String shopName, TranslationCharsVO translationCharsVO) {
        // 获取该用户的accessToken
        PCUsersDO userByName = pcUsersRepo.getUserByShopName(shopName);
        translationCharsVO.setAccessToken(userByName.getAccessToken());

        // 判断是否有订单标识 有的话 就直接返回true
        String redisOrderId = ordersRedisService.getOrderId(shopName, translationCharsVO.getSubGid());
        if (!"null".equals(redisOrderId)) {
            appInsights.trackTrace("PC addCharsByShopName 用户 " + shopName + " redisOrderId: " + redisOrderId  + " orderId : " + translationCharsVO.getSubGid());
            return new BaseResponse<>().CreateErrorResponse(false);
        }

        // 根据传来的gid获取，相关订阅信息
        String subscriptionQuery = ShopifyRequestBody.getSubscriptionQuery(translationCharsVO.getSubGid());
        String shopifyByQuery = shopifyService.getShopifyData(shopName, userByName.getAccessToken(), API_VERSION_LAST, subscriptionQuery);
        appInsights.trackTrace("PC addCharsByShopNameAfterSubscribe " + shopName + " 用户 订阅信息 ：" + shopifyByQuery);

        // 判断和解析相关数据
        JSONObject queryValid = ShopifyUtils.isQueryValid(shopifyByQuery);
        if (queryValid == null) {
            return null;
        }

        // 获取用户订阅计划表的相关数据，与下面数据进行判断
        PCOrdersDO pcOrdersDO = pcOrdersRepo.getOrderBySubGid(translationCharsVO.getSubGid());
        appInsights.trackTrace("PC addCharsByShopNameAfterSubscribe " + shopName + " 用户 订阅计划表 ：" + pcOrdersDO.toString());

        String name = queryValid.getString("name");
        String status = queryValid.getString("status");
        Integer trialDays = queryValid.getInteger("trialDays");
        appInsights.trackTrace("PC addCharsByShopNameAfterSubscribe " + shopName + " 用户 免费试用天数 ：" + trialDays + " name: " + name + " status: " + status);


        Integer charsByPlanName = pcSubscriptionsRepo.getCharsByPlanName(name);
        if (name.equals(pcOrdersDO.getName()) && status.equals(pcOrdersDO.getStatus()) && trialDays > 0) {
            appInsights.trackTrace("PC addCharsByShopNameAfterSubscribe " + shopName + " 用户 第一次免费试用 ：" + translationCharsVO.getSubGid());
            String currentPeriodEnd = queryValid.getString("currentPeriodEnd");

            // 修改用户过期时间  和  费用类型
            if (currentPeriodEnd != null) {
                Instant end = Instant.parse(currentPeriodEnd);
                Timestamp subEnd = Timestamp.from(end);
                pcUserSubscriptionsRepo.updateUserFeeTypeAndEndDate(shopName, translationCharsVO.getFeeType(), subEnd);
            }

            // 添加8w额度, 需要修改免费试用订阅表
            String createdAt = queryValid.getString("createdAt");

            // 用户购买订阅时间
            Instant begin = Instant.parse(createdAt);
            Timestamp beginTimestamp = Timestamp.from(begin);

            // 试用结束时间
            Instant afterTrialDaysDays = begin.plus(trialDays, ChronoUnit.DAYS);
            Timestamp afterTrialDaysTimestamp = Timestamp.from(afterTrialDaysDays);

            // 获取用户是否已经是免费试用，是的话，将false改为true
            PCUserTrialsDO pcUserTrialsDO = pcUserTrialsRepo.getUserTrialByShopName(shopName);
            if (pcUserTrialsDO == null) {
                boolean insert = pcUserTrialsRepo.insertUserTrialAndBeginAndEnd(shopName, beginTimestamp, afterTrialDaysTimestamp);

                // 修改额度表8w额度， 在卸载和免费计划过期后不订阅后也扣掉
                boolean trial = pcUsersRepo.updateTrialPurchase(shopName);

                if (insert && trial) {
                    return new BaseResponse<>().CreateSuccessResponse(true);
                }
                return new BaseResponse<>().CreateErrorResponse(false);
            }
        } else {
            pcUserTrialsRepo.updateTrialExpiredByShopName(shopName, true);
        }

        // 添加额度
        appInsights.trackTrace("addCharsByShopNameAfterSubscribe " + shopName + " 用户 计划名 ：" + pcOrdersDO.getName() + " name: " + name + " status: " + status);
        if (name.equals(pcOrdersDO.getName()) && "ACTIVE".equals(status)) {
            // 根据用户的计划添加对应的额度
            boolean update = pcUsersRepo.updatePurchasePointsByShopName(shopName, translationCharsVO.getAccessToken(), translationCharsVO.getSubGid(), charsByPlanName);
            if (update) {
                return new BaseResponse<>().CreateSuccessResponse(true);
            }
        }
        return new BaseResponse<>().CreateErrorResponse(false);
    }
}
