package com.bogdatech.controller;

import com.bogdatech.entity.DO.UserPrivateTranslateDO;
import com.bogdatech.entity.VO.TestPrivateModelVO;
import com.bogdatech.logic.UserPrivateTranslateService;
import com.bogdatech.model.controller.response.BaseResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import static com.bogdatech.logic.PrivateKeyService.GOOGLE_MODEL;
import static com.bogdatech.logic.PrivateKeyService.OPENAI_MODEL;
import static com.bogdatech.utils.CaseSensitiveUtils.appInsights;
import static com.bogdatech.utils.UserPrivateUtils.maskString;

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
            appInsights.trackTrace(shopName + "apiKey : " + data.getApiKey());
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

    /**
     * 根据传传入的值，选择不同的模型测试是否是正常的
     * */
    @PostMapping("/testPrivateModel")
    public BaseResponse<Object> testPrivateModel(@RequestParam String shopName, @RequestBody TestPrivateModelVO data) {
        String s = userPrivateTranslateService.testPrivateModel(shopName, data.getApiName(), data.getSourceText(), data.getTargetCode(), data.getPrompt());
        if (s != null) {
            return new BaseResponse<>().CreateSuccessResponse(s);
        }
        return new BaseResponse<>().CreateErrorResponse(false);
    }

}
