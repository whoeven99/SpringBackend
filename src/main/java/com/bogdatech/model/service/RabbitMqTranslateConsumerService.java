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

import static com.bogdatech.config.RabbitMQConfig.userTranslateQueue;

import static com.bogdatech.constants.TranslateConstants.EMAIL;
import static com.bogdatech.logic.TranslateService.executorService;

@Service
public class RabbitMqTranslateConsumerService {
    private final ConnectionFactory connectionFactory;
    private final RabbitMqTranslateService rabbitMqTranslateService;
    private final ITranslationCounterService translationCounterService;
    private final ITranslatesService translatesService;

    @Autowired
    public RabbitMqTranslateConsumerService(ConnectionFactory connectionFactory, RabbitMqTranslateService rabbitMqTranslateService, ITranslationCounterService translationCounterService, ITranslatesService translatesService) {
        this.connectionFactory = connectionFactory;
        this.rabbitMqTranslateService = rabbitMqTranslateService;
        this.translationCounterService = translationCounterService;
        this.translatesService = translatesService;
    }


    // 初始化时调用一次，启动全局队列监听器
    // 启动监听器用于处理该用户的消息
    @Bean
    public void startListener() {


        SimpleMessageListenerContainer container = new SimpleMessageListenerContainer(connectionFactory);
        container.setQueues(userTranslateQueue());
        container.setAcknowledgeMode(AcknowledgeMode.AUTO); // 自动确认
        container.setPrefetchCount(5); // 每个消费者每次最多预取消息数
        container.setConcurrentConsumers(5); // 启动时并发消费者数量
        container.setMaxConcurrentConsumers(10); // 最大并发消费者数量
        container.setDefaultRequeueRejected(false); // 拒绝的消息不重新入队，进入 DLQ

        container.setMessageListener((Message message) -> {
            executorService.submit(() -> {
                String body = new String(message.getBody());
//                    System.out.printf("用户 %s 收到消息: %s%n", shopName, body);

                try {
                    RabbitMqTranslateVO rabbitMqTranslateVO = JSON.parseObject(body, RabbitMqTranslateVO.class);
                    if (EMAIL.equals(rabbitMqTranslateVO.getShopifyData())) {
                        //获取当前用户翻译状态，先不做
                        // 处理邮件发送功能
                        rabbitMqTranslateService.sendTranslateEmail(rabbitMqTranslateVO);
                    } else {
                        // 处理翻译功能
                        processMessage(rabbitMqTranslateVO);
                    }
                } catch (ClientException e1) {
                    System.out.println("到达字符限制： " + e1);
                } catch (Exception e) {
                    System.out.println("处理消息失败，消息将进入死信队列。");
                    throw new AmqpRejectAndDontRequeueException("处理失败", e); // 触发进入 DLQ
                }
            });
        });

        try {
            container.start(); // 启动监听器
        } catch (Exception e) {
            container.stop();
            System.out.println("监听器启动失败 errors ");
        }
    }



    // 模拟消息处理函数
    public void processMessage(RabbitMqTranslateVO rabbitMqTranslateVO) {
        // 这里可以放真正的翻译处理逻辑
        //判断字符是否超限
        TranslationCounterDO request1 = translationCounterService.readCharsByShopName(rabbitMqTranslateVO.getShopName());
        Integer remainingChars = rabbitMqTranslateVO.getLimitChars();
        int usedChars = request1.getUsedChars();
        // 如果字符超限，则直接返回字符超限
        if (usedChars >= remainingChars) {
            System.out.println("字符超限 processMessage");
            return;
        }
        // 修改数据库当前的数据
        translatesService.updateTranslatesResourceType(rabbitMqTranslateVO.getShopName(), rabbitMqTranslateVO.getTarget(), rabbitMqTranslateVO.getSource(), rabbitMqTranslateVO.getModeType());
        rabbitMqTranslateVO.setLimitChars(remainingChars);
        //初始化计数器
        CharacterCountUtils counter = new CharacterCountUtils();
        counter.addChars(usedChars);
        rabbitMqTranslateService.translateByModeType(rabbitMqTranslateVO, counter);
    }


}
