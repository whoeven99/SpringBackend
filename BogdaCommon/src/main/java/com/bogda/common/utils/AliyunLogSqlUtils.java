package com.bogda.common.utils;

/**
 * 阿里云SLS日志查询工具类
 * 注意：实际实现位于BogdaService模块的ProductExposureService中
 */
public class AliyunLogSqlUtils {
    // 根据shopName和事件名，曝光的uv数
    public static String getUvByShopNameAndEventName(String shopName, String eventName) {
        return "event: \"" + eventName + "\" AND shopName:\"" + shopName +
                "\" | SELECT approx_distinct(clientId) as uv";
    }

    // 根据shopName和事件名，曝光的pv数
    public static String getPvByShopNameAndEventName(String shopName, String eventName) {
        return "event: \"" + eventName + "\" AND shopName:\"" + shopName +
                "\" | SELECT count(*) as pv";
    }


    // 根据shopName和bundle title查询加购的pv
    public static String getProductAddedToCartPvByShopNameAndBundleTitle(String shopName, String bundleTitle, String eventName) {
        return "event: \"" + eventName + "\" | \n" +
                "SELECT \n" +
                "    json_extract_scalar(bundle_item, '$.title') AS bundle_title, \n" +
                "    shopName,\n" +
                "    count(*) AS PV\n" +
                "FROM \n" +
                "    log, \n" +
                "    UNNEST(cast(json_extract(extra, '$.bundle') AS array(json))) AS t(bundle_item)\n" +
                "WHERE \n" +
                "    shopName = '" + shopName + "' \n" +
                "    AND json_extract_scalar(bundle_item, '$.title') = '" + bundleTitle + "'\n" +
                "GROUP BY \n" +
                "    bundle_title, shopName";
    }

    // 根据shopName和bundle title查询加购的uv
    public static String getProductAddedToCartUvByShopNameAndBundleTitle(String shopName, String bundleTitle, String eventName) {
        return "event: \"" + eventName + "\" | \n" +
                "SELECT \n" +
                "    json_extract_scalar(bundle_item, '$.title') AS bundle_title, \n" +
                "    shopName,\n" +
                "    count(DISTINCT clientId) AS UV\n" + // 核心改动：对 clientId 进行去重计数
                "FROM \n" +
                "    log, \n" +
                "    UNNEST(cast(json_extract(extra, '$.bundle') AS array(json))) AS t(bundle_item)\n" +
                "WHERE \n" +
                "    shopName = '" + shopName + "' \n" +
                "    AND json_extract_scalar(bundle_item, '$.title') = '" + bundleTitle + "'\n" +
                "GROUP BY \n" +
                "    bundle_title, shopName";
    }

    // 计数所有含有bundle_title，且bundle_title不为NO_BUNDLE_TITLE的uv
    public static String getUvByShopNameAndBundleTitleIsNotNull(String shopName, String eventName) {
        return "* |\n" +
                "SELECT \n" +
                "    count(DISTINCT clientId) AS bundle_checkout_uv\n" +
                "FROM log\n" +
                "WHERE \n" +
                "    shopName = '" + shopName + "' \n" +
                "    AND event = '" + eventName + "'\n" +
                "    AND any_match(\n" +
                "        cast(json_extract(json_parse(extra), '$.bundle') AS array(json)), \n" +
                "        x -> (\n" +
                "            json_extract_scalar(x, '$.title') IS NOT NULL \n" +
                "            AND json_extract_scalar(x, '$.title') <> 'NO_BUNDLE_TITLE'\n" +
                "        )\n" +
                "    )";
    }



    // 根据shopName 获取支付金额的总和
    public static String getTotalPriceByShopName(String shopName, String eventName) {
        return "* | \n" +
                "SELECT \n" +
                "    json_extract_scalar(bundle_item, '$.price.currencyCode') AS currency,\n" +
                "    sum(cast(json_extract_scalar(bundle_item, '$.price.amount') AS double)) AS total_bundle_amount\n" +
                "FROM \n" +
                "    log, \n" +
                "    UNNEST(cast(json_extract(extra, '$.bundle') AS array(json))) AS t(bundle_item)\n" +
                "WHERE \n" +
                "    shopName = '" + shopName + "' \n" +
                "    AND event = '" + eventName + "'\n" +
                "    AND json_extract_scalar(bundle_item, '$.title') != 'NO_BUNDLE_TITLE'\n" +
                "GROUP BY \n" +
                "    currency";
    }

    // 根据shopName， bundleTitle获取支付金额的数组
    public static String getTotalPriceByShopNameAndBundleTitle(String shopName, String bundleTitle, String eventName) {
        return "* | \n" +
                "SELECT \n" +
                "    json_extract_scalar(bundle_item, '$.price.currencyCode') AS currency,\n" +
                "    sum(cast(json_extract_scalar(bundle_item, '$.price.amount') AS double)) AS total_bundle_amount\n" +
                "FROM \n" +
                "    log, \n" +
                "    UNNEST(cast(json_extract(extra, '$.bundle') AS array(json))) AS t(bundle_item)\n" +
                "WHERE \n" +
                "    shopName = '" + shopName + "' \n" +
                "    AND event = '" + eventName + "'\n" +
                "    AND json_extract_scalar(bundle_item, '$.title') = '" + bundleTitle + "'\n" +
                "GROUP BY \n" +
                "    currency";
    }

    // 根据shopName获取有bundle_title且bundle_title不为NO_BUNDLE_TITLE的所有支付金额
    public static String getTotalPriceByShopNameAndBundleTitleIsNotNull(String shopName, String eventName) {
        //  核心过滤：判断 bundle 数组中是否存在 title 不为 NO_BUNDLE_TITLE 的有效 bundle
        return "* |\n" +
                "SELECT \n" +
                "    json_extract_scalar(extra, '$.totalPrice.currencyCode') AS currency,\n" +
                "    sum(cast(json_extract_scalar(extra, '$.totalPrice.amount') AS double)) AS total_sum\n" +
                "FROM log\n" +
                "WHERE \n" +
                "    shopName = '" + shopName + "' \n" +
                "    AND event = '" + eventName + "' \n" +
                "    AND any_match(\n" +
                "        cast(json_extract(extra, '$.bundle') AS array(json)), \n" +
                "        x -> (json_extract_scalar(x, '$.title') IS NOT NULL AND json_extract_scalar(x, '$.title') != 'NO_BUNDLE_TITLE')\n" +
                "    )\n" +
                "GROUP BY \n" +
                "    currency";
    }

    // 有一个订单id，并且订单id上报的数据中代表享受了我们生成的优惠就+1
    public static String getOrderCountByShopName(String shopName, String eventName) {
        return "* |\n" +
                "SELECT \n" +
                "    count(*) AS valid_bundle_order_count\n" +
                "FROM log\n" +
                "WHERE \n" +
                "    shopName = '" + shopName + "' \n" +
                "    AND event = '" + eventName + "'\n" +
                "    AND any_match(\n" +
                "        cast(json_extract(extra, '$.bundle') AS array(json)), \n" +
                "        x -> (json_extract_scalar(x, '$.title') IS NOT NULL AND json_extract_scalar(x, '$.title') != 'NO_BUNDLE_TITLE')\n" +
                "    )";
    }

    public static String getOrderCountByShopName(String shopName, String eventName, String bundleTitle) {
        return "* |\n" +
                "SELECT \n" +
                "    count(*) AS valid_bundle_order_count\n" +
                "FROM log\n" +
                "WHERE \n" +
                "    shopName = '" + shopName + "' \n" +
                "    AND event = '" + eventName + "'\n" +
                "    AND any_match(\n" +
                "        cast(json_extract(extra, '$.bundle') AS array(json)), \n" +
                "        x -> (json_extract_scalar(x, '$.title') = '" + bundleTitle + "')\n" +
                "    )";
    }

    // 计数所有含有bundle_title，且bundle_title不为NO_BUNDLE_TITLE的pv
    public static String getPvByShopNameAndBundleTitleIsNotNull(String shopName, String eventName) {
        return "* |\n" +
                "SELECT \n" +
                "    count(*) AS pv\n" +
                "FROM log\n" +
                "WHERE \n" +
                "    shopName = '" + shopName + "' \n" +
                "    AND event = '" + eventName + "'\n" +
                "    AND any_match(\n" +
                "        cast(json_extract(json_parse(extra), '$.bundle') AS array(json)), \n" +
                "        x -> (\n" +
                "            json_extract_scalar(x, '$.title') IS NOT NULL \n" +
                "            AND json_extract_scalar(x, '$.title') <> 'NO_BUNDLE_TITLE'\n" +
                "        )\n" +
                "    )";
    }

    public static String getPvByShopNameAndBundleTitleIsNotNull(String shopName, String eventName, String discountTitle) {
        return "* |\n" +
                "SELECT \n" +
                "    count(*) AS pv\n" +
                "FROM log\n" +
                "WHERE \n" +
                "    shopName = '" + shopName + "' \n" +
                "    AND event = '" + eventName + "'\n" +
                "    AND any_match(\n" +
                "        cast(json_extract(json_parse(extra), '$.bundle') AS array(json)), \n" +
                "        x -> (json_extract_scalar(x, '$.title') = '" + discountTitle + "')\n" +
                "    )";
    }

    // 获取指定时间内，指定用户的金额数据
    public static String getTotalPriceByShopName(String shopName, String eventName, int day) {
        return "* |\n" +
                "SELECT \n" +
                "    date_trunc('day', __time__) AS date,\n" +
                "    json_extract_scalar(extra, '$.totalPrice.currencyCode') AS currency,\n" +
                "    sum(cast(json_extract_scalar(extra, '$.totalPrice.amount') AS double)) AS daily_total_amount\n" +
                "FROM log\n" +
                "WHERE \n" +
                "    shopName = '" + shopName + "' \n" +
                "    AND event = '" + eventName + "'\n" +
                "    AND __time__ >= to_unixtime(now() - interval '" + day + "' day) \n" +
                "GROUP BY \n" +
                "    date, currency\n" +
                "ORDER BY \n" +
                "    date DESC";
    }
    public static String getTotalPriceByShopName(String shopName, String eventName, int day, String discountName) {
        return "* |\n" +
                "SELECT \n" +
                "    date_trunc('day', __time__) AS date,\n" +
                "    json_extract_scalar(extra, '$.totalPrice.currencyCode') AS currency,\n" +
                "    sum(cast(json_extract_scalar(extra, '$.totalPrice.amount') AS double)) AS daily_total_amount\n" +
                "FROM log\n" +
                "WHERE \n" +
                "    shopName = '" + shopName + "' \n" +
                "    AND event = '" + eventName + "'\n" +
                "    AND __time__ >= to_unixtime(now() - interval '" + day + "' day) \n" +
                "    AND any_match(\n" +
                "        cast(json_extract(json_parse(extra), '$.bundle') AS array(json)), \n" +
                "        x -> (json_extract_scalar(x, '$.title') = '" + discountName + "')\n" +
                "    )\n" +
                "GROUP BY \n" +
                "    date, currency\n" +
                "ORDER BY \n" +
                "    date DESC";
    }
}
