package com.bogdatech.Service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bogdatech.entity.SubscriptionQuotaRecordDO;

public interface ISubscriptionQuotaRecordService extends IService<SubscriptionQuotaRecordDO> {
    Integer insertOne(String subscriptionId, int billingCycle);
}
