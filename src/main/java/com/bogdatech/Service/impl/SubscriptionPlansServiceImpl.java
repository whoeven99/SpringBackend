package com.bogdatech.Service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogdatech.Service.ISubscriptionPlansService;
import com.bogdatech.entity.DO.SubscriptionPlansDO;
import com.bogdatech.mapper.SubscriptionPlansMapper;
import com.volcengine.model.request.GetDailyMarketingPackageRequest;
import org.springframework.stereotype.Service;

@Service
public class SubscriptionPlansServiceImpl extends ServiceImpl<SubscriptionPlansMapper, SubscriptionPlansDO> implements ISubscriptionPlansService {
    @Override
    public Integer getCharsByPlanName(String name) {
        return baseMapper.selectOne(new LambdaQueryWrapper<SubscriptionPlansDO>().eq(SubscriptionPlansDO::getPlanName, name)).getEveryMonthToken();
    }

    @Override
    public SubscriptionPlansDO getSubscriptionPlanByPlanId(Integer planId) {
        return baseMapper.selectOne(new LambdaQueryWrapper<SubscriptionPlansDO>().eq(SubscriptionPlansDO::getPlanId, planId));
    }
}
