package com.bogda.api.logic;

import com.bogda.api.integration.model.ShopifyExtensions;
import com.bogda.api.utils.AppInsightsUtils;
import com.google.common.util.concurrent.RateLimiter;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ShopifyRateLimitService {
    
    private final Map<String, RateLimiter> shopRateLimiters = new ConcurrentHashMap<>();
    
    // 默认速率限制（保守值，避免初始请求被限流）
    private static final double DEFAULT_RATE = 2.0; // 每秒 2 个请求
    
    // 最小速率限制（防止速率过低）
    private static final double MIN_RATE = 0.5; // 每秒至少 0.5 个请求
    
    // 最大速率限制（防止速率过高）
    private static final double MAX_RATE = 20.0; // 每秒最多 20 个请求
    
    /**
     * 获取或创建商店的速率限制器
     */
    public RateLimiter getOrCreateRateLimiter(String shopName) {
        return shopRateLimiters.computeIfAbsent(shopName, k -> {
            AppInsightsUtils.trackTrace("创建新的速率限制器，商店: %s，默认速率: %.2f", shopName, DEFAULT_RATE);
            return RateLimiter.create(DEFAULT_RATE);
        });
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
        
        if (maximumAvailable == null || currentlyAvailable == null || restoreRate == null) {
            return;
        }
        
        // 计算新的速率限制
        // 策略：根据当前可用配额和恢复速率动态调整
        // 如果当前可用配额较低，降低速率；如果较高，可以提高速率
        double newRate;
        
        if (maximumAvailable <= 0) {
            // 如果最大可用为0，使用最小速率
            newRate = MIN_RATE;
        } else {
            // 计算可用配额百分比
            double availableRatio = (double) currentlyAvailable / maximumAvailable;
            
            // 根据可用配额调整速率
            // 可用配额 < 20%：使用最小速率
            // 可用配额 20%-50%：使用恢复速率的 50%
            // 可用配额 50%-80%：使用恢复速率
            // 可用配额 > 80%：使用恢复速率的 150%（但不能超过最大速率）
            if (availableRatio < 0.2) {
                newRate = Math.max(MIN_RATE, restoreRate * 0.3);
            } else if (availableRatio < 0.5) {
                newRate = Math.max(MIN_RATE, restoreRate * 0.5);
            } else if (availableRatio < 0.8) {
                newRate = Math.min(MAX_RATE, restoreRate);
            } else {
                newRate = Math.min(MAX_RATE, restoreRate * 1.5);
            }
            
            // 确保速率在合理范围内
            newRate = Math.max(MIN_RATE, Math.min(MAX_RATE, newRate));
        }
        
        // 更新速率限制器
        RateLimiter rateLimiter = getOrCreateRateLimiter(shopName);
        double currentRate = rateLimiter.getRate();
        
        // 只有当速率变化超过 10% 时才更新，避免频繁调整
        if (Math.abs(newRate - currentRate) / currentRate > 0.1) {
            rateLimiter.setRate(newRate);
            AppInsightsUtils.trackTrace("更新速率限制，商店: %s, 当前可用: %d/%.0f, 恢复速率: %.0f, 新速率: %.2f, 旧速率: %.2f",
                shopName, currentlyAvailable, maximumAvailable, restoreRate, newRate, currentRate
            );
        }
    }
}

