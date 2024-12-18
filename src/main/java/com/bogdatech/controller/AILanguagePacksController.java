package com.bogdatech.controller;

import com.bogdatech.Service.IAILanguagePacksService;
import com.bogdatech.Service.IUsersService;
import com.bogdatech.model.controller.request.UserLanguageRequest;
import com.bogdatech.model.controller.response.BaseResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/aiLanguagePacks")
public class AILanguagePacksController {

    @Autowired
    private IAILanguagePacksService aiLanguagePacksService;
    @Autowired
    private IUsersService usersService;
    //获取AI语言包的数据
    @GetMapping("/readAILanguagePacks")
    public BaseResponse<Object> readAILanguagePacks(){
        return aiLanguagePacksService.readAILanguagePacks();
    }

    //默认新增默认语言包
    @PutMapping("/addDefaultLanguagePack")
    public BaseResponse<Object> addDefaultLanguagePack(String shopName){
        return aiLanguagePacksService.addDefaultLanguagePack(shopName);
    }
    //切换语言包功能
    @PostMapping("/changeLanguagePack")
    public BaseResponse<Object> changeLanguagePack(@RequestBody UserLanguageRequest userLanguageRequest){
        return aiLanguagePacksService.changeLanguagePack(userLanguageRequest);
    }
}
