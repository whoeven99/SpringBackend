package com.bogda.api.logic.redis;

import com.bogda.api.integration.RedisIntegration;
import com.bogda.integration.utils.RedisKeyUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class RedisStoppedRepository {
    @Autowired
    private RedisIntegration redisIntegration;

    private static final String MANUAL = "1";
    private static final String TOKEN_LIMIT = "2";

    public void manuallyStopped(String shopName) {
        String key = RedisKeyUtils.STOPPED_FLAG.replace("{shopName}", shopName);
        redisIntegration.set(key, MANUAL, RedisKeyUtils.DAY_14);
    }

    public void tokenLimitStopped(String shopName) {
        String key = RedisKeyUtils.STOPPED_FLAG.replace("{shopName}", shopName);
        redisIntegration.set(key, TOKEN_LIMIT, RedisKeyUtils.DAY_14);
    }

    public boolean isTaskStopped(String shopName) {
        String key = RedisKeyUtils.STOPPED_FLAG.replace("{shopName}", shopName);
        String value = redisIntegration.get(key);
        return MANUAL.equals(value) || TOKEN_LIMIT.equals(value);
    }

    public boolean isStoppedByTokenLimit(String shopName) {
        String key = RedisKeyUtils.STOPPED_FLAG.replace("{shopName}", shopName);
        String value = redisIntegration.get(key);
        return TOKEN_LIMIT.equals(value);
    }

    public boolean removeStoppedFlag(String shopName) {
        String key = RedisKeyUtils.STOPPED_FLAG.replace("{shopName}", shopName);
        return redisIntegration.delete(key);
    }
}
