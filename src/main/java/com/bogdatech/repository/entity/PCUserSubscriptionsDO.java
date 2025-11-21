package com.bogdatech.repository.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;

@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("PC_User_Subscriptions")
public class PCUserSubscriptionsDO extends BaseDO{
    @TableField("shop_name")
    private String shopName;
    @TableField("plan_id")
    private Integer planId;
    @TableField("start_date")
    private Timestamp startData;
    @TableField("end_date")
    private Timestamp endDate;
    @TableField("feeType")
    private Integer feeType;
}
