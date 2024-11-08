package com.bogdatech.utils;

import com.bogdatech.model.controller.request.ShopifyRequest;
import com.bogdatech.model.controller.request.TranslateRequest;
import com.bogdatech.model.controller.request.TranslationCounterRequest;

public class TypeConversionUtils {

    public static ShopifyRequest convertTranslateRequestToShopifyRequest(TranslateRequest request) {
        ShopifyRequest shopifyRequest = new ShopifyRequest();
        shopifyRequest.setShopName(request.getShopName());
        shopifyRequest.setAccessToken(request.getAccessToken());
        shopifyRequest.setTarget(request.getTarget());
        return shopifyRequest;
    }

    public static TranslationCounterRequest convertTranslateRequestToTranslationCounterRequest(TranslateRequest request) {
        TranslationCounterRequest translationCounterRequest = new TranslationCounterRequest();
        translationCounterRequest.setShopName(request.getShopName());
        return translationCounterRequest;
    }
}
