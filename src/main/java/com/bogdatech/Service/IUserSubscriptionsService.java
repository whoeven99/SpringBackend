package com.bogdatech.Service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bogdatech.entity.DO.UserSubscriptionsDO;
import com.bogdatech.model.controller.request.UserSubscriptionsRequest;

public interface IUserSubscriptionsService extends IService<UserSubscriptionsDO> {
    Integer addUserSubscription(UserSubscriptionsRequest request);
    Integer getUserSubscriptionPlan(String shopName);
    Integer checkUserPlan(String shopName, int planId);
}
