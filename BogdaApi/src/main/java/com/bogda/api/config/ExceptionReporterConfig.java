package com.bogda.api.config;

import com.bogda.common.reporter.ExceptionReporterHolder;
import com.bogda.common.reporter.TraceReporterHolder;
import com.bogda.integration.aimodel.AliyunSlsIntegration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

/**
 * 启动时将 SLS 的 logException / logTrace 注册为 Common 层异常与 Trace 上报实现。
 */
@Component
public class ExceptionReporterConfig {

    @Autowired
    private AliyunSlsIntegration aliyunSlsIntegration;

    @PostConstruct
    public void init() {
        ExceptionReporterHolder.setReporter(aliyunSlsIntegration::logException);
        TraceReporterHolder.setReporter(aliyunSlsIntegration::logTrace);
    }
}
