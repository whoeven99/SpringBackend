package com.bogdatech.utils;

import com.bogdatech.config.CurrencyConfig;
import com.bogdatech.entity.DO.CurrenciesDO;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import static com.bogdatech.utils.CaseSensitiveUtils.appInsights;

@Component
public class MapUtils {
    public static Map<String, Object> getCurrencyDOS(CurrenciesDO currenciesDO) {
        Map<String, Object> map = new HashMap<>();
        map.put("currencyCode", currenciesDO.getCurrencyCode());
        map.put("currencyName", currenciesDO.getCurrencyName());
        map.put("shopName", currenciesDO.getShopName());
        map.put("id", currenciesDO.getId());
        map.put("rounding", currenciesDO.getRounding());
        map.put("exchangeRate", currenciesDO.getExchangeRate());
        map.put("primaryStatus", currenciesDO.getPrimaryStatus());

        try {
            Field field = CurrencyConfig.class.getField(currenciesDO.getCurrencyCode().toUpperCase());
            Map<String, Object> currencyInfo = (Map<String, Object>) field.get(null);
            if (currencyInfo != null){
                map.put("symbol", currencyInfo.get("symbol"));
            }else {
                map.put("symbol", "-");
                appInsights.trackTrace("符号错误 ： " + currenciesDO.getShopName());
            }

        } catch (NoSuchFieldException | IllegalAccessException e) {
            System.out.println(currenciesDO.getCurrencyCode() + "currency error :  " + e.getMessage());
            appInsights.trackTrace(" currency error :  " + e.getMessage());
        }
        return map;
    }
}
