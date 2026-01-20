package com.bogda.repository.sql;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogda.common.entity.DO.SubscriptionQuotaRecordDO;
import org.springframework.stereotype.Service;

@Service
public class SubscriptionQuotaRecordServiceImpl extends ServiceImpl<SubscriptionQuotaRecordMapper, SubscriptionQuotaRecordDO> implements ISubscriptionQuotaRecordService {
    @Override
    public Integer insertOne(String subscriptionId, int billingCycle) {
        return baseMapper.insert(new SubscriptionQuotaRecordDO(null, subscriptionId, billingCycle));
    }
}
