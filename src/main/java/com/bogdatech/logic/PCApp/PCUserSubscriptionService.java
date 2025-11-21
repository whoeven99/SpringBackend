package com.bogdatech.logic.PCApp;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.bogdatech.entity.DO.*;
import com.bogdatech.entity.VO.SubscriptionVO;
import com.bogdatech.logic.ShopifyService;
import com.bogdatech.model.controller.response.BaseResponse;
import com.bogdatech.repository.entity.PCSubscriptionsDO;
import com.bogdatech.repository.entity.PCUserSubscriptionsDO;
import com.bogdatech.repository.repo.PCOrdersServiceRepo;
import com.bogdatech.repository.repo.PCSubscriptionsRepo;
import com.bogdatech.repository.repo.PCUserSubscriptionsRepo;
import com.bogdatech.repository.repo.PCUsersServiceRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import static com.bogdatech.constants.TranslateConstants.API_VERSION_LAST;
import static com.bogdatech.requestBody.ShopifyRequestBody.getSubscriptionQuery;
import static com.bogdatech.utils.CaseSensitiveUtils.appInsights;
import static com.bogdatech.utils.StringUtils.parsePlanName;

@Component
public class PCUserSubscriptionService {
    @Autowired
    private PCUserSubscriptionsRepo pcUserSubscriptionsRepo;
    @Autowired
    private PCSubscriptionsRepo pcSubscriptionsRepo;
    @Autowired
    private PCOrdersServiceRepo pcOrdersServiceRepo;
    @Autowired
    private PCUsersServiceRepo pcUsersServiceRepo;
    @Autowired
    private ShopifyService shopifyService;

    public BaseResponse<Object> getUserSubscriptionPlan(String shopName) {
        //判断shopName的值是否有
        if (shopName == null || shopName.isEmpty()) {
            return new BaseResponse<>().CreateErrorResponse("shopName is null");
        }
        SubscriptionVO subscriptionVO = new SubscriptionVO();

        PCUserSubscriptionsDO pcUserSubscriptionsByShopName = pcUserSubscriptionsRepo.getPcUserSubscriptionsByShopName(shopName);

        if (pcUserSubscriptionsByShopName == null) {
            appInsights.trackTrace("getUserSubscriptionPlan 用户获取的数据失败： " + shopName);
            return new BaseResponse<>().CreateErrorResponse("pcUserSubscriptionsByShopName is null");
        }

        // 根据计划id 去查id名称
        PCSubscriptionsDO pcSubscriptionByShopName = pcSubscriptionsRepo.getPcSubscriptionByShopName(pcUserSubscriptionsByShopName.getPlanId());
        int planId = pcSubscriptionByShopName.getPlanId();

        // 判断计划名称
        String parsePlanType = parsePlanName(pcSubscriptionByShopName.getPlanName());
        if (parsePlanType == null) {
            return new BaseResponse<>().CreateErrorResponse("parsePlanType is null");
        }
        subscriptionVO.setPlanType(parsePlanType);
        subscriptionVO.setUserSubscriptionPlan(planId);

//        if (userSubscriptionsDO.getFeeType() == null) {
//            userSubscriptionsDO.setFeeType(0);
//        }
//        Integer userSubscriptionPlan = userSubscriptionsDO.getPlanId();

//
//        BaseResponse<Object> objectBaseResponse = checkWhiteList(shopName, subscriptionVO, userSubscriptionsDO.getFeeType());
//        if (objectBaseResponse != null) {
//            return objectBaseResponse;
//        }

        //如果是userSubscriptionPlan是1和2，传null
        if (planId == 1) {
            subscriptionVO.setCurrentPeriodEnd(null);
            subscriptionVO.setFeeType(0);
            return new BaseResponse<>().CreateSuccessResponse(subscriptionVO);
        }

//        //判断是否是免费计划
//        if (planId == 7) {
//            subscriptionVO.setUserSubscriptionPlan(7);
//            //根据shopName获取订阅计划过期的时间
//            UserTrialsDO userTrialsDO = iUserTrialsService.getOne(new QueryWrapper<UserTrialsDO>().eq("shop_name", shopName));
//            subscriptionVO.setCurrentPeriodEnd(String.valueOf(userTrialsDO.getTrialEnd()));
//            subscriptionVO.setFeeType(userSubscriptionsDO.getFeeType());
//            return new BaseResponse<>().CreateSuccessResponse(subscriptionVO);
//        }

        //根据shopName查询用户订阅计划，最新的那个，再根据最新的resourceId，查询是否过期
        String latestActiveSubscribeId = pcOrdersServiceRepo.getLatestActiveSubscribeId(shopName);

        if (latestActiveSubscribeId == null) {
            return new BaseResponse<>().CreateErrorResponse("latestActiveSubscribeId is null");
        }

        PCUsersDO pcUser = pcUsersServiceRepo.getUserByShopName(shopName);

        // 通过charsOrdersDO的id，获取信息
        // 根据新的集合获取这个订阅计划的信息
        String query = getSubscriptionQuery(latestActiveSubscribeId);
        String infoByShopify = shopifyService.getShopifyData(shopName, pcUser.getAccessToken(), API_VERSION_LAST, query);

        if (infoByShopify == null || infoByShopify.isEmpty()) {
            subscriptionVO.setFeeType(0);
            subscriptionVO.setUserSubscriptionPlan(2);
            subscriptionVO.setCurrentPeriodEnd(null);
            return new BaseResponse<>().CreateSuccessResponse(subscriptionVO);
        }

        // 根据订阅计划信息，判断是否是第一个月的开始
        JSONObject root = JSON.parseObject(infoByShopify);
        JSONObject node = root.getJSONObject("node");
        if (node == null || node.isEmpty()) {
            // 用户卸载，计划会被取消，但不确定其他情况
            subscriptionVO.setFeeType(0);
            subscriptionVO.setUserSubscriptionPlan(2);
            subscriptionVO.setCurrentPeriodEnd(null);
        } else {
//            subscriptionVO.setFeeType(userSubscriptionsDO.getFeeType());
            String currentPeriodEnd = node.getString("currentPeriodEnd");
            subscriptionVO.setCurrentPeriodEnd(currentPeriodEnd);
        }
        return new BaseResponse<>().CreateSuccessResponse(subscriptionVO);
    }

    public BaseResponse<Object> checkUserPlan(String shopName, Integer planId) {
        boolean update = pcUserSubscriptionsRepo.checkUserPlan(shopName, planId);
        if (update) {
            return new BaseResponse<>().CreateSuccessResponse(true);
        }
        return new BaseResponse<>().CreateErrorResponse("check error");
    }
}
