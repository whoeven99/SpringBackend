package com.bogdatech.controller;

import com.bogdatech.integration.ShopifyHttpIntegration;
import com.bogdatech.model.controller.request.ShopifyRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ShopifyController {
    @Autowired
    private ShopifyHttpIntegration shopifyApiIntegration;

    @PostMapping("/shopify/getIdByShopifyRequest")
    public String getIdByShopifyRequest(@RequestBody ShopifyRequest shopifyRequest) {
        return shopifyApiIntegration.sendShopifyPost(shopifyRequest);
    }
}
