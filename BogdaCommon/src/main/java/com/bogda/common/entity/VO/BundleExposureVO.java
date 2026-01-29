package com.bogda.common.entity.VO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BundleExposureVO {
    private String event; // 事件名
    private String shopName; // 店铺名
    private String productId; // 商品id
    private String clientId; // 客户id
    private String extra; //  额外字段
}
