package com.bogda.api.controller;

import com.bogda.api.entity.DO.UserPrivateTranslateDO;
import com.bogda.api.logic.UserPrivateTranslateService;
import com.bogda.api.model.controller.response.BaseResponse;
import com.bogda.common.utils.AppInsightsUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;



@RestController
@RequestMapping("/private/translate")
public class UserPrivateTranslateController {
    @Autowired
    private UserPrivateTranslateService userPrivateTranslateService;

    /**
     * 配置用户私有模型
     * */
    @PostMapping("/configPrivateModel")
    public BaseResponse<Object> configPrivateModel(@RequestParam String shopName, @RequestBody UserPrivateTranslateDO data) {
        boolean flag;
        if (data.getApiKey() != null) {
            AppInsightsUtils.trackTrace("configPrivateModel " + shopName + " apiKey : " + data.getApiKey());
            flag = userPrivateTranslateService.configPrivateModel(shopName, data);
        }else {
            flag = userPrivateTranslateService.configPrivateModelExceptApiKey(shopName, data);
        }

        if (flag) {
            return new BaseResponse<>().CreateSuccessResponse(data);
        }else {
            return new BaseResponse<>().CreateErrorResponse(false);
        }
    }

    /**
     * 查询用户数据
     * */
    @PostMapping("/getUserPrivateData")
    public BaseResponse<Object> getUserPrivateData(@RequestParam String shopName, @RequestParam Integer apiName) {
        UserPrivateTranslateDO userPrivateData = userPrivateTranslateService.getUserPrivateData(shopName, apiName);
        if (userPrivateData != null) {
            return new BaseResponse<>().CreateSuccessResponse(userPrivateData);
        }
        return new BaseResponse<>().CreateErrorResponse(false);
    }


}
