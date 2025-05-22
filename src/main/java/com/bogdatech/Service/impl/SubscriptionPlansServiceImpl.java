package com.bogdatech.Service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogdatech.Service.ISubscriptionPlansService;
import com.bogdatech.entity.DO.SubscriptionPlansDO;
import com.bogdatech.mapper.SubscriptionPlansMapper;
import org.springframework.stereotype.Service;

@Service
public class SubscriptionPlansServiceImpl extends ServiceImpl<SubscriptionPlansMapper, SubscriptionPlansDO> implements ISubscriptionPlansService {
    @Override
    public Integer getCharsByPlanName(String name) {
        return baseMapper.selectOne(new QueryWrapper<SubscriptionPlansDO>().eq("plan_name", name)).getEveryMonthToken();
    }
}
