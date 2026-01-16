package com.bogda.service.entity.DO;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("Currencies")
public class CurrenciesDO {
    @TableId(type = IdType.AUTO)
    private Integer id;
    private String shopName;
    private String currencyCode;
    private String currencyName;
    private String rounding;
    private String exchangeRate;
    private Integer primaryStatus;
}
