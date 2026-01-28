package com.bogda.common.entity.VO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BundleDisplayDataVO {
    private String shopName;
    private String discountId; // 折扣id
    private String discountName; // 折扣名称
    private Boolean status; // 状态
    private Integer exposurePv; // 曝光pv
    private Integer addToCartPv; // 加购pv
    private Double gmv; // 订单金额
    private Double conversion; // 下单pv / 曝光pv
}
