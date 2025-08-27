package com.bogdatech.utils;

import java.util.HashMap;
import java.util.Map;

import static com.bogdatech.constants.TranslateConstants.*;

public class ProgressBarUtils {

    /**
     * 根据用户翻译模块的顺序，选择对应的进度
     * */
    public static Map<String, Integer> getProgressBar(String resourceType){
        Map<String, Integer> progressData = new HashMap<>();
        progressData.put("TotalQuantity", 100);
        progressData.put("TranslateType", 1); // 1代表私有key
        switch (resourceType) {
            case SHOP:
                progressData.put("RemainingQuantity", 10);
                return progressData;
            case PAGE:
                progressData.put("RemainingQuantity", 20);
                return progressData;
            case ONLINE_STORE_THEME:
                progressData.put("RemainingQuantity", 35);
                return progressData;
            case PRODUCT:
                progressData.put("RemainingQuantity", 55);
                return progressData;
            case PRODUCT_OPTION:
                progressData.put("RemainingQuantity", 58);
                return progressData;
            case PRODUCT_OPTION_VALUE:
                progressData.put("RemainingQuantity", 60);
                return progressData;
            case COLLECTION:
                progressData.put("RemainingQuantity", 62);
                return progressData;
            case METAFIELD:
                progressData.put("RemainingQuantity", 68);
                return progressData;
            case ARTICLE:
                progressData.put("RemainingQuantity", 70);
                return progressData;
            case BLOG:
                progressData.put("RemainingQuantity", 75);
                return progressData;
            case MENU:
                progressData.put("RemainingQuantity", 77);
                return progressData;
            case LINK:
                progressData.put("RemainingQuantity", 78);
                return progressData;
            case FILTER:
                progressData.put("RemainingQuantity", 79);
                return progressData;
            case METAOBJECT:
                progressData.put("RemainingQuantity", 80);
                return progressData;
            case ONLINE_STORE_THEME_JSON_TEMPLATE:
                progressData.put("RemainingQuantity", 81);
                return progressData;
            case ONLINE_STORE_THEME_SECTION_GROUP:
                progressData.put("RemainingQuantity", 82);
                return progressData;
            case ONLINE_STORE_THEME_SETTINGS_DATA_SECTIONS:
                progressData.put("RemainingQuantity", 83);
                return progressData;
            case PACKING_SLIP_TEMPLATE:
                progressData.put("RemainingQuantity", 85);
                return progressData;
            case DELIVERY_METHOD_DEFINITION:
                progressData.put("RemainingQuantity", 86);
                return progressData;
            case SHOP_POLICY:
                progressData.put("RemainingQuantity", 88);
                return progressData;
            case PAYMENT_GATEWAY:
                progressData.put("RemainingQuantity", 95);
                return progressData;
            case SELLING_PLAN:
                progressData.put("RemainingQuantity", 97);
                return progressData;
            case SELLING_PLAN_GROUP:
                progressData.put("RemainingQuantity", 99);
                return progressData;

            default:
                progressData.put("RemainingQuantity", 0);
                return progressData;
        }
    }
}
