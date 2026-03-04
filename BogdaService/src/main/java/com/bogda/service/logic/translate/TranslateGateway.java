package com.bogda.service.logic.translate;

import com.bogda.integration.aimodel.ChatGptIntegration;
import com.bogda.integration.aimodel.GoogleMachineIntegration;
import kotlin.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class TranslateGateway {
    public static final int GOOGLE_FLAG = 0;
    public static final int GPT_FLAG = 1;

    @Autowired
    private GoogleMachineIntegration googleMachineIntegration;
    @Autowired
    private ChatGptIntegration chatGptIntegration;

    // 根据需要选择不同的翻译服务
    public Pair<String, Integer> translate(String prompt, String targetLanguage, String privateKey, Integer translateModelFlag) {
        return translate(prompt, targetLanguage, privateKey, translateModelFlag, null, null);
    }

    public Pair<String, Integer> translate(String prompt, String targetLanguage, String privateKey,
                                           Integer translateModelFlag, String privateModel) {
        return translate(prompt, targetLanguage, privateKey, translateModelFlag, privateModel, null);
    }

    public Pair<String, Integer> translate(String prompt, String targetLanguage, String privateKey,
                                           Integer translateModelFlag, String privateModel, String taskClientKey) {
        if (privateKey == null || privateKey.isBlank()) {
            return null;
        }
        if (Integer.valueOf(GOOGLE_FLAG).equals(translateModelFlag)) {
            return googleMachineIntegration.googleTranslateWithSDK(prompt, targetLanguage, privateKey, taskClientKey);
        }
        if (Integer.valueOf(GPT_FLAG).equals(translateModelFlag)) {
            return chatGptIntegration.chatWithGptByApiKey(prompt, targetLanguage, privateKey, privateModel);
        }
        return null;
    }

    public Pair<String, Integer> translate(String prompt, String targetLanguage) {
        return translate(prompt, targetLanguage, null, null);
    }
}
