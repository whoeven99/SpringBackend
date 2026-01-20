package com.bogda.service.Service.impl;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogda.service.Service.IUserSubscriptionsService;
import com.bogda.common.entity.DO.UserSubscriptionsDO;
import com.bogda.service.mapper.UserSubscriptionsMapper;
import com.bogda.common.controller.request.UserSubscriptionsRequest;
import org.springframework.stereotype.Service;

@Service
public class UserSubscriptionsServiceImpl extends ServiceImpl<UserSubscriptionsMapper, UserSubscriptionsDO> implements IUserSubscriptionsService {

    @Override
    public Integer addUserSubscription(UserSubscriptionsRequest request) {
        UserSubscriptionsDO userSubscriptionsDO = userSubscriptionsRequestToUserSubscriptionsDO(request);
        return baseMapper.insert(userSubscriptionsDO);
    }

    private UserSubscriptionsDO userSubscriptionsRequestToUserSubscriptionsDO(UserSubscriptionsRequest request){
        UserSubscriptionsDO userSubscriptionsDO = new UserSubscriptionsDO();
        userSubscriptionsDO.setShopName(request.getShopName());
        userSubscriptionsDO.setPlanId(request.getPlanId());
        userSubscriptionsDO.setStartDate(request.getStartDate());
        userSubscriptionsDO.setEndDate(request.getEndDate());
        userSubscriptionsDO.setStatus(request.getStatus());
        return userSubscriptionsDO;
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

