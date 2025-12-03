package com.bogdatech.controller;

import com.bogdatech.Service.IUserIpService;
import com.bogdatech.entity.VO.IncludeCrawlerVO;
import com.bogdatech.entity.VO.NoCrawlerVO;
import com.bogdatech.logic.UserIpService;
import com.bogdatech.model.controller.response.BaseResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

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

    // 查询剩余IP额度
    @PostMapping("/queryUserIpCount")
    public BaseResponse<Object> queryUserIpCount(@RequestParam String shopName) {
        return userIpService.queryUserIpCount(shopName);
    }
}
