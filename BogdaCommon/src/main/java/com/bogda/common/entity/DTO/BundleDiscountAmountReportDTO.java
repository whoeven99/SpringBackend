package com.bogda.common.entity.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class BundleDiscountAmountReportDTO {
    private String discountName;
    private Double discountAmount;
    private String currencyCode;
}

