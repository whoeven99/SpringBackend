package com.bogda.agenttask.queue;

import com.bogda.agenttask.queue.dto.TranslateTaskV3QueueMessage;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 启动后常驻 BRPOP 消费线程；Cosmos 30s 轮询仍作兜底。
 */
@Component
public class TranslateTaskV3QueueConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(TranslateTaskV3QueueConsumer.class);

    private final TranslateTaskV3QueueRepo translateTaskV3QueueRepo;
    private final TranslateTaskV3QueueHandler translateTaskV3QueueHandler;

    @Value("${translate.v3.queue.enabled:true}")
    private boolean queueEnabled;

    @Value("${translate.v3.queue.brpop-timeout-seconds:30}")
    private long brpopTimeoutSeconds;

    private final AtomicBoolean running = new AtomicBoolean(true);
    private final List<Thread> workerThreads = new ArrayList<>();

    public TranslateTaskV3QueueConsumer(TranslateTaskV3QueueRepo translateTaskV3QueueRepo,
                                        TranslateTaskV3QueueHandler translateTaskV3QueueHandler) {
        this.translateTaskV3QueueRepo = translateTaskV3QueueRepo;
        this.translateTaskV3QueueHandler = translateTaskV3QueueHandler;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void startWorkers() {
        if (!queueEnabled) {
            LOG.info("v3 queue consumer disabled (translate.v3.queue.enabled=false)");
            return;
        }
        startWorker("translate-v3-queue-init", TranslateTaskV3QueueStage.INIT);
        startWorker("translate-v3-queue-translate", TranslateTaskV3QueueStage.TRANSLATE);
        LOG.info("v3 queue consumer started, brpopTimeoutSeconds={}", brpopTimeoutSeconds);
    }

    private void startWorker(String threadName, TranslateTaskV3QueueStage stage) {
        Thread thread = new Thread(() -> runLoop(stage), threadName);
        thread.setDaemon(true);
        thread.start();
        workerThreads.add(thread);
    }

    private void runLoop(TranslateTaskV3QueueStage stage) {
        while (running.get()) {
            try {
                TranslateTaskV3QueueMessage message =
                        translateTaskV3QueueRepo.blockingPop(stage, brpopTimeoutSeconds);
                if (message == null) {
                    continue;
                }
                translateTaskV3QueueHandler.handle(message);
            } catch (Exception e) {
                if (running.get()) {
                    LOG.error("v3 queue consumer error, stage={}", stage, e);
                    sleepQuietly(2_000);
                }
            }
        }
        LOG.info("v3 queue consumer stopped, stage={}", stage);
    }

    @PreDestroy
    public void stopWorkers() {
        running.set(false);
        for (Thread thread : workerThreads) {
            thread.interrupt();
        }
    }

    private static void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
