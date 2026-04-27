package com.bogda.service.logic;

import com.bogda.common.reporter.TraceReporterHolder;
import com.bogda.integration.model.ShopifyExtensions;
import com.google.common.util.concurrent.RateLimiter;
import org.springframework.stereotype.Service;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ShopifyRateLimitService {

    private final Map<String, RateLimiter> shopRateLimiters = new ConcurrentHashMap<>();
    private final Map<String, ShopRateState> shopRateStates = new ConcurrentHashMap<>();

    // 默认速率限制（保守值，避免初始请求被限流）
    private static final double DEFAULT_RATE = 2.0; // 每秒 2 个请求

    // 最小速率限制（防止速率过低）
    private static final double MIN_RATE = 0.5; // 每秒至少 0.5 个请求

    // 最大速率限制（防止速率过高）
    private static final double MAX_RATE = 30.0; // 每秒最多 30 个请求

    private static final double UTILIZATION = 0.85;
    private static final double COST_EMA_ALPHA = 0.20;
    private static final double MIN_ESTIMATED_COST = 10.0;
    private static final int SAFETY_BUFFER_POINTS = 50;
    private static final int SAVE_BATCH_MIN = 20;
    private static final int SAVE_BATCH_MAX = 250;
    private static final int SAVE_BATCH_INIT = 120;
    private static final int INIT_PAGE_MIN = 20;
    private static final int INIT_PAGE_MAX = 250;
    private static final int INIT_PAGE_INIT = 120;

    /**
     * 获取或创建商店的速率限制器
     */
    public RateLimiter getOrCreateRateLimiter(String shopName) {
        return shopRateLimiters.computeIfAbsent(shopName, k -> {
            TraceReporterHolder.report("ShopifyRateLimitService.getOrCreateRateLimiter",
                    String.format("创建新的速率限制器，商店: %s，默认速率: %.2f", shopName, DEFAULT_RATE));
            return RateLimiter.create(DEFAULT_RATE);
        });
    }

    private ShopRateState getOrCreateShopRateState(String shopName) {
        return shopRateStates.computeIfAbsent(shopName, k -> new ShopRateState());
    }

    /**
     * 请求发出前，根据上一次 throttle 状态做短暂等待，减少硬性限流并提高整体吞吐稳定性。
     */
    public void beforeRequest(String shopName) {
        ShopRateState state = getOrCreateShopRateState(shopName);
        long sleepMs = 0;
        synchronized (state) {
            if (state.lastCurrentlyAvailable <= 0 || state.lastRestoreRate <= 0) {
                return;
            }
            double estimatedCost = Math.max(MIN_ESTIMATED_COST, state.avgActualCost);
            double requiredPoints = estimatedCost + SAFETY_BUFFER_POINTS;
            if (state.lastCurrentlyAvailable >= requiredPoints) {
                return;
            }
            double waitSeconds = (requiredPoints - state.lastCurrentlyAvailable) / state.lastRestoreRate;
            sleepMs = (long) Math.min(2000, Math.max(0, waitSeconds * 1000));
        }
        if (sleepMs > 0) {
            try {
                Thread.sleep(sleepMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * 根据 Shopify 响应更新速率限制
     */
    public void updateRateLimit(String shopName, ShopifyExtensions extensions) {
        if (extensions == null || extensions.getCost() == null || extensions.getCost().getThrottleStatus() == null) {
            return;
        }
        
        ShopifyExtensions.Cost.ThrottleStatus throttleStatus = extensions.getCost().getThrottleStatus();
        Double maximumAvailable = throttleStatus.getMaximumAvailable();
        Integer currentlyAvailable = throttleStatus.getCurrentlyAvailable();
        Double restoreRate = throttleStatus.getRestoreRate();
        Integer actualQueryCost = extensions.getCost().getActualQueryCost();

        if (maximumAvailable == null || currentlyAvailable == null || restoreRate == null || actualQueryCost == null) {
            return;
        }

        ShopRateState state = getOrCreateShopRateState(shopName);
        double smoothedCost;
        synchronized (state) {
            double actualCost = Math.max(MIN_ESTIMATED_COST, actualQueryCost.doubleValue());
            state.avgActualCost = (1 - COST_EMA_ALPHA) * state.avgActualCost + COST_EMA_ALPHA * actualCost;
            state.lastCurrentlyAvailable = currentlyAvailable;
            state.lastMaximumAvailable = maximumAvailable;
            state.lastRestoreRate = restoreRate;
            smoothedCost = state.avgActualCost;
        }

        double newRate = UTILIZATION * restoreRate / Math.max(MIN_ESTIMATED_COST, smoothedCost);
        newRate = Math.max(MIN_RATE, Math.min(MAX_RATE, newRate));

        RateLimiter rateLimiter = getOrCreateRateLimiter(shopName);
        double currentRate = rateLimiter.getRate();

        // 只有当速率变化超过 10% 时才更新，避免频繁调整
        if (Math.abs(newRate - currentRate) / currentRate > 0.1) {
            rateLimiter.setRate(newRate);
            TraceReporterHolder.report("ShopifyRateLimitService.updateRateLimit",
                    String.format("更新速率限制，商店: %s, 当前可用: %d/%.0f, 恢复速率: %.0f, actualCost: %d, avgCost: %.2f, 新速率: %.2f, 旧速率: %.2f",
                            shopName, currentlyAvailable, maximumAvailable, restoreRate, actualQueryCost, smoothedCost, newRate, currentRate));
        }
    }

    public int getRecommendedSaveBatchSize(String shopName) {
        ShopRateState state = getOrCreateShopRateState(shopName);
        synchronized (state) {
            return state.dynamicSaveBatchSize;
        }
    }

    public void onSaveOutcome(String shopName, boolean success, boolean throttled) {
        ShopRateState state = getOrCreateShopRateState(shopName);
        synchronized (state) {
            double availableRatio = state.lastMaximumAvailable <= 0
                    ? 0.0
                    : (double) state.lastCurrentlyAvailable / state.lastMaximumAvailable;
            if (success) {
                state.consecutiveSaveSuccess++;
                if (state.consecutiveSaveSuccess >= 5 && availableRatio > 0.6) {
                    state.dynamicSaveBatchSize = Math.min(SAVE_BATCH_MAX, state.dynamicSaveBatchSize + 20);
                    state.consecutiveSaveSuccess = 0;
                }
                return;
            }

            state.consecutiveSaveSuccess = 0;
            if (throttled || availableRatio < 0.25) {
                state.dynamicSaveBatchSize = Math.max(SAVE_BATCH_MIN, state.dynamicSaveBatchSize / 2);
            } else {
                state.dynamicSaveBatchSize = Math.max(SAVE_BATCH_MIN, state.dynamicSaveBatchSize - 20);
            }
        }
    }

    public int getRecommendedInitPageSize(String shopName, int upperBound) {
        ShopRateState state = getOrCreateShopRateState(shopName);
        synchronized (state) {
            int cappedUpper = Math.max(INIT_PAGE_MIN, Math.min(INIT_PAGE_MAX, upperBound));
            return Math.max(INIT_PAGE_MIN, Math.min(cappedUpper, state.dynamicInitPageSize));
        }
    }

    public void onInitReadOutcome(String shopName, boolean throttled) {
        ShopRateState state = getOrCreateShopRateState(shopName);
        synchronized (state) {
            double availableRatio = state.lastMaximumAvailable <= 0
                    ? 0.0
                    : (double) state.lastCurrentlyAvailable / state.lastMaximumAvailable;
            if (throttled || availableRatio < 0.25) {
                state.dynamicInitPageSize = Math.max(INIT_PAGE_MIN, state.dynamicInitPageSize / 2);
                state.consecutiveInitReadSuccess = 0;
                return;
            }

            state.consecutiveInitReadSuccess++;
            if (state.consecutiveInitReadSuccess >= 3 && availableRatio > 0.55) {
                state.dynamicInitPageSize = Math.min(INIT_PAGE_MAX, state.dynamicInitPageSize + 20);
                state.consecutiveInitReadSuccess = 0;
            }
        }
    }

    private static class ShopRateState {
        private double avgActualCost = 30.0;
        private int lastCurrentlyAvailable = 100;
        private double lastMaximumAvailable = 1000.0;
        private double lastRestoreRate = 100.0;
        private int dynamicSaveBatchSize = SAVE_BATCH_INIT;
        private int consecutiveSaveSuccess = 0;
        private int dynamicInitPageSize = INIT_PAGE_INIT;
        private int consecutiveInitReadSuccess = 0;
    }
}

