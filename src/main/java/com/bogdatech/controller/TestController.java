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
import static com.bogdatech.utils.JudgeTranslateUtils.shouldTranslate;

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
        translateService.translateFailEmail("ciwishop.myshopify.com", characterCount, localDateTime, 0, 1000, "zh-CN", "en");
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

    @GetMapping("/testJsoup")
    public String testJsoup() {
        // 测试明确不翻译的key
//        System.out.println(shouldTranslate("general.rtl_languages", "value")); // false
//        System.out.println(shouldTranslate("some.key", "value")); // true
//
//        // 测试包含.json的key
//        System.out.println(shouldTranslate("key.order_number.json", "value")); // false
//        System.out.println(shouldTranslate("some.key.json", "value")); // true
//
//        // 测试value包含px的情况
//        System.out.println(shouldTranslate("key.with.mg", "10px")); // false
//        System.out.println(shouldTranslate("key.with.px", "10px")); // true
//        System.out.println(shouldTranslate("key.with.image.json", "10px")); // false
//
//        // 测试value为空的情况
//        System.out.println(shouldTranslate("key.order_number.json", null)); // false
//
//        // 测试以#开头
//        System.out.println(shouldTranslate("123", "#short")); // false
//        System.out.println(shouldTranslate("123", "#" + "a".repeat(91))); // true
//
//        // 测试包含#
//        System.out.println(shouldTranslate("123", "key#phrase")); // false
//        System.out.println(shouldTranslate("123", "key#" + "a".repeat(30))); // true
//
//        // 测试纯数字
//        System.out.println(shouldTranslate("123", "123456")); // false
//        System.out.println(shouldTranslate("123", "123abc")); // true
//
//        // 测试URL
//        System.out.println(shouldTranslate("123", "https://example.com")); // false
//        System.out.println(shouldTranslate("123", "example.com")); // true
//
//        // 测试包含/
//        System.out.println(shouldTranslate("123", "path/to")); // false
//        System.out.println(shouldTranslate("123", "path/" + "a".repeat(16))); // true

        // 测试包含-或—
        System.out.println(shouldTranslate("123", "+1-000-123-456789")); // false
        System.out.println(shouldTranslate("123", "0.014-0.13")); // false
        System.out.println(shouldTranslate("123", "133501-QH4")); // false
        System.out.println(shouldTranslate("123", "TEWXT-WITG-ADSA")); // true

        // 测试包含px
        System.out.println(shouldTranslate("123", "10px")); // false
        System.out.println(shouldTranslate("123", "pixel")); // true

//        // 测试包含<svg>
//        System.out.println(shouldTranslate("123", "<svg></svg>")); // false
//        System.out.println(shouldTranslate("123", "svg image")); // true
//
//        // 测试空值
//        System.out.println(shouldTranslate("123", null)); // false
//        System.out.println(shouldTranslate("123", "")); // false

        return "success";
    }
}
