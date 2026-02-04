package com.bogda.common.entity.VO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class BundleDiscountAmountReportVO {
    private Boolean circuitOpen;
    private Boolean enable;
    private Double usedDailyBudget;
    private Double usedTotalBudget;
}

