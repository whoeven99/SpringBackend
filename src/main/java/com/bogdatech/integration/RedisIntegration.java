package com.bogdatech.integration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import java.util.concurrent.TimeUnit;

@Component
public class RedisIntegration {
    @Autowired
    private RedisTemplate<String, String> redisTemplate;



    /**
     * 设置缓存
     */
    public void set(String key, String value, long timeout) {
        redisTemplate.opsForValue().set(key, value, timeout, TimeUnit.SECONDS);
    }

    /**
     * 不设置缓存
     * */
    public void set(String key, String value){
        redisTemplate.opsForValue().set(key, value);
    }
    /**
     * 不设置缓存的hash
     * */
    public void setHash(String key, String field, Object value) {
        redisTemplate.opsForHash().put(key, field, value);
    }

    /**
     * hash 增加指定数据
     * */
    public Long incrementHash(String key, String field, long value){
        return redisTemplate.opsForHash().increment(key, field, value);
    }

    /**
     * hash get
     * */
    public String getHash(String key, String field){
        return (String) redisTemplate.opsForHash().get(key, field);
    }


    /**
     * 获取缓存
     */
    public Object get(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    /**
     * 删除缓存
     */
    public Boolean delete(String key) {
        return redisTemplate.delete(key);
    }

    /**
     * 判断key是否存在
     */
    public Boolean hasKey(String key) {
        return redisTemplate.hasKey(key);
    }

}
