package com.bogda.common.entity.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class BundleAmountDTO {
    private List<Map<String, String>> dailyAddRevenue;
}
