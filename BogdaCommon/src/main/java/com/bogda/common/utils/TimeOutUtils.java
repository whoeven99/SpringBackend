package com.bogda.common.utils;

import com.bogda.common.exception.FatalException;

import java.util.concurrent.*;
import java.util.function.Supplier;

public class TimeOutUtils {
    public static final int DEFAULT_TIMEOUT = 5;
    public static final TimeUnit DEFAULT_UNIT = TimeUnit.MINUTES;
    public static final int DEFAULT_MAX_RETRIES = 3;

    /**
     * 判断是否为 400 类错误（不重试，直接抛出给上层处理）。
     * 不依赖 Azure SDK，通过异常消息或反射识别 HttpResponseException(status=400)。
     */
    public static boolean isBadRequest(Throwable t) {
        while (t != null) {
            if (isHttpResponse400(t)) {
                return true;
            }
            String msg = t.getMessage();
            if (msg != null && (msg.contains("400") || msg.contains("Bad Request")
                    || msg.contains("INVALID_ARGUMENT") || msg.contains("InvalidParameter"))) {
                return true;
            }
            t = t.getCause();
        }
        return false;
    }

    private static boolean isHttpResponse400(Throwable t) {
        if (t == null || !"com.azure.core.exception.HttpResponseException".equals(t.getClass().getName())) {
            return false;
        }
        try {
            Object response = t.getClass().getMethod("getResponse").invoke(t);
            if (response != null) {
                Object code = response.getClass().getMethod("getStatusCode").invoke(response);
                if (Integer.valueOf(400).equals(code)) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
            // ignore
        }
        return false;
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
                // 400 不重试，直接抛出（兼容 ExecutionException 包装的异常）
                Throwable cause = (e instanceof ExecutionException && e.getCause() != null) ? e.getCause() : e;
                if (isBadRequest(cause)) {
                    if (cause instanceof Exception) {
                        throw (Exception) cause;
                    }
                    throw new RuntimeException(cause);
                }
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

    public static boolean isHttp400(Throwable e) {
        if (e == null) {
            return false;
        }
        String msg = e.getMessage();
        if (msg != null && (msg.contains("400") || msg.contains("InvalidParameter") || msg.contains("Bad Request"))) {
            return true;
        }
        return isHttp400(e.getCause()); // 支持 RuntimeException(cause) 包装
    }
}
