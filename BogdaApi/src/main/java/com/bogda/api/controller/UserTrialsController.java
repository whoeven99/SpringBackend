package com.bogda.api.controller;

import com.bogda.service.logic.UserTrialsService;
import com.bogda.common.controller.response.BaseResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/userTrials")
public class UserTrialsController {
    @Autowired
    private UserTrialsService userTrialsService;

    /**
     * 查询是否开过免费计划。
     * */
    @PostMapping("/isOpenFreePlan")
    public BaseResponse<Object> isFreePlan(@RequestParam String shopName) {
        return  userTrialsService.queryUserTrialByShopName(shopName);
    }

    /**
     * 查询是否开启过免费试用弹窗
     * */
    @PostMapping("/isShowFreePlan")
    public BaseResponse<Object> isShowFreePlan(@RequestParam String shopName){
        return userTrialsService.isShowFreePlan(shopName);
    }

    /**
     * 判断是否是免费试用期间
     * */
    @PostMapping("/isInFreePlanTime")
    public BaseResponse<Object> isInFreePlanTime(@RequestParam String shopName){
        return userTrialsService.isInFreePlanTime(shopName);
    }
}
