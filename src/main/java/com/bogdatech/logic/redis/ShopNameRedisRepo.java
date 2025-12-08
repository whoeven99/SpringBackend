package com.bogdatech.logic.redis;

import com.bogdatech.integration.RedisIntegration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ShopNameRedisRepo {
    @Autowired
    private RedisIntegration redisIntegration;

    private static final String KEY_PREFIX = "trans:shopName:";

    /*
    * 新建一个auto就+1  完成一个auto就-1 为0表示可以发邮件
    * 发完邮件删除，再get就是-1 不发邮件
    * */

    public Long hincrAutoTaskCount(String shopName) {
        String key = KEY_PREFIX + shopName;
        return redisIntegration.incrementHash(key, "autoTaskCount", 1L);
    }

    public Long hdecAutoTaskCount(String shopName) {
        String key = KEY_PREFIX + shopName;
        return redisIntegration.incrementHash(key, "autoTaskCount", -1L);
    }

    public Long getAutoTaskCount(String shopName) {
        String key = KEY_PREFIX + shopName;
        String value = redisIntegration.getHash(key, "autoTaskCount");
        return value == null ? -1L : Long.parseLong(value);
    }

    public void deleteShopName(String shopName) {
        String key = KEY_PREFIX + shopName;
        redisIntegration.delete(key);
    }
}
