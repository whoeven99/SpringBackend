package com.bogda.common.model.controller.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CurrencyRequest {
    private int id; //用于唯一标识每条货币记录。
    private String defaultCode; //默认货币代码（如 'USD', 'CNY'）。
    private String shopName; // 用户的ID，可以用来关联到用户表。
    private String currencyName; //国家的名字。
    private String currencyCode; //货币代码（如 'USD', 'CNY'）。
    private String rounding; // 四舍五入规则。
    private String exchangeRate; //汇率.
}
