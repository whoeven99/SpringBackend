package com.bogdatech.controller;


import com.alibaba.fastjson.JSONObject;
import com.bogdatech.Service.impl.TranslatesServiceImpl;
import com.bogdatech.entity.TranslatesDO;
import com.bogdatech.integration.ChatGptIntegration;
import com.bogdatech.integration.RateHttpIntegration;
import com.bogdatech.integration.ShopifyHttpIntegration;
import com.bogdatech.logic.TestService;
import com.bogdatech.logic.TranslateService;
import com.bogdatech.model.controller.request.CloudServiceRequest;
import com.bogdatech.model.controller.request.ShopifyRequest;
import com.bogdatech.utils.CharacterCountUtils;
import com.bogdatech.utils.JsoupUtils;
import com.microsoft.applicationinsights.TelemetryClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

import static com.bogdatech.integration.RateHttpIntegration.rateMap;
import static com.bogdatech.logic.TranslateService.SINGLE_LINE_TEXT;
import static com.bogdatech.logic.TranslateService.addData;
import static com.bogdatech.utils.ApiCodeUtils.isDatabaseLanguage;
import static com.bogdatech.utils.JsoupUtils.QWEN_MT_CODES;

@RestController
public class TestController {
    private final TranslatesServiceImpl translatesServiceImpl;
    private final ChatGptIntegration chatGptIntegration;
    private final ShopifyHttpIntegration shopifyApiIntegration;
    private final TestService testService;
    private final TranslateService translateService;
    private final JsoupUtils jsoupUtils;
    private final RateHttpIntegration rateHttpIntegration;
    TelemetryClient appInsights = new TelemetryClient();
    @Autowired
    public TestController(TranslatesServiceImpl translatesServiceImpl, ChatGptIntegration chatGptIntegration, ShopifyHttpIntegration shopifyApiIntegration, TestService testService, TranslateService translateService, JsoupUtils jsoupUtils, RateHttpIntegration rateHttpIntegration) {
        this.translatesServiceImpl = translatesServiceImpl;
        this.chatGptIntegration = chatGptIntegration;
        this.shopifyApiIntegration = shopifyApiIntegration;
        this.testService = testService;
        this.translateService = translateService;
        this.jsoupUtils = jsoupUtils;
        this.rateHttpIntegration = rateHttpIntegration;
    }

    @GetMapping("/ping")
    public String ping() {
        TelemetryClient appInsights = new TelemetryClient();
        appInsights.trackTrace("SpringBackend Ping Successful");
        return "Ping Successful!";
    }

    @GetMapping("/gpt")
    public String chat(@RequestParam String prompt) {
        return chatGptIntegration.chatWithGpt(prompt);
    }

    @PostMapping("/test/test1")
    public int test1(@RequestBody TranslatesDO name) {
        return translatesServiceImpl.updateTranslateStatus(name.getShopName(), name.getStatus(), name.getTarget(), name.getSource(), name.getAccessToken());
    }

    //通过测试环境调shopify的API
    @PostMapping("/test123")
    public String test(@RequestBody CloudServiceRequest cloudServiceRequest) {
        ShopifyRequest request = new ShopifyRequest();
        request.setShopName(cloudServiceRequest.getShopName());
        request.setAccessToken(cloudServiceRequest.getAccessToken());
        request.setTarget(cloudServiceRequest.getTarget());
        String body = cloudServiceRequest.getBody();
        JSONObject infoByShopify = shopifyApiIntegration.getInfoByShopify(request, body);
        return infoByShopify.toString();
    }

    @GetMapping("/clickTranslate")
    public void clickTranslate() {
        testService.startTask();
    }

    @GetMapping("/stop")
    public void stop() {
        testService.stopTask();
    }



    //发送成功翻译的邮件gei
    @GetMapping("/sendEmail")
    public void sendEmail() {
        CharacterCountUtils characterCount = new CharacterCountUtils();
        characterCount.addChars(100);
        LocalDateTime localDateTime = LocalDateTime.now();
        translateService.translateFailEmail("ciwishop.myshopify.com",characterCount, localDateTime, 0, 1000, "zh-CN", "en");
    }

    //获取汇率
    @GetMapping("/getRate")
    public void getRate() {
        rateHttpIntegration.getFixerRate();
        System.out.println("rateMap: " + rateMap.toString());
    }

    //测试缓存功能
    @GetMapping("/testCache")
    public String testCache() {
        return SINGLE_LINE_TEXT.toString();
    }

    @GetMapping("/testAddCache")
    public void testAddCache(String target, String value, String targetText) {
        addData(target, value, targetText);
    }

    @PostMapping("/testMT")
    public void testMT(String model, String translateText, String source, String target) {
        if (QWEN_MT_CODES.contains(target) && QWEN_MT_CODES.contains(source)) {
            System.out.println("mt翻译");
        } else {
            System.out.println("google翻译");
        }
    }

    @GetMapping("/testIsHTML")
    public void testIsHTML(String target) {
        String html = """
                """;
        boolean result = isDatabaseLanguage(target);
//        String result = translateNewHtml(html, new TranslateRequest(0, "fadsf", "asdf", "en", "zh-CN", html), new CharacterCountUtils(), "product");
        System.out.println("翻译的结果： " + result);
    }

    @GetMapping("/testPrint")
    public void testPrint(@RequestParam(value = "text") String text) {
        appInsights.trackEvent(text);
    }




}
