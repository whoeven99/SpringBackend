package com.bogdatech.controller;

import com.bogdatech.Service.IAILanguagePacksService;
import com.bogdatech.model.controller.response.BaseResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/aiLanguagePacks")
public class AILanguagePacksController {

    @Autowired
    private IAILanguagePacksService aiLanguagePacksService;
    //获取AI语言包的数据
    @GetMapping("/readAILanguagePacks")
    public BaseResponse<Object> readAILanguagePacks(){
        return aiLanguagePacksService.readAILanguagePacks();
    }
}
