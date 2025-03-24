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
import com.bogdatech.model.controller.request.TranslateRequest;
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
import static com.bogdatech.utils.ApiCodeUtils.qwenMtCode;
import static com.bogdatech.utils.JsoupUtils.QWEN_MT_CODES;
import static com.bogdatech.utils.LiquidHtmlTranslatorUtils.isHtmlEntity;
import static com.bogdatech.utils.PlaceholderUtils.hasPlaceholders;
import static com.bogdatech.utils.PlaceholderUtils.processTextWithPlaceholders;
import static com.bogdatech.utils.RegularJudgmentUtils.isValidString;

@RestController
public class TestController {
    private final TranslatesServiceImpl translatesServiceImpl;
    private final ChatGptIntegration chatGptIntegration;
    private final ShopifyHttpIntegration shopifyApiIntegration;
    private final TestService testService;
    private final TranslateService translateService;
    private final JsoupUtils jsoupUtils;
    private final RateHttpIntegration rateHttpIntegration;


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
        characterCount.addChars(10000);
        LocalDateTime localDateTime = LocalDateTime.now();
        translateService.translateSuccessEmail(new TranslateRequest(0, "ciwishop.myshopify.com", null, "en", "zh-CN", null), characterCount, localDateTime, 0, 12311);
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

    @GetMapping("/testPlaceholder")
    public void testPlaceholder() {
        // 测试用例
        String[] testCases = {
                "这是一个 {{ product.value }} 测试",               // 包含 {{ product.value }}
                "包含 %{product.value} 的文本",                    // 包含 %{product.value}
                "嵌套 {{xx[x].xx}} 数据",                         // 包含 {{xx[x].xx}}
                "特殊 {%product.value%} 格式",                    // 包含 {%product.value%}
                "普通文本没有占位符",                             // 不包含任何占位符
                null                                            // null
        };

        for (String test : testCases) {
            boolean result = hasPlaceholders(test);
            String s = processTextWithPlaceholders(test, new CharacterCountUtils(), qwenMtCode("zh-CN"), qwenMtCode("en"));
            System.out.println("翻译后的文本： " + s);
            System.out.println("文本: \" + test + \" -> 包含占位符: " + result);
        }
    }

    @GetMapping("/testText")
    public String testText(@RequestParam String text) {
            // 使用 StringEscapeUtils 解码，但只针对 ' 生效
        System.out.println("text: " + text);
        return isHtmlEntity(text);
        }


    @GetMapping("/testRegular")
    public void testRegular(@RequestParam String text) {
        // 测试用例
        String[] testCases = {
                "Unisex",        // 只有1个标点符号
                "Apparel & Accessories > Clothing Accessories > Hair Accessories > Hair Extensions",       // 2个标点符号
                "BU18",  // 2个标点符号
                "approved",        // 4个标点符号
                "{\"pending\":0,\"failed\":0,\"approved\":7}",        // 包含空格，不符合
                "74eb6025-072a-4e00-b952-f7cc816f7b9d",            // 2个标点符号
                "\"TRUE\"",          // 无标点符号
                "Feather Crochet Hair",       // 5个标点符号
                "<p><lite-youtube videoid=\"dbDDT8s3y3k\" params=\"controls=1&amp;modestbranding=1&amp;rel=0&amp;showinfo=0&amp;enablejsapi=1+\" addnoscript></lite-youtube></p>"         // 包含#，但不是问题中的“标点符号”仍算有效
        };

        for (String test : testCases) {
            System.out.println("测试字符串: " + test + " -> " + isValidString(test));
        }

    }
}
