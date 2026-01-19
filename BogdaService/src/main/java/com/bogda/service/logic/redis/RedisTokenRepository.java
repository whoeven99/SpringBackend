package com.bogda.service.logic.redis;

import com.bogda.repository.RedisIntegration;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class RedisTokenRepository {
    @Autowired
    private RedisIntegration redisIntegration;

    private static final String SHOP_NAME_USED_TOKEN_KEY = "trans_ud:tk:";

    // 存储格式
    //    {
    //        "all": 40000,
    //        "taskId1": 10000,
    //        "taskId2": 15000
    //    }

    // 从数据库初始化到redis里
    public void initUsedToken(String shopName, Integer usedToken) {
        String key = SHOP_NAME_USED_TOKEN_KEY + shopName;
        redisIntegration.setHash(key, "all", usedToken);
    }

    // 每次使用，调用这里
    public void addUsedToken(String shopName, Integer taskId, Integer usedToken) {
        String key = SHOP_NAME_USED_TOKEN_KEY + shopName;
        redisIntegration.incrementHash(key, "all", usedToken.longValue());
        redisIntegration.incrementHash(key, taskId.toString(), usedToken.longValue());
    }

    public void addUsedToken(String shopName, Integer usedToken) {
        String key = SHOP_NAME_USED_TOKEN_KEY + shopName;
        redisIntegration.incrementHash(key, "all", usedToken.longValue());
    }

    public Integer getUsedToken(String shopName) {
        String key = SHOP_NAME_USED_TOKEN_KEY + shopName;
        String value = redisIntegration.getHash(key, "all");
        return StringUtils.isEmpty(value) ? 0 : Integer.parseInt(value);
    }

    public Integer getUsedTokenByTaskId(String shopName, Integer taskId) {
        String key = SHOP_NAME_USED_TOKEN_KEY + shopName;
        String value = redisIntegration.getHash(key, taskId.toString());
        return StringUtils.isEmpty(value) ? 0 : Integer.parseInt(value);
    }
}
