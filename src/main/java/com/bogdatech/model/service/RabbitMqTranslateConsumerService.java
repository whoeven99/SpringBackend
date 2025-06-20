package com.bogdatech.model.service;

import com.alibaba.fastjson.JSON;
import com.bogdatech.Service.ITranslatesService;
import com.bogdatech.Service.ITranslationCounterService;
import com.bogdatech.entity.DO.TranslationCounterDO;
import com.bogdatech.entity.VO.RabbitMqTranslateVO;
import com.bogdatech.exception.ClientException;
import com.bogdatech.logic.RabbitMqTranslateService;
import com.bogdatech.utils.CharacterCountUtils;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

import static com.bogdatech.config.RabbitMQConfig.userExchange;
import static com.bogdatech.constants.RabbitMQConstants.*;

import static com.bogdatech.constants.TranslateConstants.EMAIL;
import static com.bogdatech.logic.TranslateService.executorService;

@Service
public class RabbitMqTranslateConsumerService {
    private final AmqpAdmin amqpAdmin;
    private final ConnectionFactory connectionFactory;
    private final RabbitMqTranslateService rabbitMqTranslateService;
    private final ITranslationCounterService translationCounterService;
    private final ITranslatesService translatesService;
    // 用于记录每个用户的监听容器
    private final Map<String, SimpleMessageListenerContainer> listenerMap = new ConcurrentHashMap<>();

    // 用于记录每个用户上次访问时间（用于清理）
    private final Map<String, Long> lastAccessMap = new ConcurrentHashMap<>();

    @Autowired
    public RabbitMqTranslateConsumerService(AmqpAdmin amqpAdmin,
                                            ConnectionFactory connectionFactory, RabbitMqTranslateService rabbitMqTranslateService, ITranslationCounterService translationCounterService, ITranslatesService translatesService) {
        this.amqpAdmin = amqpAdmin;
        this.connectionFactory = connectionFactory;
        this.rabbitMqTranslateService = rabbitMqTranslateService;
        this.translationCounterService = translationCounterService;
        this.translatesService = translatesService;
    }

    // 信号量，控制同时最多3监听器
    private final Semaphore semaphore = new Semaphore(3);

    // 用户点击翻译按钮时调用：动态创建队列和监听器
    public void handleUserRequest(String shopName) {
        String queueName = USER_TRANSLATE_QUEUE + shopName;

        // 如果该用户监听器还不存在，则创建队列并绑定、启动监听器
        if (!listenerMap.containsKey(shopName)) {
            startListener(queueName, shopName);
        }

        // 更新该用户的上次活跃时间
        lastAccessMap.put(shopName, System.currentTimeMillis());
    }



    // 启动监听器用于处理该用户的消息
    // 初始化时调用一次，启动全局队列监听器
    @Bean
    public void startListener(String queueName, String shopName) {


            SimpleMessageListenerContainer container = new SimpleMessageListenerContainer(connectionFactory);
            container.setQueueNames(queueName);
            container.setAcknowledgeMode(AcknowledgeMode.AUTO); // 自动确认
            container.setPrefetchCount(5); // 每个消费者每次最多预取消息数
            container.setConcurrentConsumers(5); // 启动时并发消费者数量
            container.setMaxConcurrentConsumers(10); // 最大并发消费者数量
            container.setDefaultRequeueRejected(false); // 拒绝的消息不重新入队，进入 DLQ

            container.setMessageListener((Message message) -> {
                String body = new String(message.getBody());
//                    System.out.printf("用户 %s 收到消息: %s%n", shopName, body);

                try {
                    RabbitMqTranslateVO rabbitMqTranslateVO = JSON.parseObject(body, RabbitMqTranslateVO.class);
                    if (EMAIL.equals(rabbitMqTranslateVO.getShopifyData())){
                        // 处理邮件发送功能
                        rabbitMqTranslateService.sendTranslateEmail(rabbitMqTranslateVO);
                    }else {
                        // 处理翻译功能
                        processMessage(rabbitMqTranslateVO);
                    }
                    // 更新该用户的活跃时间
                    lastAccessMap.put(shopName, System.currentTimeMillis());

                } catch (ClientException e1){
                    System.out.println("到达字符限制： " + e1);
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

            }
    }

    // 模拟消息处理函数
    private void processMessage(RabbitMqTranslateVO rabbitMqTranslateVO ) {
        // 这里可以放真正的翻译处理逻辑
        //获取当前的token值
        CharacterCountUtils usedCounter = new CharacterCountUtils();
        //判断字符是否超限
        TranslationCounterDO request1 = translationCounterService.readCharsByShopName(rabbitMqTranslateVO.getShopName());
        Integer remainingChars = rabbitMqTranslateVO.getLimitChars();
        int usedChars = request1.getUsedChars();
        // 如果字符超限，则直接返回字符超限
        if (usedChars >= remainingChars) {
            System.out.println("字符超限 processMessage");
            return ;
        }
        // 修改数据库当前的数据
        translatesService.updateTranslatesResourceType(rabbitMqTranslateVO.getShopName(), rabbitMqTranslateVO.getTarget(), rabbitMqTranslateVO.getSource(), rabbitMqTranslateVO.getModeType());
        rabbitMqTranslateVO.setLimitChars(remainingChars);
        //初始化计数器
        CharacterCountUtils counter = new CharacterCountUtils();
        counter.addChars(usedChars);
        rabbitMqTranslateService.translateByModeType(rabbitMqTranslateVO, usedCounter);
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
