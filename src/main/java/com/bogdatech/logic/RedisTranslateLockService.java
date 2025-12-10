package com.bogdatech.logic;

import com.bogdatech.integration.RedisIntegration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class RedisTranslateLockService {
    @Autowired
    private RedisIntegration redisIntegration;

    public static final String TRANSLATING_SHOPS_SET_KEY = "translating_shops_set_key";

    public boolean setRemove(String shopName) {
        return redisIntegration.remove(TRANSLATING_SHOPS_SET_KEY, shopName);
    }
}
