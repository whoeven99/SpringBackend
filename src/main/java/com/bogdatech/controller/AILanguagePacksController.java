package com.bogdatech.controller;

import com.bogdatech.Service.IAILanguagePacksService;
import com.bogdatech.logic.AILanguagePackService;
import com.bogdatech.model.controller.request.UserLanguageRequest;
import com.bogdatech.model.controller.response.BaseResponse;
import com.bogdatech.utils.CharacterCountUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/aiLanguagePacks")
public class AILanguagePacksController {

    private final IAILanguagePacksService aiLanguagePacksService;
    private final AILanguagePackService aiLanguagePackService;

    @Autowired
    public AILanguagePacksController(IAILanguagePacksService aiLanguagePacksService, AILanguagePackService aiLanguagePackService) {
        this.aiLanguagePacksService = aiLanguagePacksService;
        this.aiLanguagePackService = aiLanguagePackService;
    }

    //获取AI语言包的数据
    @GetMapping("/readAILanguagePacks")
    public BaseResponse<Object> readAILanguagePacks() {
        return aiLanguagePacksService.readAILanguagePacks();
    }

    //默认新增默认语言包
    @PutMapping("/addDefaultLanguagePack")
    public void addDefaultLanguagePack(String shopName) {
        aiLanguagePacksService.addDefaultLanguagePack(shopName);
    }

    //切换语言包功能
    @PostMapping("/changeLanguagePack")
    public BaseResponse<Object> changeLanguagePack(@RequestBody UserLanguageRequest userLanguageRequest) {
        return aiLanguagePacksService.changeLanguagePack(userLanguageRequest);
    }

    //获取用户的beta_description，根据这个由混元生成类目
    @GetMapping("/getBetaDescription")
    public BaseResponse<Object> getBetaDescription(String shopName, String accessToken) {
        Boolean categoryByDescription = aiLanguagePackService.getCategoryByDescription(shopName, accessToken, new CharacterCountUtils());
        return new BaseResponse().CreateSuccessResponse(categoryByDescription);
    }
}
