package com.bogdatech.repository.repo;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogdatech.repository.entity.PCUserSubscriptionsDO;
import com.bogdatech.repository.mapper.PCUserSubscriptionsMapper;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDateTime;

@Service
public class PCUserSubscriptionsRepo extends ServiceImpl<PCUserSubscriptionsMapper, PCUserSubscriptionsDO> {
    // 免费计划
    public static final int FREE_PLAN = 1;

    public boolean insertUserSubscriptions(String shopName, int planId) {
        PCUserSubscriptionsDO pcUserSubscriptionsDO = new PCUserSubscriptionsDO();
        pcUserSubscriptionsDO.setShopName(shopName);
        pcUserSubscriptionsDO.setPlanId(planId);
        return baseMapper.insert(pcUserSubscriptionsDO) > 0;
    }

    public boolean checkUserPlan(String shopName, Integer planId) {
        return baseMapper.update(new LambdaUpdateWrapper<PCUserSubscriptionsDO>().eq(PCUserSubscriptionsDO::getShopName, shopName)
                .set(PCUserSubscriptionsDO::getPlanId, planId).set(PCUserSubscriptionsDO::getUpdatedAt, Timestamp.valueOf(LocalDateTime.now()))) > 0;
    }
}
