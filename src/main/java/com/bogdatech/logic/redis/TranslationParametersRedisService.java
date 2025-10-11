package com.bogdatech.logic.redis;

import com.bogdatech.integration.RedisIntegration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class TranslationParametersRedisService {
    @Autowired
    private RedisIntegration redisIntegration;

    // 存储shop的停止标识
    private static final String STOP_TRANSLATION_KEY = "stop_translation_key";

    // 存储shop的翻译进度条参数  hash存
    private static final String PROGRESS_TRANSLATION_KEY = "pt:{shopName}:{source}:{target}";

    // 存储shop邮件发送标识
    private static final String SEND_EMAIL_KEY = "send_email_key";

    /**
     * 生成进度条key的翻译
     * */
    public static String generateProgressTranslationKey(String shopName, String source, String target) {
        if (shopName == null || source == null || target == null) {
            return null;
        }
        return PROGRESS_TRANSLATION_KEY.replace("{shopName}", shopName)
                .replace("{sourceCode}", source)
                .replace("{targetCode}", target);
    }

    /**
     * 存正在翻译的模块
     * */
    public void hsetTranslatingModule(String ptKey, String module) {
        redisIntegration.setHash(ptKey, "translating_module", module);
    }

    /**
     * 正在翻译字符串
     * */
    public void hsetTranslatingString(String ptKey, String translatingString) {
        redisIntegration.setHash(ptKey, "translating_string", translatingString);
    }

    /**
     * 翻译状态：1-初始化 2-翻译中 3-写入中
     * */
    public void hsetTranslationStatus(String ptKey, String status) {
        redisIntegration.setHash(ptKey, "translation_status", status);
    }

    /**
     * 获取shop，进度条相关数据
     * */
    public Map<Object, Object> getProgressTranslationKey(String ptKey) {
        return redisIntegration.getHashAll(ptKey);
    }

    /**
     * 存进度条数字的key：tc:{targetCode}:{source}
     * */
    public void hsetProgressNumber(String ptKey, String progressNumberKey) {
        redisIntegration.setHash(ptKey, "progress_number", progressNumberKey);
    }
}
