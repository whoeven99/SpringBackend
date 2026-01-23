package com.bogda.service.Service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogda.service.Service.ISubscriptionPlansService;
import com.bogda.common.entity.DO.SubscriptionPlansDO;
import com.bogda.service.mapper.SubscriptionPlansMapper;
import org.springframework.stereotype.Service;

@Service
public class SubscriptionPlansServiceImpl extends ServiceImpl<SubscriptionPlansMapper, SubscriptionPlansDO> implements ISubscriptionPlansService {
    @Override
    public Integer getCharsByPlanName(String name) {
        return baseMapper.selectOne(new QueryWrapper<SubscriptionPlansDO>().eq("plan_name", name)).getEveryMonthToken();
    }

    @Override
    public SubscriptionPlansDO getDataByPlanId(Integer planId) {
        return baseMapper.selectOne(new LambdaQueryWrapper<SubscriptionPlansDO>().eq(SubscriptionPlansDO::getPlanId, planId));
    }
}
