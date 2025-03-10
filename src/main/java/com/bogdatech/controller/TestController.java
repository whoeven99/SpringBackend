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
                {{ shop.name }}
                                
                Order {{ order.name }}

                {% if order.po_number != blank %}
                                
                PO number #{{ order.po_number }}
         
                {% endif %}
                                
                {{ order.created_at | date: format: "date" }}
                                
                {% if delivery_method.instructions != blank %} Delivery to {% else %} Ship to {% endif %}
                                
                {% if shipping_address != blank %} {{ shipping_address.name }} {% if shipping_address.company != blank %}
                                
                {{ shipping_address.company }} {% endif %}
                                
                {{ shipping_address.address1 }} {% if shipping_address.address2 != blank %}
                                
                {{ shipping_address.address2 }} {% endif %} {% if shipping_address.city_province_zip != blank %}
                                
                {{ shipping_address.city_province_zip }} {% endif %}
                                
                {{ shipping_address.country }} {% if shipping_address.phone != blank %}
                                
                {{ shipping_address.phone }} {% endif %} {% else %} No shipping address {% endif %}
                                
                Bill to
                                
                {% if billing_address != blank %} {{ billing_address.name }} {% if billing_address.company != blank %}
                                
                {{ billing_address.company }} {% endif %}
                                
                {{ billing_address.address1 }} {% if billing_address.address2 != blank %}
                                
                {{ billing_address.address2 }} {% endif %} {% if billing_address.city_province_zip != blank %}
                                
                {{ billing_address.city_province_zip }} {% endif %}
                                
                {{ billing_address.country }} {% else %} No billing address {% endif %}
                                
                Items
                                
                Quantity
                                
                {% comment %} To adjust the size of line item images, change desired_image_size. The other variables make sure your images print at high quality. {% endcomment %} {% assign desired_image_size = 58 %} {% assign resolution_adjusted_size = desired_image_size | times: 300 | divided_by: 72 | ceil %} {% capture effective_image_dimensions %} {{ resolution_adjusted_size }}x{{ resolution_adjusted_size }} {% endcapture %} {% for line_item in line_items_in_shipment %} {% if line_item.image != blank %}
                                
                {{ line_item.image | img_url: effective_image_dimensions | img_tag: '', 'aspect-ratio__content' }}
                                
                {% endif %}
                                
                {{ line_item.title }}
      
                {% if line_item.variant_title != blank %}
                                
                {{ line_item.variant_title }}
                                           
                {% endif %} {% if line_item.sku != blank %}
                                
                {{ line_item.sku }}
                                        
                {% endif %} {% for group in line_item.groups %}
                                
                Part of: {{ group.title }}
                                
                {% endfor %}
                                
                {{ line_item.shipping_quantity }} of {{ line_item.quantity }}
                                
                {% endfor %}
                                
                {% unless includes_all_line_items_in_order %} There are other items from your order not included in this shipment.
                                
                                
                                
                {% endunless %} {% if order.note != blank %}
                                
                Notes
                                      
                {{ order.note }}
                                
                {% endif %} {% if delivery_method.instructions != blank %}
                                
                Delivery instructions
      
                {{ delivery_method.instructions }}
                                
                {% endif %}
                                
                Thank you for shopping with us!
                                
                 {{ shop.name }}\s
                                
                {{ shop_address.summary }}
                                
                {{ shop.email }}
                                
                {{ shop.domain }}
                """;

        String result = translateNewHtml(html, new TranslateRequest(0, "fadsf", "asdf", "en", "zh-CN", html), new CharacterCountUtils(), "product");
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
