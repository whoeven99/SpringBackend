package com.bogdatech.task;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.bogdatech.Service.*;
import com.bogdatech.entity.DO.*;
import com.bogdatech.enums.InitialTaskStatusEnum;
import com.bogdatech.logic.TencentEmailService;
import com.bogdatech.logic.redis.TranslationCounterRedisService;
import com.bogdatech.logic.redis.TranslationParametersRedisService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.bogdatech.Service.IUsersService;
import com.bogdatech.logic.RabbitMqTranslateService;
import com.bogdatech.logic.redis.InitialTranslateRedisService;
import com.bogdatech.model.controller.request.TranslateRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import static com.bogdatech.logic.RabbitMqTranslateService.AUTO;
import static com.bogdatech.logic.RabbitMqTranslateService.MANUAL;
import static com.bogdatech.logic.redis.TranslationParametersRedisService.generateProgressTranslationKey;
import static com.bogdatech.utils.CaseSensitiveUtils.appInsights;
import static com.bogdatech.utils.JsonUtils.jsonToObject;
import static com.bogdatech.utils.ListUtils.convertALL;

@Component
public class InitialTranslateDbTask {
    @Autowired
    private RabbitMqTranslateService rabbitMqTranslateService;
    @Autowired
    private InitialTranslateRedisService initialTranslateRedisService;
    @Autowired
    private IUsersService iUsersService;
    @Autowired
    private ITranslatesService iTranslatesService;
    @Autowired
    private ITranslateTasksService iTranslateTasksService;
    @Autowired
    private TranslationCounterRedisService translationCounterRedisService;
    @Autowired
    private TencentEmailService tencentEmailService;
    @Autowired
    private TranslationParametersRedisService translationParametersRedisService;
    @Autowired
    private IInitialTranslateTasksService iInitialTranslateTasksService;
    @Autowired
    private IUserTranslationDataService iUserTranslationDataService;
    @Autowired
    private ITranslationCounterService iTranslationCounterService;

    private final ExecutorService initialExecutorService = Executors.newFixedThreadPool(5); // 创建initial初始化线程池
    private final ExecutorService manualExecutorService = Executors.newFixedThreadPool(5);

    /**
     * 恢复因重启或其他原因中断的手动翻译大任务的task
     */
    @PostConstruct
    public void init() {
        if (System.getenv("REDISCACHEHOSTNAME") == null) {
            return;
        }
        appInsights.trackTrace("processInitialTasksOfShop init");
        initialTranslateRedisService.setDelete(); // 删掉翻译中的所有shop
    }

    @PreDestroy
    public void preDestroy() {
        initialExecutorService.shutdown(); // 停止接收新任务
        manualExecutorService.shutdown();
        try {
            if (!initialExecutorService.awaitTermination(5, TimeUnit.MINUTES)
                    || !manualExecutorService.awaitTermination(5, TimeUnit.MINUTES)) { // 等待任务完成
                throw new IllegalStateException("任务未能在关闭前完成");
            }
        } catch (InterruptedException e) {
            appInsights.trackException(e);
            Thread.currentThread().interrupt(); // 恢复中断状态
        }
    }

    // 手动翻译初始化
    @Scheduled(fixedDelay = 30 * 1000)
    public void scanAndSubmitClickTranslateDbTask() {
        List<InitialTranslateTasksDO> clickTranslateTasks = iInitialTranslateTasksService.selectTop10Tasks(InitialTaskStatusEnum.INIT.status, MANUAL);

        appInsights.trackTrace("scanAndSubmitClickTranslateDbTask Number of clickTranslateTasks need to translate " + clickTranslateTasks.size());
        if (clickTranslateTasks.isEmpty()) {
            return;
        }

        // 遍历clickTranslateTasks，生成initialTasks
        for (InitialTranslateTasksDO task : clickTranslateTasks) {
            manualExecutorService.submit(() -> {
                processInitialTasksOfShop(task);
            });
        }
    }

    // 自动翻译初始化
    @Scheduled(fixedDelay = 50 * 1000)
    public void scanAndSubmitAutoInitialTranslateDbTask() {
        // 获取数据库中的翻译参数
        // 统计待翻译的 task
        List<InitialTranslateTasksDO> clickTranslateTasks = iInitialTranslateTasksService.selectTop10Tasks(InitialTaskStatusEnum.INIT.status, AUTO);

        appInsights.trackTrace("scanAndSubmitInitialTranslateDbTask Number of clickTranslateTasks need to translate " + clickTranslateTasks.size());
        if (clickTranslateTasks.isEmpty()) {
            return;
        }

        // 遍历clickTranslateTasks，生成initialTasks
        for (InitialTranslateTasksDO task : clickTranslateTasks) {
            initialExecutorService.submit(() -> {
                processInitialTasksOfShop(task);
            });
        }
    }

    private void processInitialTasksOfShop(InitialTranslateTasksDO singleTask) {
        String shop = singleTask.getShopName();

        // 获取用户的accessToken
        UsersDO userDO = iUsersService.getUserByName(singleTask.getShopName());

        appInsights.trackTrace("processInitialTasksOfShop task START: " + singleTask.getTaskId() + " of shop: " + shop);
        iInitialTranslateTasksService.updateStatusByTaskId(singleTask.getTaskId(), InitialTaskStatusEnum.TASKS_CREATING.status);

        // taskType为 click 是手动翻译邮件， auto 是自动翻译邮件 ， key 是私有key邮件（这个暂时未实现）
        rabbitMqTranslateService.initialTasks(
                shop, userDO.getAccessToken(),
                singleTask.getSource(), singleTask.getTarget(),
                jsonToObject(singleTask.getTranslateSettings3(), new TypeReference<List<String>>() {
                }),
                singleTask.isHandle(),
                singleTask.getTranslateSettings1(),
                singleTask.isCover(),
                singleTask.getCustomKey(),
                singleTask.getTaskType());

        appInsights.trackTrace("processInitialTasksOfShop task FINISH successfully: " + singleTask.getTaskId() + " of shop: " + shop);
        iInitialTranslateTasksService.updateStatusByTaskId(singleTask.getTaskId(), InitialTaskStatusEnum.TASKS_CREATED.status);

    }

    @Scheduled(fixedDelay = 60 * 1000)
    public void scanAndSendEmail() {
        // 1. 查询待处理任务
        List<InitialTranslateTasksDO> taskList = iInitialTranslateTasksService.selectTasksByStatusAndNotSendEmail(
                        Arrays.asList(InitialTaskStatusEnum.TASKS_CREATING.status, InitialTaskStatusEnum.TASKS_CREATED.status,
                                InitialTaskStatusEnum.TRANSLATED_WRITING_SHOPIFY.status), false);

        for (InitialTranslateTasksDO task : taskList) {
            TranslatesDO translate = iTranslatesService.getSingleTranslateDO(task.getShopName(), task.getSource(), task.getTarget());
            if (translate == null) {
                continue;
            }

            Long costToken = translationCounterRedisService.getLanguageData(task.getShopName(), task.getTarget(), task.getTaskType());
            LocalDateTime createdAt = task.getCreatedAt().toLocalDateTime();
            int translateStatus = translate.getStatus();

            // 2. 状态为7：手动暂停 -> 不发邮件，只标记, 将状态改为4
            if (translateStatus == 7) {
                iInitialTranslateTasksService.updateStatusAndSendEmailByTaskId(task.getTaskId(), InitialTaskStatusEnum.TRANSLATED_WRITTEN.status, true);
                continue;
            }

            // 3. 状态为3：部分翻译，发失败邮件
            if (translateStatus == 3 && !task.isSendEmail()) {
                List<String> translationList = jsonToObject(task.getTranslateSettings3(), new TypeReference<>() {
                });
                if (translationList == null || translationList.isEmpty()) {
                    appInsights.trackTrace("scanAndSendEmail translationList为空: " + task.getShopName());
                    continue;
                }

                List<TranslateResourceDTO> resourceList = convertALL(translationList);
                tencentEmailService.translateFailEmail(task.getShopName(), createdAt, resourceList,
                        task.getTarget(), task.getSource(), costToken);

                iInitialTranslateTasksService.updateStatusAndSendEmailByTaskId(task.getTaskId(), InitialTaskStatusEnum.TRANSLATED_WRITTEN.status, true);
                continue;
            }

            // 4. 进度条状态1 改为 状态为2：翻译中
            if (translateStatus == 2 && task.getStatus() == 1) {
                // 将initial status改为2
                boolean updateFlag = iInitialTranslateTasksService.updateStatusByTaskId(task.getTaskId(), 2);
                if (!updateFlag) {
                    appInsights.trackTrace("FatalException: 修改翻译进度失败 shopName=" + task.getShopName() + " target: " + task.getTarget() + " source: " + task.getSource());
                    continue;
                }
            }

            // 5. 状态为2：翻译中 判断是否完成
            if (translateStatus == 2 && task.getStatus() == 2 && RabbitMqTranslateService.MANUAL.equals(task.getTaskType())) {
                List<TranslateTasksDO> translateTasks = iTranslateTasksService.getTranslateTasksByShopNameAndSourceAndTarget(task.getShopName(), task.getSource(), task.getTarget());

                if (translateTasks.isEmpty()) {
                    boolean updated = iTranslatesService.updateTranslateStatus(task.getShopName(), 1, task.getTarget(), task.getSource()) > 0;
                    if (!updated) {
                        appInsights.trackTrace("FatalException: 修改翻译进度失败 shopName=" + task.getShopName() + " target: " + task.getTarget() + " source: " + task.getSource());
                        continue;
                    }

                    // 更新 Redis 状态（翻译完成）
                    translationParametersRedisService.hsetTranslationStatus(generateProgressTranslationKey(task.getShopName(), task.getSource(), task.getTarget()), "3");
                    translationParametersRedisService.hsetTranslatingString(generateProgressTranslationKey(task.getShopName(), task.getSource(), task.getTarget()), "");
                    boolean updateFlag3 = iInitialTranslateTasksService.updateStatusByTaskId(task.getTaskId(), 3);
                    if (!updateFlag3) {
                        appInsights.trackTrace("FatalException: 修改翻译进度失败 shopName=" + task.getShopName() + " target: " + task.getTarget() + " source: " + task.getSource());
                        continue;
                    }
                    appInsights.trackTrace("scanAndSendEmail 用户: " + task.getShopName() + " 翻译完成，写入状态3。");
                }
                continue;
            }

            // 6. 状态为1：全部完成，可以发成功邮件
            if (translateStatus == 1 && !task.isSendEmail() && task.getStatus() == 3 && RabbitMqTranslateService.MANUAL.equals(task.getTaskType())) {
                // 判断user_Translation_Data表 里面改用户语言是否完成写入
                List<UserTranslationDataDO> userTranslationDataDOS = iUserTranslationDataService.selectWritingDataByShopNameAndTarget(task.getShopName(), task.getTarget());
                if (!userTranslationDataDOS.isEmpty()) {
                    appInsights.trackTrace("scanAndSendEmail 用户: " + task.getShopName() + " 翻译数据未写入完，等待写入。");
                    continue;
                }

                TranslationCounterDO counter = iTranslationCounterService.getTranslationCounterByShopName(task.getShopName());
                Integer limitChars = iTranslationCounterService.getMaxCharsByShopName(task.getShopName());

                // 手动翻译邮件
                tencentEmailService.translateSuccessEmail(
                        new TranslateRequest(0, task.getShopName(), null, task.getSource(), task.getTarget(), null),
                        createdAt, costToken, counter.getUsedChars(), limitChars
                );

                // 更新数据库 & Redis
                iInitialTranslateTasksService.updateStatusAndSendEmailByTaskId(task.getTaskId()
                        , InitialTaskStatusEnum.TRANSLATED_WRITTEN.status, true);

                translationParametersRedisService.hsetTranslationStatus(
                        generateProgressTranslationKey(task.getShopName(), task.getSource(), task.getTarget()), "4");
                translationParametersRedisService.hsetTranslatingString(
                        generateProgressTranslationKey(task.getShopName(), task.getSource(), task.getTarget()), "");

                appInsights.trackTrace("scanAndSendEmail 用户: " + task.getShopName() + " 翻译成功邮件已发送。");
            }

            // 自动翻译邮件是不会改Translates表状态的，所以要单独处理
            if (AUTO.equals(task.getTaskType()) && task.getStatus() == 1 && !task.isSendEmail()) {
                List<TranslateTasksDO> translateTasks = iTranslateTasksService.getTranslateTasksByShopNameAndSourceAndTarget(task.getShopName(), task.getSource(), task.getTarget());
                if (!translateTasks.isEmpty()) {
                    continue;
                }

                // 判断user_Translation_Data表 里面改用户语言是否完成写入
                List<UserTranslationDataDO> userTranslationDataDOS = iUserTranslationDataService.selectWritingDataByShopNameAndTarget(task.getShopName(), task.getTarget());
                if (!userTranslationDataDOS.isEmpty()) {
                    appInsights.trackTrace("scanAndSendEmail 用户: " + task.getShopName() + " 翻译数据未写入完，等待写入。");
                    continue;
                }

                Integer limitChars = iTranslationCounterService.getMaxCharsByShopName(task.getShopName());
                tencentEmailService.emailAutoTranslate(task.getShopName(), task.getTarget(), createdAt, Math.toIntExact(costToken), limitChars, task.getTaskId());
            }
        }
    }

}
