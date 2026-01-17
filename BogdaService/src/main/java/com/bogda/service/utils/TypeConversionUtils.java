package com.bogda.service.utils;

import com.bogda.service.controller.request.ResourceTypeRequest;
import com.bogda.service.controller.request.ShopifyRequest;
import com.bogda.service.controller.request.TranslateRequest;
import com.bogda.service.entity.DO.APGOfficialTemplateDO;
import com.bogda.service.entity.DO.APGUserTemplateDO;
import com.bogda.service.entity.DTO.TemplateDTO;

public class TypeConversionUtils {

    public static ShopifyRequest convertTranslateRequestToShopifyRequest(TranslateRequest request) {
        ShopifyRequest shopifyRequest = new ShopifyRequest();
        shopifyRequest.setShopName(request.getShopName());
        shopifyRequest.setAccessToken(request.getAccessToken());
        shopifyRequest.setTarget(request.getTarget());
        return shopifyRequest;
    }

    public static ShopifyRequest resourceTypeRequestToShopifyRequest(ResourceTypeRequest request) {
        ShopifyRequest shopifyRequest = new ShopifyRequest();
        shopifyRequest.setShopName(request.getShopName());
        shopifyRequest.setAccessToken(request.getAccessToken());
        shopifyRequest.setTarget(request.getTarget());
        return shopifyRequest;
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
}
