package com.bogda.common.utils;

/**
 * 阿里云SLS日志查询工具类
 * 注意：实际实现位于BogdaService模块的ProductExposureService中
 */
public class AliyunLogSqlUtils {
    // 1. getProductExposureUvByShopName - 查询产品根据shopName，多少天内，曝光的uv数
    public static String getProductExposureUvByShopName(String shopName) {
        return "event: \"product_viewed\" AND shopName:\"" + shopName +
                "\" | SELECT approx_distinct(clientId) as uv";
    }

    // 2. getProductExposurePvByShopName - 查询产品根据shopName，曝光的pv数
    public static String getProductExposurePvByShopName(String shopName) {
        return "event: \"product_viewed\" AND shopName:\"" + shopName +
                "\" | SELECT count(*) as pv";
    }

    // 3. getProductAddedToCartPvByShopName - 查询产品根据shopName，加购的pv数
    public static String getProductAddedToCartPvByShopName(String shopName) {
        return "event: \"product_added_to_cart\" AND shopName:\"" + shopName +
                "\" | SELECT count(*) as pv";
    }
}
