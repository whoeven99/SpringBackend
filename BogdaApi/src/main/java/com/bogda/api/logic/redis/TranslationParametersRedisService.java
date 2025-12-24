package com.bogda.api.logic.redis;

import com.bogda.api.integration.RedisIntegration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class TranslationParametersRedisService {
    @Autowired
    private RedisIntegration redisIntegration;

    // 存储shop的翻译进度条参数  hash存
    private static final String PROGRESS_TRANSLATION_KEY = "pt:{shopName}:{source}:{target}";

    public static String generateProgressTranslationKey(String shopName, String source, String target) {
        if (shopName == null || source == null || target == null) {
            return null;
        }
        return PROGRESS_TRANSLATION_KEY
                .replace("{shopName}", shopName)
                .replace("{source}", source)
                .replace("{target}", target);
    }

    public Map<String, String> getProgressTranslationKey(String ptKey) {
        return redisIntegration.hGetAll(ptKey);
    }
}
