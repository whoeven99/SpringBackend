package com.bogdatech.controller;

import com.bogdatech.integration.ShopifyHttpIntegration;
import com.bogdatech.model.controller.request.TranslateTextRequest;
import com.bogdatech.repository.JdbcRepository;
import org.springframework.beans.factory.annotation.Autowired;
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
}
