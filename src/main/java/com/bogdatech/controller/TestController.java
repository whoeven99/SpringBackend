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
import com.bogdatech.utils.CharacterCountUtils;
import com.microsoft.applicationinsights.TelemetryClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

import static com.bogdatech.integration.RateHttpIntegration.rateMap;
import static com.bogdatech.logic.TranslateService.SINGLE_LINE_TEXT;
import static com.bogdatech.logic.TranslateService.addData;
import static com.bogdatech.utils.JsonUtils.isJson;
import static com.bogdatech.utils.JsoupUtils.isHtml;
import static com.bogdatech.utils.JudgeTranslateUtils.TRANSLATABLE_KEY_PATTERN;

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
                 {"custom_css":"\\n        .freegifts-main-container .fg-section-title, .freegifts-main-container .bogos-slider-info-title { color: #121212; }\\n        .freegifts-main-container .product-title, .freegifts-main-container .bogos-gift-product-title { color: #000000; }\\n        .freegifts-main-container .original-price, .freegifts-main-container .bogos-gift-item-compare-price { color: #121212; }\\n        .freegifts-main-container .gift-price, .freegifts-main-container .bogos-gift-item-price { color: #ea5455; }\\n        .freegifts-main-container .btn-add-to-cart { color: #FFFFFF; background-color: #7367f0; }\\n        .freegifts-main-container .bogos-slider-offer-badge { background: #FFEF9D }\\n        .freegifts-main-container .bogos-slider-offer-badge.success { background: #CDFEE1 }\\n        .freegifts-main-container .bogos-slider-offer-title { color: #000000 }\\n        .freegifts-main-container .btn-add-to-cart svg path { fill: #FFFFFF; }\\n        \\n        .fg-gift-thumbnail-offer-title { color: #000000; }\\n        .fg-gift-thumbnail-container { border-color: #8A8A8A; }\\n        .fg-gift-thumbnail-offer-time { background-color: #000000; }\\n        #sca-gift-thumbnail .sca-gift-image { width: 50px; max-height: 50px; }\\n\\n        #sca-gift-icon .sca-gift-icon-img { width: 50px; max-height: 50px; }\\n        .sca-gift-icon-collection-page .sca-gift-icon-collection-img { width: 50px; max-height: 50px; }\\n\\n        #sca-promotion-glider { color: #ffffff; background-color: #F72119; }\\n\\n        #sca-fg-today-offer-iframe .sca-fg-header { background-color: #FFFFFF; }\\n        #sca-fg-today-offer-iframe .sca-fg-body { background-color: #FFFFFF; }\\n        #sca-fg-today-offer-iframe .sca-fg-today-offer-title { color: #303030 !important; }\\n        #sca-fg-today-offer-iframe .sca-fg-today-offer-subtitle { color: #616161 !important; }\\n        #sca-fg-today-offer-iframe .sca-offer-title { color: #303030; }\\n        #sca-fg-today-offer-widget { height: 70px !important; width: 70px !important; }\\n        .sca-fg-icon-success-anim { box-shadow: inset 0 0 0 #24B263; }\\n        @-webkit-keyframes animated-checkmark-fill { to { box-shadow: inset 0 0 0 30px #24B263; } }\\n        @keyframes animated-checkmark-fill { to { box-shadow: inset 0 0 0 30px #24B263; } }\\n        .sca-fg-icon-success-circle { stroke: #24B263; }\\n        #sca-fg-today-offer-iframe .sca-fg-offer .sca-offer-header-container { background-color: #F7F7F7 }\\n        #sca-fg-today-offer-iframe .sca-fg-offer.sca-offer-archived .sca-offer-header-container { background-color: #24B26325 }\\n        #sca-fg-today-offer-iframe .sca-gift-product-title { color:  #303030}\\n        #sca-fg-today-offer-iframe .sca-gift-product-discount-price { color: #24B263 }\\n        #sca-fg-today-offer-iframe .sca-gift-product-original-price { color: #616161 }\\n        #sca-fg-today-offer-iframe .sca-offer-info .sca-offer-subtitle { color: #616161 }\\n\\n        .bogos-bundles-widget { background-color: #F3F3F3;  }\\n        .bogos-bundles-widget-body .bogos-bundle-item { background-color: #FFFFFF; }\\n        .bogos-bundles-widget .bogos-bundles-widget-title { color: #303030; }\\n        .bogos-bundles-widget .bogos-bundles-widget-description { color: #616161; }\\n        .bogos-bundle-item .bogos-bundle-item-title { color: #303030; }\\n        .bogos-bundle-item .bogos-bundle-item-discount-price, .bogos-bundles-widget .bogos-bundles-total-discount-price { color: #005BD3; }\\n        .bogos-bundle-item .bogos-bundle-item-original-price, .bogos-bundles-widget .bogos-bundles-total-original-price { color: #005BD3; }\\n        .bogos-bundles-widget-footer .bogos-bundles-button-add { color: #FFFFFF; background-color: #303030; }\\n\\n        .bogos-bundles-quantity-break-widget { background-color: #F3F3F3;  }\\n        .bogos-bundles-quantity-break-widget-title { color: #303030; }\\n        .bogos-bundles-quantity-break-widget-description { color: #303030; }\\n        .bogos-bundle-quantity-break-item-original-price , .bogos-bundles-quantity-break-origin-price { color: #303030; }\\n        .bogos-bundles-quantity-break-button-add { background: #303030; color: #FFFFFF; }\\n        .bogos-bundle-quantity-break_item-container { background: #FFFFFF; }\\n        .bogos-bundle-quantity-break-label { background: #303030; color: #FFFFFF; }\\n        .bogos-bundle-quantity-break-tag { background: #F1F1F1; color: #303030; }\\n        .bogos-bundle-quantity-break-sub-title { color: #616161; }\\n        .bogos-bundle-quantity-break-item-discount-price, .bogos-bundles-quantity-break-discount-price { color: #303030; }\\n        .bogos-bundle-quantity-break-title { color: #303030; }\\n        \\n        .bogos-mix-match-widget { background-color: #F3F3F3; ; }\\n        .bogos-mix-match-widget .bogos-mix-item { background-color: #FFFFFF; }\\n        .bogos-mix-match-widget .bogos-mix-match-widget-title { color: #303030; }\\n        .bogos-mix-match-widget .bogos-mix-match-widget-description { color: #616161; }\\n        .bogos-mix-match-widget .bogos-mix-item-title { color: #303030; }\\n        .bogos-mix-match-widget .bogos-mix-item-discount-price { color: #303030; }\\n        .bogos-mix-match-widget .bogos-mix-item-original-price { color: #B5B5B5; }\\n        .bogos-mix-match-widget .bogos-mix-match-button-add { color: #FFFFFF; background-color: #303030; }\\n        .bogos-mix-match-widget .bogos-mix-match-badge-item { background-color: #FFF8DB; }\\n        .bogos-mix-match-widget .bogos-mix-match-badge-item.success { background-color: #CDFEE1; }\\n        .bogos-mix-match-widget .bogos-mix-match-badge-item .bogos-mix-match-badge-title { color: #4F4700; }\\n        .bogos-mix-match-widget .bogos-mix-match-badge-item.success .bogos-mix-match-badge-title { color: #29845A; }\\n        \\n        .bogos-bundle-page-container .bogos-bp-steps-bar, .bogos-bundle-page-container .bogos-bp-step-bar-single { background-color: #F3F3F3; }\\n        .bogos-bundle-page-container .bogos-bp-step-title { color: #303030; }\\n        .bogos-bp-steps-container .bogos-bp-step-item.active { border-bottom: solid 3px #303030; }\\n        .bogos-bundle-page-container .bogos-bp-header-title { color: #303030; }\\n        .bogos-bundle-page-container .bogos-bp-header-subtitle { color: #303030; }\\n        .bogos-bundle-page-container .bogos-bp-step-header-title { color: #303030; }\\n        .bogos-bundle-page-container .bogos-bp-step-header-subtitle { color: #616161; }\\n        .bogos-bundle-page-container .bogos-bp-product-title, .bogos-step-item .bogos-step-item-title, .bogos-product-detail-modal .bogos-product-title { color:  #303030; }\\n        .bogos-step-items-container .bogos-step-item-variant-title { color: #616161; }\\n        .bogos-bundle-page-container .bogos-bp-product-price, .bogos-step-item .bogos-step-item-discount-price, .bogos-bp-widget-footer .bogos-bp-total-discount-price {  color:  #303030; }\\n        .bogos-step-item .bogos-step-item-original-price, .bogos-bp-widget-footer .bogos-bp-total-original-price { color:  #808080; }\\n        .bogos-bundle-page-container .bogos-bp-btn-add-product, .bogos-product-detail-modal .bogos-product-add-btn { background-color: #303030; color: #FFFFFF }\\n        .bogos-bp-widget-container .bogos-bp-widget { background-color: #F3F3F3; ; }\\n        .bogos-bp-widget .bogos-bp-widget-title { color: #303030; }\\n        .bogos-bp-widget .bogos-bp-widget-description { color: #303030; }\\n        .bogos-step-items-product-require.success .bogos-step-items-product-require-title { color: #2332D5; }\\n        .bogos-bp-widget .bogos-bp-button-add { background-color: #303030; color: #FFFFFF; }\\n        .bogos-bp-widget-badges-container .bogos-bp-widget-badge-item { background-color: #FFF8DB; }\\n        .bogos-bp-widget-badges-container .bogos-bp-widget-badge-item.success { background-color: #CDFEE1; }\\n        .bogos-bp-widget-badges-container .bogos-bp-widget-badge-title { color: #4F4700; }\\n        .bogos-bp-widget-badge-item.success .bogos-bp-widget-badge-title { color: #29845A; }\\n        ","shopify_gift_icon_path":"fg-icon-red.png"}
                """;
        if (isHtml(html)){
            System.out.println("is html");
        }else {
            System.out.println("is not html");
        }

        if (isJson(html)){
            System.out.println("is json");
        }else {
            System.out.println("is not json");
        }
//        String s = translateNewHtml(html, new TranslateRequest(0, "shop", "token", "en", "zh-CN", ""), new CharacterCountUtils(), "en");
//        System.out.println("final: " + s);
    }

    @PutMapping("/testAutoTranslate")
    public void testAutoTranslate() {
        taskService.autoTranslate();
    }
}
