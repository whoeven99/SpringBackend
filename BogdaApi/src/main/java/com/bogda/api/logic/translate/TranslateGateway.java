package com.bogda.api.logic.translate;

import com.bogda.api.integration.ChatGptIntegration;
import com.bogda.api.integration.GoogleMachineIntegration;
import kotlin.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class TranslateGateway {
    @Autowired
    private GoogleMachineIntegration googleMachineIntegration;
    @Autowired
    private ChatGptIntegration chatGptIntegration;

    // 根据需要选择不同的翻译服务
    public Pair<String, Integer> translate(String prompt, String targetLanguage, String privateKey, Integer translateModelFlag) {
        if (privateKey != null) {
            if (Integer.valueOf(0).equals(translateModelFlag)) {
                // google
            } else {
//                return chatGptIntegration.chatWithGpt(prompt, null, null, targetLanguage, privateKey);
            }
        }
        return null;
    }

    public Pair<String, Integer> translate(String prompt, String targetLanguage) {
        return translate(prompt, targetLanguage, null, null);
    }
}
