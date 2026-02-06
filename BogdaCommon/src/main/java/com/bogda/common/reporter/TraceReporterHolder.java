package com.bogda.common.reporter;

/**
 * 静态持有 Trace 上报实现，由上层在启动时通过 setReporter 注入。
 */
public final class TraceReporterHolder {
    private static volatile TraceReporter reporter;

    public static void setReporter(TraceReporter r) {
        reporter = r;
    }

    public static void report(String traceName, String message) {
        if (reporter != null) {
            reporter.report(traceName, message);
        }
    }
}
