package com.bogdatech.task;

import com.bogdatech.Service.ITranslateTasksService;
import com.bogdatech.entity.DO.TranslateTasksDO;
import com.bogdatech.entity.VO.RabbitMqTranslateVO;
import com.bogdatech.integration.RedisIntegration;
import com.bogdatech.logic.RedisTranslateLockService;
import com.bogdatech.logic.redis.TranslationMonitorRedisService;
import com.bogdatech.model.service.ProcessDbTaskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import static com.bogdatech.utils.CaseSensitiveUtils.appInsights;
import static com.bogdatech.utils.JsonUtils.jsonToObject;
import static com.bogdatech.utils.RedisKeyUtils.generateTranslateLockKey;

@Component
@EnableScheduling
@EnableAsync
public class DBTask {
    @Autowired
    private ITranslateTasksService translateTasksService;
    @Autowired
    private TranslationMonitorRedisService translationMonitorRedisService;
    @Autowired
    private RedisIntegration redisIntegration;
    @Autowired
    private RedisTranslateLockService redisTranslateLockService;
    @Autowired
    private ProcessDbTaskService processDbTaskService;

    // 给dbtask单独使用的线程池，不和其他共用
    public static ExecutorService executorService = Executors.newFixedThreadPool(50);

    // 每6秒钟检查一次是否有闲置线程
    @Scheduled(fixedDelay = 6000)
    public void scanAndSubmitTasks() {
        appInsights.trackTrace("DBTask Number of active translating threads " + ((ThreadPoolExecutor) executorService).getActiveCount());
        appInsights.trackMetric("Number of active translating threads", ((ThreadPoolExecutor) executorService).getActiveCount());

        // 统计待翻译的 task
        List<TranslateTasksDO> tasks = translateTasksService.find0StatusTasks();
        translationMonitorRedisService.hsetCountOfTasks(tasks.size());
        appInsights.trackTrace("DBTask Number of tasks need to translate " + tasks.size());
        if (tasks.isEmpty()) {
            return;
        }

        // 统计shopName数量
        Set<String> shops = tasks.stream().map(TranslateTasksDO::getShopName).collect(Collectors.toSet());
        shops.forEach((shopName) -> translationMonitorRedisService.setTranslatingShop(shopName));
        appInsights.trackTrace("DBTask Number of existing shops: " + shops.size());

        // 开始处理所有task
        for (TranslateTasksDO task : tasks) {
            String shopName = task.getShopName();
            //在加锁时判断是否成功，成功-翻译；不成功跳过
            if (redisTranslateLockService.lockStore(shopName)) {
                executorService.submit(() -> {
                    appInsights.trackTrace("DBTask Lock [" + shopName + "] by thread " + Thread.currentThread().getName()
                            + "shop: " + shopName + " 锁的状态： " + redisIntegration.get(generateTranslateLockKey(shopName)));
                    try {
                        // 只执行一个线程处理这个 shopName
                        RabbitMqTranslateVO vo = jsonToObject(task.getPayload(), RabbitMqTranslateVO.class);
                        if (vo == null) {
                            redisTranslateLockService.unLockStore(shopName);
                            appInsights.trackTrace("每日须看 ： " + shopName + " 处理失败，payload为空 " + task.getPayload());
                            //将taskId 改为10（暂定）
                            translateTasksService.updateByTaskId(task.getTaskId(), 10);
                            return;
                        }

                        // 开始处理
                        processDbTaskService.runTask(vo, task);
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

