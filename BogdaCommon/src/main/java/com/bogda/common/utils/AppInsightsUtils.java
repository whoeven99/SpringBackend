package com.bogda.common.utils;
public class AppInsightsUtils {

    /**
     * 翻译消耗相关打印
     * 走我们的API
     */
    public static void printTranslateCost(int totalToken, int inputTokens, int outputTokens) {
        try {
            CaseSensitiveUtils.appInsights.trackMetric("Ciwi-Translator our total token", totalToken);
            CaseSensitiveUtils.appInsights.trackMetric("Ciwi-Translator our input token", inputTokens);
            CaseSensitiveUtils.appInsights.trackMetric("Ciwi-Translator our out token", outputTokens);
        } catch (Exception e) {
            CaseSensitiveUtils.appInsights.trackException(e);
        }
    }
}
