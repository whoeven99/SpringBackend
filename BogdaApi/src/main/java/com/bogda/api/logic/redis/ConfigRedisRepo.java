package com.bogda.api.logic.redis;

import com.bogda.api.integration.RedisIntegration;
import com.bogda.common.utils.JsonUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
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

    public boolean isWhiteList(String value, String key) {
        String configValue = getConfig(key);
        if (StringUtils.isEmpty(configValue)) {
            return false;
        }
        List<String> whiteList = JsonUtils.jsonToObject(configValue, new TypeReference<List<String>>() {
        });
        if (whiteList == null || whiteList.isEmpty()) {
            return false;
        }
        return whiteList.contains(value);
    }
}
