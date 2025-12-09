package com.bogdatech.logic;

import com.bogdatech.integration.RedisIntegration;
import com.bogdatech.utils.AESUtils;
import com.bogdatech.utils.RedisKeyUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import static com.bogdatech.utils.RedisKeyUtils.*;

@Service
public class RedisProcessService {

    @Autowired
    private RedisIntegration redisIntegration;

    public void addProcessData(String key, String field , Long value){
        if (key == null) {
            return;
        }
         redisIntegration.incrementHash(key,field,value);
    }

    public String getFieldProcessData(String key, String field){
        return redisIntegration.getHash(key,field);
    }

    public void initProcessData(String key) {
        redisIntegration.setHash(key, PROGRESS_TOTAL, 0);
        redisIntegration.setHash(key, PROGRESS_DONE, 0);
    }

    public void setCacheData(String targetCode, String targetValue, String sourceValue){
        String encryptedSource = AESUtils.encryptMD5(sourceValue);
        if (encryptedSource == null) {
            return;
        }
        String key = RedisKeyUtils.TRANSLATE_CACHE_KEY_TEMPLATE.replace("{targetCode}", targetCode)
                .replace("{source}", encryptedSource);
        redisIntegration.set(key, targetValue, DAY_14);
    }

    public String getCacheData(String targetCode, String sourceValue){
        String encryptedSource = AESUtils.encryptMD5(sourceValue);
        if (encryptedSource == null) {
            return null;
        }
        String key = RedisKeyUtils.TRANSLATE_CACHE_KEY_TEMPLATE.replace("{targetCode}", targetCode)
                .replace("{source}", encryptedSource);
        String text = redisIntegration.get(key);
        if (text != null && !"null".equals(text)){
            redisIntegration.expire(key, DAY_14);
            return text;
        }
        return null;
    }

}
