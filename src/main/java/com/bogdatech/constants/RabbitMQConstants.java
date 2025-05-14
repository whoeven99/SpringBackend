package com.bogdatech.constants;

public class RabbitMQConstants {
    // 队列名称
    public static final String SCHEDULED_TRANSLATE_QUEUE = "scheduled.translate.queue"; //定时任务的队列
    public static final String DEAD_LETTER_QUEUE = "scheduled.translate.dead.letter.queue"; //定时任务死信队列

    // 交换机名称
    public static final String SCHEDULED_TRANSLATE_EXCHANGE = "scheduled.translate.exchange";//定时任务的交换机
    public static final String DEAD_LETTER_EXCHANGE = "scheduled.translate.dead.letter.exchange"; //定时任务死信交换机

    //路由键
    public static final String SCHEDULED_TRANSLATE_ROUTING_KEY = "scheduled.translate.routing.key";//定时任务的key
    public static final String DEAD_LETTER_ROUTING_KEY = "scheduled.translate.dead.letter.routing.key";//定时任务死信的key


    //常量
    public static final String RABBIT_MQ_HOST = "RABBIT_MQ_HOST";
    public static final String RABBIT_MQ_PORT = "RABBIT_MQ_PORT";
    public static final String RABBIT_MQ_USERNAME = "RABBIT_MQ_USERNAME";
    public static final String RABBIT_MQ_PASSWORD = "RABBIT_MQ_PASSWORD";
}
