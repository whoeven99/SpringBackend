package com.bogda.api.Service.impl;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogda.api.Service.IUserSubscriptionsService;
import com.bogda.api.entity.DO.UserSubscriptionsDO;
import com.bogda.api.mapper.UserSubscriptionsMapper;
import com.bogda.api.model.controller.request.UserSubscriptionsRequest;
import com.bogda.api.utils.TypeConversionUtils;
import org.springframework.stereotype.Service;

@Service
public class UserSubscriptionsServiceImpl extends ServiceImpl<UserSubscriptionsMapper, UserSubscriptionsDO> implements IUserSubscriptionsService {

    @Override
    public Integer addUserSubscription(UserSubscriptionsRequest request) {
        UserSubscriptionsDO userSubscriptionsDO = TypeConversionUtils.UserSubscriptionsRequestToUserSubscriptionsDO(request);
        return baseMapper.insert(userSubscriptionsDO);
    }

    @Override
    public Integer getUserSubscriptionPlan(String shopName) {
        return baseMapper.getUserSubscriptionPlan(shopName);
    }

    @Override
    public Integer checkUserPlan(String shopName, int planId) {
        UserSubscriptionsDO userSubscriptionsDO = new UserSubscriptionsDO();
        userSubscriptionsDO.setShopName(shopName);
        userSubscriptionsDO.setPlanId(planId);
        return baseMapper.update(userSubscriptionsDO, new UpdateWrapper<UserSubscriptionsDO>().eq("shop_name", shopName));
    }


}

