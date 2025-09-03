package com.bogdatech.utils;

import java.util.function.Function;
import java.util.function.Supplier;

import static com.bogdatech.utils.CaseSensitiveUtils.appInsights;

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
                    appInsights.trackTrace("retryWithParam 第 " + attempt + " 次失败，等待 " + delay + " 毫秒后重试...");
                    Thread.sleep(delay);
                    delay = Math.min(delay * 2, maxDelayMillis);
                }
            } catch (Exception e) {
                appInsights.trackException(e);
                appInsights.trackTrace("retryWithParam 执行出错（第 " + attempt + " 次）：" + e.getMessage());

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


    /**
     * 通用重试方法
     * @param action         需要执行的逻辑（返回 T 类型结果）
     * @param maxRetries     最大重试次数
     * @param baseDelayMs    初始延迟（毫秒）
     * @param <T>            返回结果类型
     * @return               成功返回结果，失败返回 null
     */
    public static <T> T executeWithRetry(Supplier<T> action, int maxRetries, long baseDelayMs) {
        int attempt = 0;
        Exception lastException = null;

        while (attempt < maxRetries) {
            try {
                attempt++;
                return action.get(); // 尝试执行任务
            } catch (Exception e) {
                lastException = e;
                System.err.println("执行失败，第 " + attempt + " 次，错误：" + e.getMessage());

                if (attempt >= maxRetries) {
                    break; // 达到最大次数
                }

                // 指数退避：1s, 2s, 4s...
                try {
                    Thread.sleep(baseDelayMs * (1L << (attempt - 1)));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        if (lastException != null) {
            lastException.printStackTrace();
        }
        return null; // 全部失败，返回 null
    }
}
