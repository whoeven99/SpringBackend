package com.bogdatech.controller;

import com.bogdatech.Service.IUserIpService;
import com.bogdatech.logic.UserIpService;
import com.bogdatech.model.controller.response.BaseResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static com.bogdatech.utils.RetryUtils.retryWithParam;

@RestController
@RequestMapping("/userIp")
public class UserIpController {
    private final IUserIpService iUserIpService;

    private final UserIpService userIpService;

    @Autowired
    public UserIpController(IUserIpService userIpService, UserIpService userIpService1) {
        this.iUserIpService = userIpService;
        this.userIpService = userIpService1;
    }

    /**
     * 初始化额度UserIp表
     * */
    @PostMapping("/addOrUpdateUserIp")
    public BaseResponse<Object> addOrUpdateUserIp(@RequestParam String shopName) {
        boolean result = retryWithParam(
                iUserIpService::addOrUpdateUserIp,
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
    @PostMapping("/checkUserIp")
    public BaseResponse<Object> checkUserIp(@RequestParam String shopName) {
        Boolean b = userIpService.checkUserIp(shopName);
        if (b) {
            return new BaseResponse<>().CreateSuccessResponse(true);
        }
        return new BaseResponse<>().CreateErrorResponse(false);
    }

    // 查询剩余IP额度
    @PostMapping("/queryUserIpCount")
    public BaseResponse<Object> queryUserIpCount(@RequestParam String shopName) {
        return userIpService.queryUserIpCount(shopName);
    }
}
