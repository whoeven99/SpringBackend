package com.bogda.repository.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
@TableName("SubscriptionQuotaRecord")
public class SubscriptionQuotaRecordDO extends BaseDO{
    private String subscriptionId;
    private Integer billingCycle;
}
