package com.bogda.common.utils;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;

import java.text.NumberFormat;
import java.util.Locale;

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
