package com.bogda.api.logic.translate.modelStragety;

import com.bogda.api.integration.ALiYunTranslateIntegration;
import kotlin.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ALiYunTranslateStrategyService implements ITranslateModelStrategyService{
    @Autowired
    private ALiYunTranslateIntegration aLiYunTranslateIntegration;

    @Override
    public String getTranslateModel() {
        return ALiYunTranslateIntegration.QWEN_MAX;
    }

    @Override
    public Pair<String, Integer> chooseModelTranslate(String prompt, String target) {
        return aLiYunTranslateIntegration.userTranslate(prompt, target);
    }
}
