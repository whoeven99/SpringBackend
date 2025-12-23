package com.bogda.common.Service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bogda.common.entity.DO.SubscriptionQuotaRecordDO;

public interface ISubscriptionQuotaRecordService extends IService<SubscriptionQuotaRecordDO> {
    Integer insertOne(String subscriptionId, int billingCycle);
}
