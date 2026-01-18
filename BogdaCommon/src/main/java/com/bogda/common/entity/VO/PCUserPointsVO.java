package com.bogda.common.entity.VO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PCUserPointsVO {
    private String shopName;
    private Integer purchasePoints;
    private Integer usedPoints;
}
