package com.bogda.repository.repo;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogda.repository.entity.PCSubscriptionsDO;
import com.bogda.repository.mapper.PCSubscriptionsMapper;
import org.springframework.stereotype.Service;

@Service
public class PCSubscriptionsRepo extends ServiceImpl<PCSubscriptionsMapper, PCSubscriptionsDO> {
    public PCSubscriptionsDO getPcSubscriptionByShopName(Integer planId) {
        return baseMapper.selectOne(new LambdaQueryWrapper<PCSubscriptionsDO>().eq(PCSubscriptionsDO::getPlanId, planId));
    }

    public Integer getCharsByPlanName(String name) {
        PCSubscriptionsDO pcSubscriptionsDO = baseMapper.selectOne(new LambdaQueryWrapper<PCSubscriptionsDO>().eq(PCSubscriptionsDO::getPlanName, name));
        if (pcSubscriptionsDO == null) {
            return null;
        }
        return pcSubscriptionsDO.getEveryMonthToken();
    }
}
