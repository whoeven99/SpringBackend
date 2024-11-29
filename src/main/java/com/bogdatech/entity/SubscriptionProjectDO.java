package com.bogdatech.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("SubscriptionProject")
public class SubscriptionProjectDO {
    private Integer projectId;
    private String key;
    private String name;
    private Integer characters;
    private Float currentPrice;
    private Float compared_price;
    private String currency_code;
}
