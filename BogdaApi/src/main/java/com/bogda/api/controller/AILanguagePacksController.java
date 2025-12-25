package com.bogda.api.controller;

import com.bogda.common.service.IAILanguagePacksService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/aiLanguagePacks")
public class AILanguagePacksController {
    @Autowired
    private IAILanguagePacksService aiLanguagePacksService;

    // 默认新增默认语言包
    @PutMapping("/addDefaultLanguagePack")
    public void addDefaultLanguagePack(@RequestParam String shopName) {
        aiLanguagePacksService.addDefaultLanguagePack(shopName);
    }
}
