package com.bogda.service.logic.BundleApp.redis;

import com.bogda.common.utils.AppInsightsUtils;
import com.bogda.repository.RedisIntegration;
import kotlin.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Bundle 预算使用量 Redis 热数据（并发原子累加）
 *
 * <p>实现方式：Lua + INCRBYFLOAT，保证并发下 usedDailyBudget/usedTotalBudget 原子准确。</p>
 */
@Component
public class BundleBudgetRedisService {
    private static final long DAILY_TTL_SECONDS = 48 * 60 * 60L; // 48h
    private static final DateTimeFormatter UTC_DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    private static final String DAILY_KEY = "bd:{shopName}:{discountId}:{yyyyMMddUTC}"; // bundle daily
    private static final String TOTAL_KEY = "bt:{shopName}:{discountId}"; // bundle total

    @Autowired
    private RedisIntegration redisIntegration;

    public Pair<Double, Double> addUsedAmount(String shopName, String discountId, double amount) {
        String day = LocalDate.now(ZoneOffset.UTC).format(UTC_DAY);
        String dailyKey = DAILY_KEY.replace("{shopName}", shopName)
                .replace("{discountId}", discountId)
                .replace("{yyyyMMddUTC}", day);
        String totalKey = TOTAL_KEY.replace("{shopName}", shopName)
                .replace("{discountId}", discountId);

        try {
            Double incrementDaily = redisIntegration.incrementValue(dailyKey, amount);
            System.out.println("incrementDaily: " + incrementDaily);
            redisIntegration.expire(dailyKey, DAILY_TTL_SECONDS);
            Double incrementTotal = redisIntegration.incrementValue(totalKey, amount);
            System.out.println("incrementTotal: " + incrementTotal);
            if (incrementDaily == 0D || incrementTotal == 0D) {
                return null;
            }
            return new Pair<>(incrementDaily, incrementTotal);
        } catch (Exception e) {
            AppInsightsUtils.trackException(e);
            AppInsightsUtils.trackTrace("FatalException BundleBudgetRedisService.addUsedAmount shopName=" + shopName +
                    " discountId=" + discountId + " amount=" + amount + " err=" + e.getMessage());
            return null;
        }
    }
}

