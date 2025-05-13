package com.bogdatech.model.service;

import com.bogdatech.entity.DTO.ScheduledTranslateTaskDTO;
import com.bogdatech.entity.DTO.TranslateDTO;
import com.bogdatech.logic.TranslateService;
import com.rabbitmq.client.Channel;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;

import static com.bogdatech.constants.RabbitMQConstants.SCHEDULED_TRANSLATE_QUEUE;

@Service
public class TranslateTaskConsumerService {

    private final TranslateService translateService;

    @Autowired
    public TranslateTaskConsumerService(TranslateService translateService) {
        this.translateService = translateService;
    }

    /**
     * 接收翻译任务，并执行处理
     */
    @RabbitListener(queues = SCHEDULED_TRANSLATE_QUEUE)
    public void scheduledTranslateTask(ScheduledTranslateTaskDTO<TranslateDTO> message, Channel channel, Message rawMessage) throws IOException {
        String deliveryTag = String.valueOf(rawMessage.getMessageProperties().getDeliveryTag());


        try {
            System.out.println("接收到任务：" + message.getTaskId());
            TranslateDTO translateDTO = message.getMessageData();
            // TODO: 修改taskTranslating方法，对异常进行处理
            translateService.taskTranslating(translateDTO.getTranslateRequest(), translateDTO.getRemainingChars(), translateDTO.getCounter(), translateDTO.getTranslateModels());
            // 业务处理完成后，手动ACK消息，RabbitMQ可安全移除消息
            channel.basicAck(rawMessage.getMessageProperties().getDeliveryTag(), false);

        } catch (Exception e) {
            System.err.println("翻译任务处理失败：" + e.getMessage());

            // 可以选择不ack，丢回队列（false代表仅当前消息）
            channel.basicNack(rawMessage.getMessageProperties().getDeliveryTag(), false, true);
        }
    }
}
