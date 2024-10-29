package com.bogdatech.controller;

import com.bogdatech.integration.ShopifyHttpIntegration;
import com.bogdatech.model.controller.request.ShopifyRequest;
import com.bogdatech.query.ShopifyQuery;
import com.bogdatech.query.TestQuery;
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
        return shopifyApiIntegration.sendShopifyPost(shopifyRequest, TestQuery.TEST_QUERY);
    }

    @PostMapping("/shopify/getInfoByShopify")
    public String getInfoByShopify(@RequestBody ShopifyRequest shopifyRequest) {
        return shopifyApiIntegration.getInfoByShopify(shopifyRequest, ShopifyQuery.PRODUCT2_QUERY);
    }

}
