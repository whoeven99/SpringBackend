package com.bogda.common.Service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bogda.common.entity.DO.UserSubscriptionsDO;
import com.bogda.common.model.controller.request.UserSubscriptionsRequest;

public interface IUserSubscriptionsService extends IService<UserSubscriptionsDO> {
    Integer addUserSubscription(UserSubscriptionsRequest request);
    Integer getUserSubscriptionPlan(String shopName);
    Integer checkUserPlan(String shopName, int planId);
}
