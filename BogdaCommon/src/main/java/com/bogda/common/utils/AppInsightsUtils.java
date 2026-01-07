package com.bogda.common.utils;

import com.microsoft.applicationinsights.TelemetryClient;

public class AppInsightsUtils {
    public static TelemetryClient AppInsightsUtils = new TelemetryClient();

    public static void trackTrace(String message, Object... args) {
        AppInsightsUtils.trackTrace(String.format(message, args));
    }

    public static void trackException(Exception e) {
        AppInsightsUtils.trackException(e);
    }

    /**
     * 翻译消耗相关打印
     * 走我们的API
     */
    public static void printTranslateCost(int totalToken, int inputTokens, int outputTokens) {
        try {
            AppInsightsUtils.trackMetric("Ciwi-Translator our total token", totalToken);
            AppInsightsUtils.trackMetric("Ciwi-Translator our input token", inputTokens);
            AppInsightsUtils.trackMetric("Ciwi-Translator our out token", outputTokens);
        } catch (Exception e) {
            AppInsightsUtils.trackException(e);
        }
    }
}
