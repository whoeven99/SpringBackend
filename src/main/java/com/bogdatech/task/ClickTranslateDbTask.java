package com.bogdatech.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bogdatech.Service.ITranslationCounterService;
import com.bogdatech.entity.DO.ClickTranslateTasksDO;
import com.bogdatech.entity.DO.TranslateTasksDO;
import com.bogdatech.entity.DO.TranslationCounterDO;
import com.bogdatech.logic.RabbitMqTranslateService;
import com.bogdatech.logic.redis.ClickTranslateRedisService;
import com.bogdatech.mapper.ClickTranslateTasksMapper;
import com.bogdatech.utils.CharacterCountUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.bogdatech.logic.TranslateService.executorService;
import static com.bogdatech.utils.CaseSensitiveUtils.appInsights;

@Component
@EnableScheduling
public class ClickTranslateDbTask {
    @Autowired
    private RabbitMqTranslateService rabbitMqTranslateService;
    @Autowired
    private ClickTranslateRedisService clickTranslateRedisService;
    @Autowired
    private ClickTranslateTasksMapper clickTranslateTasksMapper;
    @Autowired
    private ITranslationCounterService iTranslationCounterService;

    /**
     * 恢复因重启或其他原因中断的手动翻译大任务的task
     */
    @PostConstruct
    public void init() {
        if (System.getenv("REDISCACHEHOSTNAME") == null) {
            return;
        }
        appInsights.trackTrace("ClickTranslateDbTaskLog init");
        clickTranslateRedisService.setDelete(); // 删掉翻译中的所有shop
    }

    //    @Scheduled(fixedRate = 10 * 1000)
    public void scanAndSubmitClickTranslateDbTask() {
        // 获取数据库中的翻译参数
        // 统计待翻译的 task
        List<ClickTranslateTasksDO> clickTranslateTasks = clickTranslateTasksMapper.selectList(new LambdaQueryWrapper<ClickTranslateTasksDO>().eq(ClickTranslateTasksDO::getStatus, 0).orderByAsc(ClickTranslateTasksDO::getCreatedAt));

        appInsights.trackTrace("ClickTranslateDbTaskLog Number of clickTranslateTasks need to translate " + clickTranslateTasks.size());
        if (clickTranslateTasks.isEmpty()) {
            return;
        }

        // 统计待获取task的shop数量
        Set<String> shops = clickTranslateTasks.stream().map(ClickTranslateTasksDO::getShopName).collect(Collectors.toSet());
        appInsights.trackTrace("ClickTranslateDbTaskLog Number of existing shops: " + shops.size());


        // 异步开启一个翻译任务
        for (String shop : shops) {
            // 该shop对应的所有task
            Set<ClickTranslateTasksDO> shopTasks = clickTranslateTasks.stream()
                    .filter(taskDo -> taskDo.getShopName().equals(shop))
                    .collect(Collectors.toSet());

            // 这一行的日志可以看到每个shop的clickTranslateDbTasks是否在减少
            appInsights.trackTrace("ClickTranslateDbTaskLog Number of shopTasks: " + shopTasks.size() + " need to translate of shop: " + shop);

            // 当前的加锁，只是为了保持一个shop只会被一个线程处理，防止进度条或者其他的状态不兼容并发翻译
            // 这里加锁的方式是将shop放进一个set
            if (clickTranslateRedisService.setAdd(shop)) {
                appInsights.trackTrace("ClickTranslateDbTaskLog new shop start translate: " + shop);
                executorService.submit(() -> {
                    try {
                        processClickTasksOfShop(shop, shopTasks);
                    } finally {
                        clickTranslateRedisService.setRemove(shop);
                    }
                });
            }
        }
    }

    private void processClickTasksOfShop(String shop, Set<ClickTranslateTasksDO> shopTasks) {
        for (ClickTranslateTasksDO task : shopTasks) {
            appInsights.trackTrace("ClickTranslateDbTaskLog task START: " + task.getTaskId() + " of shop: " + shop);

            TranslationCounterDO translationCounterDO = iTranslationCounterService.readCharsByShopName(shop);
            Integer remainingChars = iTranslationCounterService.getMaxCharsByShopName(shop);

            // 判断字符是否超限
            int usedChars = translationCounterDO.getUsedChars();
            // 初始化计数器
            CharacterCountUtils counter = new CharacterCountUtils();
            counter.addChars(usedChars);

            rabbitMqTranslateService.mqTranslate(shopifyRequest, counter, task.getTranslateSettings3(), request, remainingChars, usedChars, handleFlag, translationModel, isCover, cleanedText, emailType);
            appInsights.trackTrace("ClickTranslateDbTaskLog task FINISH successfully: " + task.getTaskId() + " of shop: " + shop);
            // TODO: Monitor 记录最后一次task完成时间（中国区时间）
        }

    }
}
