package com.bogda.repository.repo;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogda.repository.entity.PCSubscriptionQuotaRecordDO;
import com.bogda.repository.mapper.PCSubscriptionQuotaRecordMapper;
import org.springframework.stereotype.Service;

@Service
public class PCSubscriptionQuotaRecordRepo extends ServiceImpl<PCSubscriptionQuotaRecordMapper, PCSubscriptionQuotaRecordDO> {

    public PCSubscriptionQuotaRecordDO getQuotaRecordBySubscriptionIdAndBillingCycle(String subscriptionId, int i) {
        return baseMapper.selectOne(new LambdaQueryWrapper<PCSubscriptionQuotaRecordDO>().eq(PCSubscriptionQuotaRecordDO::getSubscriptionId
                , subscriptionId).eq(PCSubscriptionQuotaRecordDO::getBillingCycle, i));
    }

    public boolean insertQuotaRecord(String subscriptionId, int i) {
        PCSubscriptionQuotaRecordDO newQuotaRecord = new PCSubscriptionQuotaRecordDO();
        newQuotaRecord.setSubscriptionId(subscriptionId);
        newQuotaRecord.setBillingCycle(i);
        return baseMapper.insert(newQuotaRecord) > 0;
    }

    public PCSubscriptionQuotaRecordDO getQuotaRecordDO(String subscriptionId, int billingCycle) {
        return baseMapper.selectOne(new LambdaQueryWrapper<PCSubscriptionQuotaRecordDO>().eq(PCSubscriptionQuotaRecordDO::getSubscriptionId
                , subscriptionId).eq(PCSubscriptionQuotaRecordDO::getBillingCycle, billingCycle));
    }
}
