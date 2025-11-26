package com.bogdatech.controller;

import com.bogdatech.Service.IUserIpService;
import com.bogdatech.logic.UserIpService;
import com.bogdatech.model.controller.response.BaseResponse;
import com.bogdatech.repository.entity.UserIPRedirectionDO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.bogdatech.utils.RetryUtils.retryWithParam;

@RestController
@RequestMapping("/userIp")
public class UserIpController {
    @Autowired
    private IUserIpService iUserIpService;
    @Autowired
    private UserIpService userIpService;

    /**
     * 初始化额度UserIp表
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

    /**
     * 判断额度是否足够，如果足够，额度+1
     */
    @PostMapping("/checkUserIp")
    public BaseResponse<Object> checkUserIp(@RequestParam String shopName) {
        Boolean b = userIpService.checkUserIp(shopName);
        if (b) {
            return new BaseResponse<>().CreateSuccessResponse(true);
        }
        return new BaseResponse<>().CreateErrorResponse(false);
    }

    // 批量初始化数据
    @PostMapping("/batchAddUserIp")
    public BaseResponse<Object> batchAddUserIp(@RequestParam String shopName, @RequestBody List<UserIPRedirectionDO> userIPRedirectionDOList) {
        return userIpService.batchAddUserIp(shopName, userIPRedirectionDOList);
    }

    // 批量删除ip数据
    @PostMapping("/batchDeleteUserIp")
    public BaseResponse<Object> batchDeleteUserIp(@RequestParam String shopName, @RequestBody List<Integer> ids) {
        return userIpService.batchDeleteUserIp(shopName, ids);
    }

    // 数据更新接口
    @PostMapping("/updateUserIp")
    public BaseResponse<Object> updateUserIp(@RequestParam String shopName, @RequestBody UserIPRedirectionDO userIPRedirectionDO) {
        return userIpService.updateUserIp(shopName, userIPRedirectionDO);
    }

    // 单条状态更新接口
    @PostMapping("/updateUserIpStatus")
    public BaseResponse<Object> updateUserIpStatus(@RequestParam String shopName, @RequestParam Integer id, @RequestParam Boolean status) {
        return userIpService.updateUserIpStatus(id, status);
    }

    // 数据获取接口（应用内）
    @PostMapping("/selectUserIpList")
    public BaseResponse<Object> selectUserIpList(@RequestParam String shopName) {
        return userIpService.selectUserIpList(shopName);
    }

    // 数据获取接口（插件内）
    @PostMapping("/selectUserIpListByShopNameAndRegion")
    public BaseResponse<Object> selectUserIpListByShopNameAndRegion(@RequestParam String shopName, @RequestParam String region) {
        return userIpService.selectUserIpListByShopNameAndRegion(shopName, region);
    }

}
