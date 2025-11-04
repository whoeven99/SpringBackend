package com.bogdatech.logic.redis;

import com.bogdatech.integration.RedisIntegration;
import com.bogdatech.logic.RabbitMqTranslateService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static com.bogdatech.utils.CaseSensitiveUtils.appInsights;
import static com.bogdatech.utils.RedisKeyUtils.generateProcessKey;

@Component
public class TranslationCounterRedisService {
    @Autowired
    private RedisIntegration redisIntegration;

//    // task级 token记录
//    public static final String TASK_TOKEN_COUNTER = "task_all";

    // 手动翻译 token记录 放到进度条数据里面
    private static final String CLICK_LANGUAGE_TOKEN_COUNTER = "language_all_click";

    // 自动翻译 token记录
    private static final String AUTO_LANGUAGE_TOKEN_COUNTER = "language_all_auto";

    // 删除task级的 token记录
    public Boolean deleteTtcData(String key) {
        return redisIntegration.delete(key);
    }

    // language 递增方法 用的是进度条的key TRANSLATE_PROGRESS_KEY_TEMPLATE
    public Long increaseLanguage(String shopName, String target, long value, String translateType) {
        String key = generateProcessKey(shopName, target);
        appInsights.trackTrace("测试 increaseLanguage ： translateType： " + translateType + " key: " + key);
        if (RabbitMqTranslateService.AUTO.equals(translateType)) {
            return redisIntegration.incrementHash(key, AUTO_LANGUAGE_TOKEN_COUNTER, value);
        } else {
            return redisIntegration.incrementHash(key, CLICK_LANGUAGE_TOKEN_COUNTER, value);
        }
    }

    // 获取language 对应的数据
    public Long getLanguageData(String shopName, String target, String translateType) {
        String key = generateProcessKey(shopName, target);
        String date = null;
        appInsights.trackTrace("测试 getLanguageData ： translateType： " + translateType + " key: " + key);
        if (RabbitMqTranslateService.AUTO.equals(translateType)) {
            date = redisIntegration.getHash(key, AUTO_LANGUAGE_TOKEN_COUNTER);
        } else {
            date = redisIntegration.getHash(key, CLICK_LANGUAGE_TOKEN_COUNTER);
        }

        if (date == null || date.isEmpty() || "null".equals(date)) {
            return 0L;
        } else {
            appInsights.trackTrace("getLanguageData language: " + date + " 用户数据： " + key);
            return Long.parseLong(date);
        }
    }

    // 删除language 对应的数据
    public boolean deleteLanguage(String shopName, String target, String translateType) {
        String key = generateProcessKey(shopName, target);
        appInsights.trackTrace("测试 deleteLanguage ： translateType： " + translateType + " key: " + key);

        if (RabbitMqTranslateService.AUTO.equals(translateType)) {
            return redisIntegration.hashDelete(key, AUTO_LANGUAGE_TOKEN_COUNTER);
        } else {
            return redisIntegration.hashDelete(key, CLICK_LANGUAGE_TOKEN_COUNTER);
        }
    }
}
