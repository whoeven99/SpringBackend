package com.bogda.api.controller;

import com.alibaba.fastjson.JSONObject;
import com.bogda.api.Service.ITranslatesService;
import com.bogda.api.entity.DO.TranslatesDO;
import com.bogda.api.entity.VO.GptVO;
import com.bogda.api.entity.VO.UserDataReportVO;
import com.bogda.api.integration.GeminiIntegration;
import com.bogda.api.integration.GoogleMachineIntegration;
import com.bogda.api.logic.RedisDataReportService;
import com.bogda.api.logic.RedisProcessService;
import com.bogda.api.logic.translate.TranslateV2Service;
import com.bogda.api.model.controller.request.CloudServiceRequest;
import com.bogda.api.model.controller.request.ShopifyRequest;
import com.bogda.api.model.controller.response.BaseResponse;
import com.bogda.api.task.IpEmailTask;
import com.bogda.api.utils.ModuleCodeUtils;
import com.microsoft.applicationinsights.TelemetryClient;
import kotlin.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.BufferedInputStream;
import java.net.URL;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.bogda.api.integration.ShopifyHttpIntegration.getInfoByShopify;
import static com.bogda.api.utils.CaseSensitiveUtils.appInsights;

@RestController
public class TestController {
    @Autowired
    private RedisProcessService redisProcessService;
    @Autowired
    private RedisDataReportService redisDataReportService;
    @Autowired
    private GeminiIntegration geminiIntegration;
    @Autowired
    private IpEmailTask ipEmailTask;
    @Autowired
    private TranslateV2Service translateV2Service;
    @Autowired
    private ITranslatesService iTranslatesService;
    @Autowired
    private GoogleMachineIntegration googleMachineIntegration;

    @PostMapping("/test")
    public String test(@RequestParam String target) {
        String prompt = "翻译下面文本为中文： hello word";
//        Pair<String, Integer> stringIntegerPair = geminiIntegration.generateText("gemini-2.5-flash", prompt);
        Pair<String, Integer> stringIntegerPair = null;
        if (!ModuleCodeUtils.LANGUAGE_CODES.contains(target)) {
            stringIntegerPair = googleMachineIntegration.googleTranslateWithSDK(prompt, target);
        }

        return stringIntegerPair.getFirst();
    }

    @PostMapping("/testPic")
    public ResponseEntity<byte[]> testPic(@RequestParam String model, @RequestParam String picUrl, @RequestParam String prompt) {
//        String picUrl = "https://cdn.shopify.com/s/files/1/0892/3437/5004/files/ChatGPT_Image_Jun_25_2025_10_33_50_AM_ac0e4bff-73f3-4065-80dc-9801cb862bc3.png?v=1750856533";
//        String prompt = "翻译图片里面的文本为简体中文";
        byte[] imageBytes;
        try (BufferedInputStream in = new BufferedInputStream(new URL(picUrl).openStream())) {
            imageBytes = in.readAllBytes();
            Pair<String, Integer> stringIntegerPair = geminiIntegration.generateImage(model, prompt, imageBytes, "image/png");

            String first = stringIntegerPair.getFirst();
            return ResponseEntity.ok()
                    .contentType(MediaType.IMAGE_PNG) // 或 IMAGE_JPEG
                    .body(Base64.getDecoder().decode(first));
        } catch (Exception e) {
            appInsights.trackException(e);
            e.printStackTrace();
        }
        return null;
    }

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
