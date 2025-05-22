package com.bogdatech.Service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogdatech.Service.ISubscriptionQuotaRecordService;
import com.bogdatech.entity.DO.SubscriptionQuotaRecordDO;
import com.bogdatech.mapper.SubscriptionQuotaRecordMapper;
import org.springframework.stereotype.Service;

@Service
public class SubscriptionQuotaRecordServiceImpl extends ServiceImpl<SubscriptionQuotaRecordMapper, SubscriptionQuotaRecordDO> implements ISubscriptionQuotaRecordService {
    @Override
    public Integer insertOne(String subscriptionId, int billingCycle) {
        return baseMapper.insert(new SubscriptionQuotaRecordDO(null, subscriptionId, billingCycle));
    }
}
