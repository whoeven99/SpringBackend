package com.bogdatech.controller;

import com.bogdatech.integration.ShopifyHttpIntegration;
import com.bogdatech.model.controller.request.ShopifyRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ShopifyController {
    @Autowired
    private ShopifyHttpIntegration shopifyApiIntegration;

    @GetMapping("/shopify/getIdByShopifyRequest")
    public String getIdByShopifyRequest() {
        return shopifyApiIntegration.sendShopifyGet(new ShopifyRequest());
    }
}
