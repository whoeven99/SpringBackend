package com.bogda.api.Service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bogda.api.entity.DO.SubscriptionQuotaRecordDO;

public interface ISubscriptionQuotaRecordService extends IService<SubscriptionQuotaRecordDO> {
    Integer insertOne(String subscriptionId, int billingCycle);
}
