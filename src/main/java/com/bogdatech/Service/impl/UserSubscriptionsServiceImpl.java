package com.bogdatech.Service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogdatech.Service.IUserSubscriptionsService;
import com.bogdatech.entity.DO.UserSubscriptionsDO;
import com.bogdatech.mapper.UserSubscriptionsMapper;
import com.bogdatech.model.controller.request.UserSubscriptionsRequest;
import com.bogdatech.utils.TypeConversionUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

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
    public Boolean updateUserSubscription(String shopName, int planId) {
        return baseMapper.update(new LambdaUpdateWrapper<UserSubscriptionsDO>().eq(UserSubscriptionsDO::getShopName, shopName)
                .set(UserSubscriptionsDO::getPlanId, planId)) > 0;
    }


    @Override
    public Integer checkUserPlan(String shopName, int planId) {
        UserSubscriptionsDO userSubscriptionsDO = new UserSubscriptionsDO();
        userSubscriptionsDO.setShopName(shopName);
        userSubscriptionsDO.setPlanId(planId);
        return baseMapper.update(userSubscriptionsDO, new UpdateWrapper<UserSubscriptionsDO>().eq("shop_name", shopName));
    }

    @Override
    public boolean updateFeeTypeByShopName(String shopName, int feeType) {
        return baseMapper.update(new LambdaUpdateWrapper<UserSubscriptionsDO>().eq(UserSubscriptionsDO::getShopName, shopName).set(UserSubscriptionsDO::getFeeType, feeType)) > 0;
    }

    @Override
    public UserSubscriptionsDO getSubscriptionDataByShopName(String shopName) {
        return baseMapper.selectOne(new LambdaQueryWrapper<UserSubscriptionsDO>().eq(UserSubscriptionsDO::getShopName, shopName));
    }

    @Override
    public boolean updateFeeTypeAndEndDateByShopName(String shopName, Integer feeType, LocalDateTime subEnd) {
        return baseMapper.update(new LambdaUpdateWrapper<UserSubscriptionsDO>()
                .eq(UserSubscriptionsDO::getShopName, shopName)
                .set(UserSubscriptionsDO::getFeeType, feeType)
                .set(UserSubscriptionsDO::getEndDate, subEnd)) > 0;
    }

    @Override
    public boolean updateUserExpirationTime(String shopName, LocalDateTime subEnd) {
        return baseMapper.update(new LambdaUpdateWrapper<UserSubscriptionsDO>()
                .eq(UserSubscriptionsDO::getShopName, shopName)
                .set(UserSubscriptionsDO::getEndDate, subEnd)) > 0;
    }


}

