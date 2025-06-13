package com.bogdatech.controller;


import com.alibaba.fastjson.JSONObject;
import com.bogdatech.Service.impl.TranslatesServiceImpl;
import com.bogdatech.entity.DO.TranslatesDO;
import com.bogdatech.entity.DTO.KeyValueDTO;
import com.bogdatech.integration.ChatGptIntegration;
import com.bogdatech.integration.RateHttpIntegration;
import com.bogdatech.logic.TaskService;
import com.bogdatech.logic.TestService;
import com.bogdatech.logic.TranslateService;
import com.bogdatech.logic.UserTypeTokenService;
import com.bogdatech.model.controller.request.CloudServiceRequest;
import com.bogdatech.model.controller.request.ShopifyRequest;
import com.bogdatech.model.service.StoringDataPublisherService;
import com.bogdatech.utils.CharacterCountUtils;
import com.microsoft.applicationinsights.TelemetryClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

import static com.bogdatech.entity.DO.TranslateResourceDTO.TOKEN_MAP;
import static com.bogdatech.integration.RateHttpIntegration.rateMap;
import static com.bogdatech.integration.ShopifyHttpIntegration.getInfoByShopify;
import static com.bogdatech.logic.TranslateService.SINGLE_LINE_TEXT;
import static com.bogdatech.logic.TranslateService.addData;
import static com.bogdatech.utils.ApiCodeUtils.getLanguageName;
import static com.bogdatech.utils.JsonUtils.isJson;
import static com.bogdatech.utils.JsoupUtils.isHtml;
import static com.bogdatech.utils.JudgeTranslateUtils.*;
import static com.bogdatech.utils.PlaceholderUtils.getSimplePrompt;
import static com.bogdatech.utils.StringUtils.replaceHyphensWithSpaces;

@RestController
public class TestController {
    private final TranslatesServiceImpl translatesServiceImpl;
    private final ChatGptIntegration chatGptIntegration;
    private final TestService testService;
    private final TranslateService translateService;
    private final TaskService taskService;
    private final RateHttpIntegration rateHttpIntegration;
    private final StoringDataPublisherService storingDataPublisherService;
    private final UserTypeTokenService userTypeTokenService;

    @Autowired
    public TestController(TranslatesServiceImpl translatesServiceImpl, ChatGptIntegration chatGptIntegration, TestService testService, TranslateService translateService, TaskService taskService, RateHttpIntegration rateHttpIntegration, StoringDataPublisherService storingDataPublisherService, UserTypeTokenService userTypeTokenService) {
        this.translatesServiceImpl = translatesServiceImpl;
        this.chatGptIntegration = chatGptIntegration;
        this.testService = testService;
        this.translateService = translateService;
        this.taskService = taskService;
        this.rateHttpIntegration = rateHttpIntegration;
        this.storingDataPublisherService = storingDataPublisherService;
        this.userTypeTokenService = userTypeTokenService;
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
        JSONObject infoByShopify = getInfoByShopify(request, body);
        if (infoByShopify == null) {
            return null;
        }
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


    @GetMapping("/testHtml")
    public void testHtml() {
        String html = """ 
                啊手动阀手动阀  
                """;
        if (isHtml(html)) {
            System.out.println("is html");
        } else {
            System.out.println("is not html");
        }

        if (isJson(html)) {
            System.out.println("is json");
        } else {
            System.out.println("is not json");
        }
        String targetLanguage = getLanguageName("zh-CN");
        String prompt = getSimplePrompt(targetLanguage,  html);
        System.out.println("prompt: " + prompt);
//        String s = hunYuanTranslate(html, prompt, new CharacterCountUtils(), "zh-CN", HUN_YUAN_MODEL);
//        System.out.println("final: " + s);
//        String s = translateNewHtml(html, new TranslateRequest(0, "shop", "token", "en", "zh-CN", ""), new CharacterCountUtils(), "en");
//        System.out.println("final: " + normalizeHtml(html));
    }

    //测试theme判断
    @PostMapping("/testKeyValue")
    public String testKeyValue(@RequestBody KeyValueDTO keyValueDTO) {
        String key = keyValueDTO.getKey();
        String value = keyValueDTO.getValue();
        if (SUSPICIOUS2_PATTERN.matcher(value).matches()) {
            return "Metafield不翻译";
        }

        //通用的不翻译数据
        if (!generalTranslate(key, value)) {
            return "通用不翻译";
        }

        //如果是theme模块的数据
        if (!TRANSLATABLE_KEY_PATTERN.matcher(key).matches()) {
            return "theme不翻译";
        }
        //如果包含对应key和value，则跳过
        if (!shouldTranslate(key, value)) {
            return "theme不翻译";
        }
        return "需要翻译";
    }

    //测试自动翻译功能
    @PutMapping("/testAutoTranslate")
    public void testAutoTranslate() {
        taskService.autoTranslate();
    }

    @GetMapping("/testFreeTrialTask")
    public void testFreeTrialTask() {
        taskService.freeTrialTask();
    }

    @PostMapping("/testToken")
    public void testToken(@RequestBody ShopifyRequest request) {
        for (String key : TOKEN_MAP.keySet()
        ) {
            userTypeTokenService.testTokenCount(request, key);
        }
        System.out.println("统计结束！！！");
    }

    @GetMapping("/testHandle")
    public String testHandle(String value) {
        return replaceHyphensWithSpaces(value);
    }
}
