package com.bogda.web.controller;

import com.alibaba.fastjson.JSONObject;
import com.bogda.service.Service.ITranslatesService;
import com.bogda.service.entity.DO.TranslatesDO;
import com.bogda.service.entity.VO.UserDataReportVO;
import com.bogda.integration.aimodel.GoogleMachineIntegration;
import com.bogda.service.integration.ShopifyHttpIntegration;
import com.bogda.service.logic.RedisDataReportService;
import com.bogda.service.logic.RedisProcessService;
import com.bogda.service.logic.translate.TranslateV2Service;
import com.bogda.service.controller.request.CloudServiceRequest;
import com.bogda.service.controller.request.TranslateRequest;
import com.bogda.service.controller.response.BaseResponse;
import com.bogda.service.task.IpEmailTask;
import com.bogda.common.utils.ModuleCodeUtils;
import com.bogda.common.utils.AppInsightsUtils;
import kotlin.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
public class TestController {
    @Autowired
    private RedisProcessService redisProcessService;
    @Autowired
    private RedisDataReportService redisDataReportService;
    @Autowired
    private IpEmailTask ipEmailTask;
    @Autowired
    private TranslateV2Service translateV2Service;
    @Autowired
    private ITranslatesService iTranslatesService;
    @Autowired
    private GoogleMachineIntegration googleMachineIntegration;
    @Autowired
    private ShopifyHttpIntegration shopifyHttpIntegration;

    @PostMapping("/test")
    public Pair<String, Integer> test(@RequestBody TranslateRequest request) {
//        Pair<String, Integer> stringIntegerPair = geminiIntegration.generateText("gemini-2.5-flash", prompt);
        Pair<String, Integer> stringIntegerPair = null;
        if (!ModuleCodeUtils.LANGUAGE_CODES.contains(request.getTarget())) {
            stringIntegerPair = googleMachineIntegration.googleTranslateWithSDK(request.getContent(), request.getTarget());
        }

        return stringIntegerPair;
    }

    @GetMapping("/ping")
    public String ping() {
        AppInsightsUtils.trackTrace("SpringBackend Ping Successful");
        return "Ping Successful!";
    }

    @PostMapping("/gpt")
    public String chat() {
//        return chatGptIntegration.chatWithGpt(gptVO.getPrompt(), gptVO.getSourceText(), "ciwishop.myshopify.com", null, new CharacterCountUtils(), 2000000, false);
        return "";
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

    @GetMapping("/monitor")
    public Map<String, Object> monitor() {
        Map<String, Object> responseMap = new HashMap<>();
        return responseMap;
    }

    @GetMapping("/testEmail")
    public void testEmail() {
        ipEmailTask.sendEmailTask();
    }

    @GetMapping("/testAutoEmail")
    public void testAutoEmail(@RequestParam String shopName) {
        List<TranslatesDO> translatesDOList = iTranslatesService.listAutoTranslates(shopName);
        for (TranslatesDO translatesDO : translatesDOList) {
            translateV2Service.testAutoTranslate(shopName, translatesDO.getSource(), translatesDO.getTarget());
        }
    }
}
