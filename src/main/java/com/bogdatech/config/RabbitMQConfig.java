package com.bogdatech.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import static com.bogdatech.constants.RabbitMQConstants.*;

@Configuration
public class RabbitMQConfig {

    //连接rabbitMQ服务
    @Bean
    public ConnectionFactory connectionFactory() {
        CachingConnectionFactory connectionFactory = new CachingConnectionFactory();
//        connectionFactory.setHost(System.getenv(RABBIT_MQ_HOST));
//        connectionFactory.setPort(5672);
//        connectionFactory.setUsername(System.getenv(RABBIT_MQ_USERNAME));
//        connectionFactory.setPassword(System.getenv(RABBIT_MQ_PASSWORD));
        connectionFactory.setHost("43.155.137.91");
        connectionFactory.setUsername("testRabbitMQzz");
        connectionFactory.setPassword("RMQzztest123");
        connectionFactory.setPort(5672);
        return connectionFactory;
    }

    // 声明队列
    @Bean
    public Queue scheduledTranslateQueue() {
        return QueueBuilder.durable(SCHEDULED_TRANSLATE_QUEUE)
                .withArgument("x-dead-letter-exchange", DEAD_LETTER_EXCHANGE) // 指定死信交换机
                .withArgument("x-dead-letter-routing-key", DEAD_LETTER_ROUTING_KEY) // 指定死信路由键
         .build();
    }
    //声明用户死信队列
    @Bean
    public Queue userDeadLetterQueue() {
        return QueueBuilder.durable(USER_TRANSLATE_DEAD_LETTER_QUEUE)
                .withArgument("x-dead-letter-exchange", USER_DEAD_LETTER_EXCHANGE) // 指定死信交换机
                .withArgument("x-dead-letter-routing-key", USER_DEAD_LETTER_ROUTING_KEY) // 指定死信路由键
         .build();
    }
    // 声明定时翻译死信队列
    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable(DEAD_LETTER_QUEUE).build();
    }
    //声明用户存储数据队列
    @Bean
    public Queue userStoreQueue() {
        return QueueBuilder.durable(USER_STORE_QUEUE)
                .withArgument("x-dead-letter-exchange", USER_STORE_DEAD_LETTER_EXCHANGE) // 指定死信交换机
                .withArgument("x-dead-letter-routing-key", USER_STORE_DEAD_LETTER_ROUTING_KEY) // 指定死信路由键
                .build();
    }
    //声明用户存储死信队列
    @Bean
    public Queue userStoreDeadLetterQueue() {
        return QueueBuilder.durable(USER_STORE_DEAD_LETTER_QUEUE).build();
    }


    // 声明定时任务直连交换机
    @Bean
    public DirectExchange scheduledTranslateExchange() {
        return new DirectExchange(SCHEDULED_TRANSLATE_EXCHANGE, true, false);
    }
    //声明用户交换机
    @Bean
    public static TopicExchange userExchange() {
        return new TopicExchange(USER_TRANSLATE_EXCHANGE, true, false);
    }
    //声明用户死信交换机
    @Bean
    public static TopicExchange userDeadLetterExchange() {
        return new TopicExchange(USER_DEAD_LETTER_EXCHANGE, true, false);
    }
    // 声明定时翻译死信交换机
    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(DEAD_LETTER_EXCHANGE, true, false);
    }
    // 声明用户存储数据交换机
    @Bean
    public DirectExchange userStoreExchange() {
        return new DirectExchange(USER_STORE_EXCHANGE, true, false);
    }
    //声明用户存储数据死信交换机
    @Bean
    public DirectExchange userStoreDeadLetterExchange() {
        return new DirectExchange(USER_STORE_DEAD_LETTER_EXCHANGE, true, false);
    }

    // 队列绑定到交换机
    @Bean
    public Binding scheduledTranslateBinding() {
        return BindingBuilder.bind(scheduledTranslateQueue())
                .to(scheduledTranslateExchange())
                .with(SCHEDULED_TRANSLATE_ROUTING_KEY);
    }
    //用户死信队列绑定用户死信交换机
    @Bean
    public Binding userDeadLetterBinding() {
        return BindingBuilder.bind(userDeadLetterQueue())
                .to(userDeadLetterExchange())
                .with(USER_DEAD_LETTER_ROUTING_KEY);
    }
    // 死信定时翻译队列绑定到定时翻译死信交换机
    @Bean
    public Binding deadLetterBinding() {
        return BindingBuilder.bind(deadLetterQueue())
                .to(deadLetterExchange())
                .with(DEAD_LETTER_ROUTING_KEY);
    }
    // 用户存储数据队列绑定到用户存储数据交换机
    @Bean
    public Binding userStoreBinding() {
        return BindingBuilder.bind(userStoreQueue())
                .to(userStoreExchange())
                .with(USER_STORE_ROUTING_KEY);
    }
    // 用户存储数据死信队列绑定到用户存储数据死信交换机
    @Bean
    public Binding userStoreDeadLetterBinding() {
        return BindingBuilder.bind(userStoreDeadLetterQueue())
                .to(userStoreDeadLetterExchange())
                .with(USER_STORE_DEAD_LETTER_ROUTING_KEY);
    }

}
