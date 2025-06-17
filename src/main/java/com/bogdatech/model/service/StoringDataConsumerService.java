package com.bogdatech.model.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.bogdatech.entity.DO.TranslatesDO;
import com.bogdatech.entity.DO.TranslationCounterDO;
import com.bogdatech.entity.DO.TranslationUsageDO;
import com.bogdatech.entity.DO.UsersDO;
import com.bogdatech.entity.DTO.TaskTranslateDTO;
import com.bogdatech.model.controller.request.CloudInsertRequest;
import com.bogdatech.model.controller.request.TranslateRequest;
import com.bogdatech.utils.CharacterCountUtils;
import com.rabbitmq.client.Channel;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;

import static com.bogdatech.constants.RabbitMQConstants.USER_STORE_QUEUE;
import static com.bogdatech.logic.ShopifyService.saveToShopify;
import static com.bogdatech.utils.CaseSensitiveUtils.appInsights;
import static com.bogdatech.utils.JsonUtils.jsonToObject;

@Service
public class StoringDataConsumerService {
    /**
     * 接收存储任务,将翻译好的数据异步mq，存储到shopify本地
     * */
    @RabbitListener(queues = USER_STORE_QUEUE, concurrency = "3")
    public void userStoreData(String json, Channel channel, Message rawMessage) throws IOException {

        try {
            CloudInsertRequest cloudInsertRequest = jsonToObject(json, CloudInsertRequest.class);
//            appInsights.trackTrace("接收到存储任务，开始处理： " + cloudInsertRequest);
            //将翻译后的内容通过ShopifyAPI记录到shopify本地
            // 业务处理
            if (cloudInsertRequest == null) {
                channel.basicAck(rawMessage.getMessageProperties().getDeliveryTag(), false);
                return;
            }
            saveToShopify(cloudInsertRequest);
            // 业务处理完成后，手动ACK消息，RabbitMQ可安全移除消息
            channel.basicAck(rawMessage.getMessageProperties().getDeliveryTag(), false);

        } catch (Exception e) {
            // 不选择ack，丢到死信队列（false代表仅当前消息）
            channel.basicNack(rawMessage.getMessageProperties().getDeliveryTag(), false, false);
        }
    }
}
