package com.bogda.common.utils;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.bogda.common.config.CurrencyConfig;
import com.bogda.common.entity.DO.CurrenciesDO;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import static com.bogda.common.utils.CaseSensitiveUtils.appInsights;

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
                appInsights.trackTrace("符号错误 ： " + currenciesDO.getShopName());
            }

        } catch (NoSuchFieldException | IllegalAccessException e) {
            appInsights.trackTrace(currenciesDO.getCurrencyCode() + "currency error :  " + e.getMessage());
            appInsights.trackTrace(" currency error :  " + e.getMessage());
        }
        return map;
    }
}
