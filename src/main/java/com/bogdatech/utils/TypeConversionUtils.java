package com.bogdatech.utils;

import com.bogdatech.entity.DO.*;
import com.bogdatech.entity.DTO.TemplateDTO;
import com.bogdatech.entity.VO.GenerateDescriptionVO;
import com.bogdatech.entity.VO.GenerateDescriptionsVO;
import com.bogdatech.entity.VO.GenerateProgressBarVO;
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
        translateRequest.setTarget(request.getTarget()[0]);
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
        templateDTO.setTemplateModel(templateDO.getTemplateModel());
        templateDTO.setTemplateSubtype(templateDO.getTemplateSubtype());
        templateDTO.setTemplateClass(true);
        return templateDTO;
    }

    //将官方模板转化为通用模板
    public static TemplateDTO officialTemplateToTemplateDTO(APGOfficialTemplateDO templateDO){
        TemplateDTO templateDTO = new TemplateDTO();
        templateDTO.setIsPayment(templateDO.getIsPayment());
        templateDTO.setId(templateDO.getId());
        templateDTO.setTemplateType(templateDO.getTemplateType());
        templateDTO.setTemplateData(templateDO.getTemplateData());
        templateDTO.setTemplateTitle(templateDO.getTemplateTitle());
        templateDTO.setTemplateDescription(templateDO.getTemplateDescription());
        templateDTO.setTemplateModel(templateDO.getTemplateModel());
        templateDTO.setTemplateSubtype(templateDO.getTemplateSubtype());
        templateDTO.setTemplateClass(false);

        return templateDTO;
    }

    /**
     * 将generateDescriptionsVO转化为generateDescriptionVO
     */
    public static GenerateDescriptionVO generateDescriptionsVOToGenerateDescriptionVO(String productId,GenerateDescriptionsVO generateDescriptionsVO){
        GenerateDescriptionVO generateDescriptionVO = new GenerateDescriptionVO();
        generateDescriptionVO.setTemplateType(generateDescriptionsVO.getTemplateType());
        generateDescriptionVO.setTemplateId(generateDescriptionsVO.getTemplateId());
        generateDescriptionVO.setModel(generateDescriptionsVO.getModel());
        generateDescriptionVO.setLanguage(generateDescriptionsVO.getLanguage());
        generateDescriptionVO.setBrandSlogan(generateDescriptionsVO.getBrandSlogan());
        generateDescriptionVO.setBrandTone(generateDescriptionsVO.getBrandTone());
        generateDescriptionVO.setBrandWord(generateDescriptionsVO.getBrandWord());
        generateDescriptionVO.setSeoKeywords(generateDescriptionsVO.getSeoKeywords());
        generateDescriptionVO.setTextTone(generateDescriptionsVO.getTextTone());
        generateDescriptionVO.setProductId(productId);
        generateDescriptionVO.setContentType(generateDescriptionsVO.getContentType());
        generateDescriptionVO.setPageType(generateDescriptionsVO.getPageType());
        return generateDescriptionVO;
    }

    /**
     * 将APGUserGeneratedTaskDO转化为GenerateProgressBarVO
     * */
    public static GenerateProgressBarVO apgUserGeneratedTaskDOToGenerateProgressBarVO(APGUserGeneratedTaskDO apgUserGeneratedTaskDO, Integer totalCount, Integer unfinishedCount){
        GenerateProgressBarVO generateProgressBarVO = new GenerateProgressBarVO();
        generateProgressBarVO.setAllCount(totalCount);
        generateProgressBarVO.setUnfinishedCount(unfinishedCount);
        generateProgressBarVO.setTaskStatus(apgUserGeneratedTaskDO.getTaskStatus());
        generateProgressBarVO.setTaskModel(apgUserGeneratedTaskDO.getTaskModel());
        generateProgressBarVO.setTaskData(apgUserGeneratedTaskDO.getTaskData());
        generateProgressBarVO.setUserId(apgUserGeneratedTaskDO.getUserId());
        generateProgressBarVO.setId(apgUserGeneratedTaskDO.getId());
        return generateProgressBarVO;
    }
}
