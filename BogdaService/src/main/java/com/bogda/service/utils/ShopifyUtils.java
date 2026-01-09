package com.bogda.service.utils;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.bogda.service.entity.DO.CurrenciesDO;
import com.bogda.common.utils.AppInsightsUtils;

import java.lang.reflect.Field;
import java.text.NumberFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;


public class ShopifyUtils {

    /**
     * 解析查询的数据判断是否有效
     * 有效后，转为JSONObject类型数据
     * */
    public static JSONObject isQueryValid(String queryData){
        JSONObject root = JSON.parseObject(queryData);
        if (root == null || root.isEmpty()) {
            return null;
        }
        JSONObject node = root.getJSONObject("node");
        if (node == null || node.isEmpty()) {
            //用户卸载，计划会被取消，但不确定其他情况
            return null;
        }
        return node;
    }

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
            if (currencyInfo != null) {
                map.put("symbol", currencyInfo.get("symbol"));
            } else {
                map.put("symbol", "-");
                AppInsightsUtils.trackTrace("符号错误 ： " + currenciesDO.getShopName());
            }

        } catch (NoSuchFieldException | IllegalAccessException e) {
            AppInsightsUtils.trackTrace("FatalException : " + currenciesDO.getCurrencyCode() + "currency error :  " + e.getMessage());
        }
        return map;
    }

    /**
     * 根据name 获取对应的额度
     */
    public static Integer getAmount(String name){
        return switch (name) {
            case "50 extra times" -> 100000;
            case "100 extra times" -> 200000;
            case "200 extra times" -> 400000;
            case "300 extra times" -> 600000;
            case "500 extra times" -> 1000000;
            case "1000 extra times" -> 2000000;
            case "2000 extra times" -> 4000000;
            case "3000 extra times" -> 6000000;
            default -> 0;
        };
    }

    /**
     * 数字类数据，转为千分位
     */
    public static String getNumberFormat(String number){
        if (number == null || number.isEmpty()) {
            return number;
        }
        return NumberFormat
                .getNumberInstance(Locale.CHINA)
                .format(Long.parseLong(number));
    }
}
