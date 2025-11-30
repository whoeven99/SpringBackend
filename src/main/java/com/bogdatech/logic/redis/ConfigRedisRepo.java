package com.bogdatech.logic.redis;

import com.bogdatech.integration.RedisIntegration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ConfigRedisRepo {
    @Autowired
    private RedisIntegration redisIntegration;

    private static final String ConfigKey = "bogda:config";

    public String getConfig(String key) {
        return redisIntegration.getHash(ConfigKey, key);
    }

    public void setConfig(String key, String value) {
        redisIntegration.setHash(ConfigKey, key, value);
    }

    public Map<String, String> getAllConfigs() {
        return redisIntegration.hGetAll(ConfigKey);
    }

    // delete config
    public void delConfig(String key) {
        redisIntegration.hashDelete(ConfigKey, key);
    }
}
