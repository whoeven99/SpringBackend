package com.bogdatech.logic.redis;

import com.bogdatech.integration.RedisIntegration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class TranslationCounterRedisService {
    @Autowired
    private RedisIntegration redisIntegration;

    // task级 token记录
    private static final String TASK_TOKEN_COUNTER = "ttc:{shopName}:{target}";

    // 一个语言的 token记录 放到进度条数据里面
    private static final String LANGUAGE_TOKEN_COUNTER = "language_all";

    // 修改key
    public static String getTaskTokenCounterKey(String shopName, String target) {
        if (shopName == null || target == null) {
            return null;
        }
        return TASK_TOKEN_COUNTER
                .replace("{shopName}", shopName)
                .replace("{target}", target);
    }

    // task 递增方法
    public Long increaseTask(String key, long value) {
        return redisIntegration.incrementValue(key, value);
    }

    // 获取TASK_TOKEN_COUNTER对应的数据
    public Long getTtcData(String key) {
        String date = redisIntegration.get(key);
        if (date == null || date.isEmpty() || "null".equals(date)) {
            return 0L;
        } else {
            System.out.println("ttc: " + date);
            return Long.parseLong(date);
        }
    }

    // 删除task级的 token记录
    public Boolean deleteTtcData(String key) {
        return redisIntegration.delete(key);
    }

    // language 递增方法 用的是进度条的key TRANSLATE_PROGRESS_KEY_TEMPLATE
    public Long increaseLanguage(String key, long value) {
        return redisIntegration.incrementHash(key, LANGUAGE_TOKEN_COUNTER, value);
    }

    // 获取language 对应的数据
    public Long getLanguageData(String key) {
        String date = redisIntegration.getHash(key, LANGUAGE_TOKEN_COUNTER);
        if (date == null || date.isEmpty() || "null".equals(date)) {
            return 0L;
        } else {
            System.out.println("language: " + date);
            return Long.parseLong(date);
        }
    }

    // 删除language 对应的数据
    public boolean deleteLanguage(String key) {
        return redisIntegration.hashDelete(key, LANGUAGE_TOKEN_COUNTER);
    }
}
