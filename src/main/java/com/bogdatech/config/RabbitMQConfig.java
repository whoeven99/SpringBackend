package com.bogdatech.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static com.bogdatech.constants.RabbitMQConstants.*;

@Configuration
public class RabbitMQConfig {

    // 声明队列
    @Bean
    public Queue scheduledTranslateQueue() {
        return QueueBuilder.durable(SCHEDULED_TRANSLATE_QUEUE).build();
    }

    // 声明直连交换机
    @Bean
    public DirectExchange scheduledTranslateExchange() {
        return new DirectExchange(SCHEDULED_TRANSLATE_EXCHANGE);
    }

    // 队列绑定到交换机
    @Bean
    public Binding scheduledTranslateBinding() {
        return BindingBuilder.bind(scheduledTranslateQueue())
                .to(scheduledTranslateExchange())
                .with(SCHEDULED_TRANSLATE_ROUTING_KEY);
    }
}
