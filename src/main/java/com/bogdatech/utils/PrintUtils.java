package com.bogdatech.utils;

import com.microsoft.applicationinsights.TelemetryClient;

import java.util.HashMap;
import java.util.Map;

public class PrintUtils {
    static TelemetryClient appInsights = new TelemetryClient();
    public static void printTranslation(String targetText, String sourceText, Map<String, Object> translation, String shopName ,String resourceType) {
        //设置customDimensions为用户的翻译数据
        HashMap<String, String> properties = new HashMap<>();
        properties.put("targetText", targetText);
        properties.put("sourceText", sourceText);
        properties.put("translation", translation.toString());

        //设置customMeasurements为用户的模块数据
        HashMap<String, Double> metrics = new HashMap<>();
        metrics.put(resourceType, 1D);

        //设置打印name为用户的shopName
        appInsights.trackEvent(shopName, properties, metrics);
    }
}
