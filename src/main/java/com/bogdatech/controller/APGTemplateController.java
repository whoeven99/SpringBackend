package com.bogdatech.controller;

import com.bogdatech.Service.IAPGTemplateService;
import com.bogdatech.entity.DO.APGTemplateDO;
import com.bogdatech.entity.DTO.TemplateDTO;
import com.bogdatech.logic.APGTemplateService;
import com.bogdatech.model.controller.response.BaseResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/apg/template")
public class APGTemplateController {
    private final IAPGTemplateService iapgTemplateService;
    private final APGTemplateService apgTemplateService;

    @Autowired
    public APGTemplateController(IAPGTemplateService iapgTemplateService, APGTemplateService apgTemplateService) {
        this.iapgTemplateService = iapgTemplateService;
        this.apgTemplateService = apgTemplateService;
    }

    /**
     * 获取默认数据和用户相关数据
     * */
    @PostMapping("/getAllTemplateData")
    public BaseResponse<Object> getAllTemplateData(@RequestParam String shopName){
        List<APGTemplateDO> allTemplateData;
        allTemplateData = iapgTemplateService.getAllTemplateData(shopName);
        if (allTemplateData != null && !allTemplateData.isEmpty()){
            return new BaseResponse<>().CreateSuccessResponse(allTemplateData);
        }
        return new BaseResponse<>().CreateErrorResponse(allTemplateData);
    }

    /**
     * 从映射表里面获取用户对应模板
     * */
    @PostMapping("/getTemplateByShopName")
    public BaseResponse<Object> getTemplateByShopName(@RequestParam String shopName){
        List<TemplateDTO> templateByShopName = apgTemplateService.getTemplateByShopName(shopName);
        if (templateByShopName != null && !templateByShopName.isEmpty()){
            return new BaseResponse<>().CreateSuccessResponse(templateByShopName);
        }
        return new BaseResponse<>().CreateErrorResponse(false);
    }
}
