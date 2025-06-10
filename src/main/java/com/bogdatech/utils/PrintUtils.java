package com.bogdatech.utils;

import java.util.HashMap;
import java.util.Map;

import static com.bogdatech.utils.CaseSensitiveUtils.appInsights;
public class PrintUtils {

    /**
     * 自定义打印类型，用与打印用户翻译前后的数据
     *
     * @param targetText   用户翻译后的文本
     * @param sourceText   用户翻译前的文本
     * @param translation  用户翻译的其他数据
     * @param shopName     用户店铺名称
     * @param resourceType 用户翻译的模块类型
     */
    public static void printTranslation(String targetText, String sourceText, Map<String, Object> translation, String shopName, String resourceType, String resourceId, String source) {
        //设置customDimensions为用户的翻译数据
        HashMap<String, String> properties = new HashMap<>();
        properties.put("targetText", targetText);
        properties.put("sourceText", sourceText);
        properties.put("translation", translation.toString());

        //设置customMeasurements为用户的模块数据
        HashMap<String, Double> metrics = new HashMap<>();
        metrics.put(resourceType + "   " + source, 1D);

        //设置打印name为用户的shopName
        appInsights.trackEvent(shopName + " " + resourceId + " ", properties, metrics);
    }
}
