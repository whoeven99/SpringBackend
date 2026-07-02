package com.bogda.service.logic.redis;

import com.bogda.common.utils.StringUtils;
import com.bogda.repository.RedisIntegration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class RedisTokenRepository {
    @Autowired
    private RedisIntegration redisIntegration;

    private static final String SHOP_NAME_USED_TOKEN_KEY = "trans_ud:tk:";

    public void initUsedToken(String shopName, Integer usedToken) {
        String key = SHOP_NAME_USED_TOKEN_KEY + shopName;
        redisIntegration.setHash(key, "all", usedToken);
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
}
