package com.bogdatech.repository.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("PC_Subscription_Quota_Record")
public class PCSubscriptionQuotaRecordDO extends BaseDO{
    @TableField("subscription_id")
    private String subscriptionId;
    @TableField("billing_cycle")
    private Integer billingCycle;
}
