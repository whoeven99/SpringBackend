package com.bogda.api.logic.agent;

import com.bogda.api.entity.DO.EcommerceAdviceDO;
import com.bogda.api.entity.DO.TranslatesDO;
import com.bogda.api.integration.GeminiIntegration;
import com.bogda.api.logic.ShopifyService;
import com.bogda.common.utils.AppInsightsUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 翻译 ROI 分析 Skill：
 * 分析哪些产品、哪些语言优先翻译能带来最大收益
 */
@Component
public class TranslationROISkill implements EcommerceSkill {
    
    @Autowired
    private ShopifyService shopifyService;
    @Autowired
    private StrategyOptimizer strategyOptimizer;
    @Autowired
    private GeminiIntegration geminiIntegration;
    
    private static final String SKILL_TYPE = "TRANSLATION_ROI";
    
    @Override
    public String getSkillType() {
        return SKILL_TYPE;
    }
    
    @Override
    public List<EcommerceAdviceDO> generateAdvice(ShopContext context) {
        List<EcommerceAdviceDO> adviceList = new ArrayList<>();
        
        String bestStrategy = strategyOptimizer.selectBestStrategy(SKILL_TYPE, context.getShopName());
        
        switch (bestStrategy) {
            case "POPULAR_PRODUCTS_FIRST":
                adviceList.addAll(generatePopularProductsAdvice(context));
                break;
            case "HIGH_VALUE_LANGUAGES":
                adviceList.addAll(generateHighValueLanguagesAdvice(context));
                break;
            case "MIXED_STRATEGY":
                adviceList.addAll(generateMixedStrategyAdvice(context));
                break;
            default:
                adviceList.addAll(generatePopularProductsAdvice(context));
        }
        
        return adviceList;
    }
    
    @Override
    public void learnFromFeedback(EcommerceAdviceDO advice, Feedback feedback) {
        double reward = calculateReward(feedback);
        String strategyUsed = extractStrategyFromAdvice(advice);
        strategyOptimizer.updateStrategyPerformance(SKILL_TYPE, strategyUsed, reward);
        AppInsightsUtils.trackTrace("TranslationROISkill learned from feedback: " + 
            strategyUsed + " -> reward=" + reward);
    }
    
    @Override
    public Map<String, Double> getStrategyWeights() {
        return strategyOptimizer.getStrategyWeights(SKILL_TYPE);
    }
    
    private List<EcommerceAdviceDO> generatePopularProductsAdvice(ShopContext context) {
        List<EcommerceAdviceDO> adviceList = new ArrayList<>();
        
        if (context.getProducts() != null) {
            List<ShopContext.ProductInfo> untranslatedProducts = context.getProducts().stream()
                .filter(p -> !p.isTranslated())
                .limit(5)
                .toList();
            
            for (ShopContext.ProductInfo product : untranslatedProducts) {
                EcommerceAdviceDO advice = new EcommerceAdviceDO();
                advice.setShopName(context.getShopName());
                advice.setAdviceType("PRODUCT_TRANSLATION_PRIORITY");
                advice.setTargetResourceId(product.getId());
                advice.setTargetResourceType("PRODUCT");
                advice.setStatus("PENDING");
                advice.setCreatedAt(LocalDateTime.now());
                
                String prompt = generateAdvicePrompt(product, "POPULAR_PRODUCTS_FIRST");
                advice.setAdviceContent(callLLM(prompt));
                
                adviceList.add(advice);
            }
        }
        
        return adviceList;
    }
    
    private List<EcommerceAdviceDO> generateHighValueLanguagesAdvice(ShopContext context) {
        List<EcommerceAdviceDO> adviceList = new ArrayList<>();
        
        if (context.getLanguages() != null) {
            List<ShopContext.LanguageInfo> nonPrimaryLanguages = context.getLanguages().stream()
                .filter(l -> !l.isPrimary())
                .limit(3)
                .toList();
            
            for (ShopContext.LanguageInfo language : nonPrimaryLanguages) {
                EcommerceAdviceDO advice = new EcommerceAdviceDO();
                advice.setShopName(context.getShopName());
                advice.setAdviceType("LANGUAGE_TRANSLATION_PRIORITY");
                advice.setTargetResourceId(language.getCode());
                advice.setTargetResourceType("LANGUAGE");
                advice.setStatus("PENDING");
                advice.setCreatedAt(LocalDateTime.now());
                
                String prompt = generateLanguageAdvicePrompt(language, "HIGH_VALUE_LANGUAGES");
                advice.setAdviceContent(callLLM(prompt));
                
                adviceList.add(advice);
            }
        }
        
        return adviceList;
    }
    
    private List<EcommerceAdviceDO> generateMixedStrategyAdvice(ShopContext context) {
        List<EcommerceAdviceDO> adviceList = new ArrayList<>();
        adviceList.addAll(generatePopularProductsAdvice(context));
        adviceList.addAll(generateHighValueLanguagesAdvice(context));
        return adviceList;
    }
    
    private String generateAdvicePrompt(ShopContext.ProductInfo product, String strategy) {
        return """
            请为这个 Shopify 产品生成翻译优先级建议：
            
            产品 ID：%s
            产品标题：%s
            策略类型：%s
            
            请用中文回答，格式如下：
            【翻译优先级建议】
            优先级：高/中/低
            建议翻译语言：列出建议优先翻译的语言
            理由：为什么建议优先翻译这个产品
            """.formatted(product.getId(), product.getTitle(), strategy);
    }
    
    private String generateLanguageAdvicePrompt(ShopContext.LanguageInfo language, String strategy) {
        return """
            请为这个语言生成翻译优先级建议：
            
            语言代码：%s
            语言名称：%s
            策略类型：%s
            
            请用中文回答，格式如下：
            【翻译优先级建议】
            优先级：高/中/低
            建议翻译资源：列出建议优先翻译的资源类型（产品、页面、主题等）
            理由：为什么建议优先翻译这个语言
            """.formatted(language.getCode(), language.getName(), strategy);
    }
    
    private String callLLM(String prompt) {
        try {
            String response = geminiIntegration.generateText(GeminiIntegration.GEMINI_3_FLASH, prompt);
            return response != null ? response : "建议生成失败，请稍后重试";
        } catch (Exception e) {
            AppInsightsUtils.trackException(e);
            return "建议生成失败：" + e.getMessage();
        }
    }
    
    private double calculateReward(Feedback feedback) {
        if (feedback.getRating() != null) {
            return feedback.getRating() / 5.0;
        }
        if ("ACCEPTED".equals(feedback.getFeedbackType())) {
            return 1.0;
        }
        if ("REJECTED".equals(feedback.getFeedbackType())) {
            return 0.0;
        }
        return 0.5;
    }
    
    private String extractStrategyFromAdvice(EcommerceAdviceDO advice) {
        if (advice.getAdviceContent() != null && advice.getAdviceContent().contains("策略类型")) {
            if (advice.getAdviceContent().contains("POPULAR_PRODUCTS_FIRST")) return "POPULAR_PRODUCTS_FIRST";
            if (advice.getAdviceContent().contains("HIGH_VALUE_LANGUAGES")) return "HIGH_VALUE_LANGUAGES";
            if (advice.getAdviceContent().contains("MIXED_STRATEGY")) return "MIXED_STRATEGY";
        }
        return "POPULAR_PRODUCTS_FIRST";
    }
}
