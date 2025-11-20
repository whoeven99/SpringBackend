package com.bogdatech.task;

import com.bogdatech.repository.entity.InitialTaskV2DO;
import com.bogdatech.repository.repo.InitialTaskV2Repo;
import com.bogdatech.entity.DO.*;
import com.bogdatech.logic.TaskService;
import com.bogdatech.logic.translate.TranslateV2Service;
import com.microsoft.applicationinsights.TelemetryClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Component
@EnableAsync
@EnableScheduling
public class TranslateTask implements ApplicationListener<ApplicationReadyEvent> {
    @Autowired
    private TaskService taskService;

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        // 执行业务代码
        executorService.execute(() -> {
            taskService.translateStatus2WhenSystemRestart();
        });
    }

    /**
     * 每分钟做次打印--正在翻译中和等待翻译的用户数据
     * */
    @Scheduled(cron = "0 * * * * ?")
    public void printTranslatingAndWaitTranslatingData() {
        taskService.printTranslatingAndWaitTranslatingData();
    }



    @Autowired
    private TranslateV2Service translateV2Service;
    @Autowired
    private InitialTaskV2Repo initialTaskV2Repo;

    private final ExecutorService executorService = Executors.newFixedThreadPool(40);
    private final Set<String> initializingShops = new HashSet<>();
    private final Set<String> savingShops = new HashSet<>();
    private final Set<Integer> translatingInitialIds = new HashSet<>();

    public static TelemetryClient appInsights = new TelemetryClient();

    @Scheduled(fixedDelay = 30 * 1000)
    public void initialToTranslateTask() {
//        appInsights.trackTrace("TranslateTaskV2 start INIT");
        List<InitialTaskV2DO> initTaskList = initialTaskV2Repo.selectByStatus(0);
        if (CollectionUtils.isEmpty(initTaskList)) return;

        // 按 shopName 分组
        Map<String, List<InitialTaskV2DO>> tasksByShop = initTaskList.stream()
                .collect(Collectors.groupingBy(InitialTaskV2DO::getShopName));

//        appInsights.trackTrace("TranslateTaskV2 INITIATING shop: " + initializingShops);
        // 不同shopName并发处理，相同shopName顺序处理
        for (Map.Entry<String, List<InitialTaskV2DO>> entry : tasksByShop.entrySet()) {
            String shopName = entry.getKey();
            if (initializingShops.contains(shopName)) { // 本地内存简单做个加锁，这样后续的task  1.不会重复 2.不会卡住
                continue;
            }
            executorService.submit(() -> {
                initializingShops.add(shopName);
                List<InitialTaskV2DO> tasks = entry.getValue();
                appInsights.trackTrace("TranslateTaskV2 start INIT shop: " + shopName + " with " + tasks.size() + " tasks.");

                for (InitialTaskV2DO initialTaskV2DO : tasks) {
                    // 断电问题，在里面的needTranslate处理
                    translateV2Service.initialToTranslateTask(initialTaskV2DO);
                    appInsights.trackTrace("TranslateTaskV2 INIT success for shop: " + shopName + ", initialTaskId: " + initialTaskV2DO.getId());
                }
                initializingShops.remove(shopName);
            });
        }
    }

    @Scheduled(fixedDelay = 30 * 1000)
    public void translateEachTask() {
        List<InitialTaskV2DO> translatingTask = initialTaskV2Repo.selectByStatus(1);
        if (CollectionUtils.isEmpty(translatingTask)) return;

        // 按 initialId 分组
        Map<Integer, List<InitialTaskV2DO>> tasksByInitialId = translatingTask.stream()
                .collect(Collectors.groupingBy(InitialTaskV2DO::getId));

        for (Map.Entry<Integer, List<InitialTaskV2DO>> entry : tasksByInitialId.entrySet()) {
            Integer initialId = entry.getKey();
            if (translatingInitialIds.contains(initialId)) { // 本地内存简单做个加锁，这样后续的task  1.不会重复 2.不会卡住
                continue;
            }
            executorService.submit(() -> {
                translatingInitialIds.add(initialId);
                List<InitialTaskV2DO> tasks = entry.getValue();
                appInsights.trackTrace("TranslateTaskV2 start TRANSLATE shop: " + initialId + " with " + tasks.size() + " tasks.");

                for (InitialTaskV2DO initialTaskV2DO : tasks) {
                    // 断电
                    translateV2Service.translateEachTask(initialTaskV2DO);
                    appInsights.trackTrace("TranslateTaskV2 TRANSLATE success for shop: " + initialTaskV2DO.getShopName() + ", initialTaskId: " + initialId);
                }
                translatingInitialIds.remove(initialId);
            });
        }
    }

    @Scheduled(fixedDelay = 30 * 1000)
    public void saveToShopify() {
        List<InitialTaskV2DO> translatingTask = initialTaskV2Repo.selectByStatus(2);
        if (CollectionUtils.isEmpty(translatingTask)) return;

        // 按 shopName 分组，严格控制shopify的shopName维度的api qps
        Map<String, List<InitialTaskV2DO>> tasksByShop = translatingTask.stream()
                .collect(Collectors.groupingBy(InitialTaskV2DO::getShopName));

        for (Map.Entry<String, List<InitialTaskV2DO>> entry : tasksByShop.entrySet()) {
            String shopName = entry.getKey();
            if (savingShops.contains(shopName)) { // 本地内存简单做个加锁，这样后续的task  1.不会重复 2.不会卡住
                continue;
            }
            executorService.submit(() -> {
                savingShops.add(shopName);
                List<InitialTaskV2DO> tasks = entry.getValue();
                appInsights.trackTrace("TranslateTaskV2 start SAVING SHOPIFY shop: " + shopName + " with " + tasks.size() + " tasks.");

                for (InitialTaskV2DO initialTaskV2DO : tasks) {
                    // 断电
                    translateV2Service.saveToShopify(initialTaskV2DO);
                    appInsights.trackTrace("TranslateTaskV2 SAVED SHOPIFY success for shop: " + shopName + ", initialTaskId: " + initialTaskV2DO.getId());
                }
                savingShops.remove(shopName);
            });
        }
    }

    // 定时30秒扫描一次
//    @Scheduled(fixedRate = 30 * 1000)
    public void sendEmail() {
        List<InitialTaskV2DO> translatingTask = initialTaskV2Repo.selectByStatus(3);
        if (CollectionUtils.isEmpty(translatingTask)) return;

        for (InitialTaskV2DO initialTaskV2DO : translatingTask) {
            // 断电
            translateV2Service.sendEmail(initialTaskV2DO);
        }
    }
}
