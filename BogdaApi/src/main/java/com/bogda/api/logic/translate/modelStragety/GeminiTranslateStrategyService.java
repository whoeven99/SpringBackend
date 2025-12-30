package com.bogda.api.logic.translate.modelStragety;

import com.bogda.api.integration.GeminiIntegration;
import kotlin.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class GeminiTranslateStrategyService implements ITranslateModelStrategyService{
    @Autowired
    private GeminiIntegration geminiIntegration;

    @Override
    public String getTranslateModel() {
        return GeminiIntegration.Gemini_3_FLASH;
    }

    @Override
    public Pair<String, Integer> chooseModelTranslate(String prompt, String target) {
        String model = GeminiIntegration.Gemini_3_FLASH;
        return geminiIntegration.generateText(model, prompt);
    }
}
