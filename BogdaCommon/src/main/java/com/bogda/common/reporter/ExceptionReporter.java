package com.bogda.common.reporter;

/**
 * 异常上报抽象，由上层（如 SLS、AppInsights）实现，供 Common 内静态工具类使用。
 */
@FunctionalInterface
public interface ExceptionReporter {
    void report(String scene, Throwable ex);
}
