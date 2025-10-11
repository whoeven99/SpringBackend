package com.bogdatech.logic.redis;

import com.bogdatech.integration.RedisIntegration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class TranslationParametersRedisService {
    @Autowired
    private RedisIntegration redisIntegration;

    // 存储shop的停止标识
    private static final String STOP_TRANSLATION_KEY = "stop_translation_key";

    // 存储shop的翻译进度条参数
    private static final String PROGRESS_TRANSLATION_KEY = "progress_translation_key";

    // 存储shop上次的翻译进度条参数
    private static final String LAST_PROGRESS_TRANSLATION_KEY = "last_progress_translation_key";

    // 存储shop邮件发送标识
    private static final String SEND_EMAIL_KEY = "send_email_key";


}
