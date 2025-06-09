package com.bogdatech.controller;

import com.bogdatech.logic.UserTrialsService;
import com.bogdatech.model.controller.response.BaseResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.bogdatech.utils.RetryUtils.retryWithParam;

@RestController
@RequestMapping("/userTrials")
public class UserTrialsController {
    private final UserTrialsService userTrialsService;

    @Autowired
    public UserTrialsController(UserTrialsService userTrialsService) {
        this.userTrialsService = userTrialsService;
    }

    /**
     * 开启免费订阅
     * */
    @PostMapping("/startFreePlan")
    public BaseResponse<Object> startFreePlan(String shopName){
        boolean result = retryWithParam(
                userTrialsService::insertUserTrial,
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
    public BaseResponse<Object> isFreePlan(String shopName){
        boolean result = retryWithParam(
                userTrialsService::queryUserTrialByShopName,
                shopName,
                3,
                1000,
                8000
        );
        if (result){
            return new BaseResponse<>().CreateSuccessResponse(true);
        }
        return new BaseResponse<>().CreateErrorResponse(false);
    }

}
