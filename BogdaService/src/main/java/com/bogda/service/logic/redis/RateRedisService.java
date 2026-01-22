package com.bogda.service.logic.redis;

import com.bogda.repository.RedisIntegration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class RateRedisService {
    @Autowired
    private RedisIntegration redisIntegration;

    private static final String RATE_REDIS_KEY = "bogda:rate";
    private static final long RATE_REDIS_TTL_SECONDS = 2 * 24 * 60 * 60;

    public void refreshRates(Map<String, Double> rates) {
        if (rates == null || rates.isEmpty()) {
            return;
        }
        // 全量覆盖，避免旧币种残留
        redisIntegration.delete(RATE_REDIS_KEY);
        rates.forEach((code, value) -> redisIntegration.setHash(RATE_REDIS_KEY, code, value));
        redisIntegration.expire(RATE_REDIS_KEY, RATE_REDIS_TTL_SECONDS);
    }

    public Map<String, Double> getRates() {
        Map<String, String> raw = redisIntegration.hGetAll(RATE_REDIS_KEY);
        Map<String, Double> result = new HashMap<>();
        raw.forEach((k, v) -> {
            if (v != null && !"null".equalsIgnoreCase(v)) {
                result.put(k, Double.valueOf(v));
            }
        });
        return result;
    }

    public Map<String, Object> getRatesAsObject() {
        Map<String, Object> result = new HashMap<>();
        getRates().forEach(result::put);
        return result;
    }

    public Double getRate(String currencyCode) {
        String v = redisIntegration.getHash(RATE_REDIS_KEY, currencyCode);
        if (v == null || "null".equalsIgnoreCase(v)) {
            return null;
        }
        return Double.valueOf(v);
    }

    public boolean isEmpty() {
        return redisIntegration.hGetAll(RATE_REDIS_KEY).isEmpty();
    }
}

