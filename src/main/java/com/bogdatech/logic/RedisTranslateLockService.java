package com.bogdatech.logic;

import com.bogdatech.integration.RedisIntegration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class RedisTranslateLockService {
    @Autowired
    private RedisIntegration redisIntegration;

    public static final String TRANSLATING_SHOPS_SET_KEY = "translating_shops_set_key";

    public boolean setAdd(String shopName) {
        return redisIntegration.setSet(TRANSLATING_SHOPS_SET_KEY, shopName);
    }

    public boolean setRemove(String shopName) {
        return redisIntegration.remove(TRANSLATING_SHOPS_SET_KEY, shopName);
    }

    public Set<String> members() {
        return redisIntegration.getSet(TRANSLATING_SHOPS_SET_KEY);
    }

    public boolean setDelete() {
        return redisIntegration.delete(TRANSLATING_SHOPS_SET_KEY);
    }
}
