package com.bogda.api.entity.VO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class SubscriptionVO {
    private Integer userSubscriptionPlan;
    private String currentPeriodEnd;
    private Integer feeType;
    private String planType;
}
