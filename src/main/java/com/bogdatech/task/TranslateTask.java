package com.bogdatech.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bogdatech.Service.impl.InitialTaskV2Repo;
import com.bogdatech.Service.impl.TranslatesServiceImpl;
import com.bogdatech.entity.DO.*;
import com.bogdatech.logic.TaskService;
import com.bogdatech.logic.translate.TranslateV2Service;
import com.bogdatech.utils.JsonUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

import static com.bogdatech.constants.TranslateConstants.API_VERSION_LAST;
import static com.bogdatech.logic.RabbitMqTranslateService.AUTO;
import static com.bogdatech.logic.TranslateService.executorService;
import static com.bogdatech.requestBody.ShopifyRequestBody.getShopLanguageQuery;
import static com.bogdatech.utils.CaseSensitiveUtils.appInsights;

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

    // 执行结束之后，再等30秒执行下一次
//    @Scheduled(fixedDelay = 30 * 1000)
    public void initialToTranslateTask() {
        List<InitialTaskV2DO> initTaskList = initialTaskV2Repo.selectByStatus(0);
        if (CollectionUtils.isEmpty(initTaskList)) return;

        for (InitialTaskV2DO initialTaskV2DO : initTaskList) {
            // 断电问题，在里面的needTranslate处理
            translateV2Service.initialToTranslateTask(initialTaskV2DO);
        }
    }

    // 定时30秒扫描一次
//    @Scheduled(fixedRate = 30 * 1000)
    public void translateEachTask() {
        List<InitialTaskV2DO> translatingTask = initialTaskV2Repo.selectByStatus(1);
        if (CollectionUtils.isEmpty(translatingTask)) return;

        for (InitialTaskV2DO initialTaskV2DO : translatingTask) {
            // 断电
            translateV2Service.translateEachTask(initialTaskV2DO);
        }
    }

    // 定时30秒扫描一次
//    @Scheduled(fixedRate = 30 * 1000)
    public void saveToShopify() {
        List<InitialTaskV2DO> translatingTask = initialTaskV2Repo.selectByStatus(2);
        if (CollectionUtils.isEmpty(translatingTask)) return;

        for (InitialTaskV2DO initialTaskV2DO : translatingTask) {
            // 断电
            translateV2Service.saveToShopify(initialTaskV2DO);
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
