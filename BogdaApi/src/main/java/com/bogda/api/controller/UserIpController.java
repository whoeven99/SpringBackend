package com.bogda.api.controller;

import com.bogda.service.Service.IUserIpService;
import com.bogda.common.controller.response.BaseResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import static com.bogda.common.utils.RetryUtils.retryWithParam;

@RestController
@RequestMapping("/userIp")
public class UserIpController {
    @Autowired
    private IUserIpService iUserIpService;

    /**
     * 初始化 UserIp 表记录（开启 IP 定位时由 Admin 调用）
     */
    @PostMapping("/addOrUpdateUserIp")
    public BaseResponse<Object> addOrUpdateUserIp(@RequestParam String shopName) {
        boolean result = retryWithParam(
                iUserIpService::addOrUpdateUserIp,
                shopName,
                3,
                1000,
                8000
        );
        if (result) {
            return new BaseResponse<>().CreateSuccessResponse(shopName);
        }
        return new BaseResponse<>().CreateErrorResponse(shopName);
    }
}
