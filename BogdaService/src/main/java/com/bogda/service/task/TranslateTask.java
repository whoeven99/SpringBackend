package com.bogda.service.task;

import com.bogda.service.Service.ITranslatesService;
import com.bogda.common.entity.DO.TranslatesDO;
import com.bogda.service.logic.TencentEmailService;
import com.bogda.service.logic.translate.TranslateV2Service;
import com.bogda.repository.entity.InitialTaskV2DO;
import com.bogda.repository.repo.InitialTaskV2Repo;
import com.microsoft.applicationinsights.TelemetryClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@EnableAsync
@EnableScheduling
public class TranslateTask {
    @Autowired
    private TencentEmailService tencentEmailService;
    @Autowired
    private TranslateV2Service translateV2Service;
    @Autowired
    private InitialTaskV2Repo initialTaskV2Repo;
    @Autowired
    private ITranslatesService translatesService;

    private final ExecutorService executorService = Executors.newFixedThreadPool(100);
    private final Set<String> initializingShops = new HashSet<>();
    private final Set<String> savingShops = new HashSet<>();
    private final Set<Integer> translatingInitialIds = new HashSet<>();

    public static TelemetryClient AppInsightsUtils = new TelemetryClient();

    private <T> void process(int status,
                             Function<InitialTaskV2DO, T> groupByFunc,
                             Set<T> shopsSet,
                             String taskName,
                             Consumer<InitialTaskV2DO> taskConsumer) {
        List<InitialTaskV2DO> tasks = new ArrayList<>();
        if (status == 5) {
            // 一天内 状态为中断 且 没有写入时间
            tasks = initialTaskV2Repo.selectByStoppedAndNotSaveLastDay();
        } else {
            tasks = initialTaskV2Repo.selectByStatus(status);
        }
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
                AppInsightsUtils.trackTrace("TranslateTaskV2 start " + taskName + " group: " + groupKey + " with " + groupTasks.size() + " tasks.");

                try {
                    for (InitialTaskV2DO initialTaskV2DO : groupTasks) {
                        taskConsumer.accept(initialTaskV2DO);
                        AppInsightsUtils.trackTrace("TranslateTaskV2 " + taskName + " success for group: " + groupKey + ", initialTaskId: " + initialTaskV2DO.getId());
                    }
                } catch (Exception e) {
                    AppInsightsUtils.trackTrace("FatalException TaskRunFailed " + taskName + " " + e.getMessage());
                    AppInsightsUtils.trackException(e);
                } finally {
                    shopsSet.remove(groupKey);
                }
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

        process(5,
                InitialTaskV2DO::getShopName,
                savingShops, "SAVE SHOPIFY",
                translateV2Service::saveToShopify);
    }

    @Scheduled(fixedDelay = 30 * 1000)
    public void sendEmail() {
        // 自动翻译的邮件
        List<InitialTaskV2DO> autoTask = initialTaskV2Repo.selectByTaskTypeAndNotEmail("auto");
        if (!CollectionUtils.isEmpty(autoTask)) {

            Set<String> shopNames = autoTask.stream()
                    .map(InitialTaskV2DO::getShopName)
                    .collect(Collectors.toSet());

            for (String shopName : shopNames) {

                List<InitialTaskV2DO> shopTasks =
                        initialTaskV2Repo.selectByShopNameAndType(shopName, "auto");

                if (CollectionUtils.isEmpty(shopTasks)) {
                    continue;
                }

                // 1. 按状态分组
                List<InitialTaskV2DO> translatedTasks = shopTasks.stream()
                        .filter(t -> t.getStatus() == 3)
                        .collect(Collectors.toList());

                List<InitialTaskV2DO> partialTasks = shopTasks.stream()
                        .filter(t -> t.getStatus() == 5)
                        .collect(Collectors.toList());

                // 2. 判断是否全部属于 4 / 5
                if (translatedTasks.size() + partialTasks.size() != shopTasks.size()) {
                    continue;
                }

                // 3. 已翻译 → 发送成功邮件
                if (!translatedTasks.isEmpty()) {
                    tencentEmailService.sendAutoTranslateEmail(shopName, translatedTasks);
                }

                // 4. 部分翻译（额度不足）→ 发送部分翻译邮件
                if (!partialTasks.isEmpty()) {
                    tencentEmailService.sendTranslatePartialEmail(shopName, partialTasks, "auto translation");
                }

                // 5. 更新发送邮件状态 & 最终状态
                for (InitialTaskV2DO task : shopTasks) {
                    if (task.getStatus().equals(TranslateV2Service.InitialTaskStatus.SAVE_DONE_SENDING_EMAIL.getStatus())) {
                        initialTaskV2Repo.updateSendEmailAndStatusById(true,
                                TranslateV2Service.InitialTaskStatus.ALL_DONE.getStatus(), task.getId());
                    } else {
                        initialTaskV2Repo.updateSendEmailById(task.getId(), true);
                    }
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

    // 自动翻译，每小时整点执行一次，只翻译拆创建小时=当前小时的店铺
    // TODO 刚启动的时候断电怎么处理
    @Scheduled(cron = "0 0 * * * ?")
    public void autoTranslateTask() {
        List<TranslatesDO> translatesDOList = translatesService.readAllTranslates();
        if (CollectionUtils.isEmpty(translatesDOList)) {
            return;
        }
        for (TranslatesDO translatesDO : translatesDOList) {
            translateV2Service.autoTranslateV2(translatesDO.getShopName(), translatesDO.getSource(), translatesDO.getTarget());
        }
    }

    @Scheduled(fixedDelay = 13 * 1000 * 60)
    public void cleanTask() {
        // 3天前 且 isDeleted 的任务清理掉
        List<InitialTaskV2DO> cleanTask = initialTaskV2Repo.selectTaskBeforeDaysAndDeleted(3);
        if (CollectionUtils.isEmpty(cleanTask)) {
            return;
        }

        AppInsightsUtils.trackTrace("TranslateTaskV2 cleanTask: " + cleanTask.size() + " tasks.");
        translateV2Service.cleanTask(cleanTask.get(0));
    }
}
