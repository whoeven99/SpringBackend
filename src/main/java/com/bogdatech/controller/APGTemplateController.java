package com.bogdatech.controller;

import com.bogdatech.Service.IAPGTemplateService;
import com.bogdatech.entity.DO.APGTemplateDO;
import com.bogdatech.model.controller.response.BaseResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/apg/template")
public class APGTemplateController {
    private final IAPGTemplateService iapgTemplateService;

    @Autowired
    public APGTemplateController(IAPGTemplateService iapgTemplateService) {
        this.iapgTemplateService = iapgTemplateService;
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
}
