package com.bogda.common.reporter;

/**
 * 静态持有异常上报实现，由上层在启动时通过 setReporter 注入。
 */
public final class ExceptionReporterHolder {
    private static volatile ExceptionReporter reporter;

    public static void setReporter(ExceptionReporter r) {
        reporter = r;
    }

    public static void report(String scene, Throwable ex) {
        if (reporter != null) {
            reporter.report(scene, ex);
        }
    }
}
