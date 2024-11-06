package com.bogdatech.logic;

import com.bogdatech.integration.ShopifyHttpIntegration;
import com.bogdatech.model.controller.request.ShopifyRequest;
import com.bogdatech.repository.JdbcRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

@Component
public class ShopifyService {

    @Autowired
    private ShopifyHttpIntegration shopifyApiIntegration;

    @Autowired
    private JdbcRepository jdbcRepository;

    public String shopifyApi(@RequestBody ShopifyRequest shopifyRequest, String query,  Map<String, Object> variables) {
        String string1 = shopifyApiIntegration.sendShopifyPost(shopifyRequest, query, variables);
        return string1;
    }
}
