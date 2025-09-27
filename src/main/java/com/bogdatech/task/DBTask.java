package com.bogdatech.task;

import com.bogdatech.Service.ITranslateTasksService;
import com.bogdatech.entity.DO.TranslateTasksDO;
import com.bogdatech.entity.VO.RabbitMqTranslateVO;
import com.bogdatech.integration.RedisIntegration;
import com.bogdatech.logic.RedisTranslateLockService;
import com.bogdatech.model.service.RabbitMqTranslateConsumerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import static com.bogdatech.logic.TranslateService.OBJECT_MAPPER;
import static com.bogdatech.utils.CaseSensitiveUtils.appInsights;
import static com.bogdatech.utils.RedisKeyUtils.generateTranslateLockKey;

@Component
@EnableScheduling
@EnableAsync
public class DBTask {
    @Autowired
    private RabbitMqTranslateConsumerService rabbitMqTranslateConsumerService;
    @Autowired
    private ITranslateTasksService translateTasksService;
    @Autowired
    private RedisTranslateLockService redisTranslateLockService;
    @Autowired
    private RedisIntegration redisIntegration;

    // 给dbtask单独使用的线程池，不和其他共用
    public static ExecutorService executorService = Executors.newFixedThreadPool(10);

    // 每6秒钟检查一次是否有闲置线程
    @Scheduled(fixedDelay = 6000)
    public void scanAndSubmitTasks() {
        //查询 0 状态的记录，过滤掉 shop 已被锁定的
        List<TranslateTasksDO> tasks = new ArrayList<>();
        ThreadPoolExecutor tpe = null;
        try {
            tasks = translateTasksService.find0StatusTasks();
            tpe = (ThreadPoolExecutor) executorService;
        } catch (Exception e) {
            appInsights.trackTrace("获取task集合失败 errors");
        }
        if (tasks.isEmpty()) {
            return;
        }

        for (TranslateTasksDO task : tasks) {
            String shopName = task.getShopName();
            //在加锁时判断是否成功，成功-翻译；不成功跳过
            // TODO 这里换到RedisLockUtils
            if (redisTranslateLockService.lockStore(shopName)) {
                ThreadPoolExecutor finalTpe = tpe;
                executorService.submit(() -> {
                    appInsights.trackTrace("Lock [" + shopName + "] by thread " + Thread.currentThread().getName() + "shop: " + shopName + " 锁的状态： " + redisIntegration.get(generateTranslateLockKey(shopName)));
                    if (finalTpe != null) {
                        appInsights.trackMetric("Number of active translating threads", finalTpe.getActiveCount());
                    }
                    try {
                        // 只执行一个线程处理这个 shopName
                        // TODO 用 jsonUtils?
                        RabbitMqTranslateVO vo = OBJECT_MAPPER.readValue(task.getPayload(), RabbitMqTranslateVO.class);
                        rabbitMqTranslateConsumerService.startTranslate(vo, task);
                    } catch (Exception e) {
                        appInsights.trackTrace("clickTranslation " + shopName + " 处理失败 errors : " + e);
                        //将该模块状态改为4
                        translateTasksService.updateByTaskId(task.getTaskId(), 4);
                        appInsights.trackException(e);
                    } finally {
                        redisTranslateLockService.unLockStore(shopName);
                    }
                });

            }
        }
    }
}

