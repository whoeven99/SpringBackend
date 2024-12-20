package com.bogdatech.controller;


import com.alibaba.fastjson.JSONObject;
import com.bogdatech.Service.impl.TranslatesServiceImpl;
import com.bogdatech.entity.TranslatesDO;
import com.bogdatech.integration.ChatGptIntegration;
import com.bogdatech.integration.ShopifyHttpIntegration;
import com.bogdatech.model.controller.request.CloudServiceRequest;
import com.bogdatech.model.controller.request.ShopifyRequest;
import com.microsoft.applicationinsights.TelemetryClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
public class TestController {
    private final TranslatesServiceImpl translatesServiceImpl;
    private final ChatGptIntegration chatGptIntegration;
    private final ShopifyHttpIntegration shopifyApiIntegration;

    @Autowired
    public TestController(TranslatesServiceImpl translatesServiceImpl, ChatGptIntegration chatGptIntegration, ShopifyHttpIntegration shopifyApiIntegration) {
        this.translatesServiceImpl = translatesServiceImpl;
        this.chatGptIntegration = chatGptIntegration;
        this.shopifyApiIntegration = shopifyApiIntegration;
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

    //微软翻译API(待删）
//    @PostMapping("/testAzure")
//    public String testAzure(@RequestBody TranslateRequest request) {
//        return translateApiIntegration.microsoftTranslate(request);
//    }

    //火山翻译API(待删）
//    @PostMapping("/testVolcano")
//    public String testVolcano(@RequestBody TranslateRequest request) {
//        return translateApiIntegration.huoShanTranslate(request);
//    }

//    @PostMapping("/test")
//    public String test(@RequestBody String content) {
//
////        try {
////            // 使用 JSoup 将 HTML 内容转换为 Document
////            Document document = Jsoup.parse(content);
////            // 获取纯文本内容，去掉 HTML 标签
////            String textContent = document.text();
////            System.out.println("text: " + textContent);
////            return new ResponseEntity<>(textContent, HttpStatus.OK);
////        } catch (Exception e) {
////            return new ResponseEntity<>("Failed to parse HTML content", HttpStatus.INTERNAL_SERVER_ERROR);
////        }
////        return jsoupUtils.isHtml(content);
//        return jsoupUtils.translateHtml(content, new TranslateRequest(0,null, null, "en", "zh-CN", content ), new CharacterCountUtils(), new AILanguagePacksDO(0,"General", null, "Accurately translate the product data of the e-commerce website into zh-CN. No additional text is required.Please keep the text format unchanged.Punctuation should be consistent with the original text.Translate: " + content, 1));
//    }
}
