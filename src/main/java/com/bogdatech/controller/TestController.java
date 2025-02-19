package com.bogdatech.controller;


import com.alibaba.dashscope.exception.InputRequiredException;
import com.alibaba.dashscope.exception.NoApiKeyException;
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
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.applicationinsights.TelemetryClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.bogdatech.integration.ALiYunTranslateIntegration.callWithMessage;
import static com.bogdatech.integration.ALiYunTranslateIntegration.callWithMessages;
import static com.bogdatech.integration.RateHttpIntegration.rateMap;
import static com.bogdatech.utils.CalculateTokenUtils.calculateToken;
import static com.bogdatech.utils.StringUtils.countWords;

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
//	@GetMapping("/test")
//	public List<JdbcTestModel> test() {
//		return jdbcTestRepository.sqlTest();
//	}

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

    @GetMapping("/countWords")
    public int countWord(@RequestParam String text) {
//        System.out.println("要计数的文本：" + text);
        return countWords(text);
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

    //测试siliconflow的返回值，是否可以用json解析
    @PostMapping("/testJson")
    public void testJson(@RequestBody String jsonString) {
        // 使用 Fastjson 将 JSON 字符串转换为 List
//        List<String> list = JSON.parseArray(jsonString, String.class);
        //对返回的数据做处理，只要[]里面的数据
        String regex = "\\[(.*?)\\]";
        Pattern pattern = Pattern.compile(regex, Pattern.DOTALL); // 使用 Pattern.DOTALL 让点号匹配换行符
        Matcher matcher = pattern.matcher(jsonString);

        List<String> list = null;
        if (matcher.find()) {
            // 提取并输出方括号中的内容
            String extractedData = matcher.group(1);
            extractedData = extractedData.replaceAll("\\\\n\\\\\\\\", "");
            extractedData = extractedData.replaceAll("\\\\", "");
            // 使用 Jackson 来解析 JSON 数组为 List<String>
            try {
                ObjectMapper objectMapper = new ObjectMapper();
                // 解析提取出来的 JSON 数组字符串为 List<String>
                list = objectMapper.readValue("[" + extractedData + "]", List.class);

                // 输出 List<String>
                System.out.println("Extracted List: " + list);
            } catch (JsonMappingException e) {
                throw new RuntimeException(e);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }
        // 打印 List 内容
        for (String item : list) {
            System.out.println(item);
        }
    }

    //测试token计数的差距
    @PostMapping("/testToken")
    public void testToken(@RequestBody String text) {
        int rate = 1;
        int i = calculateToken(text, rate);
        System.out.println("token: " + i);
    }

    @GetMapping("/tongyitest")
    public String tongyitest(String cueWord, String translate, String model) {

        String generationResult;
        try {
            generationResult = callWithMessage(model, translate, cueWord);
        } catch (NoApiKeyException | InputRequiredException e) {
            throw new RuntimeException(e);
        }
        return generationResult;
    }

    @GetMapping("/tongyitests")
    public List<String> tongyitests(String cueWord, String model) {
        List<String> list = new ArrayList<String>();
        list.add("ANONER Convertible Chair Bed Sleeper with Memory Foam %26 Pillow Fold Out Chair Bed Couch Lounge Chaise for Living Room Bedroom Guest Room Home Office, Dark Green");
        list.add("Casual Pink Mountain Landscape Printed White Pullover With Round Neckline And Long Sleeves");
        list.add("Casual-Fitting For Women, Suitable For Autumn/Winter,ROYLAMP Womens Oversized Hoodies Pullover Fleece Long Sleeve Hooded Sweatshirts Casual Fall Winter Outfits Tops");
        List<String> generationResult ;
        try {
            generationResult = callWithMessages(model, list, cueWord);
        } catch (NoApiKeyException | InputRequiredException e) {
            throw new RuntimeException(e);
        }
        return generationResult;
    }
}
