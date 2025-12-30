package com.bogda.api.logic.translate.modelStragety;

import com.bogda.api.integration.ChatGptIntegration;
import kotlin.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;


@Component
public class Gpt4TranslateStrategyService implements ITranslateModelStrategyService{
    @Autowired
    private ChatGptIntegration chatGptIntegration;
    @Override
    public String getTranslateModel() {
        return ChatGptIntegration.GPT_4;
    }

    @Override
    public Pair<String, Integer> chooseModelTranslate(String prompt, String target) {
        return chatGptIntegration.chatWithGpt(prompt, target);
    }
}
