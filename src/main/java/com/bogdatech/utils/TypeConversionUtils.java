package com.bogdatech.utils;

import com.bogdatech.context.TranslateContext;
import com.bogdatech.entity.DO.TranslateTextDO;
import com.bogdatech.entity.DO.UserSubscriptionsDO;
import com.bogdatech.entity.VO.RabbitMqTranslateVO;
import com.bogdatech.model.controller.request.*;

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

    public static TranslateRequest registerTransactionRequestToTranslateRequest(RegisterTransactionRequest request) {
        TranslateRequest translateRequest = new TranslateRequest();
        translateRequest.setAccessToken(request.getAccessToken());
        translateRequest.setShopName(request.getShopName());
        translateRequest.setTarget(request.getTarget());
        translateRequest.setContent(request.getValue());
        translateRequest.setSource(request.getLocale());
        return translateRequest;
    }

    public static TranslateTextRequest registerTransactionRequestToTranslateTextRequest(RegisterTransactionRequest registerTransactionRequest) {
        TranslateTextRequest request = new TranslateTextRequest();
        request.setShopName(registerTransactionRequest.getShopName());
        request.setTargetText(registerTransactionRequest.getValue());
        request.setDigest(registerTransactionRequest.getTranslatableContentDigest());
        request.setTargetCode(registerTransactionRequest.getTarget());
        request.setResourceId(registerTransactionRequest.getResourceId());
        request.setSourceCode(registerTransactionRequest.getLocale());
        request.setTextKey(registerTransactionRequest.getKey());
        return request;
    }

    public static TranslateTextDO registerTransactionRequestToTranslateTextDO(RegisterTransactionRequest registerTransactionRequest) {
        TranslateTextDO request = new TranslateTextDO();
        request.setShopName(registerTransactionRequest.getShopName());
        request.setTargetText(registerTransactionRequest.getValue());
        request.setDigest(registerTransactionRequest.getTranslatableContentDigest());
        request.setTargetCode(registerTransactionRequest.getTarget());
        request.setResourceId(registerTransactionRequest.getResourceId());
        request.setSourceCode(registerTransactionRequest.getLocale());
        request.setTextKey(registerTransactionRequest.getKey());
        return request;
    }

    public static CloudServiceRequest shopifyToCloudServiceRequest(ShopifyRequest request) {
        CloudServiceRequest cloudServiceRequest = new CloudServiceRequest();
        cloudServiceRequest.setShopName(request.getShopName());
        cloudServiceRequest.setAccessToken(request.getAccessToken());
        cloudServiceRequest.setTarget(request.getTarget());
        return cloudServiceRequest;
    }

    public static CloudServiceRequest translateRequestToCloudServiceRequest(TranslateRequest request) {
        CloudServiceRequest cloudServiceRequest = new CloudServiceRequest();
        cloudServiceRequest.setShopName(request.getShopName());
        cloudServiceRequest.setAccessToken(request.getAccessToken());
        cloudServiceRequest.setTarget(request.getTarget());
        return cloudServiceRequest;
    }

    public static ShopifyRequest resourceTypeRequestToShopifyRequest(ResourceTypeRequest request) {
        ShopifyRequest shopifyRequest = new ShopifyRequest();
        shopifyRequest.setShopName(request.getShopName());
        shopifyRequest.setAccessToken(request.getAccessToken());
        shopifyRequest.setTarget(request.getTarget());
        return shopifyRequest;
    }

    public static UserSubscriptionsDO UserSubscriptionsRequestToUserSubscriptionsDO(UserSubscriptionsRequest request){
        UserSubscriptionsDO userSubscriptionsDO = new UserSubscriptionsDO();
        userSubscriptionsDO.setShopName(request.getShopName());
        userSubscriptionsDO.setPlanId(request.getPlanId());
        userSubscriptionsDO.setStartDate(request.getStartDate());
        userSubscriptionsDO.setEndDate(request.getEndDate());
        userSubscriptionsDO.setStatus(request.getStatus());
        return userSubscriptionsDO;
    }

    public static ShopifyRequest RegisterTransactionRequestToShopifyRequest(RegisterTransactionRequest transaction){
        ShopifyRequest shopifyRequest = new ShopifyRequest();
        shopifyRequest.setShopName(transaction.getShopName());
        shopifyRequest.setAccessToken(transaction.getAccessToken());
        shopifyRequest.setTarget(transaction.getTarget());
        return shopifyRequest;
    }

    public static TranslateRequest ClickTranslateRequestToTranslateRequest(ClickTranslateRequest request){
        TranslateRequest translateRequest = new TranslateRequest();
        translateRequest.setSource(request.getSource());
        translateRequest.setTarget(request.getTarget());
        translateRequest.setAccessToken(request.getAccessToken());
        translateRequest.setShopName(request.getShopName());
        return translateRequest;
    }

    public static TranslateRequest TargetListRequestToTranslateRequest(TargetListRequest targetListRequest){
        TranslateRequest translateRequest = new TranslateRequest();
        translateRequest.setAccessToken(targetListRequest.getAccessToken());
        translateRequest.setShopName(targetListRequest.getShopName());
        translateRequest.setSource(targetListRequest.getSource());
        return translateRequest;
    }

    //将TranslateContext转化为RabbitMqTranslateVO
    public static RabbitMqTranslateVO translateContextToRabbitMqTranslateVO(TranslateContext translateContext){
        RabbitMqTranslateVO rabbitMqTranslateVO = new RabbitMqTranslateVO();
        rabbitMqTranslateVO.setSource(translateContext.getSource());
        rabbitMqTranslateVO.setTarget(translateContext.getShopifyRequest().getTarget());
        rabbitMqTranslateVO.setAccessToken(translateContext.getShopifyRequest().getAccessToken());
        rabbitMqTranslateVO.setShopName(translateContext.getShopifyRequest().getShopName());
        rabbitMqTranslateVO.setGlossaryMap(translateContext.getGlossaryMap());
        rabbitMqTranslateVO.setHandleFlag(translateContext.getHandleFlag());
        rabbitMqTranslateVO.setLanguagePack(translateContext.getLanguagePackId());
        rabbitMqTranslateVO.setShopifyData(translateContext.getShopifyData());
        rabbitMqTranslateVO.setModeType(translateContext.getTranslateResource().getResourceType());
        return rabbitMqTranslateVO;
    }
}
