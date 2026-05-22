package com.bogda.agenttask.queue;

import com.bogda.agenttask.queue.dto.TranslateTaskV3QueueMessage;
import com.bogda.repository.container.TranslateTaskV3DO;
import com.bogda.repository.container.TranslateTaskV3ScheduleLogDO;
import com.bogda.repository.repo.translate.TranslateTaskV3CosmosRepo;
import com.bogda.repository.repo.translate.TranslateTaskV3CosmosStatus;
import com.bogda.repository.repo.translate.TranslateTaskV3ScheduleLogCosmosRepo;
import com.bogda.service.logic.translate.TranslateV3Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.UUID;

/**
 * 消费 Redis 队列消息：点读 Cosmos 后同步执行 INIT / TRANSLATE。
 */
@Component
public class TranslateTaskV3QueueHandler {

    private static final Logger LOG = LoggerFactory.getLogger(TranslateTaskV3QueueHandler.class);

    private final TranslateTaskV3CosmosRepo translateTaskV3CosmosRepo;
    private final TranslateV3Service translateV3Service;
    private final TranslateTaskV3QueueRepo translateTaskV3QueueRepo;
    private final TranslateTaskV3ScheduleLogCosmosRepo scheduleLogRepo;

    public TranslateTaskV3QueueHandler(TranslateTaskV3CosmosRepo translateTaskV3CosmosRepo,
                                       TranslateV3Service translateV3Service,
                                       TranslateTaskV3QueueRepo translateTaskV3QueueRepo,
                                       TranslateTaskV3ScheduleLogCosmosRepo scheduleLogRepo) {
        this.translateTaskV3CosmosRepo = translateTaskV3CosmosRepo;
        this.translateV3Service = translateV3Service;
        this.translateTaskV3QueueRepo = translateTaskV3QueueRepo;
        this.scheduleLogRepo = scheduleLogRepo;
    }

    public void handle(TranslateTaskV3QueueMessage message) {
        if (message == null || StringUtils.isEmpty(message.getTaskId())) {
            return;
        }
        TranslateTaskV3QueueStage stage = message.getStage() == null
                ? TranslateTaskV3QueueStage.INIT
                : message.getStage();
        if (stage == TranslateTaskV3QueueStage.TRANSLATE) {
            handleTranslate(message);
        } else {
            handleInit(message);
        }
    }

    private void handleInit(TranslateTaskV3QueueMessage message) {
        String taskId = message.getTaskId().trim();
        String shopHint = safeShop(message.getShopName());
        TranslateTaskV3DO task = translateTaskV3CosmosRepo.findByIdResolved(taskId, shopHint);
        if (task == null) {
            LOG.warn("v3 queue init skip, task not found, taskId={}, shopHint={}", taskId, shopHint);
            recordProcessLog(message, TranslateTaskV3QueueStage.INIT, null, null, false, "Task not found");
            return;
        }
        Integer status = task.getStatus();
        if (status != null && status != TranslateTaskV3CosmosStatus.INIT_PENDING) {
            LOG.info("v3 queue init skip, status not pending, taskId={}, shop={}, status={}",
                    taskId, task.getShopName(), status);
            recordProcessLog(message, TranslateTaskV3QueueStage.INIT, status, status, false, "Status not INIT_PENDING");
            maybeEnqueueTranslateAfterInit(task);
            return;
        }
        LOG.info("v3 queue init start, taskId={}, shop={}", taskId, task.getShopName());
        recordProcessLog(message, TranslateTaskV3QueueStage.INIT, status, status, true, "INIT processing started");
        translateV3Service.runInitTaskV3FromQueue(task);
        TranslateTaskV3DO updatedTask = translateTaskV3CosmosRepo.findByIdResolved(taskId, task.getShopName());
        maybeEnqueueTranslateAfterInit(updatedTask);
        recordProcessLog(message, TranslateTaskV3QueueStage.INIT, status, updatedTask != null ? updatedTask.getStatus() : status,
                true, "INIT processing completed");
    }

    private void handleTranslate(TranslateTaskV3QueueMessage message) {
        String taskId = message.getTaskId().trim();
        String shopHint = safeShop(message.getShopName());
        TranslateTaskV3DO task = translateTaskV3CosmosRepo.findByIdResolved(taskId, shopHint);
        if (task == null) {
            LOG.warn("v3 queue translate skip, task not found, taskId={}, shopHint={}", taskId, shopHint);
            recordProcessLog(message, TranslateTaskV3QueueStage.TRANSLATE, null, null, false, "Task not found");
            return;
        }
        if (!isEligibleForTranslate(task)) {
            LOG.info("v3 queue translate skip, not eligible, taskId={}, shop={}, status={}, statusText={}",
                    taskId, task.getShopName(), task.getStatus(), task.getStatusText());
            recordProcessLog(message, TranslateTaskV3QueueStage.TRANSLATE, task.getStatus(), task.getStatus(),
                    false, "Not eligible for translation");
            return;
        }
        LOG.info("v3 queue translate start, taskId={}, shop={}", taskId, task.getShopName());
        recordProcessLog(message, TranslateTaskV3QueueStage.TRANSLATE, task.getStatus(), task.getStatus(), true, "TRANSLATE processing started");
        translateV3Service.runTranslateTaskV3FromQueue(task);
        recordProcessLog(message, TranslateTaskV3QueueStage.TRANSLATE, task.getStatus(), task.getStatus(), true, "TRANSLATE processing completed");
    }

    private void recordProcessLog(TranslateTaskV3QueueMessage message, TranslateTaskV3QueueStage stage,
                                  Integer statusBefore, Integer statusAfter, boolean success, String message_text) {
        try {
            TranslateTaskV3ScheduleLogDO log = new TranslateTaskV3ScheduleLogDO();
            log.setId(UUID.randomUUID().toString());
            log.setShopName(message.getShopName() != null ? message.getShopName() : "");
            log.setTaskId(message.getTaskId());
            log.setEventType("PROCESS_" + stage.toString() + (success ? "_END" : "_ERROR"));
            log.setStatusBefore(statusBefore);
            log.setStatusAfter(statusAfter);
            log.setQueueStage(stage.toString());
            log.setProcessedAt(System.currentTimeMillis());
            log.setMessage(message_text);
            log.setSuccess(success);
            log.setCreatedAt(System.currentTimeMillis());
            log.setSource("QueueHandler.handle");

            scheduleLogRepo.append(log);
        } catch (Exception e) {
            LOG.warn("v3 queue failed to record process log, taskId={}", message.getTaskId(), e);
        }
    }

    private void maybeEnqueueTranslateAfterInit(TranslateTaskV3DO task) {
        if (task == null || !isEligibleForTranslate(task)) {
            return;
        }
        translateTaskV3QueueRepo.enqueueTranslate(task.getId(), task.getShopName());
        LOG.info("v3 queue enqueued TRANSLATE after init, taskId={}, shop={}", task.getId(), task.getShopName());
    }

    private static boolean isEligibleForTranslate(TranslateTaskV3DO task) {
        if (task == null || task.getStatus() == null) {
            return false;
        }
        int status = task.getStatus();
        if (status == TranslateTaskV3CosmosStatus.INIT_DONE) {
            return true;
        }
        if (status == TranslateTaskV3CosmosStatus.TRANSLATE_RUNNING) {
            return true;
        }
        String text = task.getStatusText();
        if (status == 1 && text != null) {
            String normalized = text.trim();
            return "TRANSLATE_PENDING".equalsIgnoreCase(normalized)
                    || "INIT_DONE".equalsIgnoreCase(normalized);
        }
        return false;
    }

    private static String safeShop(String shopName) {
        return shopName == null ? "" : shopName.trim();
    }
}
