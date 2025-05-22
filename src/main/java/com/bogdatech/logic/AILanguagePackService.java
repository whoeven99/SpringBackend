package com.bogdatech.logic;

import com.bogdatech.entity.DO.AILanguagePacksDO;
import org.springframework.stereotype.Component;

import static com.bogdatech.utils.StringUtils.replaceLanguage;

@Component
public class AILanguagePackService {


    //获取完整的prompt
    public String getCompletePrompt(AILanguagePacksDO aiLanguagePacksDO, String translateResourceType, String target){
        //        System.out.println("prompt: " + prompt);
    return replaceLanguage(aiLanguagePacksDO.getPromotWord(), target, translateResourceType, aiLanguagePacksDO.getPackName());
    }


}
