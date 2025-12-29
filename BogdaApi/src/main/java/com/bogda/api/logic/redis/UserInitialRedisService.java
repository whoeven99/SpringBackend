package com.bogda.api.logic.redis;

import com.bogda.api.integration.RedisIntegration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class UserInitialRedisService {
    @Autowired
    private RedisIntegration redisIntegration;

    // 存储用户默认主题
    private static final String USER_DEFAULT_THEME = "udt:{shopName}";

    // 存储用户默认语言
    private static final String USER_DEFAULT_LANGUAGE = "udl:{shopName}";


    // 存用户的默认主题
    public void setUserDefaultTheme(String shopName, String themeId) {
        String key = USER_DEFAULT_THEME.replace("{shopName}", shopName);
        redisIntegration.set(key, themeId);
    }

    // 获取用户的默认主题
    public String getUserDefaultTheme(String shopName) {
        String key = USER_DEFAULT_THEME.replace("{shopName}", shopName);
        return redisIntegration.get(key);
    }

    // 存用户的默认语言
    public void setUserDefaultLanguage(String shopName, String language) {
        String key = USER_DEFAULT_LANGUAGE.replace("{shopName}", shopName);
        redisIntegration.set(key, language);
    }

    // 获取用户的默认语言
    public String getUserDefaultLanguage(String shopName) {
        String key = USER_DEFAULT_LANGUAGE.replace("{shopName}", shopName);
        return redisIntegration.get(key);
    }
}
