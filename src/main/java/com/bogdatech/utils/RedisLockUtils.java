package com.bogdatech.utils;

import com.bogdatech.integration.RedisIntegration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class RedisLockUtils {
    @Autowired
    private RedisIntegration redisIntegration;

    public boolean lock(String key) {
        return redisIntegration.trySetValueIfAbsent(key, "1");
    }

    public void unLock(String key) {
        redisIntegration.delete(key);
    }
}
