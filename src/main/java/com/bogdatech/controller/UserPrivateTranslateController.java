package com.bogdatech.controller;

import com.bogdatech.entity.DO.UserPrivateTranslateDO;
import com.bogdatech.logic.UserPrivateTranslateService;
import com.bogdatech.model.controller.response.BaseResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import static com.bogdatech.utils.CaseSensitiveUtils.appInsights;

@RestController
@RequestMapping("/private/translate")
public class UserPrivateTranslateController {
    private final UserPrivateTranslateService userPrivateTranslateService;

    @Autowired
    public UserPrivateTranslateController(UserPrivateTranslateService userPrivateTranslateService) {
        this.userPrivateTranslateService = userPrivateTranslateService;
    }

    /**
     * 配置用户私有模型
     * */
    @PostMapping("/configPrivateModel")
    public BaseResponse<Object> configPrivateModel(@RequestParam String shopName, @RequestBody UserPrivateTranslateDO data) {
        boolean flag;
        if (data.getApiKey() != null) {
            appInsights.trackTrace("configPrivateModel " + shopName + " apiKey : " + data.getApiKey());
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
