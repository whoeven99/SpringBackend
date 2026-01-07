package com.bogda.api.utils;

import com.bogda.api.exception.FatalException;

import java.util.concurrent.*;
import java.util.function.Supplier;

import com.bogda.common.utils.AppInsightsUtils;
import com.google.common.util.concurrent.RateLimiter;
public class TimeOutUtils {
    public static final int DEFAULT_TIMEOUT = 5;
    public static final TimeUnit DEFAULT_UNIT = TimeUnit.MINUTES;
    public static final int DEFAULT_MAX_RETRIES = 3;
    public static RateLimiter rateLimiter1 = RateLimiter.create(1.0);
    public static RateLimiter rateLimiter2 = RateLimiter.create(2.0);

    public static <T> T callWithTimeoutAndRetry(Supplier<T> task,
                                                long timeout,
                                                TimeUnit unit,
                                                int maxRetries, RateLimiter rateLimiter) throws Exception {
        Exception lastException = new FatalException("调用超时，且重试无用");

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            rateLimiter.acquire(); // 获取许可，确保符合 QPS 限制
            ExecutorService executor = Executors.newSingleThreadExecutor();
            Future<T> future = executor.submit(task::get);

            try {
                // 等待任务完成（带超时）
                return future.get(timeout, unit);
            } catch (TimeoutException e) {
                future.cancel(true);
                lastException = e;
                AppInsightsUtils.trackTrace("FatalException task 调用超时（" + timeout + " " + unit + "），正在重试... [第" + attempt + "次]");
                AppInsightsUtils.trackException(e);
            } catch (Exception e) {
                future.cancel(true);
                lastException = e;
                AppInsightsUtils.trackTrace("FatalException 调用异常: " + e.getMessage() + "，正在重试... [第" + attempt + "次]");
                AppInsightsUtils.trackException(e);
            } finally {
                executor.shutdownNow(); // 确保线程被回收
            }
        }

        throw lastException; // 所有重试都失败，抛出最后的异常
    }

    public static <T> T callWithTimeoutAndRetry(Supplier<T> task,
                                                long timeout,
                                                TimeUnit unit,
                                                int maxRetries) throws Exception {
        Exception lastException = new FatalException("调用超时，且重试无用");

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            ExecutorService executor = Executors.newSingleThreadExecutor();
            Future<T> future = executor.submit(task::get);

            try {
                // 等待任务完成（带超时）
                return future.get(timeout, unit);
            } catch (TimeoutException e) {
                future.cancel(true);
                lastException = e;
                AppInsightsUtils.trackTrace("FatalException task 调用超时（" + timeout + " " + unit + "），正在重试... [第" + attempt + "次]");
                AppInsightsUtils.trackException(e);
            } catch (Exception e) {
                future.cancel(true);
                lastException = e;
                AppInsightsUtils.trackTrace("FatalException 调用异常: " + e.getMessage() + "，正在重试... [第" + attempt + "次]");
                AppInsightsUtils.trackException(e);
            } finally {
                executor.shutdownNow(); // 确保线程被回收
            }
        }

        throw lastException; // 所有重试都失败，抛出最后的异常
    }

    public static <T> T callWithTimeoutAndRetry(Supplier<T> task) throws Exception {
        return callWithTimeoutAndRetry(task, DEFAULT_TIMEOUT, DEFAULT_UNIT, DEFAULT_MAX_RETRIES);
    }

    public static <T> T callWithTimeoutAndRetry(Supplier<T> task, RateLimiter rateLimiter) throws Exception {
        return callWithTimeoutAndRetry(task, DEFAULT_TIMEOUT, DEFAULT_UNIT, DEFAULT_MAX_RETRIES, rateLimiter);
    }
}
