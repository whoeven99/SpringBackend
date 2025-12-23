package com.bogda.api.entity.DO;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("UserSubscriptions")
public class UserSubscriptionsDO {
    @TableId(type = IdType.AUTO)
    private Integer subscriptionId;
    private String shopName;
    private Integer planId;
    private Integer status;
    private Integer feeType;
    private LocalDateTime startDate; // 订阅开始日期
    private LocalDateTime endDate; // 订阅结束日期
}
