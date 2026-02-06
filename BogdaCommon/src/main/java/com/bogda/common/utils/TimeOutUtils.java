package com.bogda.common.utils;

import com.bogda.common.exception.FatalException;
import com.bogda.common.reporter.ExceptionReporterHolder;
import com.bogda.common.reporter.TraceReporterHolder;

import java.util.concurrent.*;
import java.util.function.Supplier;

public class TimeOutUtils {
    public static final int DEFAULT_TIMEOUT = 5;
    public static final TimeUnit DEFAULT_UNIT = TimeUnit.MINUTES;
    public static final int DEFAULT_MAX_RETRIES = 3;

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
                TraceReporterHolder.report("TimeOutUtils.callWithTimeoutAndRetry",
                        "FatalException task 调用超时（" + timeout + " " + unit + "），正在重试... [第" + attempt + "次]");
                ExceptionReporterHolder.report("TimeOutUtils.callWithTimeoutAndRetry", e);
            } catch (Exception e) {
                future.cancel(true);
                lastException = e;
                TraceReporterHolder.report("TimeOutUtils.callWithTimeoutAndRetry",
                        "FatalException 调用异常: " + e.getMessage() + "，正在重试... [第" + attempt + "次]");
                ExceptionReporterHolder.report("TimeOutUtils.callWithTimeoutAndRetry", e);
            } finally {
                executor.shutdownNow(); // 确保线程被回收
            }
        }

        throw lastException; // 所有重试都失败，抛出最后的异常
    }

    public static <T> T callWithTimeoutAndRetry(Supplier<T> task) throws Exception {
        return callWithTimeoutAndRetry(task, DEFAULT_TIMEOUT, DEFAULT_UNIT, DEFAULT_MAX_RETRIES);
    }
}
