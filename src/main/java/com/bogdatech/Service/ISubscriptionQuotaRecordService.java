package com.bogdatech.Service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bogdatech.entity.DO.SubscriptionQuotaRecordDO;

public interface ISubscriptionQuotaRecordService extends IService<SubscriptionQuotaRecordDO> {
    Integer insertOne(String subscriptionId, int billingCycle);

    SubscriptionQuotaRecordDO getSubscriptionQuotaRecordDataByIdAndBillingCycle(String subscriptionId, int billingCycle);
}
