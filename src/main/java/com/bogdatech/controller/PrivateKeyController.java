package com.bogdatech.controller;

import com.bogdatech.Service.ITranslatesService;
import com.bogdatech.Service.ITranslationCounterService;
import com.bogdatech.logic.PrivateKeyService;
import com.bogdatech.model.controller.request.ClickTranslateRequest;
import com.bogdatech.model.controller.response.BaseResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/privateKey")
public class PrivateKeyController {
    private final PrivateKeyService privateKeyService;
    private final ITranslationCounterService translationCounterService;
    private final ITranslatesService translatesService;
    @Autowired
    public PrivateKeyController(PrivateKeyService privateKeyService, ITranslationCounterService translationCounterService, ITranslatesService translatesService) {
        this.privateKeyService = privateKeyService;
        this.translationCounterService = translationCounterService;
        this.translatesService = translatesService;
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
}
