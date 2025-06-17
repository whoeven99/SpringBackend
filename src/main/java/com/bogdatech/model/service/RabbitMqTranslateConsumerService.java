package com.bogdatech.model.service;

import com.alibaba.fastjson.JSON;
import com.bogdatech.entity.VO.RabbitMqTranslateVO;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

import static com.bogdatech.config.RabbitMQConfig.userExchange;
import static com.bogdatech.constants.RabbitMQConstants.*;
import static com.bogdatech.logic.RabbitMqTranslateService.translateByModeType;
import static com.bogdatech.logic.TranslateService.executorService;

@Service
public class RabbitMqTranslateConsumerService {
    private final AmqpAdmin amqpAdmin;
    private final ConnectionFactory connectionFactory;
    // 用于记录每个用户的监听容器
    private final Map<String, SimpleMessageListenerContainer> listenerMap = new ConcurrentHashMap<>();

    // 用于记录每个用户上次访问时间（用于清理）
    private final Map<String, Long> lastAccessMap = new ConcurrentHashMap<>();

    @Autowired
    public RabbitMqTranslateConsumerService(AmqpAdmin amqpAdmin,
                                            ConnectionFactory connectionFactory) {
        this.amqpAdmin = amqpAdmin;
        this.connectionFactory = connectionFactory;
    }

    // 信号量，控制同时最多3监听器
    private final Semaphore semaphore = new Semaphore(3);

    // 用户点击翻译按钮时调用：动态创建队列和监听器
    public void handleUserRequest(String shopName) {
        String queueName = USER_TRANSLATE_QUEUE + shopName;

        // 如果该用户监听器还不存在，则创建队列并绑定、启动监听器
        if (!listenerMap.containsKey(shopName)) {
            declareQueueAndBinding(queueName, shopName);
            startListener(queueName, shopName);
        }

        // 更新该用户的上次活跃时间
        lastAccessMap.put(shopName, System.currentTimeMillis());
    }

    // 声明一个队列并绑定到交换机
    private void declareQueueAndBinding(String queueName, String shopName) {
        Map<String, Object> args = new HashMap<>();
        args.put("x-dead-letter-exchange", USER_DEAD_LETTER_EXCHANGE);      // 设置死信交换机
        args.put("x-dead-letter-routing-key", USER_DEAD_LETTER_ROUTING_KEY);       // 设置死信路由键

        // 创建非持久、非排他、自动删除的队列
        Queue queue = new Queue(queueName, false, false, true, args);
        Binding binding = BindingBuilder
                .bind(queue)
                .to(userExchange())
                .with(USER_TRANSLATE_ROUTING_KEY + shopName);

        amqpAdmin.declareQueue(queue);  // 注册队列
        amqpAdmin.declareBinding(binding);  // 注册绑定
    }

    // 启动监听器用于处理该用户的消息
    private void startListener(String queueName, String shopName) {
        executorService.submit(() -> {
            try {
                semaphore.acquire(); // 获取许可，限制最大并发数
                SimpleMessageListenerContainer container = new SimpleMessageListenerContainer(connectionFactory);
                container.setQueueNames(queueName);
                container.setAcknowledgeMode(AcknowledgeMode.AUTO); // 自动确认
                container.setPrefetchCount(1); // 每次处理一条
                container.setDefaultRequeueRejected(false); // 拒绝的消息不重新入队，进入 DLQ

                container.setMessageListener((Message message) -> {
                    String body = new String(message.getBody());
                    System.out.printf("用户 %s 收到消息: %s%n", shopName, body);

                    try {
                        // 处理消息逻辑
                        processMessage(body);

                        // 更新该用户的活跃时间
                        lastAccessMap.put(shopName, System.currentTimeMillis());

                    } catch (Exception e) {
                        System.out.println("处理消息失败，消息将进入死信队列。");
                        throw new AmqpRejectAndDontRequeueException("处理失败", e); // 触发进入 DLQ
                    }
                });

                try {
                    container.start(); // 启动监听器
                    listenerMap.put(shopName, container);
                } catch (Exception e) {
                    container.stop();
                    semaphore.release(); // 释放许可
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.err.println("监听器启动被中断: " + shopName);
            }
        });
    }

    // 模拟消息处理函数
    private void processMessage(String msg) {
        System.out.println("msg: " + msg);
        if (msg.contains("fail")) {
            throw new RuntimeException("模拟处理失败");
        }
        // 这里可以放真正的翻译处理逻辑
        //将msg转为为RabbitMqTranslateVO类型
        RabbitMqTranslateVO rabbitMqTranslateVO = JSON.parseObject(msg, RabbitMqTranslateVO.class);
        translateByModeType(rabbitMqTranslateVO);
    }

    // 清理不活跃用户队列和监听器
    public void stopAndCleanupInactiveQueues(long idleTimeoutMillis) {
        long now = System.currentTimeMillis();

        for (String shopName : new HashSet<>(lastAccessMap.keySet())) {
            long lastUsed = lastAccessMap.get(shopName);

            // 超过指定时间未活跃的用户进行清理
            if (now - lastUsed > idleTimeoutMillis) {
                String queueName = "translation.queue." + shopName;

                SimpleMessageListenerContainer container = listenerMap.remove(shopName);
                if (container != null) {
                    container.stop();
                    System.out.printf("监听器已停止：%s%n", shopName);
                    semaphore.release(); // 释放槽位
                }

                amqpAdmin.deleteQueue(queueName); // 删除队列
                lastAccessMap.remove(shopName);

                System.out.printf("已清理用户 %s 的队列和监听器%n", shopName);
            }
        }
    }

}
