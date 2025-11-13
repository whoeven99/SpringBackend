package com.bogdatech.controller;

import com.bogdatech.Service.IAPGUsersService;
import com.bogdatech.entity.DO.APGUserTemplateDO;
import com.bogdatech.entity.DO.APGUserTemplateMappingDO;
import com.bogdatech.entity.DO.APGUsersDO;
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
    @Autowired
    private APGTemplateService apgTemplateService;
    @Autowired
    private IAPGUsersService iapgUsersService;


    /**
     * 获取默认数据和用户相关数据
     */
    @PostMapping("/getAllTemplateData")
    public BaseResponse<Object> getAllTemplateData(@RequestParam String shopName, @RequestBody TemplateDTO templateDTO) {
        List<TemplateDTO> allTemplateData = new ArrayList<>();
        //获取用户id
        APGUsersDO userDO = iapgUsersService.getUserByShopName(shopName);
        if (userDO == null) {
            return new BaseResponse<>().CreateErrorResponse("shopName not exist");
        }
        if (templateDTO.getTemplateClass()) {
            //获取用户模板
            List<TemplateDTO> allUserTemplateData = apgTemplateService.getAllUserTemplateData(userDO.getId(), templateDTO.getTemplateModel(), templateDTO.getTemplateSubtype(), templateDTO.getTemplateType());
            if (allUserTemplateData != null) {
                allTemplateData.addAll(allUserTemplateData);
            }
        } else {
            //先获取用户模板映射关系，获取官方的模板，获取用户个人的模板
            List<TemplateDTO> templateByShopName = apgTemplateService.getAllOfficialTemplateData(userDO.getId(), templateDTO.getTemplateModel(), templateDTO.getTemplateSubtype(), templateDTO.getTemplateType());
            if (templateByShopName != null) {
                allTemplateData.addAll(templateByShopName);
            }
        }

        if (!allTemplateData.isEmpty()) {
            return new BaseResponse<>().CreateSuccessResponse(allTemplateData);
        }
        return new BaseResponse<>().CreateErrorResponse(allTemplateData);
    }

    /**
     * 从映射表里面获取用户对应官方和用户模板
     */
    @PostMapping("/getTemplateByShopName")
    public BaseResponse<Object> getTemplateByShopName(@RequestParam String shopName) {
        List<TemplateDTO> templateByShopName = apgTemplateService.getTemplateByShopName(shopName);
        if (templateByShopName != null && !templateByShopName.isEmpty()) {
            return new BaseResponse<>().CreateSuccessResponse(templateByShopName);
        }
        return new BaseResponse<>().CreateErrorResponse(false);
    }

    /**
     * 用户创建自定义模板
     */
    @PostMapping("/createUserTemplate")
    public BaseResponse<Object> createUserTemplate(@RequestParam String shopName, @RequestBody APGUserTemplateDO apgUserTemplateDO) {
        Boolean result = apgTemplateService.createUserTemplate(shopName, apgUserTemplateDO);
        if (result) {
            return new BaseResponse<>().CreateSuccessResponse(true);
        }
        return new BaseResponse<>().CreateErrorResponse(false);
    }

    /**
     * 软删除用户模板数据
     */
    @PostMapping("/deleteUserTemplate")
    public BaseResponse<Object> deleteUserTemplate(@RequestParam String shopName, @RequestBody TemplateDTO templateDTO) {
        Boolean result = apgTemplateService.deleteUserTemplate(shopName, templateDTO);
        if (result) {
            return new BaseResponse<>().CreateSuccessResponse(true);
        }
        return new BaseResponse<>().CreateErrorResponse(false);
    }

    /**
     * 添加官方模板或用户模板到映射表，返回模板id
     */
    @PostMapping("/addOfficialOrUserTemplate")
    public BaseResponse<Object> addOfficialOrUserTemplate(@RequestParam String shopName, @RequestBody APGUserTemplateMappingDO apgUserTemplateMappingDO) {
        Boolean result = apgTemplateService.addOfficialOrUserTemplate(shopName, apgUserTemplateMappingDO.getTemplateId(), apgUserTemplateMappingDO.getTemplateType());
        if (result) {
            return new BaseResponse<>().CreateSuccessResponse(apgUserTemplateMappingDO.getTemplateId());
        }
        return new BaseResponse<>().CreateErrorResponse(apgUserTemplateMappingDO.getTemplateId());
    }

    /**
     * preview 方法，用于查看对应模板id下的实例数据
     */
    @GetMapping("/previewExampleDataByTemplateId")
    public BaseResponse<Object> previewExampleDataByTemplateId(@RequestParam String shopName, @RequestParam Long templateId) {
        String s = apgTemplateService.previewExampleDataByTemplateId(shopName, templateId);
        if (s != null) {
            return new BaseResponse<>().CreateSuccessResponse(s);
        } else {
            return new BaseResponse<>().CreateErrorResponse(false);
        }
    }

}
