package com.bogdatech.logic.redis;

import com.bogdatech.integration.RedisIntegration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static com.bogdatech.utils.CaseSensitiveUtils.appInsights;

@Component
public class TranslationCounterRedisService {
    @Autowired
    private RedisIntegration redisIntegration;

    // task级 token记录
    public static final String TASK_TOKEN_COUNTER = "task_all";

    // 一个语言的 token记录 放到进度条数据里面
    private static final String LANGUAGE_TOKEN_COUNTER = "language_all";


    // task 递增方法
    public Long increaseTask(String key, long value) {
        appInsights.trackTrace("increaseTask key: " + key + " value: " + value);
        return redisIntegration.incrementHash(key, TASK_TOKEN_COUNTER, value);
    }

    // 获取TASK_TOKEN_COUNTER对应的数据
    public Long getTtcData(String key) {
        String date = redisIntegration.getHash(key, TASK_TOKEN_COUNTER);
        if (date == null || date.isEmpty() || "null".equals(date)) {
            return 0L;
        } else {
            appInsights.trackTrace("getTtcData: " + date);
            return Long.parseLong(date);
        }
    }

    // 删除task级的 token记录
    public Boolean deleteTtcData(String key) {
        return redisIntegration.delete(key);
    }

    // language 递增方法 用的是进度条的key TRANSLATE_PROGRESS_KEY_TEMPLATE
    public Long increaseLanguage(String key, long value) {
        appInsights.trackTrace("increaseLanguage key: " + key + " value: " + value);
        return redisIntegration.incrementHash(key, LANGUAGE_TOKEN_COUNTER, value);
    }

    // 获取language 对应的数据
    public Long getLanguageData(String key) {
        String date = redisIntegration.getHash(key, LANGUAGE_TOKEN_COUNTER);
        if (date == null || date.isEmpty() || "null".equals(date)) {
            return 0L;
        } else {
            appInsights.trackTrace("getLanguageData language: " + date);
            return Long.parseLong(date);
        }
    }

    // 删除language 对应的数据
    public boolean deleteLanguage(String key) {
        return redisIntegration.hashDelete(key, LANGUAGE_TOKEN_COUNTER);
    }
}
