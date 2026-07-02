package com.bogda.task.task;

import com.bogda.common.reporter.ExceptionReporterHolder;
import com.bogda.common.reporter.TraceReporterHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 一次性清理：已迁移至 TSF 的 Spring 侧冗余表数据。
 * <p>
 * 启用：部署 BogdaTask 时设置 {@code bogda.migrated-tables-cleanup.enabled=true}，
 * 跑完确认日志后改回 {@code false} 并重新部署。
 */
@Component
public class MigratedTablesDataCleanupTask {
    private static final List<String> TABLES_IN_DELETE_ORDER = List.of(
            "User_RightsAndInterests",
            "RightsAndInterests",
            "Glossary",
            "WidgetConfigurations",
            "User_Liquid",
            "User_Page_Fly",
            "Translate_Save_Failed_Tasks",
            "User_Translation_Data",
            "Vocabulary"
    );

    private final AtomicBoolean started = new AtomicBoolean(false);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Value("${bogda.migrated-tables-cleanup.enabled:false}")
    private boolean enabled;

    @Value("${spring.profiles.active:${ApplicationEnv:${spring.config.activate.on-profile:local}}}")
    private String env;

    @EventListener(ApplicationReadyEvent.class)
    public void runOnceOnStartup() {
        if (!enabled || !started.compareAndSet(false, true)) {
            return;
        }
        if (!isCloudEnv(env)) {
            TraceReporterHolder.report(
                    "MigratedTablesDataCleanupTask.runOnceOnStartup",
                    "skipped: enabled but env is not test/prod (" + env + ")");
            return;
        }

        TraceReporterHolder.report(
                "MigratedTablesDataCleanupTask.runOnceOnStartup",
                "starting migrated tables data cleanup, tableCount=" + TABLES_IN_DELETE_ORDER.size());

        for (String table : TABLES_IN_DELETE_ORDER) {
            clearTable(table);
        }

        TraceReporterHolder.report(
                "MigratedTablesDataCleanupTask.runOnceOnStartup",
                "migrated tables data cleanup finished");
    }

    private void clearTable(String tableName) {
        String qualified = "dbo.[" + tableName + "]";
        try {
            Long before = countRows(tableName);
            jdbcTemplate.execute("TRUNCATE TABLE " + qualified);
            TraceReporterHolder.report(
                    "MigratedTablesDataCleanupTask.clearTable",
                    "TRUNCATE " + tableName + " ok, rowsBefore=" + before);
        } catch (Exception truncateError) {
            try {
                Long before = countRows(tableName);
                int deleted = jdbcTemplate.update("DELETE FROM " + qualified);
                TraceReporterHolder.report(
                        "MigratedTablesDataCleanupTask.clearTable",
                        "DELETE " + tableName + " ok, rowsBefore=" + before + ", rowsDeleted=" + deleted
                                + ", truncateFailed=" + truncateError.getMessage());
            } catch (Exception deleteError) {
                ExceptionReporterHolder.report("MigratedTablesDataCleanupTask.clearTable." + tableName, deleteError);
                TraceReporterHolder.report(
                        "MigratedTablesDataCleanupTask.clearTable",
                        "FatalException failed " + tableName + ": " + deleteError.getMessage());
            }
        }
    }

    private Long countRows(String tableName) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT COUNT_BIG(1) FROM dbo.[" + tableName + "]",
                    Long.class);
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isCloudEnv(String rawEnv) {
        if (rawEnv == null || rawEnv.trim().isEmpty()) {
            return false;
        }
        return Arrays.stream(rawEnv.split(","))
                .map(String::trim)
                .anyMatch(v -> "test".equalsIgnoreCase(v) || "prod".equalsIgnoreCase(v));
    }
}
