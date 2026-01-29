package com.bogda.repository.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("Bundle_Users_Discount")
public class BundleUsersDiscountDO extends BaseDO{
    @TableField("shop_name")
    private String shopName;
    @TableField("discount_id")
    private String discountId; // 折扣id
    @TableField("discount_name")
    private String discountName; // 折扣名称
    private Boolean status; // 状态
    @TableField("exposure_pv")
    private Integer exposurePv; // 曝光pv
    @TableField("add_to_cart_pv")
    private Integer addToCartPv; // 加购pv
    private Double gmv; // 订单金额
    @TableField("checkout_started_pv")
    private Integer checkoutStartedPv; // 下单pv
}
