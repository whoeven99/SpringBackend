package com.bogdatech.controller;


import com.alibaba.fastjson.JSONObject;
import com.bogdatech.Service.impl.TranslatesServiceImpl;
import com.bogdatech.entity.TranslatesDO;
import com.bogdatech.integration.ChatGptIntegration;
import com.bogdatech.integration.RateHttpIntegration;
import com.bogdatech.integration.ShopifyHttpIntegration;
import com.bogdatech.logic.TaskService;
import com.bogdatech.logic.TestService;
import com.bogdatech.logic.TranslateService;
import com.bogdatech.model.controller.request.CloudServiceRequest;
import com.bogdatech.model.controller.request.ShopifyRequest;
import com.bogdatech.model.controller.request.TranslateRequest;
import com.bogdatech.model.controller.request.UserRequest;
import com.bogdatech.utils.CharacterCountUtils;
import com.microsoft.applicationinsights.TelemetryClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

import static com.bogdatech.integration.RateHttpIntegration.rateMap;
import static com.bogdatech.logic.TranslateService.SINGLE_LINE_TEXT;
import static com.bogdatech.logic.TranslateService.addData;
import static com.bogdatech.utils.AESUtils.decrypt;
import static com.bogdatech.utils.AESUtils.encrypt;
import static com.bogdatech.utils.JudgeTranslateUtils.TRANSLATABLE_KEY_PATTERN;
import static com.bogdatech.utils.LiquidHtmlTranslatorUtils.translateNewHtml;

@RestController
public class TestController {
    private final TranslatesServiceImpl translatesServiceImpl;
    private final ChatGptIntegration chatGptIntegration;
    private final ShopifyHttpIntegration shopifyApiIntegration;
    private final TestService testService;
    private final TranslateService translateService;
    private final TaskService taskService;
    private final RateHttpIntegration rateHttpIntegration;
    @Autowired
    public TestController(TranslatesServiceImpl translatesServiceImpl, ChatGptIntegration chatGptIntegration, ShopifyHttpIntegration shopifyApiIntegration, TestService testService, TranslateService translateService, TaskService taskService, RateHttpIntegration rateHttpIntegration) {
        this.translatesServiceImpl = translatesServiceImpl;
        this.chatGptIntegration = chatGptIntegration;
        this.shopifyApiIntegration = shopifyApiIntegration;
        this.testService = testService;
        this.translateService = translateService;
        this.taskService = taskService;
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
    public void testJsoup() {
        String key = "general.animations_type";
        if (!TRANSLATABLE_KEY_PATTERN.matcher(key).matches()) {
            System.out.println("不匹配");
        }
    }

    @GetMapping("/testHtml")
    public void testHtml() {
        String html = """
               """;
        String s = translateNewHtml(html, new TranslateRequest(0, "shop", "token", "en", "zh-CN", ""), new CharacterCountUtils(), "en");
        System.out.println("final: " + s);
    }

    @PutMapping("/testAutoTranslate")
    public void testAutoTranslate() {
        taskService.autoTranslate();
    }

    @GetMapping("/testEncrypt")
    public void testAES(String text) {
        try {
            String target = encrypt(text);
            System.out.println("encrypt: " + target);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @PostMapping("/testDecrypt")
    public void testDecrypt(@RequestBody UserRequest userRequest) {
        try {
            System.out.println("text: " + userRequest.getShopName());
            String target = decrypt(userRequest.getShopName());
            System.out.println("decrypt: " + target);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
