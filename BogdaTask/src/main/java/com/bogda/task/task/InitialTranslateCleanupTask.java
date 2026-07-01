package com.bogda.task.task;

import com.bogda.common.reporter.ExceptionReporterHolder;
import com.bogda.repository.entity.InitialTaskV2DO;
import com.bogda.repository.repo.InitialTaskV2Repo;
import com.bogda.service.logic.translate.InitialTranslateCleanupService;
import javax.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;
import java.util.function.Supplier;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class InitialTranslateCleanupTask {
    private static final int RETENTION_DAYS = 3;
    private static final int CLEANUP_POOL_SIZE = 30;
    private static final int SCHEDULE_INTERVAL_MS = 3 * 60 * 1000;
    private static final int EXECUTOR_SHUTDOWN_TIMEOUT_SECONDS = 55;
    private static final int DRAIN_POLL_INTERVAL_MS = 1000;

    @Autowired
    private InitialTaskV2Repo initialTaskV2Repo;
    @Autowired
    private InitialTranslateCleanupService initialTranslateCleanupService;

    @Value("${spring.profiles.active:${ApplicationEnv:${spring.config.activate.on-profile:local}}}")
    private String env;

    private final ExecutorService cleanupExecutor = Executors.newFixedThreadPool(CLEANUP_POOL_SIZE);
    private final Set<Integer> cleaningInitialTaskIds = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean legacyOneTimeCleanupStarted = new AtomicBoolean(false);
    private final AtomicBoolean orphanOneTimeCleanupStarted = new AtomicBoolean(false);

    @PreDestroy
    public void shutdownExecutor() {
        cleanupExecutor.shutdown();
        try {
            if (!cleanupExecutor.awaitTermination(EXECUTOR_SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                cleanupExecutor.shutdownNow();
                cleanupExecutor.awaitTermination(10, TimeUnit.SECONDS);
            }
        } catch (InterruptedException e) {
            cleanupExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 一次性：已软删且 3 天内 + 未软删且超过 3 天的 Initial 任务物理清理。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void runLegacyInitialCleanupOnce() {
        if (!isCloudEnv(env) || !legacyOneTimeCleanupStarted.compareAndSet(false, true)) {
            return;
        }
        cleanupExecutor.submit(() -> drainInitialTasks(
                "InitialTranslateCleanupTask.runLegacyInitialCleanupOnce",
                () -> initialTaskV2Repo.selectOneEligibleForLegacyOneTimeCleanup(cleaningInitialTaskIds)));
    }

    /**
     * 一次性：孤儿 Translate 子任务物理清理。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void runOrphanTranslateCleanupOnce() {
        if (!isCloudEnv(env) || !orphanOneTimeCleanupStarted.compareAndSet(false, true)) {
            return;
        }
        cleanupExecutor.submit(() -> {
            try {
                initialTranslateCleanupService.physicalDeleteOrphanTranslateTasks();
            } catch (Exception e) {
                ExceptionReporterHolder.report("InitialTranslateCleanupTask.runOrphanTranslateCleanupOnce", e);
            }
        });
    }

    /**
     * 常规轮询：已软删且超过 retention 天的 Initial 任务物理清理。
     */
    @Scheduled(fixedRate = SCHEDULE_INTERVAL_MS)
    public void cleanExpiredInitialTasks() {
        if (!isCloudEnv(env)) {
            return;
        }
        submitEligibleTasks(() -> initialTaskV2Repo.selectOneEligibleForCleanup(
                RETENTION_DAYS, cleaningInitialTaskIds));
    }

    private void submitEligibleTasks(Supplier<InitialTaskV2DO> taskSupplier) {
        while (cleaningInitialTaskIds.size() < CLEANUP_POOL_SIZE) {
            InitialTaskV2DO task = taskSupplier.get();
            if (task == null) {
                break;
            }
            submitPhysicalDelete(task.getId());
        }
    }

    private void drainInitialTasks(String errorScene, Supplier<InitialTaskV2DO> taskSupplier) {
        try {
            while (true) {
                submitEligibleTasks(taskSupplier);
                if (cleaningInitialTaskIds.isEmpty() && taskSupplier.get() == null) {
                    break;
                }
                Thread.sleep(DRAIN_POLL_INTERVAL_MS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            ExceptionReporterHolder.report(errorScene, e);
        }
    }

    private void submitPhysicalDelete(Integer initialTaskId) {
        if (!cleaningInitialTaskIds.add(initialTaskId)) {
            return;
        }
        try {
            cleanupExecutor.submit(() -> {
                try {
                    initialTranslateCleanupService.physicalDeleteInitialTask(initialTaskId);
                } catch (Exception e) {
                    ExceptionReporterHolder.report("InitialTranslateCleanupTask.submitPhysicalDelete", e);
                } finally {
                    cleaningInitialTaskIds.remove(initialTaskId);
                }
            });
        } catch (RejectedExecutionException e) {
            cleaningInitialTaskIds.remove(initialTaskId);
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
