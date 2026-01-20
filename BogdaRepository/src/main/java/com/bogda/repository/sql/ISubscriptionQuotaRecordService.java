package com.bogda.repository.sql;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bogda.common.entity.DO.SubscriptionQuotaRecordDO;

public interface ISubscriptionQuotaRecordService extends IService<SubscriptionQuotaRecordDO> {
    Integer insertOne(String subscriptionId, int billingCycle);
}
