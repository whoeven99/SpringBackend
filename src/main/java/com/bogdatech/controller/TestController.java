package com.bogdatech.controller;


import com.alibaba.fastjson.JSONObject;
import com.bogdatech.Service.impl.TranslatesServiceImpl;
import com.bogdatech.Service.impl.TranslationCounterServiceImpl;
import com.bogdatech.entity.DTO.KeyValueDTO;
import com.bogdatech.entity.DO.TranslatesDO;
import com.bogdatech.entity.DO.TranslationCounterDO;
import com.bogdatech.integration.ChatGptIntegration;
import com.bogdatech.integration.RateHttpIntegration;
import com.bogdatech.integration.ShopifyHttpIntegration;
import com.bogdatech.logic.TaskService;
import com.bogdatech.logic.TestService;
import com.bogdatech.logic.TranslateService;
import com.bogdatech.model.controller.request.CloudServiceRequest;
import com.bogdatech.model.controller.request.ShopifyRequest;
import com.bogdatech.utils.CharacterCountUtils;
import com.microsoft.applicationinsights.TelemetryClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Scanner;

import static com.bogdatech.integration.RateHttpIntegration.rateMap;
import static com.bogdatech.logic.TranslateService.SINGLE_LINE_TEXT;
import static com.bogdatech.logic.TranslateService.addData;
import static com.bogdatech.utils.JsonUtils.isJson;
import static com.bogdatech.utils.JsoupUtils.isHtml;
import static com.bogdatech.utils.JudgeTranslateUtils.*;

@RestController
public class TestController {
    private final TranslatesServiceImpl translatesServiceImpl;
    private final ChatGptIntegration chatGptIntegration;
    private final ShopifyHttpIntegration shopifyApiIntegration;
    private final TestService testService;
    private final TranslateService translateService;
    private final TaskService taskService;
    private final RateHttpIntegration rateHttpIntegration;
    private final TranslationCounterServiceImpl translationCounterService;

    @Autowired
    public TestController(TranslatesServiceImpl translatesServiceImpl, ChatGptIntegration chatGptIntegration, ShopifyHttpIntegration shopifyApiIntegration, TestService testService, TranslateService translateService, TaskService taskService, RateHttpIntegration rateHttpIntegration, TranslationCounterServiceImpl translationCounterService) {
        this.translatesServiceImpl = translatesServiceImpl;
        this.chatGptIntegration = chatGptIntegration;
        this.shopifyApiIntegration = shopifyApiIntegration;
        this.testService = testService;
        this.translateService = translateService;
        this.taskService = taskService;
        this.rateHttpIntegration = rateHttpIntegration;
        this.translationCounterService = translationCounterService;
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


    @GetMapping("/testHtml")
    public void testHtml() {
        String html = """
                                
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
//        String s = translateNewHtml(html, new TranslateRequest(0, "shop", "token", "en", "zh-CN", ""), new CharacterCountUtils(), "en");
//        System.out.println("final: " + s);
    }

    //测试theme判断
    @PostMapping("/testKeyValue")
    public void testKeyValue(@RequestBody KeyValueDTO keyValueDTO) {
        String key = keyValueDTO.getKey();
        String value = keyValueDTO.getValue();
        //通用的不翻译数据
        if (!generalTranslate(key, value)) {
            System.out.println("不翻译");
            return;
        }

        //如果是theme模块的数据

        if (!TRANSLATABLE_KEY_PATTERN.matcher(key).matches()) {
            System.out.println("不翻译");
            return;
        }
        //如果包含对应key和value，则跳过
        if (!shouldTranslate(key, value)) {
            System.out.println("不翻译");
            return;
        }

        System.out.println("需要翻译");

    }

    //测试自动翻译功能
    @PutMapping("/testAutoTranslate")
    public void testAutoTranslate() {
        taskService.autoTranslate();
    }

    @GetMapping("/testCharacterCount")
    public void testCharacterCount() {
        String shopName = "ciwishop.myshopify.com";
        TranslationCounterDO translationCounterDO = translationCounterService.readCharsByShopName(shopName);
        System.out.println("一开始的token数： " + translationCounterDO.getUsedChars());
        CharacterCountUtils usedCounter = new CharacterCountUtils();
        usedCounter.addChars(translationCounterDO.getUsedChars());
        CharacterCountUtils totalCounter = new CharacterCountUtils();
        totalCounter.addChars(translationCounterDO.getUsedChars());
        Scanner scanner = new Scanner(System.in);

        while (true){
            System.out.println("请输入要翻译的字符数");
            int num = scanner.nextInt();
            totalCounter.addChars(num);
            int i = 3;
            while (i>0){
                System.out.println("请输入手动翻译的token数");
                int token = scanner.nextInt();
                usedCounter.addChars(token);
                i--;
            }

            translateService.judgeCounterByOldAndNew(usedCounter, shopName, totalCounter);
        }

    }
}
