package com.bogda.common.utils;

import java.util.function.Function;

import static com.bogda.common.utils.CaseSensitiveUtils.appInsights;

public class RetryUtils {

    /**
     * 支持带参数的重试机制（带指数退避）。
     *
     * @param <T> 输入参数的类型
     * @param task 带一个参数的函数，返回 boolean 表示是否成功
     * @param input 传入的参数
     * @param maxRetries 最大重试次数
     * @param initialDelayMillis 初始延迟时间（毫秒）
     * @param maxDelayMillis 最大延迟时间（毫秒）
     * @return 如果在尝试次数内成功返回 true，否则返回 false。
     */
    public static <T> boolean retryWithParam(Function<T, Boolean> task,
                                             T input,
                                             int maxRetries,
                                             long initialDelayMillis,
                                             long maxDelayMillis) {
        long delay = initialDelayMillis;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                if (task.apply(input)) {
                    return true;
                }

                if (attempt < maxRetries) {
                    appInsights.trackTrace("FatalException retryWithParam 第 " + attempt + " 次失败，等待 " + delay + " 毫秒后重试...");
                    Thread.sleep(delay);
                    delay = Math.min(delay * 2, maxDelayMillis);
                }
            } catch (Exception e) {
                appInsights.trackException(e);
                appInsights.trackTrace("FatalException retryWithParam 执行出错（第 " + attempt + " 次）：" + e.getMessage());

                if (attempt < maxRetries) {
                    try {
                        Thread.sleep(delay);
                        delay = Math.min(delay * 2, maxDelayMillis);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return false;
                    }
                }
            }
        }

        return false;
    }

}
