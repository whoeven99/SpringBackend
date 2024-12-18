package com.bogdatech.logic;

import com.bogdatech.Service.IAILanguagePacksService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static com.bogdatech.utils.StringUtils.replaceLanguage;

@Component
public class AILanguagePackService {
    @Autowired
    private IAILanguagePacksService aiLanguagePacksService;

    //获取完整的promot
    public String getCompletePromot(String shopName, String target){
        Integer packId = aiLanguagePacksService.getPackIdByShopName(shopName);
        String promot = aiLanguagePacksService.getPromotByPackId(packId);
        return  replaceLanguage(promot, target) ;

    }


}
