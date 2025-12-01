package com.bogdatech.utils;

import com.bogdatech.entity.VO.SubscriptionVO;
import com.bogdatech.model.controller.response.BaseResponse;

public class WhiteListUtils {
    // todo @庄泽 逻辑挪到外面去，这里就返回boolean
    // 也放到configRedisRepo
    public static BaseResponse<Object> checkWhiteList(String shopName, SubscriptionVO subscriptionVO, Integer feeType){
        if ("5bf8b3.myshopify.com".equals(shopName) || "c5ba7c-7c.myshopify.com".equals(shopName) || "digitevil.myshopify.com".equals(shopName)) {
            subscriptionVO.setUserSubscriptionPlan(6);
            subscriptionVO.setCurrentPeriodEnd(null);
            subscriptionVO.setFeeType(feeType);
            subscriptionVO.setPlanType("Premium");
            return new BaseResponse<>().CreateSuccessResponse(subscriptionVO);
        }
        return null;
    }
}
