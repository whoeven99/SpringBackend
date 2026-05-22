package com.bogda.agenttask.queue;

import com.bogda.agenttask.queue.dto.TranslateTaskV3QueueMessage;
import com.bogda.repository.container.TranslateTaskV3DO;
import com.bogda.repository.repo.translate.TranslateTaskV3CosmosRepo;
import com.bogda.repository.repo.translate.TranslateTaskV3CosmosStatus;
import com.bogda.service.logic.translate.TranslateV3Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 消费 Redis 队列消息：点读 Cosmos 后同步执行 INIT / TRANSLATE。
 */
@Component
public class TranslateTaskV3QueueHandler {

    private static final Logger LOG = LoggerFactory.getLogger(TranslateTaskV3QueueHandler.class);

    private final TranslateTaskV3CosmosRepo translateTaskV3CosmosRepo;
    private final TranslateV3Service translateV3Service;
    private final TranslateTaskV3QueueRepo translateTaskV3QueueRepo;

    public TranslateTaskV3QueueHandler(TranslateTaskV3CosmosRepo translateTaskV3CosmosRepo,
                                       TranslateV3Service translateV3Service,
                                       TranslateTaskV3QueueRepo translateTaskV3QueueRepo) {
        this.translateTaskV3CosmosRepo = translateTaskV3CosmosRepo;
        this.translateV3Service = translateV3Service;
        this.translateTaskV3QueueRepo = translateTaskV3QueueRepo;
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
            return;
        }
        Integer status = task.getStatus();
        if (status != null && status != TranslateTaskV3CosmosStatus.INIT_PENDING) {
            LOG.info("v3 queue init skip, status not pending, taskId={}, shop={}, status={}",
                    taskId, task.getShopName(), status);
            maybeEnqueueTranslateAfterInit(task);
            return;
        }
        LOG.info("v3 queue init start, taskId={}, shop={}", taskId, task.getShopName());
        translateV3Service.runInitTaskV3FromQueue(task);
        maybeEnqueueTranslateAfterInit(translateTaskV3CosmosRepo.findByIdResolved(taskId, task.getShopName()));
    }

    private void handleTranslate(TranslateTaskV3QueueMessage message) {
        String taskId = message.getTaskId().trim();
        String shopHint = safeShop(message.getShopName());
        TranslateTaskV3DO task = translateTaskV3CosmosRepo.findByIdResolved(taskId, shopHint);
        if (task == null) {
            LOG.warn("v3 queue translate skip, task not found, taskId={}, shopHint={}", taskId, shopHint);
            return;
        }
        if (!isEligibleForTranslate(task)) {
            LOG.info("v3 queue translate skip, not eligible, taskId={}, shop={}, status={}, statusText={}",
                    taskId, task.getShopName(), task.getStatus(), task.getStatusText());
            return;
        }
        LOG.info("v3 queue translate start, taskId={}, shop={}", taskId, task.getShopName());
        translateV3Service.runTranslateTaskV3FromQueue(task);
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
