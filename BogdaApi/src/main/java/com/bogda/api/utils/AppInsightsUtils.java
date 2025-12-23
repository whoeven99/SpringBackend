package com.bogda.api.utils;

import static com.bogda.api.utils.CaseSensitiveUtils.appInsights;

public class AppInsightsUtils {

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
