package com.bogdatech.entity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("SubscriptionPlans")
public class SubscriptionPlansDO {
    @TableId(type = IdType.AUTO)
    private Integer planId;
    private String planName;
    private String description;
    private Double price;
    private Integer maxTranslationsMonth;
    private Integer everyMonthToken;
}
