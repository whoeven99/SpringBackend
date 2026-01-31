package com.bogda.common.entity.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class BundleAvgConversionIndicatorDTO {
    private Double avgConversionIndicator;
    private Double avgConversion;
}
