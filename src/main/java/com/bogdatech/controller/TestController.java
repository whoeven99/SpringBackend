package com.bogdatech.controller;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bogdatech.entity.DO.InitialTranslateTasksDO;
import com.bogdatech.entity.VO.GptVO;
import com.bogdatech.entity.VO.UserDataReportVO;
import com.bogdatech.logic.RedisDataReportService;
import com.bogdatech.logic.RedisProcessService;
import com.bogdatech.logic.RedisTranslateLockService;
import com.bogdatech.logic.TaskService;
import com.bogdatech.mapper.InitialTranslateTasksMapper;
import com.bogdatech.model.controller.request.CloudServiceRequest;
import com.bogdatech.model.controller.request.ShopifyRequest;
import com.bogdatech.model.controller.response.BaseResponse;
import com.bogdatech.task.IpEmailTask;
import com.microsoft.applicationinsights.TelemetryClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.bogdatech.integration.ShopifyHttpIntegration.getInfoByShopify;
import static com.bogdatech.logic.TranslateService.executorService;
import static com.bogdatech.utils.CaseSensitiveUtils.appInsights;

@RestController
public class TestController {
    @Autowired
    private TaskService taskService;
    @Autowired
    private RedisProcessService redisProcessService;
    @Autowired
    private RedisDataReportService redisDataReportService;
    @Autowired
    private RedisTranslateLockService redisTranslateLockService;
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

    // 通过测试环境调shopify的API
    @PostMapping("/test123")
    public String test(@RequestBody CloudServiceRequest cloudServiceRequest) {
        ShopifyRequest request = new ShopifyRequest();
        request.setShopName(cloudServiceRequest.getShopName());
        request.setAccessToken(cloudServiceRequest.getAccessToken());
        request.setTarget(cloudServiceRequest.getTarget());
        String body = cloudServiceRequest.getBody();
        JSONObject infoByShopify = getInfoByShopify(request, body);
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

    // 暂时存在下
    @GetMapping("/testUnlock")
    public Boolean testUnlock(@RequestParam String shopName) {
        return redisTranslateLockService.setRemove(shopName);
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

    @Autowired
    private InitialTranslateTasksMapper initialTranslateTasksMapper;

    @GetMapping("/monitor")
    public Map<String, Object> monitor() {
        // Initial Task 未开始，进行中的任务
        List<InitialTranslateTasksDO> initialTranslateTasksDOS0 = initialTranslateTasksMapper.selectList(
                new LambdaQueryWrapper<InitialTranslateTasksDO>()
                        .eq(InitialTranslateTasksDO::getStatus, 0)
                        .orderByAsc(InitialTranslateTasksDO::getCreatedAt));

        List<InitialTranslateTasksDO> initialTranslateTasksDOS2 = initialTranslateTasksMapper.selectList(
                new LambdaQueryWrapper<InitialTranslateTasksDO>()
                        .eq(InitialTranslateTasksDO::getStatus, 2)
                        .orderByAsc(InitialTranslateTasksDO::getCreatedAt));

        Map<String, Object> responseMap = new HashMap<>();
        responseMap.put("Step0-创建总翻译任务的用户-未开始（则说明有问题）", initialTranslateTasksDOS0.stream().map(InitialTranslateTasksDO::getShopName).collect(Collectors.toSet()));
        responseMap.put("Step0-创建总翻译任务的用户-进行中", initialTranslateTasksDOS2.stream().map(InitialTranslateTasksDO::getShopName).collect(Collectors.toSet()));
        return responseMap;
    }

    @GetMapping("/testEmail")
    public void testEmail() {
        ipEmailTask.sendEmailTask();
    }
}
