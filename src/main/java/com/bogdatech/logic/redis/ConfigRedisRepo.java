package com.bogdatech.logic.redis;

import com.bogdatech.integration.RedisIntegration;
import com.bogdatech.utils.JsonUtils;
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

    public boolean singleTranslateWhiteList(String shopName) {
        String value = getConfig("singleTranslateWhiteList");
        if (StringUtils.isEmpty(value)) {
            return false;
        }
        List<String> shopList = JsonUtils.jsonToObject(value, new TypeReference<List<String>>() {
        });
        if (shopList == null || shopList.isEmpty()) {
            return false;
        }
        return shopList.contains(shopName);
    }
}
