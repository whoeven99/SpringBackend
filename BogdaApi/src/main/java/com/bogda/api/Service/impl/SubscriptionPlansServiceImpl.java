package com.bogda.api.Service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogda.api.Service.ISubscriptionPlansService;
import com.bogda.api.entity.DO.SubscriptionPlansDO;
import com.bogda.api.mapper.SubscriptionPlansMapper;
import org.springframework.stereotype.Service;

@Service
public class SubscriptionPlansServiceImpl extends ServiceImpl<SubscriptionPlansMapper, SubscriptionPlansDO> implements ISubscriptionPlansService {
    @Override
    public Integer getCharsByPlanName(String name) {
        return baseMapper.selectOne(new QueryWrapper<SubscriptionPlansDO>().eq("plan_name", name)).getEveryMonthToken();
    }
}
