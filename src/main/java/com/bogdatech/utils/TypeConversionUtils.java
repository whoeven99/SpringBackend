package com.bogdatech.utils;

import com.bogdatech.context.TranslateContext;
import com.bogdatech.entity.DO.APGOfficialTemplateDO;
import com.bogdatech.entity.DO.APGUserTemplateDO;
import com.bogdatech.entity.DO.TranslateTextDO;
import com.bogdatech.entity.DO.UserSubscriptionsDO;
import com.bogdatech.entity.DTO.TemplateDTO;
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

    public static TranslateRequest registerTransactionRequestToTranslateRequest(RegisterTransactionRequest request) {
        TranslateRequest translateRequest = new TranslateRequest();
        translateRequest.setAccessToken(request.getAccessToken());
        translateRequest.setShopName(request.getShopName());
        translateRequest.setTarget(request.getTarget());
        translateRequest.setContent(request.getValue());
        translateRequest.setSource(request.getLocale());
        return translateRequest;
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


    //将用户模板转化为通用模板
    public static TemplateDTO userTemplateToTemplateDTO(APGUserTemplateDO templateDO){
        TemplateDTO templateDTO = new TemplateDTO();
        templateDTO.setId(templateDO.getId());
        templateDTO.setTemplateType(templateDO.getTemplateType());
        templateDTO.setTemplateData(templateDO.getTemplateData());
        templateDTO.setTemplateTitle(templateDO.getTemplateTitle());
        templateDTO.setTemplateDescription(templateDO.getTemplateDescription());
        templateDTO.setTemplateClass(true);
        return templateDTO;
    }

    //将官方模板转化为通用模板
    public static TemplateDTO officialTemplateToTemplateDTO(APGOfficialTemplateDO templateDO){
        TemplateDTO templateDTO = new TemplateDTO();
        templateDTO.setId(templateDO.getId());
        templateDTO.setTemplateType(templateDO.getTemplateType());
        templateDTO.setTemplateData(templateDO.getTemplateData());
        templateDTO.setTemplateTitle(templateDO.getTemplateTitle());
        templateDTO.setTemplateDescription(templateDO.getTemplateDescription());
        templateDTO.setTemplateClass(false);
        return templateDTO;
    }

}
