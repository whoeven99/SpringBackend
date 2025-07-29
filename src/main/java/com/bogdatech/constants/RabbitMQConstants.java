package com.bogdatech.constants;

public class RabbitMQConstants {
    // 队列名称
    public static final String SCHEDULED_TRANSLATE_QUEUE = "scheduled.translate.queue"; //定时任务的队列
    public static final String DEAD_LETTER_QUEUE = "scheduled.translate.dead.letter.queue"; //定时任务死信队列
    public static final String USER_TRANSLATE_DEAD_LETTER_QUEUE = "translate.dead.letter.user.queue"; //用户翻译死信队列
    public static final String USER_STORE_QUEUE = "translate.user.store.queue"; //用户翻译存储队列
    public static final String USER_STORE_DEAD_LETTER_QUEUE = "translate.user.store.dead.letter.queue"; //用户翻译存储死信队列
    public static final String USER_EMAIL_DELAY_QUEUE = "user.email.delay.queue"; //用户邮件延迟队列
    public static final String USER_EMAIL_DLX_QUEUE = "user.email.dlx.queue"; //用户邮件死信队列

    // 交换机名称
    public static final String SCHEDULED_TRANSLATE_EXCHANGE = "scheduled.translate.exchange";//定时任务的交换机
    public static final String DEAD_LETTER_EXCHANGE = "scheduled.translate.dead.letter.exchange"; //定时任务死信交换机
    public static final String USER_DEAD_LETTER_EXCHANGE = "translate.dead.letter.user.exchange"; //用户翻译死信交换机
    public static final String USER_TRANSLATE_EXCHANGE = "translate.user.exchange"; //用户翻译交换机
    public static final String USER_STORE_EXCHANGE = "translate.user.store.exchange"; //用户翻译存储交换机
    public static final String USER_STORE_DEAD_LETTER_EXCHANGE = "translate.user.store.dead.letter.exchange"; //用户翻译存储死信交换机
    public static final String USER_EMAIL_DELAY_EXCHANGE = "user.email.delay.exchange"; //用户邮件延迟交换机
    public static final String USER_EMAIL_DLX_EXCHANGE = "user.email.dlx.exchange"; //用户邮件死信交换机
    public static final String USER_STORE_DELAY_QUEUE = "translate.user.store.delay.queue"; //用户翻译存储延迟队列

    //路由键
    public static final String SCHEDULED_TRANSLATE_ROUTING_KEY = "scheduled.translate.routing.key";//定时任务的key
    public static final String DEAD_LETTER_ROUTING_KEY = "scheduled.translate.dead.letter.routing.key";//定时任务死信的key
    public static final String USER_DEAD_LETTER_ROUTING_KEY = "translate.dead.letter.user.routing.key";//用户翻译死信的key
    public static final String USER_STORE_ROUTING_KEY = "translate.user.store.routing.key";//用户翻译存储的key
    public static final String USER_STORE_DEAD_LETTER_ROUTING_KEY = "translate.user.store.dead.letter.routing.key";//用户翻译存储死信的key
    public static final String USER_EMAIL_DELAY_ROUTING_KEY = "user.email.delay.routing.key";//用户邮件延迟的key
    public static final String USER_EMAIL_DLX_ROUTING_KEY = "user.email.dlx.routing.key";//用户邮件死信的key

    //常量
    public static final String RABBIT_MQ_HOST = "RABBIT_MQ_HOST";
    public static final String RABBIT_MQ_USERNAME = "RABBIT_MQ_USERNAME";
    public static final String RABBIT_MQ_PASSWORD = "RABBIT_MQ_PASSWORD";
}
