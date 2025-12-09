package com.bogdatech.task;

import com.bogdatech.logic.TencentEmailService;
import com.bogdatech.logic.redis.ShopNameRedisRepo;
import com.bogdatech.repository.entity.InitialTaskV2DO;
import com.bogdatech.repository.repo.InitialTaskV2Repo;
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
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@EnableAsync
@EnableScheduling
public class TranslateTask implements ApplicationListener<ApplicationReadyEvent> {
    @Autowired
    private TaskService taskService;
    @Autowired
    private ShopNameRedisRepo shopNameRedisRepo;
    @Autowired
    private TencentEmailService tencentEmailService;

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        // 执行业务代码
        executorService.execute(() -> {
            taskService.translateStatus2WhenSystemRestart();
        });
    }

    /**
     * 每分钟做次打印--正在翻译中和等待翻译的用户数据
     */
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

    private <T> void process(int status,
                             Function<InitialTaskV2DO, T> groupByFunc,
                             Set<T> shopsSet,
                             String statusName,
                             Consumer<InitialTaskV2DO> taskConsumer) {
        List<InitialTaskV2DO> tasks = initialTaskV2Repo.selectByStatus(status);
        if (CollectionUtils.isEmpty(tasks)) {
            return;
        }

        // 按 groupByFunc 分组
        Map<T, List<InitialTaskV2DO>> tasksByGroup = tasks.stream()
                .collect(Collectors.groupingBy(groupByFunc));

        // 不同组并发处理，相同组顺序处理
        for (Map.Entry<T, List<InitialTaskV2DO>> entry : tasksByGroup.entrySet()) {
            T groupKey = entry.getKey();
            if (shopsSet.contains(groupKey)) { // 本地内存简单做个加锁，这样后续的task  1.不会重复 2.不会卡住
                continue;
            }
            executorService.submit(() -> {
                shopsSet.add(groupKey);
                List<InitialTaskV2DO> groupTasks = entry.getValue();
                appInsights.trackTrace("TranslateTaskV2 start " + statusName + " group: " + groupKey + " with " + groupTasks.size() + " tasks.");

                for (InitialTaskV2DO initialTaskV2DO : groupTasks) {
                    taskConsumer.accept(initialTaskV2DO);
                    appInsights.trackTrace("TranslateTaskV2 " + statusName + " success for group: " + groupKey + ", initialTaskId: " + initialTaskV2DO.getId());
                }
                shopsSet.remove(groupKey);
            });
        }
    }

    @Scheduled(fixedDelay = 30 * 1000)
    public void initialToTranslateTask() {
        process(0,
                InitialTaskV2DO::getShopName,
                initializingShops, "INIT",
                translateV2Service::initialToTranslateTask);
    }

    @Scheduled(fixedDelay = 30 * 1000)
    public void translateEachTask() {
        process(1,
                InitialTaskV2DO::getId,
                translatingInitialIds, "TRANSLATE",
                translateV2Service::translateEachTask);
    }

    @Scheduled(fixedDelay = 30 * 1000)
    public void saveToShopify() {
        process(2,
                InitialTaskV2DO::getShopName,
                savingShops, "SAVE SHOPIFY",
                translateV2Service::saveToShopify);
    }

    // 定时30秒扫描一次
    @Scheduled(fixedDelay = 30 * 1000)
    public void sendEmail() {
        // 自动翻译的邮件
        List<InitialTaskV2DO> autoTask = initialTaskV2Repo.selectByTaskTypeAndNotEmail("auto");
        if (!CollectionUtils.isEmpty(autoTask)) {
            Set<String> shopNames = autoTask.stream()
                    .map(InitialTaskV2DO::getShopName)
                    .collect(Collectors.toSet());
            for (String shopName : shopNames) {
                if (shopNameRedisRepo.getAutoTaskCount(shopName).equals(0L)) {
                    List<InitialTaskV2DO> shopTasks = initialTaskV2Repo.selectByShopNameAndType(shopName, "auto");
                    tencentEmailService.sendAutoTranslateEmail(shopName, shopTasks);

                    for (InitialTaskV2DO shopTask : shopTasks) {
                        shopTask.setSendEmail(true);
                        initialTaskV2Repo.updateToStatus(shopTask, TranslateV2Service.InitialTaskStatus.ALL_DONE.getStatus());
                    }
                    shopNameRedisRepo.deleteShopName(shopName);
                }
            }
        }

        // 手动翻译邮件
        List<InitialTaskV2DO> manualTask = initialTaskV2Repo.selectByStatusAndTaskType(3, "manual");
        manualTask.addAll(initialTaskV2Repo.selectByStoppedAndNotEmail("manual"));
        if (CollectionUtils.isEmpty(manualTask)) {
            return;
        }

        for (InitialTaskV2DO initialTaskV2DO : manualTask) {
            translateV2Service.sendManualEmail(initialTaskV2DO);
        }
    }
}
