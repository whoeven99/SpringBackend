package com.bogda.task.task;

import com.bogda.common.reporter.TraceReporterHolder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * IP 周报邮件：Switcher 配置已迁移至 TSF，Spring 侧不再维护 ipOpen 店铺列表。
 * 待 TSF 侧接入后恢复。
 */
@Component
public class IpEmailTask {

    @Scheduled(cron = "0 0 4 ? * SAT")
    public void sendEmailTask() {
        TraceReporterHolder.report(
                "IpEmailTask.sendEmailTask",
                "skipped: switcher config migrated to TSF; IP report email disabled in Spring");
    }
}
