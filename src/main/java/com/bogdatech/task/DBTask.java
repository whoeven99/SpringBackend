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

import javax.annotation.PostConstruct;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import static com.bogdatech.utils.CaseSensitiveUtils.appInsights;
import static com.bogdatech.utils.JsonUtils.jsonToObject;

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
    private final ExecutorService executorService = Executors.newCachedThreadPool(); // 线程数可根据实际情况调整

    @PostConstruct
    public void init() {
        if (System.getenv("REDISCACHEHOSTNAME") == null) { return; }
        appInsights.trackTrace("DBTaskLog init");
        redisTranslateLockService.setDelete(); // 删掉翻译中的所有shop
    }

    // 每30秒钟轮询一次是否有新的shop需要翻译
    @Scheduled(fixedRate = 30 * 1000)
    public void scanAndSubmitTasks() {
        // 统计待翻译的 task
        List<TranslateTasksDO> tasks = translateTasksService.find0StatusTasks();
        translationMonitorRedisService.hsetCountOfTasks(tasks.size());
        appInsights.trackTrace("DBTaskLog Number of tasks need to translate " + tasks.size());
        if (tasks.isEmpty()) {
            return;
        }

        // 统计待翻译的shop数量
        Set<String> shops = tasks.stream().map(TranslateTasksDO::getShopName).collect(Collectors.toSet());
        shops.forEach((shopName) -> translationMonitorRedisService.setTranslatingShop(shopName));
        appInsights.trackTrace("DBTaskLog Number of existing shops: " + shops.size());

        // 翻译中的shop数量
        Set<String> translatingShops = redisTranslateLockService.members();
        appInsights.trackTrace("DBTaskLog Number of translating Shops: " + translatingShops.size() + " " + translatingShops);

        // 对当前的shop轮询，抢到锁的时候，会在这个task把把当前shop的所有task都处理掉
        for (String shop : shops) {
            // 该shop对应的所有task
            Set<TranslateTasksDO> shopTasks = tasks.stream()
                    .filter(taskDo -> taskDo.getShopName().equals(shop))
                    .collect(Collectors.toSet());

            // 这一行的日志可以看到每个shop的task是否在减少
            appInsights.trackTrace("DBTaskLog Number of shopTasks: " + shopTasks.size() + " need to translate of shop: " + shop);

            // 当前的加锁，只是为了保持一个shop只会被一个线程处理，防止进度条或者其他的状态不兼容并发翻译
            // 这里加锁的方式是将shop放进一个set
            if (redisTranslateLockService.setAdd(shop)) {
                appInsights.trackTrace("DBTaskLog new shop start translate: " + shop);
                executorService.submit(() -> {
                    try {
                        processShopTasks(shop, shopTasks);
                    } finally {
                        redisTranslateLockService.setRemove(shop);
                    }
                });
            }
        }
    }

    private void processShopTasks(String shop, Set<TranslateTasksDO> shopTasks) {
        for (TranslateTasksDO task : shopTasks) {
            appInsights.trackTrace("DBTaskLog new task start: " + task.getTaskId() + " of shop: " + shop);
            try {
                RabbitMqTranslateVO vo = jsonToObject(task.getPayload(), RabbitMqTranslateVO.class);
                if (vo == null) {
                    appInsights.trackTrace("DBTaskLog FatalException: " + shop + " 解析失败 " + task.getPayload());
                    //将taskId 改为10（暂定）
                    translateTasksService.updateByTaskId(task.getTaskId(), 10);
                    return;
                }

                // 开始处理
                processDbTaskService.runTask(vo, task);
                appInsights.trackTrace("DBTaskLog task finish successfully: " + task.getTaskId() + " of shop: " + shop);
            } catch (Exception e) {
                appInsights.trackTrace("DBTaskLog FatalException " + shop + " 任务处理失败 " + e);
                //将该模块状态改为4
                translateTasksService.updateByTaskId(task.getTaskId(), 4);
                appInsights.trackException(e);
            }
        }
    }
}

