package com.bogdatech.repository.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("PC_Subscriptions")
public class PCSubscriptionsDO extends BaseDO{
    @TableField("plan_name")
    private String planName;
    @TableField("plan_id")
    private Integer planId;
    private String description;
    private Double price;
    @TableField("every_month_token")
    private Integer everyMonthToken;

}
