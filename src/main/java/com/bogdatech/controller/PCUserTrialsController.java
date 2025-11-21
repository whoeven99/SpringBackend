package com.bogdatech.controller;

import com.bogdatech.logic.PCApp.PCUserTrialsService;
import com.bogdatech.model.controller.response.BaseResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static com.bogdatech.utils.RetryUtils.retryWithParam;

@RestController
@RequestMapping("/pc/userTrials")
public class PCUserTrialsController {
    @Autowired
    private PCUserTrialsService pcUserTrialsService;

    /**
     * 开启免费订阅
     * */
    @PostMapping("/startFreePlan")
    public BaseResponse<Object> startFreePlan(@RequestParam String shopName){
        boolean result = retryWithParam(
                pcUserTrialsService::insertUserTrial,
                shopName,
                3,
                1000,
                8000
        );
        if (result){
            return new BaseResponse<>().CreateSuccessResponse(shopName);
        }
        return new BaseResponse<>().CreateErrorResponse(shopName);
    }

    /**
     * 查询是否开过免费计划。
     * */
    @PostMapping("/isOpenFreePlan")
    public BaseResponse<Object> isFreePlan(@RequestParam String shopName) {
        return  pcUserTrialsService.queryUserTrialByShopName(shopName);
    }

    /**
     * 查询是否开启过免费试用弹窗
     * */
    @PostMapping("/isShowFreePlan")
    public BaseResponse<Object> isShowFreePlan(@RequestParam String shopName){
        return pcUserTrialsService.isShowFreePlan(shopName);
    }

    /**
     * 判断是否是免费试用期间
     * */
    @PostMapping("/isInFreePlanTime")
    public BaseResponse<Object> isInFreePlanTime(@RequestParam String shopName){
        return pcUserTrialsService.isInFreePlanTime(shopName);
    }}
