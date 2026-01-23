package com.bogda.api.controller;

import com.bogda.service.logic.DataRatingService;
import com.bogda.common.controller.response.BaseResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static com.bogda.common.utils.ModuleCodeUtils.getLanguageName;

@RestController
@RequestMapping("/rating")
public class DataRatingController {
    @Autowired
    private DataRatingService dataRatingService;

    //查询三表的是否开启
    @PostMapping("/getDBConfiguration")
    public BaseResponse<Object> queryDBConfiguration(@RequestParam String shopName) {
        Map<String, Boolean> stringBooleanMap = dataRatingService.queryDBConfiguration(shopName);
        if (stringBooleanMap.isEmpty()) {
            return new BaseResponse<>().CreateErrorResponse(false);
        }
        return new BaseResponse<>().CreateSuccessResponse(stringBooleanMap);

    }

    /**
     * 获取用户设置的语言的翻译状态与db的状态的统计
     */
    @PostMapping("/getTranslationStatus")
    public BaseResponse<Object> getTranslationStatus(@RequestParam String shopName, @RequestParam String source) {
        Map<String, Integer> translationStatus = dataRatingService.getTranslationStatus(shopName, source);
        if (translationStatus == null || translationStatus.isEmpty()) {
            return new BaseResponse<>().CreateErrorResponse(false);
        }
        translationStatus.remove(getLanguageName(source));
        return new BaseResponse<>().CreateSuccessResponse(translationStatus);
    }

    //查询评分信息 语言60% 工具40%
    @PostMapping("/getRatingInfo")
    public BaseResponse<Object> getSubscriptionInfo(@RequestParam String shopName, @RequestParam String source) {
        Double ratingInfo = dataRatingService.getRatingInfo(shopName, source);
        if (ratingInfo == null) {
            return new BaseResponse<>().CreateErrorResponse(false);
        }
        return new BaseResponse<>().CreateSuccessResponse(ratingInfo);
    }
}
