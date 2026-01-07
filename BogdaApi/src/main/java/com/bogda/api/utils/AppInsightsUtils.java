package com.bogda.api.utils;

import com.microsoft.applicationinsights.TelemetryClient;

public class AppInsightsUtils {
    public static TelemetryClient appInsights = new TelemetryClient();

    public static void trackTrace(String message, Object... args) {
        appInsights.trackTrace(String.format(message, args));
    }

    public static void trackException(Exception e) {
        appInsights.trackException(e);
    }

    /**
     * 翻译消耗相关打印
     * 走我们的API
     */
    public static void printTranslateCost(int totalToken, int inputTokens, int outputTokens) {
        try {
            appInsights.trackMetric("Ciwi-Translator our total token", totalToken);
            appInsights.trackMetric("Ciwi-Translator our input token", inputTokens);
            appInsights.trackMetric("Ciwi-Translator our out token", outputTokens);
        } catch (Exception e) {
            appInsights.trackException(e);
        }
    }
}
