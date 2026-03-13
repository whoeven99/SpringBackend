package com.bogda.repository;

import com.bogda.common.reporter.ExceptionReporterHolder;
import com.bogda.common.reporter.TraceReporterHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.concurrent.TimeUnit;

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
     * Hash key 不存在的时候才set
     */
    public boolean setHashValueIfAbsent(String key, String field, String value) {
        try {
            Boolean result = redisTemplate.opsForHash().putIfAbsent(key, field, value);
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            TraceReporterHolder.report("RedisIntegration.setHashValueIfAbsent", "FatalException setHashValueIfAbsent " + key + " " + value);
            ExceptionReporterHolder.report("RedisIntegration.setHashValueIfAbsent", e);
        }
        return false;
    }

    /**
     * 设置缓存
     */
    public void set(String key, String value, long timeout) {
        try {
            redisTemplate.opsForValue().set(key, value, timeout, TimeUnit.SECONDS);
        } catch (Exception e) {
            TraceReporterHolder.report("RedisIntegration.set", "FatalException set " + key + " " + value);
            ExceptionReporterHolder.report("RedisIntegration.set", e);
        }
    }

    /**
     * 不设置缓存
     */
    public void set(String key, String value) {
        try {
            redisTemplate.opsForValue().set(key, value);
        } catch (Exception e) {
            TraceReporterHolder.report("RedisIntegration.set", "FatalException set " + key + " " + value);
            ExceptionReporterHolder.report("RedisIntegration.set", e);
        }
    }

    /**
     * 不设置缓存的hash
     */
    public void setHash(String key, String field, Object value) {
        try {
            redisTemplate.opsForHash().put(key, field, value);
        } catch (Exception e) {
            TraceReporterHolder.report("RedisIntegration.setHash", "FatalException setHash " + key + " " + field + " " + value);
            ExceptionReporterHolder.report("RedisIntegration.setHash", e);
        }
    }

    public Long incrementHash(String key, String field, Integer value) {
        try {
            return redisTemplate.opsForHash().increment(key, field, value.longValue());
        } catch (Exception e) {
            TraceReporterHolder.report("RedisIntegration.incrementHash", "FatalException incrementHash " + key + " " + field + " " + value);
            ExceptionReporterHolder.report("RedisIntegration.incrementHash", e);
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
            TraceReporterHolder.report("RedisIntegration.incrementHash", "FatalException incrementHash " + key + " " + field + " " + value);
            ExceptionReporterHolder.report("RedisIntegration.incrementHash", e);
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
            TraceReporterHolder.report("RedisIntegration.incrementValue", "FatalException incrementValue " + key + " " + delta);
            ExceptionReporterHolder.report("RedisIntegration.incrementValue", e);
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
            TraceReporterHolder.report("RedisIntegration.getHash", "FatalException getHash " + key + " " + field);
            ExceptionReporterHolder.report("RedisIntegration.getHash", e);
        }
        return null;
    }

    // TODO getHashAll 都挪到这里
    public Map<String, String> hGetAll(String key) {
        Map<Object, Object> map = null;
        try {
            map = redisTemplate.opsForHash().entries(key);
        } catch (Exception e) {
            TraceReporterHolder.report("RedisIntegration.hGetAll", "FatalException hGetAll " + key);
            ExceptionReporterHolder.report("RedisIntegration.hGetAll", e);
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
            TraceReporterHolder.report("RedisIntegration.setSet", "FatalException setSet " + key + " " + value);
            ExceptionReporterHolder.report("RedisIntegration.setSet", e);
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
            TraceReporterHolder.report("RedisIntegration.remove", "FatalException remove " + key + " " + value);
            ExceptionReporterHolder.report("RedisIntegration.remove", e);
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
            TraceReporterHolder.report("RedisIntegration.get", "FatalException get " + key);
            ExceptionReporterHolder.report("RedisIntegration.get", e);
        }
        return "null";
    }


    /**
     * multiGet
     */
    public List<String> multiGet(Collection<String> keys) {
        try {
            return redisTemplate.opsForValue().multiGet(keys);
        } catch (Exception e) {
            TraceReporterHolder.report("RedisIntegration.multiGet", "FatalException multiGet " + keys);
            ExceptionReporterHolder.report("RedisIntegration.multiGet", e);
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
            TraceReporterHolder.report("RedisIntegration.delete", "FatalException delete " + key);
            ExceptionReporterHolder.report("RedisIntegration.delete", e);
        }
        return false;
    }

    /**
     * 删除Hash的field
     */
    public boolean hashDelete(String key, String field) {
        try {
            return redisTemplate.opsForHash().delete(key, field) > 0;
        } catch (Exception e) {
            TraceReporterHolder.report("RedisIntegration.hashDelete", "FatalException hashDelete " + key);
            ExceptionReporterHolder.report("RedisIntegration.hashDelete", e);
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
            TraceReporterHolder.report("RedisIntegration.expire", "FatalException expire " + key + " " + timeout);
            ExceptionReporterHolder.report("RedisIntegration.expire", e);
        }
        return false;
    }
}
