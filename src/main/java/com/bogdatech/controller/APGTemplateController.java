package com.bogdatech.controller;

import com.bogdatech.Service.IAPGTemplateService;
import com.bogdatech.entity.DO.APGTemplateDO;
import com.bogdatech.entity.DO.APGUserTemplateDO;
import com.bogdatech.entity.DTO.TemplateDTO;
import com.bogdatech.logic.APGTemplateService;
import com.bogdatech.model.controller.response.BaseResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
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
    public BaseResponse<Object> getAllTemplateData(@RequestParam String shopName, @RequestBody TemplateDTO templateDTO){
        List<TemplateDTO> allTemplateData = new ArrayList<>();
        //先获取用户模板映射关系，获取官方的模板，获取用户个人的模板
        List<TemplateDTO> templateByShopName = apgTemplateService.getAllOfficialTemplateData(templateDTO.getTemplateModel(), templateDTO.getTemplateSubtype());
        if (templateByShopName != null){
            allTemplateData.addAll(templateByShopName);
        }

        //获取用户模板
        List<TemplateDTO> allUserTemplateData = apgTemplateService.getAllUserTemplateData(shopName, templateDTO.getTemplateModel(), templateDTO.getTemplateSubtype());
        if (allUserTemplateData != null){
            allTemplateData.addAll(allUserTemplateData);
        }

        if (!allTemplateData.isEmpty()){
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

    /**
     * 用户创建自定义模板
     * */
    @PostMapping("/createUserTemplate")
    public BaseResponse<Object> createUserTemplate(@RequestParam String shopName, @RequestBody APGUserTemplateDO apgUserTemplateDO){
        Boolean result = apgTemplateService.createUserTemplate(shopName, apgUserTemplateDO);
        if (result){
            return new BaseResponse<>().CreateSuccessResponse(true);
        }
        return new BaseResponse<>().CreateErrorResponse(false);
    }

    /**
     * 软删除用户模板数据
     * */
    @PostMapping("/deleteUserTemplate")
    public BaseResponse<Object> deleteUserTemplate(@RequestParam String shopName, @RequestBody TemplateDTO templateDTO){
        Boolean result = apgTemplateService.deleteUserTemplate(shopName, templateDTO);
        if (result){
            return new BaseResponse<>().CreateSuccessResponse(true);
        }
        return new BaseResponse<>().CreateErrorResponse(false);
    }
}
