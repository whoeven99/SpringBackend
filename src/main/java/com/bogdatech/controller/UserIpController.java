package com.bogdatech.controller;

import com.bogdatech.Service.IUserIpService;
import com.bogdatech.model.controller.response.BaseResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.bogdatech.utils.RetryUtils.retryWithParam;

@RestController
@RequestMapping("/userIp")
public class UserIpController {
    private final IUserIpService userIpService;

    @Autowired
    public UserIpController(IUserIpService userIpService) {
        this.userIpService = userIpService;
    }

    /**
     * 初始化额度UserIp表
     * */
    @PostMapping("/addOrUpdateUserIp")
    public BaseResponse<Object> addOrUpdateUserIp(String shopName) {
        boolean result = retryWithParam(
                userIpService::addOrUpdateUserIp,
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
     * 判断额度是否足够，如果足够，额度+1
     * */
//    @PostMapping("/checkUserIp")
//    public BaseResponse<Object> checkUserIp(String shopName) {
//        userIpService.checkUserIp(shopName);
//    }
}
