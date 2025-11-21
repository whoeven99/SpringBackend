package com.bogdatech.repository.repo;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogdatech.repository.entity.PCSubscriptionsDO;
import com.bogdatech.repository.mapper.PCSubscriptionsMapper;
import org.springframework.stereotype.Service;

@Service
public class PCSubscriptionsRepo extends ServiceImpl<PCSubscriptionsMapper, PCSubscriptionsDO> {
    public PCSubscriptionsDO getPcSubscriptionByShopName(Integer planId) {
        return baseMapper.selectOne(new LambdaQueryWrapper<PCSubscriptionsDO>().eq(PCSubscriptionsDO::getPlanId, planId));
    }
}
