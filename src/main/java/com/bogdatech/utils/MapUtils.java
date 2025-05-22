package com.bogdatech.utils;

import com.bogdatech.config.CurrencyConfig;
import com.bogdatech.entity.DO.CurrenciesDO;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

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
            map.put("symbol", currencyInfo.get("symbol"));
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
        return map;
    }
}
