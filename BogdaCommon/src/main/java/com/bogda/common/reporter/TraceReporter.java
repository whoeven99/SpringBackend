package com.bogda.common.reporter;

/**
 * Trace 日志上报抽象，由上层（如 SLS、AppInsights）实现，供 Common 内静态工具类使用。
 */
@FunctionalInterface
public interface TraceReporter {
    void report(String traceName, String message);
}
