package com.bogdatech.repository.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("User_IP_Count")
public class UserIPCountDO extends BaseDO{
    private String shopName;
    @TableField("count_type")
    private String countType;
    @TableField("count_value")
    private Integer countValue;
}
