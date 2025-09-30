package com.bogdatech.task;

import com.bogdatech.Service.ITranslateTasksService;
import com.bogdatech.entity.DO.TranslateTasksDO;
import com.bogdatech.logic.redis.TranslationMonitorRedisService;
import com.bogdatech.utils.RedisLockUtils;
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

@Component
@EnableScheduling
@EnableAsync
public class DBTask {
    @Autowired
    private ITranslateTasksService translateTasksService;
    @Autowired
    private RedisLockUtils redisLockUtils;
    @Autowired
    private TranslationMonitorRedisService translationMonitorRedisService;

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

        // 开始翻译所有task
        for (TranslateTasksDO task : tasks) {
            String shopName = task.getShopName();
            redisLockUtils.translateLock(shopName, task);
        }
    }
}

