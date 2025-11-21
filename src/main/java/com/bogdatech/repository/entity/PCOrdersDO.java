package com.bogdatech.repository.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("PC_Orders")
public class PCOrdersDO extends BaseDO{
    @TableField("order_id")
    private String orderId;
    @TableField("shop_name")
    private String shopName;
    private Double amount;
    private String name;
    private String status;
    @TableField("confirmation_url")
    private String confirmationUrl;
}
