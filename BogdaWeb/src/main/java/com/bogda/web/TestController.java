package com.bogda.web;

import com.alibaba.fastjson.JSONObject;
import com.bogda.integration.aimodel.RateHttpIntegration;
import com.bogda.service.Service.ITranslatesService;
import com.bogda.common.entity.DO.TranslatesDO;
import com.bogda.common.entity.VO.UserDataReportVO;
import com.bogda.integration.aimodel.GoogleMachineIntegration;
import com.bogda.integration.shopify.ShopifyHttpIntegration;
import com.bogda.service.logic.RedisDataReportService;
import com.bogda.service.logic.RedisProcessService;
import com.bogda.service.logic.redis.RateRedisService;
import com.bogda.service.logic.translate.TranslateV2Service;
import com.bogda.common.controller.request.CloudServiceRequest;
import com.bogda.common.controller.request.TranslateRequest;
import com.bogda.common.controller.response.BaseResponse;
import com.bogda.common.utils.ModuleCodeUtils;
import com.bogda.common.utils.AppInsightsUtils;
import kotlin.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class TestController {
    @Value("${test.key:default-test-key}")
    private String testKey;
    @Autowired
    private RedisProcessService redisProcessService;
    @Autowired
    private RedisDataReportService redisDataReportService;
    @Autowired
    private TranslateV2Service translateV2Service;
    @Autowired
    private ITranslatesService iTranslatesService;
    @Autowired
    private GoogleMachineIntegration googleMachineIntegration;
    @Autowired
    private ShopifyHttpIntegration shopifyHttpIntegration;
    @Autowired
    private RateHttpIntegration rateHttpIntegration;
    @Autowired
    private RateRedisService rateRedisService;

    @PostMapping("/test")
    public String test() {
        // 从当前激活 profile 对应的 application-*.properties 中读取 test.key
        // test 环境 -> application-test.properties
        // prod 环境 -> application-prod.properties
        return testKey;
    }

    @GetMapping("/ping")
    public String ping() {
        AppInsightsUtils.trackTrace("SpringBackend Ping Successful");
        return "Ping Successful!";
    }

    // 通过测试环境调shopify的API
    @PostMapping("/test123")
    public String test(@RequestBody CloudServiceRequest cloudServiceRequest) {
        String body = cloudServiceRequest.getBody();
        JSONObject infoByShopify = shopifyHttpIntegration.getInfoByShopify(cloudServiceRequest.getShopName(), cloudServiceRequest.getAccessToken(), body);
        if (infoByShopify == null || infoByShopify.isEmpty()) {
            return null;
        }
        return infoByShopify.toString();
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
        AppInsightsUtils.trackTrace(data);
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

    @GetMapping("/testAutoEmail")
    public void testAutoEmail(@RequestParam String shopName) {
        List<TranslatesDO> translatesDOList = iTranslatesService.listAutoTranslates(shopName);
        for (TranslatesDO translatesDO : translatesDOList) {
            translateV2Service.testAutoTranslate(shopName, translatesDO.getSource(), translatesDO.getTarget());
        }
    }

    // 手动获取rate
    @GetMapping("/testRate")
    public void testRate() {
        var rates = rateHttpIntegration.getFixerRate();
        rateRedisService.refreshRates(rates);
    }
}
