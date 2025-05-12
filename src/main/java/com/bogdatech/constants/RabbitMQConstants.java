package com.bogdatech.constants;

public class RabbitMQConstants {
    // 队列名称
    public static final String SCHEDULED_TRANSLATE_QUEUE = "scheduled.translate.queue"; //定时任务的队列

    // 交换机名称
    public static final String SCHEDULED_TRANSLATE_EXCHANGE = "scheduled.translate.exchange";//定时任务的交换机

    //路由键
    public static final String SCHEDULED_TRANSLATE_ROUTING_KEY = "scheduled.translate.routing.key";//定时任务的队列
}
