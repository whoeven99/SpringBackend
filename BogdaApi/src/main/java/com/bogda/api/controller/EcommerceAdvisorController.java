package com.bogda.api.controller;

import com.bogda.api.entity.DO.EcommerceAdviceDO;
import com.bogda.api.logic.agent.EcommerceAdvisorAgent;
import com.bogda.api.logic.agent.Feedback;
import com.bogda.api.model.controller.response.BaseResponse;
import com.bogda.common.utils.AppInsightsUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 电商建议 AI Agent 控制器
 */
@RestController
@RequestMapping("/api/agent/ecommerce")
public class EcommerceAdvisorController {
    
    @Autowired
    private EcommerceAdvisorAgent agent;
    
    /**
     * 生成电商建议
     */
    @PostMapping("/advice/generate")
    public BaseResponse<List<EcommerceAdviceDO>> generateAdvice(
            @RequestParam String shopName,
            @RequestParam String accessToken) {
        try {
            List<EcommerceAdviceDO> advice = agent.generateAdvice(shopName, accessToken);
            return BaseResponse.SuccessResponse(advice);
        } catch (Exception e) {
            AppInsightsUtils.trackException(e);
            return new BaseResponse<>().CreateErrorResponse("生成建议失败：" + e.getMessage());
        }
    }
    
    /**
     * 提交反馈
     */
    @PostMapping("/feedback")
    public BaseResponse<Void> submitFeedback(@RequestBody Feedback feedback) {
        try {
            agent.processFeedback(feedback);
            return BaseResponse.SuccessResponse(null);
        } catch (Exception e) {
            AppInsightsUtils.trackException(e);
            return new BaseResponse<>().CreateErrorResponse("提交反馈失败：" + e.getMessage());
        }
    }
    
    /**
     * 获取建议历史
     */
    @GetMapping("/advice/history")
    public BaseResponse<List<EcommerceAdviceDO>> getAdviceHistory(
            @RequestParam String shopName) {
        try {
            List<EcommerceAdviceDO> history = agent.getAdviceHistory(shopName);
            return BaseResponse.SuccessResponse(history);
        } catch (Exception e) {
            AppInsightsUtils.trackException(e);
            return new BaseResponse<>().CreateErrorResponse("获取历史建议失败：" + e.getMessage());
        }
    }
    
    /**
     * 获取学习状态
     */
    @GetMapping("/learning/status")
    public BaseResponse<Map<String, Map<String, Double>>> getLearningStatus() {
        try {
            Map<String, Map<String, Double>> status = agent.getLearningStatus();
            return BaseResponse.SuccessResponse(status);
        } catch (Exception e) {
            AppInsightsUtils.trackException(e);
            return new BaseResponse<>().CreateErrorResponse("获取学习状态失败：" + e.getMessage());
        }
    }
}
