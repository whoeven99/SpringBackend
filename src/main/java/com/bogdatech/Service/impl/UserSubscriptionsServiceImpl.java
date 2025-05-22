package com.bogdatech.Service.impl;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogdatech.Service.IUserSubscriptionsService;
import com.bogdatech.entity.DO.UserSubscriptionsDO;
import com.bogdatech.mapper.UserSubscriptionsMapper;
import com.bogdatech.model.controller.request.UserSubscriptionsRequest;
import com.bogdatech.utils.TypeConversionUtils;
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
    public Boolean updateUserSubscription(UserSubscriptionsRequest request) {
        return null;
    }

    @Override
    public Integer checkUserPlan(String shopName, int planId) {
        UserSubscriptionsDO userSubscriptionsDO = new UserSubscriptionsDO();
        userSubscriptionsDO.setShopName(shopName);
        userSubscriptionsDO.setPlanId(planId);
        return baseMapper.update(userSubscriptionsDO, new UpdateWrapper<UserSubscriptionsDO>().eq("shop_name", shopName));
    }


}

