package com.bogdatech.utils;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.springframework.stereotype.Component;
@Component
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

}
