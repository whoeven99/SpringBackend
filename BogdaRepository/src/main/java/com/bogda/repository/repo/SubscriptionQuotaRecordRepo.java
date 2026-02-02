package com.bogda.repository.repo;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogda.repository.entity.SubscriptionQuotaRecordDO;
import com.bogda.repository.mapper.SubscriptionQuotaRecordMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SubscriptionQuotaRecordRepo extends ServiceImpl<SubscriptionQuotaRecordMapper, SubscriptionQuotaRecordDO> {
    public Integer saveNewRecord(String subscriptionId, int i) {
        return baseMapper.insert(new SubscriptionQuotaRecordDO(subscriptionId, i));
    }

    public SubscriptionQuotaRecordDO getLatestBySubscriptionId(String subscriptionId) {
        List<SubscriptionQuotaRecordDO> subscriptionQuotaRecordDOS = baseMapper.selectList(
                new QueryWrapper<SubscriptionQuotaRecordDO>().eq("subscription_id", subscriptionId)
                        .orderByDesc("created_at"));

        if (subscriptionQuotaRecordDOS.size() > 0) {
            return subscriptionQuotaRecordDOS.get(0);
        }
        return null;
    }

    public SubscriptionQuotaRecordDO getDataByIdAndBillingCycle(String subscriptionId, int billingCycle) {
        return baseMapper.selectOne(new QueryWrapper<SubscriptionQuotaRecordDO>().eq("subscription_id", subscriptionId)
                .eq("billing_cycle", billingCycle));
    }
}
