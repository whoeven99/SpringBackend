package com.bogda.api.logic.agent;

import com.bogda.api.entity.DO.EcommerceAdviceDO;

import java.util.List;
import java.util.Map;

public interface EcommerceSkill {
    String getSkillType();
    List<EcommerceAdviceDO> generateAdvice(ShopContext context);
    void learnFromFeedback(EcommerceAdviceDO advice, Feedback feedback);
    Map<String, Double> getStrategyWeights();
}
