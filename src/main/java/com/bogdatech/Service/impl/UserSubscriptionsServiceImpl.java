package com.bogdatech.Service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogdatech.Service.IUserSubscriptionsService;
import com.bogdatech.entity.UserSubscriptionsDO;
import com.bogdatech.mapper.UserSubscriptionsMapper;
import com.bogdatech.model.controller.request.UserSubscriptionsRequest;
import com.bogdatech.utils.TypeConversionUtils;
import org.springframework.stereotype.Service;

@Service
public class UserSubscriptionsServiceImpl extends ServiceImpl<UserSubscriptionsMapper, UserSubscriptionsDO> implements IUserSubscriptionsService {

    @Override
    public Integer addUserSubscription(UserSubscriptionsRequest request) {//TODO 对传入的数据进行限制
        UserSubscriptionsDO userSubscriptionsDO = TypeConversionUtils.UserSubscriptionsRequestToUserSubscriptionsDO(request);
        return baseMapper.insert(userSubscriptionsDO);
    }

    @Override
    public String getUserSubscriptionPlan(String shopName) {
        return baseMapper.getUserSubscriptionPlan(shopName);
    }

    @Override
    public Boolean updateUserSubscription(UserSubscriptionsRequest request) {
        return null;
    }


}

