package com.bogdatech.task;

import com.bogdatech.Service.ITranslateTasksService;
import com.bogdatech.entity.DO.TranslateTasksDO;
import com.bogdatech.entity.VO.RabbitMqTranslateVO;
import com.bogdatech.model.service.RabbitMqTranslateConsumerService;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

import static com.bogdatech.logic.TranslateService.OBJECT_MAPPER;
import static com.bogdatech.logic.TranslateService.executorService;
import static com.bogdatech.utils.CaseSensitiveUtils.appInsights;

@Component
@EnableScheduling
@EnableAsync
public class RabbitMqTask {
    private final RabbitMqTranslateConsumerService rabbitMqTranslateConsumerService;
    private final ITranslateTasksService translateTasksService;

    public RabbitMqTask(RabbitMqTranslateConsumerService rabbitMqTranslateConsumerService, ITranslateTasksService translateTasksService) {
        this.rabbitMqTranslateConsumerService = rabbitMqTranslateConsumerService;
        this.translateTasksService = translateTasksService;
    }

    public static final ConcurrentHashMap<String, ReentrantLock> SHOP_LOCKS = new ConcurrentHashMap<>();
    public static final Set<String> PROCESSING_SHOPS = ConcurrentHashMap.newKeySet();

    // 每6秒钟检查一次是否有闲置线程
    @Scheduled(fixedDelay = 10000)
    public void scanAndSubmitTasks() {
        //查询 0 状态的记录，过滤掉 shop 已被锁定的
        List<TranslateTasksDO> tasks = new ArrayList<>();
        try {
            tasks = translateTasksService.find0StatusTasks();
        } catch (Exception e) {
            appInsights.trackTrace("获取task集合失败 errors");
        }
        if (tasks.isEmpty()) {
            return;
        }
        for (TranslateTasksDO task : tasks) {
            String shopName = task.getShopName();
            //判断PROCESSING_SHOPS里面是否有该用户，是跳过，否的话，翻译
            if (!PROCESSING_SHOPS.contains(shopName)) {
                PROCESSING_SHOPS.add(shopName);
                executorService.submit(() -> {
                    if (tryLock(shopName)) {
                        appInsights.trackTrace("Lock [" + shopName + "] by thread " + Thread.currentThread().getName() + "shop: " + SHOP_LOCKS.get(shopName));
                        try {
                            // 只执行一个线程处理这个 shopName
                            RabbitMqTranslateVO vo = OBJECT_MAPPER.readValue(task.getPayload(), RabbitMqTranslateVO.class);
                            rabbitMqTranslateConsumerService.startTranslate(vo, task);
                        } catch (Exception e) {
                            appInsights.trackTrace("处理失败 errors : " + e);
                            //将该模块状态改为4
                            translateTasksService.updateByTaskId(task.getTaskId(), 4);
                        } finally {
                            unlock(shopName);
                            PROCESSING_SHOPS.remove(shopName);
                        }
                    }
                });
            }
        }
    }

    /**
     * 尝试加锁
     *
     * @param shopName 商品名称（锁标识）
     * @return true：成功获取锁；false：已被锁定
     */
    public static boolean tryLock(String shopName) {
        // 获取或创建锁对象（非阻塞）
        ReentrantLock lock = SHOP_LOCKS.computeIfAbsent(shopName, key -> new ReentrantLock());

        // 尝试获取锁（非阻塞）
        return lock.tryLock();
    }

    /**
     * 释放锁
     *
     * @param shopName 商品名称（锁标识）
     */
    public static void unlock(String shopName) {
        ReentrantLock lock = SHOP_LOCKS.get(shopName);
        if (lock != null && lock.isHeldByCurrentThread()) {
            appInsights.trackTrace("unlock before Lock [" + shopName + "] by thread " + Thread.currentThread().getName());
            lock.unlock();
            appInsights.trackTrace("unlock after Lock [" + shopName + "] by thread " + Thread.currentThread().getName());
        }
    }


}

