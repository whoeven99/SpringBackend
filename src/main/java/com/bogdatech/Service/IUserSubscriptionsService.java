package com.bogdatech.Service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bogdatech.entity.UserSubscriptionsDO;
import com.bogdatech.model.controller.request.UserSubscriptionsRequest;

public interface IUserSubscriptionsService extends IService<UserSubscriptionsDO> {
    Integer addUserSubscription(UserSubscriptionsRequest request);
    String getUserSubscriptionPlan(String shopName);

    Boolean updateUserSubscription(UserSubscriptionsRequest request);
}
