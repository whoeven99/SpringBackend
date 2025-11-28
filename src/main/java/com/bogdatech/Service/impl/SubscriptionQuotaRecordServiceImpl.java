package com.bogdatech.Service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogdatech.Service.ISubscriptionQuotaRecordService;
import com.bogdatech.entity.DO.SubscriptionQuotaRecordDO;
import com.bogdatech.mapper.SubscriptionQuotaRecordMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SubscriptionQuotaRecordServiceImpl extends ServiceImpl<SubscriptionQuotaRecordMapper, SubscriptionQuotaRecordDO> implements ISubscriptionQuotaRecordService {
    @Override
    public Integer insertOne(String subscriptionId, int billingCycle) {
        return baseMapper.insert(new SubscriptionQuotaRecordDO(subscriptionId, billingCycle));
    }

    @Override
    public SubscriptionQuotaRecordDO getNewestSubscriptionData(String id) {
        List<SubscriptionQuotaRecordDO> subscriptionQuotaRecords = baseMapper.selectList(new LambdaQueryWrapper<SubscriptionQuotaRecordDO>().eq(SubscriptionQuotaRecordDO::getSubscriptionId, id)
                .orderByDesc(SubscriptionQuotaRecordDO::getBillingCycle));
        return !subscriptionQuotaRecords.isEmpty() ? subscriptionQuotaRecords.get(0) : null;
    }
}
