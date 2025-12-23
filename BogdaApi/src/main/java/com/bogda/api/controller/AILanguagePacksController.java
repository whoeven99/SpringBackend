package com.bogda.api.controller;

import com.bogda.api.Service.IAILanguagePacksService;
import com.bogda.api.model.controller.request.UserLanguageRequest;
import com.bogda.api.model.controller.response.BaseResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/aiLanguagePacks")
public class AILanguagePacksController {
    @Autowired
    private IAILanguagePacksService aiLanguagePacksService;

    //默认新增默认语言包
    @PutMapping("/addDefaultLanguagePack")
    public void addDefaultLanguagePack(@RequestParam String shopName) {
        aiLanguagePacksService.addDefaultLanguagePack(shopName);
    }
}
