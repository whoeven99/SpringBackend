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
    private static final String STOP_TRANSLATION_KEY = "stop_translation_key_";

    // 存储shop的翻译进度条参数  hash存
    private static final String PROGRESS_TRANSLATION_KEY = "pt:{shopName}:{source}:{target}";

    public static final String TRANSLATING_STRING = "translating_string";
    public static final String TRANSLATION_STATUS = "translation_status";

    // 写入状态
    private static final String PROGRESS_WRITE_KEY = "pw:{shopName}:{target}";
    public static final String WRITE_TOTAL = "write_total";

    /**
     * 生成写入状态的key
     */
    public static String generateWriteStatusKey(String shopName, String target) {
        if (shopName == null || target == null) {
            return null;
        }
        return PROGRESS_WRITE_KEY.replace("{shopName}", shopName)
                .replace("{target}", target);
    }

    /**
     * 存写入状态的数据 done 和 total
     */
    public Long addWritingData(String pwKey, String model, long data) {
        return redisIntegration.incrementHash(pwKey, model, data);
    }

    /**
     * 生成进度条key的翻译
     */
    public static String generateProgressTranslationKey(String shopName, String source, String target) {
        if (shopName == null || source == null || target == null) {
            return null;
        }
        return PROGRESS_TRANSLATION_KEY
                .replace("{shopName}", shopName)
                .replace("{source}", source)
                .replace("{target}", target);
    }

    /**
     * 正在翻译字符串
     */
    public void hsetTranslatingString(String ptKey, String translatingString) {
        redisIntegration.setHash(ptKey, TRANSLATING_STRING, translatingString);
    }

    /**
     * 翻译状态：1-初始化 2-翻译中 3-写入中 4-已完成
     */
    public void hsetTranslationStatus(String ptKey, String status) {
        redisIntegration.setHash(ptKey, TRANSLATION_STATUS, status);
    }

    /**
     * 获取shop，进度条相关数据
     */
    public Map<String, String> getProgressTranslationKey(String ptKey) {
        return redisIntegration.hGetAll(ptKey);
    }

    /**
     * 删除用户的停止标识
     */
    public Boolean delStopTranslationKey(String shopName) {
        return redisIntegration.delete(STOP_TRANSLATION_KEY + shopName);
    }

    /**
     * 判断获取到的停止标识是否是 “1”
     */
    public Boolean isStopped(String shopName) {
        return "1".equals(redisIntegration.get(STOP_TRANSLATION_KEY + shopName));
    }

}
