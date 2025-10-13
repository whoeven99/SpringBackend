package com.bogdatech.task;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.bogdatech.logic.redis.TranslationMonitorRedisService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bogdatech.Service.ITranslationCounterService;
import com.bogdatech.Service.IUsersService;
import com.bogdatech.entity.DO.InitialTranslateTasksDO;
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
        appInsights.trackTrace("processInitialTasksOfShop init");
        initialTranslateRedisService.setDelete(); // 删掉翻译中的所有shop
    }

    @Scheduled(fixedRate = 55 * 1000)
    public void scanAndSubmitInitialTranslateDbTask() {
        // 获取数据库中的翻译参数
        // 统计待翻译的 task
        List<InitialTranslateTasksDO> clickTranslateTasks = initialTranslateTasksMapper.selectList(new LambdaQueryWrapper<InitialTranslateTasksDO>().eq(InitialTranslateTasksDO::getStatus, 0).orderByAsc(InitialTranslateTasksDO::getCreatedAt));

        appInsights.trackTrace("scanAndSubmitInitialTranslateDbTask Number of clickTranslateTasks need to translate " + clickTranslateTasks.size());
        if (clickTranslateTasks.isEmpty()) {
            return;
        }

        // 统计待获取task的shop数量
        Set<String> shops = clickTranslateTasks.stream().map(InitialTranslateTasksDO::getShopName).collect(Collectors.toSet());
        appInsights.trackTrace("scanAndSubmitInitialTranslateDbTask Number of existing shops: " + shops.size());

        // 异步开启一个翻译任务
        for (String shop : shops) {
            // 该shop对应的所有task
            Set<InitialTranslateTasksDO> shopTasks = clickTranslateTasks.stream()
                    .filter(taskDo -> taskDo.getShopName().equals(shop))
                    .collect(Collectors.toSet());

            // 这一行的日志可以看到每个shop的clickTranslateDbTasks是否在减少
            appInsights.trackTrace("scanAndSubmitInitialTranslateDbTask Number of shopTasks: " + shopTasks.size() + " need to translate of shop: " + shop);

            // Monitor 记录shop开始的时间（中国区时间）
            String chinaTime = ZonedDateTime.now(ZoneId.of("Asia/Shanghai")).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            translationMonitorRedisService.hsetStartTranslationAt(shop, chinaTime);

            // 当前的加锁，只是为了保持一个shop只会被一个线程处理，防止进度条或者其他的状态不兼容并发翻译
            // 这里加锁的方式是将shop放进一个set
            if (initialTranslateRedisService.setAdd(shop)) {
                appInsights.trackTrace("scanAndSubmitInitialTranslateDbTask new shop start translate: " + shop);
                System.out.println("scanAndSubmitInitialTranslateDbTask new shop start translate: " + shop);
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

    private void processInitialTasksOfShop(String shop, Set<InitialTranslateTasksDO> shopTasks) {
        // 获取用户的accessToken
        UsersDO userDO = iUsersService.getOne(new LambdaQueryWrapper<UsersDO>().eq(UsersDO::getShopName, shop));

        for (InitialTranslateTasksDO task : shopTasks) {
            appInsights.trackTrace("processInitialTasksOfShop task START: " + task.getTaskId() + " of shop: " + shop);
            System.out.println("processInitialTasksOfShop task START: " + task.getTaskId() + " of shop: " + shop);
            initialTranslateTasksMapper.update(new LambdaUpdateWrapper<InitialTranslateTasksDO>().eq(InitialTranslateTasksDO::getTaskId, task.getTaskId()).set(InitialTranslateTasksDO::getStatus, 2));

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

            // taskType为 click 是手动翻译邮件， auto 是自动翻译邮件 ， key 是私有key邮件（这个暂时未实现）
            rabbitMqTranslateService.mqTranslate(new ShopifyRequest(shop, userDO.getAccessToken(), API_VERSION_LAST, task.getTarget()), counter, modelList, new TranslateRequest(0, shop, userDO.getAccessToken(), task.getSource(), task.getTarget(), null), remainingChars, usedChars, task.isHandle(), task.getTranslateSettings1(), task.isCover(), task.getCustomKey(), task.getTaskType());
            appInsights.trackTrace("processInitialTasksOfShop task FINISH successfully: " + task.getTaskId() + " of shop: " + shop);
            System.out.println("processInitialTasksOfShop task FINISH successfully: " + task.getTaskId() + " of shop: " + shop);
            initialTranslateTasksMapper.update(new LambdaUpdateWrapper<InitialTranslateTasksDO>().eq(InitialTranslateTasksDO::getTaskId, task.getTaskId()).set(InitialTranslateTasksDO::getStatus, 1));
        }

    }
}
