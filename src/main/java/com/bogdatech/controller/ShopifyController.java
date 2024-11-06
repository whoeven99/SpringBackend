package com.bogdatech.controller;

import com.alibaba.fastjson.JSONObject;
import com.bogdatech.integration.ShopifyHttpIntegration;
import com.bogdatech.integration.TestingEnvironment;
import com.bogdatech.model.controller.request.ShopifyRequest;
import com.bogdatech.model.controller.request.TranslateTextRequest;
import com.bogdatech.query.ShopifyQuery;
import com.bogdatech.repository.JdbcRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ShopifyController {
    @Autowired
    private ShopifyHttpIntegration shopifyApiIntegration;

    @Autowired
    private JdbcRepository jdbcRepository;

    @PostMapping("/shopify/test")
    public void test(@RequestBody TranslateTextRequest request) {
        int i = jdbcRepository.insertTranslateText(request);
        if (i > 0) {
            System.out.println("insert success");
        }else {
            System.out.println("insert fail");
        }
    }

    @GetMapping("/test123")
    public String test() {
        ShopifyRequest shopifyRequest = new ShopifyRequest();
        shopifyRequest.setShopName("quickstart-0f992326.myshopify.com");
        shopifyRequest.setAccessToken("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");
        shopifyRequest.setTarget("it");
        ShopifyQuery query = new ShopifyQuery();
        String query2 = query.test();
        JSONObject infoByShopify = shopifyApiIntegration.getInfoByShopify(shopifyRequest, query2);
        return infoByShopify.toString();
    }

    @GetMapping("/testPing")
    public String testPing(@RequestBody ShopifyRequest shopifyRequest) {
        TestingEnvironment testingEnvironment = new TestingEnvironment();
        ShopifyQuery query = new ShopifyQuery();
        String query2 = query.test();
        String string = testingEnvironment.sendShopifyPost(shopifyRequest);
        return string;
    }

}
