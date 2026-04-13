package com.bogda.common.utils;

import com.bogda.common.reporter.ExceptionReporterHolder;
import com.bogda.common.reporter.TraceReporterHolder;
import java.util.concurrent.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.function.Supplier;

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
                    TraceReporterHolder.report("RetryUtils.retryWithParam", "FatalException retryWithParam 第 " + attempt + " 次失败，等待 " + delay + " 毫秒后重试...");
                    Thread.sleep(delay);
                    delay = Math.min(delay * 2, maxDelayMillis);
                }
            } catch (Exception e) {
                ExceptionReporterHolder.report("RetryUtils.retryWithParam",e);
                TraceReporterHolder.report("RetryUtils.retryWithParam", "FatalException retryWithParam 执行出错（第 " + attempt + " 次）");

                if (attempt < maxRetries) {
                    try {
                        Thread.sleep(delay);
                        delay = Math.min(delay * 2, maxDelayMillis);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        ExceptionReporterHolder.report("RetryUtils.retryWithParam",ie);
                        return false;
                    }
                }
            }
        }

        return false;
    }

    /**
     * 通用带指数退避 + 超时控制的重试方法
     * - 成功立即返回结果 T
     * - 仅在任务抛出异常时进行指数退避重试（最多 maxRetries 次）
     * - 新增超时控制（单次任务超时）
     * - 不再泄漏 ExecutorService 线程
     * - 日志更清晰：重试时不打 Fatal，最终失败才打 Fatal
     * - 最后失败返回 null（符合你之前的 Kimi 返回风格）
     */
    public static final int DEFAULT_MAX_RETRIES = 2;          // 最多重试 2 次（总共尝试 3 次）
    public static final long DEFAULT_INITIAL_DELAY = 1000;    // 初始退避 1 秒
    public static final long DEFAULT_MAX_DELAY = 30000;       // 最大退避 30 秒
    public static final long DEFAULT_TIMEOUT = 5;            // 单次请求超时 5 分钟
    public static final TimeUnit DEFAULT_UNIT = TimeUnit.MINUTES;
    public static <T> T retryWithParamAndTime(Supplier<T> task,
                                       int maxRetries,
                                       long initialDelayMillis,
                                       long maxDelayMillis,
                                       long timeout,
                                       TimeUnit unit) {

        long delay = initialDelayMillis;

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            ExecutorService executor = Executors.newSingleThreadExecutor();
            try {
                Future<T> future = executor.submit(task::get);
                T result = future.get(timeout, unit);   // 带超时等待

                executor.shutdown();
                return result;                          // 成功直接返回
            } catch (Exception e) {                     // 超时、执行异常等都视为失败
                executor.shutdownNow();                 // 立即关闭线程池

                if (attempt == maxRetries) {
                    // 最终失败
                    ExceptionReporterHolder.report("RetryUtils.retryWithParamAndTime", e);
                    TraceReporterHolder.report("RetryUtils.retryWithParamAndTime",
                            "FatalException retryWithParamAndTime 执行出错（第 " + (attempt + 1) + " 次，最终失败）");
                    return null;
                }

                // 重试日志 + 指数退避
                TraceReporterHolder.report("RetryUtils.retryWithParamAndTime",
                        "retryWithParamAndTime 第 " + (attempt + 1) + " 次失败，等待 " + delay + " 毫秒后重试...");

                try {
                    Thread.sleep(delay);
                    delay = Math.min(delay * 2, maxDelayMillis);   // 指数退避
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    ExceptionReporterHolder.report("RetryUtils.retryWithParamAndTime", ie);
                    return null;
                }
            } finally {
                if (!executor.isShutdown()) {
                    executor.shutdownNow();
                }
            }
        }
        return null;
    }
}
