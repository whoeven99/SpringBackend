package com.bogdatech.controller;

import com.azure.security.keyvault.secrets.SecretClient;
import com.azure.security.keyvault.secrets.models.KeyVaultSecret;
import com.bogdatech.Service.ITranslatesService;
import com.bogdatech.Service.IUserPrivateService;
import com.bogdatech.logic.PrivateKeyService;
import com.bogdatech.model.controller.request.ClickTranslateRequest;
import com.bogdatech.model.controller.request.UserPrivateRequest;
import com.bogdatech.model.controller.response.BaseResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import static com.bogdatech.constants.UserPrivateConstants.GOOGLE;
import static com.bogdatech.integration.PrivateIntegration.googleTranslate;
import static com.bogdatech.utils.StringUtils.replaceDot;

@RestController
@RequestMapping("/privateKey")
public class PrivateKeyController {
    private final PrivateKeyService privateKeyService;
    private final SecretClient secretClient;
    private final IUserPrivateService userPrivateService;

    @Autowired
    public PrivateKeyController(PrivateKeyService privateKeyService, SecretClient secretClient, IUserPrivateService userPrivateService) {
        this.privateKeyService = privateKeyService;
        this.secretClient = secretClient;
        this.userPrivateService = userPrivateService;
    }

    /**
     * 用户通过私有key翻译
     * */
    @PutMapping("/translate")
    public BaseResponse<Object> translate(@RequestParam String shopName, @RequestBody ClickTranslateRequest clickTranslateRequest) {
        return privateKeyService.judgePrivateKey(shopName, clickTranslateRequest);
    }


    /**
     * 存用户的Google的apikey
     * */
    @PutMapping("/saveGoogleKey")
    public BaseResponse<Object> saveGoogleKey(@RequestBody UserPrivateRequest userPrivateRequest) {
        //调一次google接口， 用于判断key值是否有效
        try {
            googleTranslate("a",  userPrivateRequest.getSecret(), "zh-CN");
        } catch (Exception e) {
            return new BaseResponse<>().CreateErrorResponse("key_error");
        }

        String shopName = replaceDot(userPrivateRequest.getShopName());

        //存用户的shopName到数据库中
        //根据模型切换存储方法
        Integer i = null;
        String googleKey = shopName + "-" + GOOGLE;
//        appInsights.trackTrace("shopName: " + shopName);
        if (userPrivateRequest.getModel().equals(GOOGLE) && userPrivateRequest.getAmount()!=null && userPrivateRequest.getSecret() != null) {
            //新增或修改google相关方法
            i = userPrivateService.addOrUpdateGoogleUserData(userPrivateRequest.getShopName(), googleKey, userPrivateRequest.getAmount());
        }

        //存用户的key到微软服务器中
        if (i != null && i == 1) {
            KeyVaultSecret keyVaultSecret = secretClient.setSecret(googleKey, userPrivateRequest.getSecret());
            if (keyVaultSecret != null) {
                return new BaseResponse<>().CreateSuccessResponse(userPrivateRequest);
            }
        }


        return new BaseResponse<>().CreateErrorResponse("save_error");
    }


    
}
