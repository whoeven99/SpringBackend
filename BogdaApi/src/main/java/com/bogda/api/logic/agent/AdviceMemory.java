package com.bogda.api.logic.agent;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bogda.api.entity.DO.EcommerceAdviceDO;
import com.bogda.repository.entity.AdviceFeedbackDO;
import com.bogda.repository.entity.EcommerceAdviceDO as RepoEcommerceAdviceDO;
import com.bogda.repository.mapper.AdviceFeedbackMapper;
import com.bogda.repository.mapper.EcommerceAdviceMapper;
import com.bogda.common.utils.AppInsightsUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 建议存储器：管理建议和反馈的数据库操作
 */
@Component
public class AdviceMemory {
    
    @Autowired
    private EcommerceAdviceMapper ecommerceAdviceMapper;
    @Autowired
    private AdviceFeedbackMapper adviceFeedbackMapper;
    
    public void saveAdvice(EcommerceAdviceDO advice) {
        RepoEcommerceAdviceDO repoAdvice = new RepoEcommerceAdviceDO();
        BeanUtils.copyProperties(advice, repoAdvice);
        if (repoAdvice.getCreatedAt() == null) {
            repoAdvice.setCreatedAt(LocalDateTime.now());
        }
        if (repoAdvice.getUpdatedAt() == null) {
            repoAdvice.setUpdatedAt(LocalDateTime.now());
        }
        ecommerceAdviceMapper.insert(repoAdvice);
        advice.setId(repoAdvice.getId());
        AppInsightsUtils.trackTrace("Saved advice: " + advice.getId());
    }
    
    public void updateAdvice(EcommerceAdviceDO advice) {
        RepoEcommerceAdviceDO repoAdvice = new RepoEcommerceAdviceDO();
        BeanUtils.copyProperties(advice, repoAdvice);
        repoAdvice.setUpdatedAt(LocalDateTime.now());
        ecommerceAdviceMapper.updateById(repoAdvice);
        AppInsightsUtils.trackTrace("Updated advice: " + advice.getId());
    }
    
    public EcommerceAdviceDO getAdvice(Long id) {
        RepoEcommerceAdviceDO repoAdvice = ecommerceAdviceMapper.selectById(id);
        if (repoAdvice == null) {
            return null;
        }
        EcommerceAdviceDO advice = new EcommerceAdviceDO();
        BeanUtils.copyProperties(repoAdvice, advice);
        return advice;
    }
    
    public List<EcommerceAdviceDO> getAdviceByShopName(String shopName) {
        List<RepoEcommerceAdviceDO> repoAdviceList = ecommerceAdviceMapper.selectList(
            new LambdaQueryWrapper<RepoEcommerceAdviceDO>()
                .eq(RepoEcommerceAdviceDO::getShopName, shopName)
                .orderByDesc(RepoEcommerceAdviceDO::getCreatedAt)
                .last("LIMIT 50")
        );
        
        List<EcommerceAdviceDO> result = new ArrayList<>();
        for (RepoEcommerceAdviceDO repoAdvice : repoAdviceList) {
            EcommerceAdviceDO advice = new EcommerceAdviceDO();
            BeanUtils.copyProperties(repoAdvice, advice);
            result.add(advice);
        }
        return result;
    }
    
    public void saveFeedback(Feedback feedback) {
        AdviceFeedbackDO repoFeedback = new AdviceFeedbackDO();
        repoFeedback.setAdviceId(feedback.getAdviceId());
        repoFeedback.setShopName(feedback.getShopName());
        repoFeedback.setFeedbackType(feedback.getFeedbackType());
        repoFeedback.setRating(feedback.getRating());
        repoFeedback.setNotes(feedback.getNotes());
        repoFeedback.setFeedbackAt(LocalDateTime.now());
        adviceFeedbackMapper.insert(repoFeedback);
        AppInsightsUtils.trackTrace("Saved feedback for advice: " + feedback.getAdviceId());
    }
}
