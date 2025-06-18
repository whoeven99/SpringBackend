package com.bogdatech.model.service;

import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import static com.bogdatech.constants.RabbitMQConstants.*;
import static com.bogdatech.utils.CaseSensitiveUtils.appInsights;

@Service
public class StoringDataPublisherService {
    private final RabbitTemplate rabbitTemplate;

    @Autowired
    public StoringDataPublisherService(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    private static final int MAX_RETRY_ATTEMPTS = 5;
    private static final long INITIAL_RETRY_DELAY_MS = 1000; // 初始重试间隔：1秒
    /**
     * 存储翻译完成数据到Mq
     */
    public void storingData(String message) {

        int attempt = 0;
        long delay = INITIAL_RETRY_DELAY_MS;

        while (attempt < MAX_RETRY_ATTEMPTS) {
            try {
                rabbitTemplate.convertAndSend(
                        USER_STORE_EXCHANGE,
                        USER_STORE_ROUTING_KEY,
                        message,
                        msg -> {
                            msg.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                            return msg;
                        }
                );

                appInsights.trackTrace("存储任务 消息发送成功：" + message);
                return; // 成功发送后，结束重试
            } catch (Exception e) {
                attempt++;
                appInsights.trackTrace(String.format("存储任务 消息发送失败，第 %d 次尝试：%s", attempt, e.getMessage()));

                if (attempt >= MAX_RETRY_ATTEMPTS) {
                    appInsights.trackTrace("存储任务 消息最终发送失败，超过最大重试次数：" + message);
                    break;
                }

                try {
                    Thread.sleep(delay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt(); // 保持中断状态
                    appInsights.trackTrace("存储任务 重试等待期间被中断");
                    break;
                }

                delay *= 2; // 指数退避
            }
        }

    }
}
