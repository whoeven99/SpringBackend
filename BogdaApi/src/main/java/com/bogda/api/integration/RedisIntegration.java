package com.bogda.api.integration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.concurrent.TimeUnit;

import static com.bogda.common.utils.CaseSensitiveUtils.appInsights;

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
        try {
            redisTemplate.opsForValue().set(key, value, timeout, TimeUnit.SECONDS);
        } catch (Exception e) {
            appInsights.trackTrace("FatalException set " + key + " " + value + " " + e.getMessage());
        }
    }

    /**
     * 不设置缓存
     */
    public void set(String key, String value) {
        try {
            redisTemplate.opsForValue().set(key, value);
        } catch (Exception e) {
            appInsights.trackTrace("FatalException set " + key + " " + value + " " + e.getMessage());
        }
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

    public Long incrementHash(String key, String field, Integer value) {
        try {
            return redisTemplate.opsForHash().increment(key, field, value.longValue());
        } catch (Exception e) {
            appInsights.trackTrace("FatalException incrementHash " + key + " " + field + " " + value + " " + e.getMessage());
        }
        return 0L;
    }

    /**
     * hash 增加指定数据
     */
    public Long incrementHash(String key, String field, long value) {
        try {
            return redisTemplate.opsForHash().increment(key, field, value);
        } catch (Exception e) {
            appInsights.trackTrace("FatalException incrementHash " + key + " " + field + " " + value + " " + e.getMessage());
        }
        return 0L;
    }

    /**
     * 对指定 key 的值进行自增
     */
    public Long incrementValue(String key, long delta) {
        try {
            return redisTemplate.opsForValue().increment(key, delta);
        } catch (Exception e) {
            appInsights.trackTrace("FatalException incrementValue " + key + " " + delta + " " + e.getMessage());
        }
        return 0L;
    }

    /**
     * hash get
     */
    public String getHash(String key, String field) {
        try {
            Object res = redisTemplate.opsForHash().get(key, field);
            return res != null ? res.toString() : null;
        } catch (Exception e) {
            appInsights.trackTrace("FatalException getHash " + key + " " + field + " " + e.getMessage());
        }
        return null;
    }

    // TODO getHashAll 都挪到这里
    public Map<String, String> hGetAll(String key) {
        Map<Object, Object> map = null;
        try {
            map = redisTemplate.opsForHash().entries(key);
        } catch (Exception e) {
            appInsights.trackTrace("FatalException hGetAll " + key + " " + e.getMessage());
        }

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
        Long add = null;
        try {
            add = redisTemplate.opsForSet().add(key, value);
        } catch (Exception e) {
            appInsights.trackTrace("FatalException setSet " + key + " " + value + " " + e.getMessage());
        }
        return add != null && add > 0;
    }

    public Set<String> getSet(String key) {
        return redisTemplate.opsForSet().members(key);
    }

    public Boolean remove(String key, String value) {
        Long remove = null;
        try {
            remove = redisTemplate.opsForSet().remove(key, value);
        } catch (Exception e) {
            appInsights.trackTrace("FatalException remove " + key + " " + value + " " + e.getMessage());
        }
        return remove != null && remove > 0;
    }

    /**
     * 获取缓存
     */
    public String get(String key) {
        try {
            return redisTemplate.opsForValue().get(key) + "";
        } catch (Exception e) {
            appInsights.trackTrace("FatalException get " + key + " " + e.getMessage());
        }
        return "null";
    }


    /**
     * multiGet
     * */
    public List<String> multiGet(Collection<String> keys) {
        try {
            return redisTemplate.opsForValue().multiGet(keys);
        } catch (Exception e) {
            appInsights.trackTrace("FatalException multiGet " + keys + " " + e.getMessage());
        }
        return new ArrayList<>();
    }

    /**
     * 删除缓存
     */
    public Boolean delete(String key) {
        try {
            return redisTemplate.delete(key);
        } catch (Exception e) {
            appInsights.trackTrace("FatalException delete " + key + " " + e.getMessage());
        }
        return false;
    }

    /**
     * 删除Hash的field
     * */
    public boolean hashDelete(String key, String field) {
        try {
            return redisTemplate.opsForHash().delete(key, field) > 0;
        } catch (Exception e) {
            appInsights.trackTrace("FatalException hashDelete " + key + " " + e.getMessage());
        }
        return false;
    }

    /**
     * 重新设置过期时间
     */
    public Boolean expire(String key, long timeout) {
        try {
            return redisTemplate.expire(key, timeout, TimeUnit.SECONDS);
        } catch (Exception e) {
            appInsights.trackTrace("FatalException expire " + key + " " + timeout + " " + e.getMessage());
        }
        return false;
    }
}
