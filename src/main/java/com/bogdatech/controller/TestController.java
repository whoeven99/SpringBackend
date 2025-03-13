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
import static com.bogdatech.utils.ApiCodeUtils.qwenMtCode;
import static com.bogdatech.utils.CalculateTokenUtils.googleCalculateToken;
import static com.bogdatech.utils.JsoupUtils.QWEN_MT_CODES;
import static com.bogdatech.utils.LiquidHtmlTranslatorUtils.translateNewHtml;
import static com.bogdatech.utils.PlaceholderUtils.hasPlaceholders;
import static com.bogdatech.utils.PlaceholderUtils.processTextWithPlaceholders;
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


    //测试token计数的差距
    @PostMapping("/testToken")
    public void testToken(@RequestBody String text) {
        int i = googleCalculateToken(text);
        System.out.println("token: " + i);
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
    public void testIsHTML() {
        String html = """
                <p dir=""auto""><img alt=""\"" src=""https://cdn.shopify.com/s/files/1/0661/3450/7676/files/2ddf9655c203e84f4d56815777b6b941.jpg?v=1733024912""></p>
                <p dir=""auto"">我是旺旺，在大理生活了好几年，爬山是我生活中的一大乐趣，山里花草树木四季生长的变化，让我一次次的回到这里，每次的感受都是一样的。</p>
                <p dir=""auto"">我们的徒步小组是由几位热爱徒步的好友组成，除了我之外，还有带着边境牧羊犬的裁缝师傅、带着狗狗元宝的极限运动和水晶寻宝的DD、喜欢历史文化的汪越、经营“欢雀”餐厅的欢欢、以及 CrossFit 爱好者兼冷餐制作者红红。野生蘑菇采集小组的杨敏也加入了我们，她最喜欢的路线是“蕨类森林”。偶尔，其他专业小组的成员也会加入。</p>
                <p dir=""auto"">您可以在这里了解有关我们的朋友的更多信息：<span> </span><strong><a href=""https://dalitrip.com/blogs/life-in-dali/introduction-of-our-guides"">我们的向导</a></strong><strong></strong></p>
                <p dir=""auto"">我不能说我已经探索过苍山的每一个角落，但我对苍山各个山峰下不同海拔的植物和森林确实很了解。</p>
                <p dir=""auto"">于是有一天，当我徒步穿越山谷时，我随意决定往上爬，却惊奇地发现一片巨大的蕨类森林。这是我在苍山其他地区从未见过的。我想把它变成一条徒步路线。距离约 6-7 公里，走起来很舒服，不会太累。海拔高度为 500 米，是一条中等难度的徒步路线，非常适合那些体力充沛、喜欢在天然森林中行走的人。</p>
                <p dir=""auto""><strong>出发时间 1</strong> ：上午10:00（自带午餐，在森林里野餐）</p>
                <p dir=""auto""><strong>出发时间 2</strong> ：下午 2:00</p>
                <p dir=""auto""><strong>徒步时间</strong>：4小时</p>
                <p dir=""auto""><strong>集合地点</strong>：古城北5公里</p>
                <p dir=""auto"">溪流、森林、不同种类的蕨类植物、雨季的野生蘑菇、柠檬草、杜鹃花和野生茶园——我将在下面一一介绍。</p>
                <p dir=""auto""><strong>里白蕨</strong><span> </span>– 这是这片森林中最常见的蕨类植物。它沿着徒步小径密集地生长。</p>
                <p dir=""auto""><img src=""https://cdn.shopify.com/s/files/1/0661/3450/7676/files/8461733762405_.pic.jpg?v=1733762633"" alt=""\""></p>
                <p dir=""auto""><strong><img src=""https://cdn.shopify.com/s/files/1/0661/3450/7676/files/8281733762379_.pic.jpg?v=1733762633"" alt=""\""></strong></p>
                <p dir=""auto""><strong>树上的铁线蕨</strong></p>
                <p dir=""auto""><strong><img src=""https://cdn.shopify.com/s/files/1/0661/3450/7676/files/8431733762401_.pic.jpg?v=1733762634"" alt=""\""></strong></p>
                <p dir=""auto""><strong><img alt=""\"" src=""https://cdn.shopify.com/s/files/1/0661/3450/7676/files/8421733762400_.pic.jpg?v=1733762633""></strong></p>
                <p dir=""auto""><strong>鳞蕨</strong><span> </span>– 生长在地面上，形成一片斑块。</p>
                <p dir=""auto""><img alt=""\"" src=""https://cdn.shopify.com/s/files/1/0661/3450/7676/files/8411733762399_.pic.jpg?v=1733762633""></p>
                <p dir=""auto""><span>瓦苇 </span>– 生长在树上。</p>
                <p dir=""auto""><strong><img alt=""\"" src=""https://cdn.shopify.com/s/files/1/0661/3450/7676/files/8391733762396_.pic.jpg?v=1733762633""></strong></p>
                <p dir=""auto""><strong>斛蕨</strong></p>
                <p dir=""auto""><strong><img alt=""\"" src=""https://cdn.shopify.com/s/files/1/0661/3450/7676/files/8381733762394_.pic.jpg?v=1733762633""></strong></p>
                <p dir=""auto""><strong>成熟的蕨菜（当地叫：龙爪菜）</strong></p>
                <p dir=""auto""><strong><img alt=""\"" src=""https://cdn.shopify.com/s/files/1/0661/3450/7676/files/8371733762393_.pic.jpg?v=1733762633""></strong></p>
                <p dir=""auto"">最近一次来这里徒步，我带上了采摘小组的小景和燕子，她们对植物的敏锐观察力帮助我们发现了更多的蕨类植物和许多其他植物。</p>
                <p dir=""auto""><img alt=""\"" src=""https://cdn.shopify.com/s/files/1/0661/3450/7676/files/8161733762365_.pic.jpg?v=1733762633""></p>
                <p dir=""auto""> </p>
                <p dir=""auto"">我们摘了一些香茅，后来回到家，我用它做了泰式冬阴功鱼汤。</p>
                <p dir=""auto""><img alt=""\"" src=""https://cdn.shopify.com/s/files/1/0661/3450/7676/files/8361733762391_.pic.jpg?v=1733762633""></p>
                <p dir=""auto""> </p>
                <p dir=""auto"">我们还发现了野草莓和盛开的杜鹃花，可以采摘来做汤。</p>
                <p dir=""auto""><img alt=""\"" src=""https://cdn.shopify.com/s/files/1/0661/3450/7676/files/8351733762389_.pic.jpg?v=1733762633""></p>
                <p dir=""auto""><img alt=""\"" src=""https://cdn.shopify.com/s/files/1/0661/3450/7676/files/8341733762388_.pic.jpg?v=1733762633""></p>
                <p dir=""auto""> </p>
                <p dir=""auto"">我们遇到了一片里白蕨类植物，燕子就进去看看有没有蘑菇。</p>
                <p dir=""auto""><img alt=""\"" src=""https://cdn.shopify.com/s/files/1/0661/3450/7676/files/8441733762403_.pic.jpg?v=1733762634""></p>
                <p dir=""auto""> </p>
                <p dir=""auto"">我们甚至发现了一些野生茶树，并且很幸运地发现了本季的第一朵蘑菇。</p>
                <p dir=""auto""><img alt=""\"" src=""https://cdn.shopify.com/s/files/1/0661/3450/7676/files/8311733762384_.pic.jpg?v=1733762633""></p>
                <p dir=""auto""> </p>
                <p dir=""auto"">还有一些<span> 菝葜</span> ，燕子说它可以治疗癌症。</p>
                <p dir=""auto""><img alt=""\"" src=""https://cdn.shopify.com/s/files/1/0661/3450/7676/files/8301733762382_.pic.jpg?v=1733762633""></p>
                <p dir=""auto""> </p>
                <p dir=""auto"">当然，我们的小朋友炸毛（毛茸茸的小狗）也加入了我们。</p>
                <p dir=""auto""><img alt=""\"" src=""https://cdn.shopify.com/s/files/1/0661/3450/7676/files/8291733762381_.pic.jpg?v=1733762634""></p>
                <p dir=""auto""> </p>
                <p dir=""auto"">沿途风景优美</p>
                <p dir=""auto""><img alt=""\"" src=""https://cdn.shopify.com/s/files/1/0661/3450/7676/files/8221733762371_.pic.jpg?v=1733762634""></p>
                <p dir=""auto""><img alt=""\"" src=""https://cdn.shopify.com/s/files/1/0661/3450/7676/files/8211733762370_.pic.jpg?v=1733762633""></p>
                <p dir=""auto""><img alt=""\"" src=""https://cdn.shopify.com/s/files/1/0661/3450/7676/files/8241733762373_.pic.jpg?v=1733762633""></p>
                <p dir=""auto""><img alt=""\"" src=""https://cdn.shopify.com/s/files/1/0661/3450/7676/files/8201733762369_.pic.jpg?v=1733762633""></p>
                """;

        String result = translateNewHtml(html, new TranslateRequest(0, "fadsf", "asdf", "zh-CN", "ja", html), new CharacterCountUtils(), "product");
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
                "",                                             // 空字符串
                null                                            // null
        };

        for (String test : testCases) {
            boolean result = hasPlaceholders(test);
            String s = processTextWithPlaceholders(test, new CharacterCountUtils(), qwenMtCode("zh-CN"), qwenMtCode("en"));
            System.out.println("翻译后的文本： " + s);
            System.out.println("文本: \" + test + \" -> 包含占位符: " + result);
        }
    }

}
