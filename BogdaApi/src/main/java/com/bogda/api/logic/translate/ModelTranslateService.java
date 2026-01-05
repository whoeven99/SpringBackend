package com.bogda.api.logic.translate;

import com.bogda.api.integration.ALiYunTranslateIntegration;
import com.bogda.api.integration.ChatGptIntegration;
import com.bogda.api.integration.GeminiIntegration;
import com.bogda.api.utils.CaseSensitiveUtils;
import kotlin.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ModelTranslateService {
    @Autowired
    private ALiYunTranslateIntegration aLiYunTranslateIntegration;
    @Autowired
    private GeminiIntegration geminiIntegration;
    @Autowired
    private ChatGptIntegration chatGptIntegration;

    public Pair<String, Integer> modelTranslate(String aiModel, String prompt, String target) {
        Pair<String, Integer> pair = null;
        if (ALiYunTranslateIntegration.QWEN_MAX.equals(aiModel)){
            pair =  aLiYunTranslateIntegration.userTranslate(prompt, target);
        }else if (ChatGptIntegration.GPT_4.equals(aiModel)){
            pair =  chatGptIntegration.chatWithGpt(prompt, target);
        }else if (GeminiIntegration.Gemini_3_FLASH.equals(aiModel)){
            pair =  geminiIntegration.generateText(prompt, target);
        }

        if (pair == null){
            // 做一个保底处理，当pair为null的时候，用qwen-max再翻译一次，如果再为null，就直接返回
            CaseSensitiveUtils.appInsights.trackTrace("FatalException  " + aiModel
                    + " 翻译失败， 数据如下，用qwen翻译 : " + prompt);
            return aLiYunTranslateIntegration.userTranslate(prompt, target);
        }

        return pair;
    }
}
