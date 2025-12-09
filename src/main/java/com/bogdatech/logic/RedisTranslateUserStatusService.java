package com.bogdatech.logic;

import com.bogdatech.integration.RedisIntegration;
import com.bogdatech.utils.RedisKeyUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class RedisTranslateUserStatusService {
    @Autowired
    private RedisIntegration redisIntegration;

    /**
     * 存用户每种语言翻译状态 todo 哪里get他
     */
    public void saveTranslateStatus(String shopName, String targetCode, String sourceCode, String status) {
        String key = RedisKeyUtils.TRANSLATE_USER_STATUS.replace("{shopName}", shopName)
                .replace("{sourceCode}", sourceCode)
                .replace("{targetCode}", targetCode);
        redisIntegration.set(key, status);
    }
}
