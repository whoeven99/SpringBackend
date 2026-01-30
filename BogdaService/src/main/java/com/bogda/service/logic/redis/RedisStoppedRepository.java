package com.bogda.service.logic.redis;

import com.bogda.repository.RedisIntegration;
import com.bogda.common.utils.RedisKeyUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class RedisStoppedRepository {
    @Autowired
    private RedisIntegration redisIntegration;

    private static final String MANUAL = "1";
    private static final String TOKEN_LIMIT = "2";

    // 单条停止逻辑
    public void manuallyStopped(String shopName, Integer initialId) {
        String key = RedisKeyUtils.STOPPED_FLAG_SINGLE.replace("{shopName}", shopName).replace("{InitialId}", String.valueOf(initialId));
        redisIntegration.set(key, MANUAL, RedisKeyUtils.DAY_14);
    }

    public void tokenLimitStopped(String shopName, Integer initialId) {
        String key = RedisKeyUtils.STOPPED_FLAG_SINGLE.replace("{shopName}", shopName).replace("{InitialId}", String.valueOf(initialId));
        redisIntegration.set(key, TOKEN_LIMIT, RedisKeyUtils.DAY_14);
    }

    public boolean isTaskStopped(String shopName, Integer initialId) {
        String key = RedisKeyUtils.STOPPED_FLAG_SINGLE.replace("{shopName}", shopName).replace("{InitialId}", String.valueOf(initialId));
        String value = redisIntegration.get(key);
        return MANUAL.equals(value) || TOKEN_LIMIT.equals(value);
    }

    public boolean isStoppedByTokenLimit(String shopName, Integer initialId) {
        String key = RedisKeyUtils.STOPPED_FLAG_SINGLE.replace("{shopName}", shopName).replace("{InitialId}", String.valueOf(initialId));
        String value = redisIntegration.get(key);
        return TOKEN_LIMIT.equals(value);
    }

    public boolean removeStoppedFlag(String shopName, Integer initialId) {
        String key = RedisKeyUtils.STOPPED_FLAG_SINGLE.replace("{shopName}", shopName).replace("{InitialId}", String.valueOf(initialId));
        return redisIntegration.delete(key);
    }
}
