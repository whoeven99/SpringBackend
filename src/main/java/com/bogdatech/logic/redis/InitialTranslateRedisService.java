package com.bogdatech.logic.redis;

import com.bogdatech.integration.RedisIntegration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class InitialTranslateRedisService {
    @Autowired
    private RedisIntegration redisIntegration;

    // 手动翻译key
    private static final String CLICK_TRANSLATE_KEY = "click_translate_key";

    // 手动翻译 加单个锁
    public boolean setAdd(String shopName) {
        return redisIntegration.setSet(CLICK_TRANSLATE_KEY, shopName);
    }

    // 手动翻译 删除单个锁
    public boolean setRemove(String shopName) {
        return redisIntegration.remove(CLICK_TRANSLATE_KEY, shopName);
    }

    // 手动翻译 删除所有的锁
    public boolean setDelete() {
        return redisIntegration.delete(CLICK_TRANSLATE_KEY);
    }


}
