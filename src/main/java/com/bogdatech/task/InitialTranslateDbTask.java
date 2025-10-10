package com.bogdatech.task;

import com.bogdatech.logic.redis.TranslationMonitorRedisService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bogdatech.Service.ITranslationCounterService;
import com.bogdatech.Service.IUsersService;
import com.bogdatech.entity.DO.ClickTranslateTasksDO;
import com.bogdatech.entity.DO.TranslationCounterDO;
import com.bogdatech.entity.DO.UsersDO;
import com.bogdatech.logic.RabbitMqTranslateService;
import com.bogdatech.logic.redis.InitialTranslateRedisService;
import com.bogdatech.mapper.InitialTranslateTasksMapper;
import com.bogdatech.model.controller.request.ShopifyRequest;
import com.bogdatech.model.controller.request.TranslateRequest;
import com.bogdatech.utils.CharacterCountUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.bogdatech.constants.TranslateConstants.API_VERSION_LAST;
import static com.bogdatech.logic.TranslateService.executorService;
import static com.bogdatech.utils.CaseSensitiveUtils.appInsights;
import static com.bogdatech.utils.JsonUtils.jsonToObject;

@Component
@EnableScheduling
public class InitialTranslateDbTask {
    @Autowired
    private RabbitMqTranslateService rabbitMqTranslateService;
    @Autowired
    private InitialTranslateRedisService initialTranslateRedisService;
    @Autowired
    private InitialTranslateTasksMapper initialTranslateTasksMapper;
    @Autowired
    private ITranslationCounterService iTranslationCounterService;
    @Autowired
    private IUsersService iUsersService;
    @Autowired
    private TranslationMonitorRedisService translationMonitorRedisService;

    /**
     * 恢复因重启或其他原因中断的手动翻译大任务的task
     */
    @PostConstruct
    public void init() {
        if (System.getenv("REDISCACHEHOSTNAME") == null) {
            return;
        }
        appInsights.trackTrace("ClickTranslateDbTaskLog init");
        initialTranslateRedisService.setDelete(); // 删掉翻译中的所有shop
    }

    @Scheduled(fixedRate = 55 * 1000)
    public void scanAndSubmitInitialTranslateDbTask() {
        // 获取数据库中的翻译参数
        // 统计待翻译的 task
        List<ClickTranslateTasksDO> clickTranslateTasks = initialTranslateTasksMapper.selectList(new LambdaQueryWrapper<ClickTranslateTasksDO>().eq(ClickTranslateTasksDO::getStatus, 0).orderByAsc(ClickTranslateTasksDO::getCreatedAt));

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

            // Monitor 记录shop开始的时间（中国区时间）
            String chinaTime = ZonedDateTime.now(ZoneId.of("Asia/Shanghai")).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            translationMonitorRedisService.hsetStartTranslationAt(shop, chinaTime);

            // 当前的加锁，只是为了保持一个shop只会被一个线程处理，防止进度条或者其他的状态不兼容并发翻译
            // 这里加锁的方式是将shop放进一个set
            if (initialTranslateRedisService.setAdd(shop)) {
                appInsights.trackTrace("ClickTranslateDbTaskLog new shop start translate: " + shop);
                executorService.submit(() -> {
                    try {
                        processInitialTasksOfShop(shop, shopTasks);
                    } finally {
                        initialTranslateRedisService.setRemove(shop);
                    }
                });
            }
        }
    }

    private void processInitialTasksOfShop(String shop, Set<ClickTranslateTasksDO> shopTasks) {
        // 获取用户的accessToken
        UsersDO userDO = iUsersService.getUserByName(shop);

        for (ClickTranslateTasksDO task : shopTasks) {
            appInsights.trackTrace("ClickTranslateDbTaskLog task START: " + task.getTaskId() + " of shop: " + shop);

            // 获取已使用字符数和剩余字符数
            TranslationCounterDO translationCounterDO = iTranslationCounterService.readCharsByShopName(shop);
            Integer remainingChars = iTranslationCounterService.getMaxCharsByShopName(shop);
            int usedChars = translationCounterDO.getUsedChars();

            // 初始化计数器
            CharacterCountUtils counter = new CharacterCountUtils();
            counter.addChars(usedChars);

            // 转化模块类型
            List<String> modelList = jsonToObject(
                    task.getTranslateSettings3(),
                    new TypeReference<List<String>>() {
                    }
            );

            rabbitMqTranslateService.mqTranslate(new ShopifyRequest(shop, userDO.getAccessToken(), API_VERSION_LAST, task.getTarget()), counter, modelList, new TranslateRequest(0, shop, userDO.getAccessToken(), task.getSource(), task.getTarget(), null), remainingChars, usedChars, task.isHandle(), task.getTranslateSettings1(), task.isCover(), task.getCustomKey(), true);
            appInsights.trackTrace("ClickTranslateDbTaskLog task FINISH successfully: " + task.getTaskId() + " of shop: " + shop);

        }

    }
}
