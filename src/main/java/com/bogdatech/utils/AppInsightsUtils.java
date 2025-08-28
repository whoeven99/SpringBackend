package com.bogdatech.utils;

import static com.bogdatech.utils.CaseSensitiveUtils.appInsights;

public class AppInsightsUtils {

    /**
     * 翻译消耗相关打印
     * 走我们的API
     *
     * @param totalToken   总token
     * @param inputTokens  输入token
     * @param outputTokens 输出token
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

    /**
     * 翻译消耗相关打印
     * 走用户的API
     * @param totalToken   总token
     */
    public static void printPrivateTranslateCost(int totalToken) {
        try {
            appInsights.trackMetric("Ciwi-Translator private total token", totalToken);
        } catch (Exception e) {
            appInsights.trackException(e);
        }
    }
}
