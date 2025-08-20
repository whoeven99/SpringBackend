package com.bogdatech.utils;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.bogdatech.model.controller.request.CloudServiceRequest;
import com.bogdatech.model.controller.request.ShopifyRequest;

import static com.bogdatech.constants.TranslateConstants.API_VERSION_LAST;
import static com.bogdatech.integration.ShopifyHttpIntegration.getInfoByShopify;
import static com.bogdatech.logic.ShopifyService.getShopifyDataByCloud;

public class ShopifyUtils {
    /**
     * 根据传入的查询语句，获取相关数据
     * */
    public static String getShopifyByQuery(String query, String shopName, String accessToken){
        String infoByShopify;
        String env = System.getenv("ApplicationEnv");
        //根据新的集合获取这个订阅计划的信息
        if ("prod".equals(env) || "dev".equals(env)) {
            infoByShopify = String.valueOf(getInfoByShopify(new ShopifyRequest(shopName, accessToken, API_VERSION_LAST, null), query));
        } else {
            infoByShopify = getShopifyDataByCloud(new CloudServiceRequest(shopName, accessToken, API_VERSION_LAST, "en", query));
        }
        return infoByShopify;
    }

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
