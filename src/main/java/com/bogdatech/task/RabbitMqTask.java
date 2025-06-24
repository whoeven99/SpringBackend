package com.bogdatech.task;

import com.bogdatech.Service.ITranslateTasksService;
import com.bogdatech.entity.DO.TranslateTasksDO;
import com.bogdatech.entity.VO.RabbitMqTranslateVO;
import com.bogdatech.model.service.RabbitMqTranslateConsumerService;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import static com.bogdatech.logic.TranslateService.OBJECT_MAPPER;
import static com.bogdatech.logic.TranslateService.executorService;
import static com.bogdatech.utils.CaseSensitiveUtils.appInsights;

@Component
@EnableScheduling
@EnableAsync
public class RabbitMqTask {
    private final RabbitMqTranslateConsumerService rabbitMqTranslateConsumerService;
    private final ITranslateTasksService translateTasksService;
    public RabbitMqTask(RabbitMqTranslateConsumerService rabbitMqTranslateConsumerService, ITranslateTasksService translateTasksService) {
        this.rabbitMqTranslateConsumerService = rabbitMqTranslateConsumerService;
        this.translateTasksService = translateTasksService;
    }
    public static final ConcurrentHashMap<String, Boolean> SHOP_LOCKS = new ConcurrentHashMap<>();

    // 每3秒钟检查一次是否有闲置线程
    @Scheduled(fixedDelay = 3000)
    public void scanAndSubmitTasks() {
        //查询 0 状态的记录，过滤掉 shop 已被锁定的
        List<TranslateTasksDO> tasks = new ArrayList<>();
        try {
            tasks = translateTasksService.find0StatusTasks();
        } catch (Exception e) {
            appInsights.trackTrace("获取task集合失败 errors");
        }
        if (tasks.isEmpty()) {
            return;
        }
        for (TranslateTasksDO task : tasks) {
            String shopName = task.getShopName();
            if (SHOP_LOCKS.putIfAbsent(shopName, true) == null) {
                //获取TranslateTasks中的RabbitMqTranslateDO
                RabbitMqTranslateVO rabbitMqTranslateVO = null;
                try {
                    rabbitMqTranslateVO = OBJECT_MAPPER.readValue(task.getPayload(), RabbitMqTranslateVO.class);
                } catch (JsonProcessingException e) {
                    appInsights.trackTrace("payload 无法转化 errors");
                    throw new RuntimeException(e);
                }

                // 加锁成功，提交任务到线程池
                RabbitMqTranslateVO finalRabbitMqTranslateVO = rabbitMqTranslateVO;
                executorService.submit(() -> rabbitMqTranslateConsumerService.startTranslate(finalRabbitMqTranslateVO, task));
                return;
            }
        }
    }
}
