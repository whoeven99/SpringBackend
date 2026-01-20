package com.bogda.service.Service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogda.service.Service.ISubscriptionQuotaRecordService;
import com.bogda.common.entity.DO.SubscriptionQuotaRecordDO;
import com.bogda.service.mapper.SubscriptionQuotaRecordMapper;
import org.springframework.stereotype.Service;

@Service
public class SubscriptionQuotaRecordServiceImpl extends ServiceImpl<SubscriptionQuotaRecordMapper, SubscriptionQuotaRecordDO> implements ISubscriptionQuotaRecordService {
    @Override
    public Integer insertOne(String subscriptionId, int billingCycle) {
        return baseMapper.insert(new SubscriptionQuotaRecordDO(null, subscriptionId, billingCycle));
    }
}
