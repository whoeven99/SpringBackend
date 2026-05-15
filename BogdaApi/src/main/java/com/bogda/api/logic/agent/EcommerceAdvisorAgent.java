package com.bogda.api.logic.agent;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bogda.api.entity.DO.EcommerceAdviceDO;
import com.bogda.api.logic.ShopifyService;
import com.bogda.common.utils.AppInsightsUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 电商建议 AI Agent：
 * 协调多个 Skill 生成建议，并处理反馈进行自我学习
 */
@Service
public class EcommerceAdvisorAgent {
    
    @Autowired
    private List<EcommerceSkill> skills;
    @Autowired
    private ShopifyService shopifyService;
    @Autowired
    private AdviceMemory adviceMemory;
    
    /**
     * 为商家生成建议
     */
    public List<EcommerceAdviceDO> generateAdvice(String shopName, String accessToken) {
        AppInsightsUtils.trackTrace("EcommerceAdvisorAgent generating advice for: " + shopName);
        
        ShopContext context = collectShopContext(shopName, accessToken);
        
        List<EcommerceAdviceDO> allAdvice = new ArrayList<>();
        
        for (EcommerceSkill skill : skills) {
            try {
                List<EcommerceAdviceDO> skillAdvice = skill.generateAdvice(context);
                for (EcommerceAdviceDO advice : skillAdvice) {
                    adviceMemory.saveAdvice(advice);
                }
                allAdvice.addAll(skillAdvice);
                AppInsightsUtils.trackTrace("Skill " + skill.getSkillType() + 
                    " generated " + skillAdvice.size() + " advice");
            } catch (Exception e) {
                AppInsightsUtils.trackException(e);
                AppInsightsUtils.trackTrace("Failed to generate advice from skill: " + 
                    skill.getSkillType());
            }
        }
        
        return allAdvice;
    }
    
    /**
     * 处理商家反馈
     */
    public void processFeedback(Feedback feedback) {
        AppInsightsUtils.trackTrace("Processing feedback for advice: " + feedback.getAdviceId());
        
        EcommerceAdviceDO advice = adviceMemory.getAdvice(feedback.getAdviceId());
        if (advice == null) {
            AppInsightsUtils.trackTrace("Advice not found: " + feedback.getAdviceId());
            return;
        }
        
        adviceMemory.saveFeedback(feedback);
        
        // 更新建议状态
        advice.setStatus(feedback.getFeedbackType());
        advice.setUpdatedAt(LocalDateTime.now());
        adviceMemory.updateAdvice(advice);
        
        // 让相关 Skill 学习
        for (EcommerceSkill skill : skills) {
            if (advice.getAdviceType().contains(skill.getSkillType().replace("_", "")) ||
                skill.getSkillType().startsWith(advice.getAdviceType())) {
                skill.learnFromFeedback(advice, feedback);
            }
        }
    }
    
    /**
     * 获取商家的历史建议
     */
    public List<EcommerceAdviceDO> getAdviceHistory(String shopName) {
        return adviceMemory.getAdviceByShopName(shopName);
    }
    
    /**
     * 获取策略学习状态
     */
    public Map<String, Map<String, Double>> getLearningStatus() {
        Map<String, Map<String, Double>> status = new java.util.HashMap<>();
        for (EcommerceSkill skill : skills) {
            status.put(skill.getSkillType(), skill.getStrategyWeights());
        }
        return status;
    }
    
    /**
     * 收集店铺上下文信息
     */
    private ShopContext collectShopContext(String shopName, String accessToken) {
        ShopContext context = new ShopContext();
        context.setShopName(shopName);
        context.setAccessToken(accessToken);
        
        List<ShopContext.ProductInfo> products = new ArrayList<>();
        ShopContext.ProductInfo sampleProduct = new ShopContext.ProductInfo();
        sampleProduct.setId("gid://shopify/Product/1");
        sampleProduct.setTitle("示例产品");
        sampleProduct.setDescription("这是一个示例产品的描述");
        sampleProduct.setTranslated(false);
        products.add(sampleProduct);
        context.setProducts(products);
        
        List<ShopContext.LanguageInfo> languages = new ArrayList<>();
        ShopContext.LanguageInfo enLang = new ShopContext.LanguageInfo();
        enLang.setCode("en");
        enLang.setName("English");
        enLang.setPrimary(true);
        languages.add(enLang);
        
        ShopContext.LanguageInfo esLang = new ShopContext.LanguageInfo();
        esLang.setCode("es");
        esLang.setName("Español");
        esLang.setPrimary(false);
        languages.add(esLang);
        
        context.setLanguages(languages);
        
        return context;
    }
}
