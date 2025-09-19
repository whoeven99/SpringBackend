package com.bogdatech.logic;

import com.bogdatech.integration.RedisIntegration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import static com.bogdatech.utils.AESUtils.encryptMD5;
import static com.bogdatech.utils.RedisKeyUtils.*;

@Service
public class RedisProcessService {

    @Autowired
    private RedisIntegration redisIntegration;

    /**
     * redis添加进度条指定数据
     * */
    public void addProcessData(String key, String field , Long value){
        if (key == null) {
            return;
        }
         redisIntegration.incrementHash(key,field,value);
    }

    /**
     * redis获取进度条相关数据
     * */
    public String getFieldProcessData(String key, String field){
        return redisIntegration.getHash(key,field);
    }

    /**
     * 初始化对应数据
     * */
    public void initProcessData(String key) {
        redisIntegration.setHash(key, PROGRESS_TOTAL, 0);
        redisIntegration.setHash(key, PROGRESS_DONE, 0);
    }

    /**
     * 将翻译数据存入redis，设置缓存时间为2周
     * 数据格式为 tr:{shopName}:{digest}
     * @param targetCode 目标语言
     * @param targetValue 目标语言翻译后的文本
     * @param sourceValue 源语言文本
     * */
    public void setCacheData(String targetCode, String targetValue, String sourceValue){
        String encryptedSource = encryptMD5(sourceValue);
        String key = generateCacheKey(targetCode, encryptedSource);
        redisIntegration.set(key, targetValue, DAY_14);
    }

    /**
     * 获取缓存中对应的数据
     * 重置原先的时间
     * @param targetCode 目标语言
     * @param sourceValue 源语言文本
     * */
    public String getCacheData(String targetCode, String sourceValue){
        String encryptedSource = encryptMD5(sourceValue);
        String key = generateCacheKey(targetCode, encryptedSource);
        String text = redisIntegration.get(key);
        if (text != null && !"null".equals(text)){
            redisIntegration.expire(key, DAY_14);
            return text;
        }
        return null;
    }

}
