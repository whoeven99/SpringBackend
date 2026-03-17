package com.bogda.repository;

import com.bogda.common.reporter.ExceptionReporterHolder;
import com.bogda.common.reporter.TraceReporterHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.concurrent.TimeUnit;

@Component
public class RedisIntegration {

    private static final Logger log = LoggerFactory.getLogger(RedisIntegration.class);
    /** 最大重试次数 */
    private static final int MAX_RETRIES = 3;
    /** 初始退避时间（毫秒），后续按指数递增：100ms → 200ms → 400ms */
    private static final long INITIAL_BACKOFF_MS = 100;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    /** 有返回值的 Redis 操作 */
    @FunctionalInterface
    private interface RedisOperation<T> {
        T execute() throws Exception;
    }

    /** 无返回值的 Redis 操作 */
    @FunctionalInterface
    private interface VoidRedisOperation {
        void execute() throws Exception;
    }

    /**
     * 带重试的 Redis 操作执行器（有返回值）
     * <p>最多重试 {@link #MAX_RETRIES} 次，每次失败后按指数退避等待。
     * 若线程被中断则立即终止重试；全部重试耗尽后上报异常并返回默认值。</p>
     *
     * @param methodName   方法名，用于日志和异常上报
     * @param context      操作上下文描述，用于日志和异常上报
     * @param operation    实际的 Redis 操作
     * @param defaultValue 全部重试失败后的兜底返回值
     */
    private <T> T executeWithRetry(String methodName, String context,
                                   RedisOperation<T> operation, T defaultValue) {
        Exception lastException = null;
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                return operation.execute();
            } catch (Exception e) {
                lastException = e;
                if (attempt < MAX_RETRIES) {
                    long backoff = INITIAL_BACKOFF_MS * (1L << (attempt - 1));
                    log.warn("FatalException [{}] 第 {}/{} 次重试，{}ms 后执行, context: {}",
                            methodName, attempt, MAX_RETRIES, backoff, context, e);
                    try {
                        Thread.sleep(backoff);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        TraceReporterHolder.report(methodName,
                "FatalException after " + MAX_RETRIES + " retries, " + context);
        ExceptionReporterHolder.report(methodName, lastException);
        return defaultValue;
    }

    /**
     * 带重试的 Redis 操作执行器（无返回值）
     *
     * @param methodName 方法名，用于日志和异常上报
     * @param context    操作上下文描述，用于日志和异常上报
     * @param operation  实际的 Redis 操作
     */
    private void executeWithRetryVoid(String methodName, String context,
                                      VoidRedisOperation operation) {
        executeWithRetry(methodName, context, () -> {
            operation.execute();
            return null;
        }, null);
    }
    /**
     * key 不存在时才 set
     */
    public boolean trySetValueIfAbsent(String key, String value) {
        return executeWithRetry("RedisIntegration.trySetValueIfAbsent",
                "trySetValueIfAbsent " + key,
                () -> Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(key, value)),
                false);
    }

    /**
     * Hash key 不存在时才 set，存在则不覆盖
     */
    public boolean setHashValueIfAbsent(String key, String field, String value) {
        return executeWithRetry("RedisIntegration.setHashValueIfAbsent",
                "setHashValueIfAbsent " + key + " " + value,
                () -> Boolean.TRUE.equals(redisTemplate.opsForHash().putIfAbsent(key, field, value)),
                false);
    }

    /**
     * 设置缓存（带过期时间，单位：秒）
     */
    public void set(String key, String value, long timeout) {
        executeWithRetryVoid("RedisIntegration.set", "set " + key,
                () -> redisTemplate.opsForValue().set(key, value, timeout, TimeUnit.SECONDS));
    }

    /**
     * 设置缓存（不设置过期时间）
     */
    public void set(String key, String value) {
        executeWithRetryVoid("RedisIntegration.set", "set " + key,
                () -> redisTemplate.opsForValue().set(key, value));
    }

    /**
     * 设置 Hash 字段值（不设置过期时间）
     */
    public void setHash(String key, String field, Object value) {
        executeWithRetryVoid("RedisIntegration.setHash",
                "setHash " + key + " " + field,
                () -> redisTemplate.opsForHash().put(key, field, value));
    }

    /**
     * Hash 字段自增（Integer 类型）
     */
    public Long incrementHash(String key, String field, Integer value) {
        return executeWithRetry("RedisIntegration.incrementHash",
                "incrementHash " + key + " " + field + " " + value,
                () -> redisTemplate.opsForHash().increment(key, field, value.longValue()),
                0L);
    }

    /**
     * Hash 字段自增（long 类型）
     */
    public Long incrementHash(String key, String field, long value) {
        return executeWithRetry("RedisIntegration.incrementHash",
                "incrementHash " + key + " " + field + " " + value,
                () -> redisTemplate.opsForHash().increment(key, field, value),
                0L);
    }

    /**
     * 对指定 key 的值进行自增
     */
    public Long incrementValue(String key, long delta) {
        return executeWithRetry("RedisIntegration.incrementValue",
                "incrementValue " + key + " " + delta,
                () -> redisTemplate.opsForValue().increment(key, delta),
                0L);
    }

    /**
     * 获取 Hash 字段值
     */
    public String getHash(String key, String field) {
        return executeWithRetry("RedisIntegration.getHash",
                "getHash " + key + " " + field,
                () -> {
                    Object res = redisTemplate.opsForHash().get(key, field);
                    return res != null ? res.toString() : null;
                },
                null);
    }

    /**
     * 获取 Hash 所有字段和值
     */
    public Map<String, String> hGetAll(String key) {
        return executeWithRetry("RedisIntegration.hGetAll",
                "hGetAll " + key,
                () -> {
                    Map<Object, Object> map = redisTemplate.opsForHash().entries(key);
                    if (CollectionUtils.isEmpty(map)) {
                        return new HashMap<>();
                    }
                    Map<String, String> result = new HashMap<>();
                    for (Map.Entry<Object, Object> entry : map.entrySet()) {
                        result.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
                    }
                    return result;
                },
                new HashMap<>());
    }

    /**
     * 向 Set 集合添加元素
     */
    public Boolean setSet(String key, String value) {
        return executeWithRetry("RedisIntegration.setSet",
                "setSet " + key + " " + value,
                () -> {
                    Long add = redisTemplate.opsForSet().add(key, value);
                    return add != null && add > 0;
                },
                false);
    }

    /**
     * 获取 Set 集合所有元素
     */
    public Set<String> getSet(String key) {
        return executeWithRetry("RedisIntegration.getSet",
                "getSet " + key,
                () -> redisTemplate.opsForSet().members(key),
                Collections.emptySet());
    }

    /**
     * 从 Set 集合移除指定元素
     */
    public Boolean remove(String key, String value) {
        return executeWithRetry("RedisIntegration.remove",
                "remove " + key + " " + value,
                () -> {
                    Long removed = redisTemplate.opsForSet().remove(key, value);
                    return removed != null && removed > 0;
                },
                false);
    }

    /**
     * 获取缓存
     */
    public String get(String key) {
        return executeWithRetry("RedisIntegration.get",
                "get " + key,
                () -> redisTemplate.opsForValue().get(key) + "",
                "null");
    }


    /**
     * 批量获取多个 key 的值
     */
    public List<String> multiGet(Collection<String> keys) {
        return executeWithRetry("RedisIntegration.multiGet",
                "multiGet " + keys,
                () -> redisTemplate.opsForValue().multiGet(keys),
                new ArrayList<>());
    }

    /**
     * 删除缓存
     */
    public Boolean delete(String key) {
        return executeWithRetry("RedisIntegration.delete",
                "delete " + key,
                () -> redisTemplate.delete(key),
                false);
    }

    /**
     * 删除 Hash 指定字段
     */
    public boolean hashDelete(String key, String field) {
        return executeWithRetry("RedisIntegration.hashDelete",
                "hashDelete " + key + " " + field,
                () -> redisTemplate.opsForHash().delete(key, field) > 0,
                false);
    }

    /**
     * 设置 key 的过期时间（单位：秒）
     */
    public Boolean expire(String key, long timeout) {
        return executeWithRetry("RedisIntegration.expire",
                "expire " + key + " " + timeout,
                () -> redisTemplate.expire(key, timeout, TimeUnit.SECONDS),
                false);
    }
}
