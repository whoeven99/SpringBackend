package com.bogdatech.utils;

import com.bogdatech.Service.ITranslateTasksService;
import com.bogdatech.entity.DO.TranslateTasksDO;
import com.bogdatech.entity.VO.RabbitMqTranslateVO;
import com.bogdatech.integration.RedisIntegration;
import com.bogdatech.logic.RedisTranslateLockService;
import com.bogdatech.model.service.ProcessDbTaskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import static com.bogdatech.task.DBTask.executorService;
import static com.bogdatech.utils.CaseSensitiveUtils.appInsights;
import static com.bogdatech.utils.JsonUtils.jsonToObject;
import static com.bogdatech.utils.RedisKeyUtils.generateTranslateLockKey;

@Component
public class RedisLockUtils {
    @Autowired
    private RedisIntegration redisIntegration;
    @Autowired
    private RedisTranslateLockService redisTranslateLockService;
    @Autowired
    private ProcessDbTaskService processDbTaskService;
    @Autowired
    private ITranslateTasksService translateTasksService;

    /**
     * 在加锁时判断是否成功，成功-翻译；不成功跳过
     * */
    public void translateLock(String shopName, TranslateTasksDO task){
        //在加锁时判断是否成功，成功-翻译；不成功跳过
        if (redisTranslateLockService.lockStore(shopName)) {
            executorService.submit(() -> {
                appInsights.trackTrace("DBTask Lock [" + shopName + "] by thread " + Thread.currentThread().getName()
                        + "shop: " + shopName + " 锁的状态： " + redisIntegration.get(generateTranslateLockKey(shopName)));
                try {
                    // 只执行一个线程处理这个 shopName
                    RabbitMqTranslateVO vo = jsonToObject(task.getPayload(), RabbitMqTranslateVO.class);
                    if (vo == null) {
                        redisTranslateLockService.unLockStore(shopName);
                        appInsights.trackTrace("每日须看 ： " + shopName + " 处理失败，payload为空 " + task.getPayload());
                        //将taskId 改为10（暂定）
                        translateTasksService.updateByTaskId(task.getTaskId(), 10);
                        return;
                    }
                    processDbTaskService.startTranslate(vo, task);
                } catch (Exception e) {
                    appInsights.trackTrace("clickTranslation " + shopName + " 处理失败 errors : " + e);
                    //将该模块状态改为4
                    translateTasksService.updateByTaskId(task.getTaskId(), 4);
                    appInsights.trackException(e);
                } finally {
                    redisTranslateLockService.unLockStore(shopName);
                }
            });

        }
    }
}
