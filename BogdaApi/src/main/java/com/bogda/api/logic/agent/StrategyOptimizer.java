package com.bogda.api.logic.agent;

import com.bogda.common.utils.AppInsightsUtils;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 策略优化器：使用多臂老虎机算法平衡探索与利用
 */
@Component
public class StrategyOptimizer {
    private final Map<String, Map<String, Double>> strategyWeights = new HashMap<>();
    private final Map<String, Map<String, Integer>> strategyAttempts = new HashMap<>();
    private static final double EPSILON_DECAY = 0.99;
    private static final double MIN_EPSILON = 0.05;
    
    public String selectBestStrategy(String skillType, String shopName) {
        Map<String, Double> weights = strategyWeights.computeIfAbsent(skillType, k -> new HashMap<>());
        Map<String, Integer> attempts = strategyAttempts.computeIfAbsent(skillType, k -> new HashMap<>());
        
        double epsilon = calculateEpsilon(skillType);
        
        if (Math.random() < epsilon) {
            return selectRandomStrategy(skillType);
        }
        
        return weights.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse("DEFAULT");
    }
    
    public void updateStrategyPerformance(String skillType, String strategy, double reward) {
        Map<String, Double> weights = strategyWeights.computeIfAbsent(skillType, k -> new HashMap<>());
        Map<String, Integer> attempts = strategyAttempts.computeIfAbsent(skillType, k -> new HashMap<>());
        
        double oldWeight = weights.getOrDefault(strategy, 0.5);
        int attemptCount = attempts.getOrDefault(strategy, 0);
        
        double newWeight = oldWeight + (reward - oldWeight) / (attemptCount + 1);
        
        weights.put(strategy, newWeight);
        attempts.put(strategy, attemptCount + 1);
        
        AppInsightsUtils.trackTrace("StrategyOptimizer updated: " + skillType + " - " + strategy + 
            " -> " + newWeight + " (attempt: " + (attemptCount + 1) + ")");
    }
    
    public Map<String, Double> getStrategyWeights(String skillType) {
        return new HashMap<>(strategyWeights.getOrDefault(skillType, new HashMap<>()));
    }
    
    private double calculateEpsilon(String skillType) {
        Map<String, Integer> attempts = strategyAttempts.getOrDefault(skillType, new HashMap<>());
        int totalAttempts = attempts.values().stream().mapToInt(Integer::intValue).sum();
        double epsilon = Math.max(MIN_EPSILON, 1.0 / Math.sqrt(totalAttempts + 1));
        return epsilon;
    }
    
    private String selectRandomStrategy(String skillType) {
        Map<String, Double> weights = strategyWeights.getOrDefault(skillType, new HashMap<>());
        List<String> strategies = List.copyOf(weights.keySet());
        if (strategies.isEmpty()) {
            strategies = List.of("DEFAULT");
        }
        return strategies.get((int) (Math.random() * strategies.size()));
    }
}
