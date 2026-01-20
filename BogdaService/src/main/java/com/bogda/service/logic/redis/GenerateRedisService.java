package com.bogda.service.logic.redis;

import com.bogda.repository.RedisIntegration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Component
public class GenerateRedisService {
    @Autowired
    private RedisIntegration redisIntegration;

    private static final String GENERATE_SHOP_SET_KEY = "bogda:generate:shop";
    private static final String GENERATE_SHOP_LOCK_KEY = "bogda:generate:shop:lock:{userId}";
    private static final long GENERATE_SHOP_LOCK_TTL_SECONDS = 6 * 60 * 60;

    private static final String GENERATE_SHOP_STOP_FLAG_KEY = "bogda:generate:stopFlag";

    public boolean tryAcquireGenerateShop(Long userId) {
        if (userId == null) {
            return false;
        }
        String lockKey = generateShopLockKey(userId);
        boolean locked = redisIntegration.trySetValueIfAbsent(lockKey, "1");
        if (!locked) {
            return false;
        }
        redisIntegration.expire(lockKey, GENERATE_SHOP_LOCK_TTL_SECONDS);
        redisIntegration.setSet(GENERATE_SHOP_SET_KEY, String.valueOf(userId));
        return true;
    }

    public void releaseGenerateShop(Long userId) {
        if (userId == null) {
            return;
        }
        redisIntegration.delete(generateShopLockKey(userId));
        redisIntegration.remove(GENERATE_SHOP_SET_KEY, String.valueOf(userId));
    }

    public Set<Long> getGenerateShop() {
        Set<String> members = redisIntegration.getSet(GENERATE_SHOP_SET_KEY);
        if (members == null || members.isEmpty()) {
            return new HashSet<>();
        }
        Set<Long> result = new HashSet<>();
        for (String member : members) {
            if (member == null || member.isBlank()) {
                continue;
            }
            String lockValue = redisIntegration.get(generateShopLockKey(member));
            if (lockValue == null || "null".equalsIgnoreCase(lockValue)) {
                redisIntegration.remove(GENERATE_SHOP_SET_KEY, member);
                continue;
            }
            result.add(Long.valueOf(member));
        }
        return result;
    }

    public Boolean setStopFlag(Long userId, boolean flag) {
        if (userId == null) {
            return null;
        }
        String field = String.valueOf(userId);
        String previous = redisIntegration.getHash(GENERATE_SHOP_STOP_FLAG_KEY, field);
        redisIntegration.setHash(GENERATE_SHOP_STOP_FLAG_KEY, field, flag);
        return parseBooleanNullable(previous);
    }

    public boolean getStopFlag(Long userId) {
        if (userId == null) {
            return false;
        }
        String v = redisIntegration.getHash(GENERATE_SHOP_STOP_FLAG_KEY, String.valueOf(userId));
        Boolean parsed = parseBooleanNullable(v);
        return parsed != null && parsed;
    }

    public Map<Long, Boolean> getStopFlags() {
        Map<String, String> raw = redisIntegration.hGetAll(GENERATE_SHOP_STOP_FLAG_KEY);
        Map<Long, Boolean> result = new HashMap<>();
        raw.forEach((k, v) -> {
            if (k == null || k.isBlank()) {
                return;
            }
            result.put(Long.valueOf(k), Boolean.TRUE.equals(parseBooleanNullable(v)));
        });
        return result;
    }

    private static String generateShopLockKey(Long userId) {
        return GENERATE_SHOP_LOCK_KEY.replace("{userId}", String.valueOf(userId));
    }

    private static String generateShopLockKey(String userId) {
        return GENERATE_SHOP_LOCK_KEY.replace("{userId}", userId);
    }

    private static Boolean parseBooleanNullable(String v) {
        if (v == null || v.isBlank() || "null".equalsIgnoreCase(v)) {
            return null;
        }
        if ("true".equalsIgnoreCase(v)) {
            return true;
        }
        if ("false".equalsIgnoreCase(v)) {
            return false;
        }
        return Boolean.valueOf(v);
    }
}

