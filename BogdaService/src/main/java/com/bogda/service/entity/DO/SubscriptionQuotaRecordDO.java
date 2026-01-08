package com.bogda.service.entity.DO;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
@TableName("SubscriptionQuotaRecord")
public class SubscriptionQuotaRecordDO {
    @TableId(type = IdType.AUTO)
    private Integer id;
    private String subscriptionId;
    private Integer billingCycle;
}
