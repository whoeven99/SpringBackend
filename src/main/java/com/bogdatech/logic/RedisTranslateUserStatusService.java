package com.bogdatech.logic;

import com.bogdatech.integration.RedisIntegration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import static com.bogdatech.utils.RedisKeyUtils.generateTranslateUserStatusKey;

@Service
public class RedisTranslateUserStatusService {
    @Autowired
    private RedisIntegration redisIntegration;

    /**
     * 存用户每种语言翻译状态
     */
    public void saveTranslateStatus(String shopName, String targetCode, String sourceCode, String status) {
        String key = generateTranslateUserStatusKey(shopName, sourceCode, targetCode);
        redisIntegration.set(key, status);
    }

}
