package com.bogdatech.logic.redis;

import com.bogdatech.integration.RedisIntegration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class UserIpRedisService {
    @Autowired
    private RedisIntegration redisIntegration;

    // 用户ip key
    private static final String IP_USER_TARGET_KEY = "ip:{shopName}:{targetCode}";
    private static final String NO_CURRENCY_CODE = "no_currency_code";
    private static final String NO_LANGUAGE_CODE = "no_language_code";
    private static final String ALL_IP_COUNT = "all_ip_count";

    // 生成对应的key
    public static String generalIpKey(String shopName, String targetCode) {
        if (shopName == null ) {
            return null;
        }

        return IP_USER_TARGET_KEY.replace("{shopName}", shopName)
                .replace("{targetCode}", targetCode);
    }


    //
}
