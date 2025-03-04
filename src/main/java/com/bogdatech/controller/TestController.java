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
import static com.bogdatech.utils.JsoupUtils.isHtml;
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
    public void testIsHTML(){
        String string = "<ul><li> <a href=\"/products/hiking-to-boluo-temple-and-enjoy-the-vegan-food-%E5%A4%8D%E5%88%B6\" title=\"松林轻松徒步 | 农田 · 小溪 · 森林\">轻松徒步经过苍山溪流，进入森林</a></li><li><a href=\"/products/picking-mushroom-dinner-in-village-family\" title=\"采蘑菇和在乡村家庭共进晚餐\">夏季（6-10月）和采摘组的姑娘们去森林里采蘑菇</a></li><li><a href=\"/products/star-gazing-boat-trip-on-the-most-beautiful-lake-in-dali\" title=\"观星 | 大理最美湖泊游船之旅\">和阿土一起坐船去湖上观星</a></li><li><a href=\"/products/sunset-light-hiking-at-huoshan-mountain-top\" title=\"绕湖半圈，在山顶看日落\">在山顶观看壮丽的苍山日落和开阔的洱海</a></li><li><a href=\"/products/%E6%B4%B1%E6%BA%90%E4%B9%A1%E6%9D%91%E6%97%85%E8%A1%8C-%E5%9D%90%E8%88%B9-%E6%B8%A9%E6%B3%89%E5%92%8C%E7%A9%86%E6%96%AF%E6%9E%97%E6%9D%91%E5%BA%84%E6%95%A3%E6%AD%A5\" title=\"大理洱源乡村游\">乘船游洱源乡野，穆斯林村庄，观看乳扇制作，享受温泉</a></li><li><a href=\"/products/a-day-with-the-mountain-village-family\" title=\"深山里“亲戚”家的一天 | 苍山的另一边\">去深山里探访“亲戚”家，品尝山里美食，去原始森林，牧场徒步</a></li><li><a href=\"/products/go-to-local-traditional-market-ancient-town-in-a-valley-every-tuesday\" title=\"去凤羽传统市集赶集 |  山谷里的茶马古镇 (每周二)\">每周二，去凤羽传统市集赶集，拜访山谷里的茶马古镇</a></li><li><a href=\"/products/birdwatching-on-a-boat-trip\" title=\"乘船观鸟\">冬季乘船游览，观赏数百只西伯利亚鸟类</a></li><li><a href=\"/products/paddleboardsup-to-the-lake-at-the-foot-of-the-mountain\" title=\"桨板 | 去山下美丽的湖泊\">划桨板 | 去山下美丽的湖泊，深入芦苇荡</a></li><li><a href=\"/products/picking-plant-in-the-mountains-natural-dyeing-by-the-creek\" title=\"Mini徒步去山间采摘 | 溪边拓染\">在山间采集植物 | 在叮咚流水的溪边拓染</a>  或者 <a href=\"/products/making-moss-jars-in-the-forest\" title=\"去森林里制作苔藓瓶\">制作一个苔藓瓶</a>，都可以带回家</li><li><a href=\"/products/hiking-in-the-fern-forest\" title=\"徒步蕨类森林（中距离）\">徒步进入蕨类森林，认识这种神奇的植物</a></li><li><a href=\"/products/f\" title=\"自然观察Mini徒步 | 鸟类，植物和昆虫\">自然观察 | 和菲比上山认识鸟类，植物和昆虫</a></li><li><a href=\"/products/cycling-around-mountain-and-lake-exploring-villages-and-people\" title=\"骑行苍山洱海 | 探寻村落与人文\">乡村骑行 | 上苍山，下洱海，探寻村落与人</a></li><li><a href=\"/products/picking-mushroom-dinner-in-village-family-复制\" title=\"森林野道捡松果 | 徒步后寂照庵吃素斋\">森林野道捡松果 | 徒步后寂照庵吃素斋</a></li><li>去云工开物的院子，给你的<a href=\"/products/woodblock-carving-for-your-pet\" title=\"给你的宠物刻一块版画\">宠物刻一块版画</a>，<a href=\"/products/making-a-d-tuned-short-chinese-flute-by-yourself\" title=\"自己做一管D调短箫\">做一管短箫</a>，或者<a href=\"/products/kintsugi\" title=\"做一日金缮师\">学习下金缮</a>，<a href=\"/products/horse-and-armour-block-printing-experience-blessings-under-the-carving-knife-复制-2\" title=\"自己打磨一颗犀珠\">磨一颗犀珠</a></li><li>和李真好<a href=\"/products/making-a-plant-wall-art-for-your-home\" title=\"用植物和果实制作一幅画框\">用植物和果实制作一幅画框</a>，或者<a href=\"/products/mushroom-specimens-lamp-making\" title=\"制作「蘑菇灯」| 用自己森林里采集的蘑菇\">一盏蘑菇小夜灯</a></li><li>和景山自己采集泥土，<a href=\"/products/pottery-meets-plants-and-painting\" title=\"陶艺遇上植物和绘画\">制作一件可以日常用的陶器</a>，或者<a href=\"/products/horse-and-armour-block-printing-experience-blessings-under-the-carving-knife-复制\" title=\"制作陶土风铃：景山陶艺工作坊\">陶土风铃</a></li><li>在金工作坊，<a href=\"/products/make-a-silver-ring-by-yourself\" title=\"制作一枚自己的银戒指\">制作一枚自己的银戒指</a>，染一块蓝染茶垫</li><li><a href=\"/products/bafel\" title=\"与Aruna一起制作贝果\">与Aruna一起制作贝果</a>，或者<a href=\"/products/dali-flower-cake-making-experience-with-aruna\" title=\"和Aruna学做大理玫瑰鲜花饼\">当地的鲜花饼</a></li><li>在山谷里的茶园，<a href=\"/products/horse-and-armour-block-printing-experience-blessings-under-the-carving-knife-复制-1\" title=\"（4月-11月）采茶炒茶 | 溪畔的茶园\">春夏去采茶</a>， 四季可以<a href=\"/products/tea-roasting-tasting-experience-mo-cui-tea-house-复制\" title=\"做一饼自己的普洱茶\">做普洱茶饼</a>，冬天<a href=\"/products/april-nov-picking-and-make-tea-tea-garden-by-the-valley-stream-复制\" title=\"去森林里的茶社品尝烤茶\">烤一壶茶和朋友相聚</a></li></ul><p><strong>春天：</strong></p><ul><li>二月，<a href=\"/products/土蜂蜜\" title=\"松鹤村毛哥家 「梅树林里的土蜂蜜」 500克\">去看松鹤村的梅花，现场采割野蜂蜜</a></li><li>四月开始，<a href=\"/products/horse-and-armour-block-printing-experience-blessings-under-the-carving-knife-复制-1\" title=\"（4月-11月）采茶炒茶 | 溪畔的茶园\">去山谷里的茶园采春茶，做茶</a></li><li>六月开始，<a href=\"/products/picking-mushroom-dinner-in-village-family\" title=\"（6-10月）上山采蘑菇 | 在农家吃菌子餐\">和采摘组的姑娘们去森林里采菌子，下山在村子里吃农家饭</a></li></ul><p><strong>夏天：</strong></p><ul><li><a href=\"/products/shangri-la-tibet-area-grassland-camping-ranches-and-rivers\" title=\"（5月-10月）2天香格里拉（西藏地区）露营\">去香格里拉草原露营 5-10月</a></li><li><a href=\"/products/night-insect-observation-at-the-foot-of-cangshan\" title=\"（仅在夏季）夜间昆虫观察 | 苍山脚下\">夜间昆虫观察 | 苍山脚下（7-10月）</a></li><li><a href=\"/products/paddleboard-into-the-lake-at-the-mountain-foot-复制-1\" title=\"（6月、7月）深山采梅子 | 酿一瓶青梅酒\">青梅成熟季节，去山里采摘，泡一罐青梅酒6-7月</a></li></ul><p><strong>秋天：</strong></p><ul><li><a href=\"/products/2-days-shangri-la-tibet-area-grassland-camping-ranches-and-rivers-spring-summer-only-复制\" title=\"怒江边2天露营（靠近缅甸边境）\">去怒江畔露营，去看高黎贡山和咖啡豆</a></li></ul><p>冬天：</p><ul><li><a href=\"/products/birdwatching-on-a-boat-trip\" title=\"湖上坐船观鸟的旅行 ｜去自然的深处（周二，周六）\">候鸟到来，和王斌去湖上坐船观鸟</a></li><li><a href=\"/products/star-gazing-boat-trip-on-the-most-beautiful-lake-in-dali\" title=\"和阿土去观星 | 坐船在夜晚的星空之下的湖上\">冬天晴朗，星空灿烂，和阿土去观星</a></li><li><a href=\"/products/june-july-picking-plums-in-the-deep-mountains-making-a-bottle-of-green-plum-wine-复制\" title=\"（1 月末-2 月中旬）徒步山里的梅花林·农家采割土蜂蜜\">梅花盛开，去梅花林的村庄徒步</a><br/></li></ul><p><strong>如果有时间，可以去远一些的地方：</strong></p><ul><li>沙溪，茶马古道上的偏远古镇，山上的佛教石窟，住我们朋友们的家</li><li><a href=\"/products/exploring-nodeng-a-journey-into-an-ancient-mountain-village\" title=\"探索诺登：2天古老山村之旅\">诺邓，一个因盐而繁荣的山，自然和历史都值得感受</a></li><li><a href=\"/products/3-days-shangri-la-shaxi-ancient-town\" title=\"3 天的旅行 | 香格里拉 和 沙溪古镇\">香格里拉和沙溪三天，走进真正的西藏草原生活，回程住沙溪古镇的村子里</a></li></ul><p>更多选择活动的方式：<a href=\"/blogs/data/select\" title=\"根据「年龄，类型，时长，距离大理远近，有没有小狗」选择活动\">根据年龄，活动时间，强度，距离选择活动</a></p>";
        if (isHtml(string)){
            System.out.println("是html");
        }
    }

    @GetMapping("/testPlaceholder")
    public void testPlaceholder(){
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
            System.out.println("文本: \"" + test + "\" -> 包含占位符: " + result);
        }
    }

}
