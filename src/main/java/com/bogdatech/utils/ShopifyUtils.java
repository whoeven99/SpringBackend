package com.bogdatech.utils;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.bogdatech.model.controller.request.CloudServiceRequest;
import com.bogdatech.model.controller.request.ShopifyRequest;

import java.time.Duration;
import java.time.Instant;

import static com.bogdatech.constants.TranslateConstants.API_VERSION_LAST;
import static com.bogdatech.integration.ShopifyHttpIntegration.getInfoByShopify;
import static com.bogdatech.logic.ShopifyService.getShopifyDataByCloud;
import static com.bogdatech.requestBody.ShopifyRequestBody.getShopCreatedDateQuery;
import static com.bogdatech.utils.CaseSensitiveUtils.appInsights;

public class ShopifyUtils {
    /**
     * 根据传入的查询语句，获取相关数据
     * */
    public static String getShopifyByQuery(String query, String shopName, String accessToken){
        String infoByShopify;
        String env = System.getenv("ApplicationEnv");
        // TODO shopify service
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

    /**
     * 风控处理：如果商店创建日期，如果少于 30 天：
     * 1.不出免费试用的入口
     * 2. 不发放任何额度
     * 这里只做判断是否少于30天
     * */
    public static boolean isShopCreatedLessThan30Days(String shopName, String accessToken){
        String query = getShopCreatedDateQuery();
        String shopifyByQuery = getShopifyByQuery(query, shopName, accessToken);
        if (shopifyByQuery == null) {
            return false;
        }

        // 解析数据，判断是否少于30天
        JSONObject jsonObject = JSONObject.parseObject(shopifyByQuery);
        if (jsonObject == null){
            return false;
        }
        String createdAt = jsonObject
                .getJSONObject("shop")
                .getString("createdAt");

        //判断createdAt到现在的时间是否大于30天
        Instant createdAts = Instant.parse(createdAt);
        Instant now = Instant.now();

        // 计算时间差
        long days = Duration.between(createdAts, now).toDays();

        // 判断是否超过30天
        if (days > 30) {
            appInsights.trackTrace("isShopCreatedLessThan30Days " + shopName + " 创建时间已超过30天, 共 " + days + " 天");
            return true;
        } else {
            appInsights.trackTrace("isShopCreatedLessThan30Days " + shopName + " 创建时间在30天内, 共 " + days + " 天");
            return false;
        }
    }
}
