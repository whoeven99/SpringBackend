package com.bogda.api.entity.DO;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("SubscriptionProject")
public class SubscriptionProjectDO {
    @TableId(type = IdType.AUTO)
    private Integer projectId;
    private String projectKey;
    private String name;
    private Integer characters;
    private Float currentPrice;
    private Float compared_price;
    private String currency_code;
}
