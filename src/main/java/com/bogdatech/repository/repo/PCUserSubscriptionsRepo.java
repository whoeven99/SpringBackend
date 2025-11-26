package com.bogdatech.repository.repo;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogdatech.entity.DO.UserSubscriptionsDO;
import com.bogdatech.repository.entity.PCUserSubscriptionsDO;
import com.bogdatech.repository.mapper.PCUserSubscriptionsMapper;
import org.springframework.stereotype.Service;
import java.sql.Timestamp;
import java.time.Instant;

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
                .set(PCUserSubscriptionsDO::getPlanId, planId).set(PCUserSubscriptionsDO::getUpdatedAt, Timestamp.from(Instant.now()))) > 0;
    }

    public PCUserSubscriptionsDO getPcUserSubscriptionsByShopName(String shopName) {
        return baseMapper.selectOne(new LambdaQueryWrapper<PCUserSubscriptionsDO>().eq(PCUserSubscriptionsDO::getShopName, shopName));
    }

    public boolean updateUserFeeTypeAndEndDate(String shopName, Integer feeType, Timestamp subEnd) {
        return baseMapper.update(new LambdaUpdateWrapper<PCUserSubscriptionsDO>().eq(PCUserSubscriptionsDO::getShopName, shopName)
                .set(PCUserSubscriptionsDO::getFeeType, feeType).set(PCUserSubscriptionsDO::getEndDate, subEnd)
                .set(PCUserSubscriptionsDO::getUpdatedAt, Timestamp.from(Instant.now()))) > 0;
    }

    public boolean updateUserEndDate(String shopName, Timestamp subEnd) {
        return baseMapper.update(new LambdaUpdateWrapper<PCUserSubscriptionsDO>().eq(PCUserSubscriptionsDO::getShopName,
                shopName).set(PCUserSubscriptionsDO::getEndDate, subEnd)) > 0;
    }

    public boolean updateUserPlanIdByShopName(String shopName, int i) {
        return baseMapper.update(new LambdaUpdateWrapper<PCUserSubscriptionsDO>().eq(PCUserSubscriptionsDO::getShopName
                , shopName).set(PCUserSubscriptionsDO::getPlanId, i)) > 0;
    }

    public boolean updateFeeType(String shopName, Integer feeType) {
        return baseMapper.update(new LambdaUpdateWrapper<PCUserSubscriptionsDO>().eq(PCUserSubscriptionsDO::getShopName
                , shopName).set(PCUserSubscriptionsDO::getFeeType, feeType)) > 0;
    }
}
