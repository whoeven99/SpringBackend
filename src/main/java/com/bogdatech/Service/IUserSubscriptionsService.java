package com.bogdatech.Service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bogdatech.entity.DO.UserSubscriptionsDO;
import com.bogdatech.model.controller.request.UserSubscriptionsRequest;

import java.time.LocalDateTime;

public interface IUserSubscriptionsService extends IService<UserSubscriptionsDO> {
    Integer addUserSubscription(UserSubscriptionsRequest request);
    Integer getUserSubscriptionPlan(String shopName);

    Boolean updateUserSubscription(String shopName, int planId);

    Integer checkUserPlan(String shopName, int planId);

    boolean updateFeeTypeByShopName(String shopName, int feeType);

    UserSubscriptionsDO getSubscriptionDataByShopName(String shopName);

    boolean updateFeeTypeAndEndDateByShopName(String shopName, Integer feeType, LocalDateTime subEnd);

    boolean updateUserExpirationTime(String shopName, LocalDateTime subEnd);
}
