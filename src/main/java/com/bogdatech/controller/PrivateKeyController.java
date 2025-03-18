package com.bogdatech.controller;

import com.azure.security.keyvault.secrets.SecretClient;
import com.azure.security.keyvault.secrets.models.KeyVaultSecret;
import com.bogdatech.Service.ITranslatesService;
import com.bogdatech.Service.ITranslationCounterService;
import com.bogdatech.Service.IUserPrivateService;
import com.bogdatech.logic.PrivateKeyService;
import com.bogdatech.model.controller.request.ClickTranslateRequest;
import com.bogdatech.model.controller.request.UserPrivateRequest;
import com.bogdatech.model.controller.response.BaseResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import static com.bogdatech.constants.UserPrivateConstants.GOOGLE;

@RestController
@RequestMapping("/privateKey")
public class PrivateKeyController {
    private final PrivateKeyService privateKeyService;
    private final ITranslationCounterService translationCounterService;
    private final ITranslatesService translatesService;
    private final SecretClient secretClient;
    private final IUserPrivateService userPrivateService;

    @Autowired
    public PrivateKeyController(PrivateKeyService privateKeyService, ITranslationCounterService translationCounterService, ITranslatesService translatesService, SecretClient secretClient, IUserPrivateService userPrivateService) {
        this.privateKeyService = privateKeyService;
        this.translationCounterService = translationCounterService;
        this.translatesService = translatesService;
        this.secretClient = secretClient;
        this.userPrivateService = userPrivateService;
    }


    //用于测试google是否可以调用
    //后面再加上对openai的调用
    @PostMapping("/test")
    public void test(String text, String source, String apiKey, String target) {
        privateKeyService.test(text, source, apiKey, target);
    }

    //用户通过私有key翻译
    @PostMapping("/translate")
    public BaseResponse<Object> translate(@RequestBody ClickTranslateRequest clickTranslateRequest) {
        return privateKeyService.judgePrivateKey(clickTranslateRequest);
    }


    //存用户的Google的apikey
    @PutMapping("/saveGoogleKey")
    public BaseResponse<Object> saveGoogleKey(@RequestBody UserPrivateRequest userPrivateRequest) {
        //存用户的shopName到数据库中
        //根据模型切换存储方法
        Integer i = null;
        String googleKey = userPrivateRequest.getShopName() + "-" + GOOGLE;
        if (userPrivateRequest.getModel().equals("google") && userPrivateRequest.getAmount()!=null && userPrivateRequest.getSecret() != null) {
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
