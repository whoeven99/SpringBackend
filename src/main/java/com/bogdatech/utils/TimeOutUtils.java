package com.bogdatech.utils;

import com.bogdatech.exception.FatalException;

import java.util.concurrent.*;
import java.util.function.Supplier;

import static com.bogdatech.utils.CaseSensitiveUtils.appInsights;

public class TimeOutUtils {

    public static final int DEFAULT_TIMEOUT = 5;
    public static final TimeUnit DEFAULT_UNIT = TimeUnit.MINUTES;
    public static final int DEFAULT_MAX_RETRIES = 3;

    /**
     * 带超时和重试的外部 API 调用
     *
     * @param task       需要执行的任务
     * @param timeout    超时时间
     * @param unit       时间单位
     * @param maxRetries 最大重试次数
     * @param <T>        返回值类型
     * @return 成功返回结果，否则抛异常
     * @throws Exception 如果所有重试都失败
     */
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
                appInsights.trackTrace("每日须看 task 调用超时（" + timeout + " " + unit + "），正在重试... [第" + attempt + "次]");
                appInsights.trackException(e);
            } catch (Exception e) {
                future.cancel(true);
                lastException = e;
                appInsights.trackTrace("每日须看 调用异常: " + e.getMessage() + "，正在重试... [第" + attempt + "次]");
                appInsights.trackException(e);
            } finally {
                executor.shutdownNow(); // 确保线程被回收
            }
        }

        throw lastException; // 所有重试都失败，抛出最后的异常
    }

}
