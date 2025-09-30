package com.bogdatech.task;

import com.bogdatech.Service.ITranslateTasksService;
import com.bogdatech.entity.DO.TranslateTasksDO;
import com.bogdatech.utils.RedisLockUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import static com.bogdatech.utils.CaseSensitiveUtils.appInsights;

@Component
@EnableScheduling
@EnableAsync
public class DBTask {
    @Autowired
    private ITranslateTasksService translateTasksService;
    @Autowired
    private RedisLockUtils redisLockUtils;

    // 给dbtask单独使用的线程池，不和其他共用
    public static ExecutorService executorService = Executors.newFixedThreadPool(50);

    // 每6秒钟检查一次是否有闲置线程
    @Scheduled(fixedDelay = 6000)
    public void scanAndSubmitTasks() {
        appInsights.trackTrace("DBTask Number of active translating threads " + ((ThreadPoolExecutor) executorService).getActiveCount());
        appInsights.trackMetric("Number of active translating threads", ((ThreadPoolExecutor) executorService).getActiveCount());

        //查询status为0状态的task
        List<TranslateTasksDO> tasks = translateTasksService.find0StatusTasks();
        appInsights.trackTrace("DBTask Number of tasks need to translate " + tasks.size());

        // 统计shopName数量
        Set<String> shopNames = new HashSet<>();
        tasks.forEach(task -> shopNames.add(task.getShopName()));
        appInsights.trackTrace("DBTask Number of existing shops: " + shopNames.size());
        if (tasks.isEmpty()) {
            return;
        }

        for (TranslateTasksDO task : tasks) {
            String shopName = task.getShopName();
            redisLockUtils.translateLock(shopName, task);
        }
    }
}

