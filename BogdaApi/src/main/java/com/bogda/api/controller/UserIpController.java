package com.bogda.api.controller;

import com.bogda.api.Service.IUserIpService;
import com.bogda.api.entity.VO.IncludeCrawlerVO;
import com.bogda.api.entity.VO.NoCrawlerVO;
import com.bogda.api.logic.UserIpService;
import com.bogda.api.model.controller.response.BaseResponse;
import com.bogda.repository.entity.UserIPRedirectionDO;
import com.bogda.common.utils.AppInsightsUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.bogda.common.utils.RetryUtils.retryWithParam;

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

        // 获取ip跳转表数据
        List<UserIPRedirectionDO> userIPRedirectionDOS = userIpService.selectAllIpRedirectionByShopName(shopName);

        if (b) {
            return new BaseResponse<>().CreateSuccessResponse(UserIpService.ipReturn(userIPRedirectionDOS));
        }
        return new BaseResponse<>().CreateErrorResponse(false);
    }

    // 含爬虫打印日志
    @PostMapping("/includeCrawlerPrintLog")
    public BaseResponse<Object> includeCrawlerPrintLog(@RequestParam String shopName, @RequestBody IncludeCrawlerVO includeCrawlerVO) {
       return userIpService.includeCrawlerPrintLog(shopName, includeCrawlerVO);
    }

    // 不含爬虫打印日志
    @PostMapping("/noCrawlerPrintLog")
    public BaseResponse<Object> noCrawlerPrintLog(@RequestParam String shopName, @RequestBody NoCrawlerVO noCrawlerVO) {
        return userIpService.noCrawlerPrintLog(shopName, noCrawlerVO);
    }

    // 批量初始化数据
    @PostMapping("/syncUserIp")
    public BaseResponse<Object> syncUserIp(@RequestParam String shopName, @RequestBody List<UserIPRedirectionDO> userIPRedirectionDOList) {
        try {
            return userIpService.syncUserIp(shopName, userIPRedirectionDOList);
        } catch (Exception e) {
           AppInsightsUtils.trackTrace("FatalException syncUserIp error" + e.getMessage());
        }
        return new BaseResponse<>().CreateErrorResponse("syncUserIp error");
    }

    // 数据更新接口
    @PostMapping("/updateUserIp")
    public BaseResponse<Object> updateUserIp(@RequestParam String shopName, @RequestBody UserIPRedirectionDO userIPRedirectionDO) {
        return userIpService.updateUserIp(shopName, userIPRedirectionDO);
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

    // 查询剩余IP额度
    @PostMapping("/queryUserIpCount")
    public BaseResponse<Object> queryUserIpCount(@RequestParam String shopName) {
        return userIpService.queryUserIpCount(shopName);
    }
}
