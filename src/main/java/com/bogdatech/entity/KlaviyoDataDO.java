package com.bogdatech.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("klaviyoData")
public class KlaviyoDataDO {

    private String shopName;
    private String name;
    private String type;
    private String stringId;

}