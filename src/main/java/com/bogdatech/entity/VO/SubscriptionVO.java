package com.bogdatech.entity.VO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class SubscriptionVO {
    private Integer userSubscriptionPlan;
    private String currentPeriodEnd;
    private String feeType;
}
