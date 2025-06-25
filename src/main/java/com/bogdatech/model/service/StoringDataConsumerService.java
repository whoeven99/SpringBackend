package com.bogdatech.model.service;

import com.bogdatech.model.controller.request.CloudInsertRequest;
import com.rabbitmq.client.Channel;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

import static com.bogdatech.constants.RabbitMQConstants.USER_STORE_QUEUE;
import static com.bogdatech.logic.ShopifyService.saveToShopify;
import static com.bogdatech.utils.CaseSensitiveUtils.appInsights;
import static com.bogdatech.utils.JsonUtils.jsonToObject;

@Service
public class StoringDataConsumerService {


    public static ExecutorService storeExecutorService = new ThreadPoolExecutor(
            2,   // 核心线程数（比 vCPU 多一点）
            4,  // 最大线程数（vCPU * 4）
            10L, TimeUnit.SECONDS, // 空闲线程存活时间
            new LinkedBlockingQueue<>(10), // 任务队列（避免内存过载）
            new ThreadPoolExecutor.CallerRunsPolicy() // 拒绝策略
    );

    public static final ConcurrentHashMap<String, ReentrantLock> LOCK_MAP = new ConcurrentHashMap<>();


    /**
     * 接收存储任务,将翻译好的数据异步mq，存储到shopify本地
     */
    @RabbitListener(queues = USER_STORE_QUEUE, concurrency = "3", ackMode = "MANUAL")
    public void userStoreData(String json, Channel channel, Message rawMessage,@Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        long safeDeliveryTag = deliveryTag;
        storeExecutorService.execute(() -> {
            storeData(json, channel, safeDeliveryTag);
        });

    }

    public void storeData(String json, Channel channel, long rawMessage) {
        CloudInsertRequest cloudInsertRequest = jsonToObject(json, CloudInsertRequest.class);
        ReentrantLock lock = LOCK_MAP.computeIfAbsent(cloudInsertRequest.getShopName(), k -> new ReentrantLock());
        boolean locked = false;
        // 业务处理
        try {
            locked = lock.tryLock(30, TimeUnit.SECONDS);  // 防止死锁

            if (!locked) {
                appInsights.trackTrace("Could not acquire lock for shopName errors : " + cloudInsertRequest.getShopName());
                channel.basicNack(rawMessage, false, true); // 重试
                return;
            }

            // 开始业务处理
            saveToShopify(cloudInsertRequest);
            channel.basicAck(rawMessage, false);
        } catch (Exception e) {
            appInsights.trackTrace("Error processing shop errors : " + cloudInsertRequest.getShopName() + ", errors : " + e.getMessage());
            try {
                channel.basicNack(rawMessage, false, true); // 允许重试
            } catch (IOException ex) {
                appInsights.trackTrace("errors while nacking message errors : " + ex.getMessage());
            }
        } finally {
            if (locked) {
                lock.unlock();
            }
        }
    }
}
