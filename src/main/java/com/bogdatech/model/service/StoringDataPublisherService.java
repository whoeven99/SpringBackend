package com.bogdatech.model.service;

import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import static com.bogdatech.constants.RabbitMQConstants.*;

@Service
public class StoringDataPublisherService {
    private final RabbitTemplate rabbitTemplate;

    @Autowired
    public StoringDataPublisherService(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }
    /**
     * 存储翻译完成数据到Mq
     */
    public void storingData(String message) {

        // 发送消息到指定交换机和路由键
        rabbitTemplate.convertAndSend(
                USER_STORE_EXCHANGE,
                USER_STORE_ROUTING_KEY,
                message,
                msg -> {
                    // 设置消息为持久化（deliveryMode = PERSISTENT）
                    msg.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                    return msg;
                }
        );

    }
}
