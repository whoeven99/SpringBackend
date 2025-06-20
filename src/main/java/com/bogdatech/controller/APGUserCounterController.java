package com.bogdatech.controller;

import com.bogdatech.Service.IAPGUserCounterService;
import com.bogdatech.entity.DO.APGUserCounterDO;
import com.bogdatech.model.controller.response.BaseResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import static com.bogdatech.utils.RetryUtils.retryWithParam;

@RestController
@RequestMapping("/apg/userCounter")
public class APGUserCounterController {
    private final IAPGUserCounterService iapgUserCounterService;

    @Autowired
    public APGUserCounterController(IAPGUserCounterService iapgUserCounterService) {
        this.iapgUserCounterService = iapgUserCounterService;
    }

    /**
     * 用户计数器初始化
     * */
    @GetMapping("/initUserCounter")
    public BaseResponse<Object> initUserCounter(@RequestParam String shopName){
        boolean result = retryWithParam(
                iapgUserCounterService::initUserCounter,
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
     * 获取用户计数器信息，4项数据
     * */
    @PostMapping("/getUserCounter")
    public BaseResponse<Object> getUserCounter(@RequestParam String shopName){
        APGUserCounterDO userCounter = iapgUserCounterService.getUserCounter(shopName);
        if (userCounter != null){
            return new BaseResponse<>().CreateSuccessResponse(userCounter);
        }
        return new BaseResponse<>().CreateErrorResponse(shopName);
    }
}
