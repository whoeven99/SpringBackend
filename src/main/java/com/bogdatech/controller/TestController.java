package com.bogdatech.controller;

import com.bogdatech.entity.VO.GptVO;
import com.bogdatech.entity.VO.UserDataReportVO;
import com.bogdatech.integration.ShopifyHttpIntegration;
import com.bogdatech.logic.RedisDataReportService;
import com.bogdatech.logic.RedisProcessService;
import com.bogdatech.model.controller.request.CloudServiceRequest;
import com.bogdatech.model.controller.request.ShopifyRequest;
import com.bogdatech.model.controller.response.BaseResponse;
import com.bogdatech.task.IpEmailTask;
import com.microsoft.applicationinsights.TelemetryClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import static com.bogdatech.constants.TranslateConstants.API_VERSION_LAST;
import static com.bogdatech.utils.CaseSensitiveUtils.appInsights;

@RestController
public class TestController {
    @Autowired
    private RedisProcessService redisProcessService;
    @Autowired
    private RedisDataReportService redisDataReportService;
    @Autowired
    private IpEmailTask ipEmailTask;

    @GetMapping("/ping")
    public String ping() {
        TelemetryClient appInsights = new TelemetryClient();
        appInsights.trackTrace("SpringBackend Ping Successful");
        return "Ping Successful!";
    }

    @PostMapping("/gpt")
    public String chat(@RequestBody GptVO gptVO) {
//        return chatGptIntegration.chatWithGpt(gptVO.getPrompt(), gptVO.getSourceText(), "ciwishop.myshopify.com", null, new CharacterCountUtils(), 2000000, false);
        return "";
    }

    @Autowired
    private ShopifyHttpIntegration shopifyHttpIntegration;

    // 通过测试环境调shopify的API
    @PostMapping("/test123")
    public String test(@RequestBody CloudServiceRequest cloudServiceRequest) {
        ShopifyRequest request = new ShopifyRequest();
        request.setShopName(cloudServiceRequest.getShopName());
        request.setAccessToken(cloudServiceRequest.getAccessToken());
        request.setTarget(cloudServiceRequest.getTarget());
        String body = cloudServiceRequest.getBody();
        return shopifyHttpIntegration.getInfoByShopify(cloudServiceRequest.getShopName(),
                cloudServiceRequest.getAccessToken(), API_VERSION_LAST, body);
    }

    //测试获取缓存功能
    @GetMapping("/testCache")
    public String testCache(@RequestParam String target, @RequestParam String value) {
        return redisProcessService.getCacheData(target, value);
    }

    @GetMapping("/testAddCache")
    public void testAddCache(String target, String value, String targetText) {
        redisProcessService.setCacheData(target, targetText, value);
    }

    /**
     * 单纯的打印信息
     */
    @PostMapping("/frontEndPrinting")
    public void frontEndPrinting(@RequestBody String data) {
        appInsights.trackTrace(data);
    }

    /**
     * 数据上传
     */
    @PostMapping("/saveUserDataReport")
    public void userDataReport(@RequestParam String shopName, @RequestBody UserDataReportVO userDataReportVO) {
        redisDataReportService.saveUserDataReport(shopName, userDataReportVO);
    }

    /**
     * 读取相关数据
     */
    @PostMapping("/getUserDataReport")
    public BaseResponse<Object> getUserDataReport(@RequestParam String shopName, @RequestBody UserDataReportVO userDataReportVO) {
        String userDataReport = redisDataReportService.getUserDataReport(shopName, userDataReportVO.getTimestamp(), userDataReportVO.getDayData());
        if (userDataReport != null) {
            return new BaseResponse<>().CreateSuccessResponse(userDataReport);
        }
        return new BaseResponse<>().CreateErrorResponse(false);
    }

    @GetMapping("/testEmail")
    public void testEmail() {
        ipEmailTask.sendEmailTask();
    }
}
