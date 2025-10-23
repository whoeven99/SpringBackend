package com.bogdatech.logic.redis;

import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.bogdatech.integration.RedisIntegration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class TranslationParametersRedisService {
    @Autowired
    private RedisIntegration redisIntegration;

    // 存储shop的停止标识
    private static final String STOP_TRANSLATION_KEY = "stop_translation_key_";

    // 存储shop的翻译进度条参数  hash存
    private static final String PROGRESS_TRANSLATION_KEY = "pt:{shopName}:{source}:{target}";

    // 存储shop邮件发送标识
    private static final String SEND_EMAIL_KEY = "send_email_key";

    public static final String TRANSLATING_MODULE = "translating_module";
    public static final String TRANSLATING_STRING = "translating_string";
    public static final String TRANSLATION_STATUS = "translation_status";

    // 写入状态
    private static final String PROGRESS_WRITE_KEY = "pw:{shopName}:{target}";
    public static final String WRITE_DONE = "write_done";
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
     * 删除写入key
     * */
    public void delWritingDataKey(String shopName, String target) {
        redisIntegration.delete(generateWriteStatusKey(shopName, target));
    }

    /**
     * 存写入状态的数据 done 和 total
     */
    public Long addWritingData(String pwKey, String model, long data) {
        return redisIntegration.incrementHash(pwKey, model, data);
    }

    /**
     * 获取写入状态数据
     */
    public Map<String, Integer> getWritingData(String shopName, String target) {
        Map<Object, Object> hashAll = redisIntegration.getHashAll(generateWriteStatusKey(shopName, target));
        if (!CollectionUtils.isEmpty(hashAll)) {
            return hashAll.entrySet().stream().collect(Collectors.toMap(
                    e -> String.valueOf(e.getKey()),
                    e -> Integer.parseInt(e.getValue().toString())));
        }
        return new HashMap<>();
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
     * 存正在翻译的模块
     */
    public void hsetTranslatingModule(String ptKey, String module) {
        redisIntegration.setHash(ptKey, TRANSLATING_MODULE, module);
    }

    /**
     * 正在翻译字符串
     */
    public void hsetTranslatingString(String ptKey, String translatingString) {
        redisIntegration.setHash(ptKey, TRANSLATING_STRING, translatingString);
    }

    /**
     * 翻译状态：1-初始化 2-翻译中 3-写入中
     */
    public void hsetTranslationStatus(String ptKey, String status) {
        redisIntegration.setHash(ptKey, TRANSLATION_STATUS, status);
    }

    public Map<String, String> hgetAll(String ptKey) {
        return redisIntegration.hGetAll(ptKey);
    }

    /**
     * 获取shop，进度条相关数据
     */
    public Map<Object, Object> getProgressTranslationKey(String ptKey) {
        return redisIntegration.getHashAll(ptKey);
    }

    /**
     * 存进度条数字的key：tr:{shopName}:{targetCode}
     */
    public void hsetProgressNumber(String ptKey, String progressNumberKey) {
        redisIntegration.setHash(ptKey, "progress_number", progressNumberKey);
    }

    /**
     * 存用户的停止标识，如果返回ture，存成功； 如果返回false，存失败
     */
    public Boolean setStopTranslationKey(String shopName) {
        redisIntegration.set(STOP_TRANSLATION_KEY + shopName, "1");
        return true;
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
