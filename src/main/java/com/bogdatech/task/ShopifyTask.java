package com.bogdatech.task;

import com.bogdatech.logic.ShopifyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
@EnableAsync
public class ShopifyTask {
    @Autowired
    private ShopifyService shopifyService;


}
