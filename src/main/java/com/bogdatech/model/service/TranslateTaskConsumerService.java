package com.bogdatech.model.service;

import com.bogdatech.entity.DTO.TranslateDTO;
import com.bogdatech.logic.TranslateService;
import com.bogdatech.model.controller.request.TranslateRequest;
import com.bogdatech.utils.CharacterCountUtils;
import com.rabbitmq.client.Channel;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;

import static com.bogdatech.constants.RabbitMQConstants.SCHEDULED_TRANSLATE_QUEUE;
import static com.bogdatech.utils.JsonUtils.jsonToObject;

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
    public void scheduledTranslateTask(String json, Channel channel, Message rawMessage) throws IOException {
        String deliveryTag = String.valueOf(rawMessage.getMessageProperties().getDeliveryTag());
        TranslateDTO translateDTO = jsonToObject(json, TranslateDTO.class);
        try {
            //判断该用户是否正在翻译，正在翻译就不翻译了
            if (translateDTO.getStatus() == 2) {
//                如果正在翻译，则将任务丢回队列
                channel.basicNack(rawMessage.getMessageProperties().getDeliveryTag(), false, true);
                return;
            }
            //初始化计数器
            CharacterCountUtils counter = new CharacterCountUtils();
            counter.addChars(translateDTO.getUsedChars());
            //autoTranslateException，对异常进行处理
            TranslateRequest request = new TranslateRequest(0, translateDTO.getShopName(), translateDTO.getAccessToken(), translateDTO.getSource(), translateDTO.getTarget(),null);
            translateService.autoTranslateException(request, translateDTO.getRemainingChars(), counter, translateDTO.getUsedChars());
            // 业务处理完成后，手动ACK消息，RabbitMQ可安全移除消息
            channel.basicAck(rawMessage.getMessageProperties().getDeliveryTag(), false);

        } catch (Exception e) {
            // 可以选择不ack，丢回队列（false代表仅当前消息）
            channel.basicNack(rawMessage.getMessageProperties().getDeliveryTag(), false, true);
        }
    }
}
