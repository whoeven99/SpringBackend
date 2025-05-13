package com.bogdatech.model.service;

import com.bogdatech.entity.DTO.ScheduledTranslateTaskDTO;
import com.bogdatech.entity.DTO.TranslateDTO;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

import static com.bogdatech.constants.RabbitMQConstants.SCHEDULED_TRANSLATE_EXCHANGE;
import static com.bogdatech.constants.RabbitMQConstants.SCHEDULED_TRANSLATE_ROUTING_KEY;

@Service
public class TranslateTaskPublisherService {

    private final RabbitTemplate rabbitTemplate;

    @Autowired
    public TranslateTaskPublisherService(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * 自动翻译发送翻译任务消息到 MQ
     */
    public void sendScheduledTranslateTask(ScheduledTranslateTaskDTO<TranslateDTO> message) {
        message.setCreatedAt(LocalDateTime.now());

        System.out.println("发送翻译任务消息：" + message);

        // 发送消息到指定交换机和路由键
        rabbitTemplate.convertAndSend(
                SCHEDULED_TRANSLATE_EXCHANGE,
                SCHEDULED_TRANSLATE_ROUTING_KEY,
                message,
                msg -> {
                    // 设置消息为持久化（deliveryMode = PERSISTENT）
                    msg.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                    return msg;
                }
        );

    }

}
