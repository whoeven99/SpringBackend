package com.bogdatech.integration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static com.bogdatech.utils.CaseSensitiveUtils.appInsights;

@Component
public class RedisIntegration {
    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    /**
     * key 不存在时才 set
     */
    public boolean trySetValueIfAbsent(String key, String value) {
        Boolean result = redisTemplate.opsForValue()
                .setIfAbsent(key, value);
        return Boolean.TRUE.equals(result);
    }

    /**
     * 设置缓存
     */
    public void set(String key, String value, long timeout) {
        redisTemplate.opsForValue().set(key, value, timeout, TimeUnit.SECONDS);
    }

    /**
     * 不设置缓存
     */
    public void set(String key, String value) {
        redisTemplate.opsForValue().set(key, value);
    }

    /**
     * 不设置缓存的hash
     */
    public void setHash(String key, String field, Object value) {
        try {
            redisTemplate.opsForHash().put(key, field, value);
        } catch (Exception e) {
            appInsights.trackTrace("FatalException setHash " + key + " " + field + " " + value + " " + e.getMessage());
        }
    }

    /**
     * hash 增加指定数据
     */
    public Long incrementHash(String key, String field, long value) {
        return redisTemplate.opsForHash().increment(key, field, value);
    }

    /**
     * 对指定 key 的值进行自增
     */
    public Long incrementValue(String key, long delta) {
        return redisTemplate.opsForValue().increment(key, delta);
    }

    /**
     * hash get
     */
    public String getHash(String key, String field) {
        return redisTemplate.opsForHash().get(key, field) + "";
    }

    /**
     * hash GETALL
     */
    public Map<Object, Object> getHashAll(String key) {
        return redisTemplate.opsForHash().entries(key);
    }

    // TODO getHashAll 都挪到这里
    public Map<String, String> hGetAll(String key) {
        Map<Object, Object> map = redisTemplate.opsForHash().entries(key);
        if (CollectionUtils.isEmpty(map)) {
            return new HashMap<>();
        }
        Map<String, String> result = new HashMap<>();
        for (Map.Entry<Object, Object> entry : map.entrySet()) {
            result.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
        }
        return result;
    }

    /**
     * Set存
     */
    public Boolean setSet(String key, String value) {
        Long add = redisTemplate.opsForSet().add(key, value);
        return add != null && add > 0;
    }

    public Set<String> getSet(String key) {
        return redisTemplate.opsForSet().members(key);
    }

    public Boolean remove(String key, String value) {
        Long remove = redisTemplate.opsForSet().remove(key, value);
        return remove != null && remove > 0;
    }

    /**
     * 获取缓存
     */
    public String get(String key) {
        return redisTemplate.opsForValue().get(key) + "";
    }


    /**
     * multiGet
     * */
    public List<String> multiGet(Collection<String> keys) {
        return redisTemplate.opsForValue().multiGet(keys);
    }

    /**
     * 删除缓存
     */
    public Boolean delete(String key) {
        return redisTemplate.delete(key);
    }

    /**
     * 删除Hash的field
     * */
    public boolean hashDelete(String key, String field) {
        return redisTemplate.opsForHash().delete(key, field) > 0;
    }

    /**
     * 判断key是否存在
     */
    public Boolean hasKey(String key) {
        return redisTemplate.hasKey(key);
    }

    /**
     * 重新设置过期时间
     */
    public Boolean expire(String key, long timeout) {
        return redisTemplate.expire(key, timeout, TimeUnit.SECONDS);
    }
}
