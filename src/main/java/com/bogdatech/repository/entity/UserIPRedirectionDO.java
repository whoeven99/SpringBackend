package com.bogdatech.repository.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("User_IP_Redirection")
public class UserIPRedirectionDO extends BaseDO{
    @TableField("shop_name")
    private String shopName;
    private String region;
    @TableField("language_code")
    private String languageCode;
    @TableField("currency_code")
    private String currencyCode;
}
