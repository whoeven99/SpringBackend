package com.bogdatech.logic.redis;

import com.bogdatech.integration.RedisIntegration;
import com.bogdatech.logic.RabbitMqTranslateService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static com.bogdatech.utils.RedisKeyUtils.generateProcessKey;

@Component
public class TranslationCounterRedisService {
    @Autowired
    private RedisIntegration redisIntegration;

    // 手动翻译 token记录 放到进度条数据里面
    private static final String CLICK_LANGUAGE_TOKEN_COUNTER = "language_all_click";

    // 自动翻译 token记录
    private static final String AUTO_LANGUAGE_TOKEN_COUNTER = "language_all_auto";

    // language 递增方法 用的是进度条的key TRANSLATE_PROGRESS_KEY_TEMPLATE
    public Long increaseLanguage(String shopName, String target, long value, String translateType) {
        String key = generateProcessKey(shopName, target);

        if (RabbitMqTranslateService.AUTO.equals(translateType)) {
            return redisIntegration.incrementHash(key, AUTO_LANGUAGE_TOKEN_COUNTER, value);
        } else {
            return redisIntegration.incrementHash(key, CLICK_LANGUAGE_TOKEN_COUNTER, value);
        }
    }
}
